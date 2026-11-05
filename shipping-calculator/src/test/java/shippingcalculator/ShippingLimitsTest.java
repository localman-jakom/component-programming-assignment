package shippingcalculator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Boundary tests on both sides of every limit: the absolute item limits (26,500 kg, 1,360 cm)
// and the tighter limits of each transport mode.
class ShippingLimitsTest {

    private final UnitNormaliser normaliser = new UnitNormaliser();

    @ParameterizedTest(name = "{0} kg is accepted")
    @CsvSource({"0.001", "70", "26499.99", "26500", "26500.0000001"})
    void weightsUpToTheLimitAreAccepted(double kg) {
        assertDoesNotThrow(() -> new PackageDetails(kg, 10, 10, 10));
    }

    @ParameterizedTest(name = "{0} kg is rejected")
    @CsvSource({"26500.01", "30000"})
    void weightsOverTheLimitAreRejected(double kg) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new PackageDetails(kg, 10, 10, 10));
        assertTrue(e.getMessage().contains("26,500 kg limit"), e.getMessage());
    }

    @ParameterizedTest(name = "side of {0} cm is accepted")
    @CsvSource({"151", "1359.99", "1360.0"})
    void sidesUpToTheLimitAreAccepted(double cm) {
        assertDoesNotThrow(() -> new PackageDetails(1, cm, 10, 10));
    }

    @ParameterizedTest(name = "{0} of {1} cm is rejected")
    @CsvSource({
            "Length, 1360.01",
            "Width,  1360.01",
            "Height, 1400"
    })
    void eachSideIsChecked(String side, double cm) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new PackageDetails(1,
                side.equals("Length") ? cm : 10, side.equals("Width") ? cm : 10, side.equals("Height") ? cm : 10));
        assertTrue(e.getMessage().startsWith(side), e.getMessage());
        assertTrue(e.getMessage().contains("1,360 cm limit"), e.getMessage());
    }

    @ParameterizedTest(name = "{0}: {1} kg fits, {2} kg does not")
    @CsvSource({
            "COURIER, 70,    70.01",
            "AIR,     1000,  1000.01",
            "ROAD,    24000, 24000.01"
            // SEA (26,500 kg) is also the absolute limit, tested above
    })
    void weightLimitPerMode(String mode, double fits, double tooHeavy) {
        TransportMode m = TransportMode.valueOf(mode);
        assertNull(m.whyCannotCarry(new PackageDetails(fits, 10, 10, 10)));
        assertTrue(m.whyCannotCarry(new PackageDetails(tooHeavy, 10, 10, 10)).contains("takes items up to"));
    }

    @ParameterizedTest(name = "{0}: {1} x {2} x {3} cm fits = {4}")
    @CsvSource({
            "COURIER, 150,  150, 150, true",
            "COURIER, 151,  10,  10,  false",
            "AIR,     300,  240, 160, true",
            "AIR,     160,  300, 240, true",     // any orientation is tried
            "AIR,     301,  10,  10,  false",
            "AIR,     250,  250, 250, false",
            "ROAD,    1360, 270, 245, true",
            // ROAD's 1,360 cm is also the absolute limit, tested above
            "ROAD,    300,  271, 100, false",
            "SEA,     1200, 239, 235, true",
            "SEA,     1201, 10,  10,  false",
            "SEA,     240,  240, 240, false"
    })
    void sizeLimitPerMode(String mode, double l, double w, double h, boolean fits) {
        String reason = TransportMode.valueOf(mode).whyCannotCarry(new PackageDetails(1, l, w, h));
        assertEquals(fits, reason == null, String.valueOf(reason));
    }

    @Test
    @DisplayName("Limits apply after unit conversion: lb, tonne and inch")
    void limitsAfterConversion() {
        assertNull(TransportMode.COURIER.whyCannotCarry(
                new PackageDetails(normaliser.toKilograms(new Measurement(154, "lb")), 10, 10, 10)));
        assertTrue(TransportMode.COURIER.whyCannotCarry(
                new PackageDetails(normaliser.toKilograms(new Measurement(155, "lb")), 10, 10, 10)) != null);
        assertDoesNotThrow(() -> ShippingLimits.checkWeightKg(normaliser.toKilograms(new Measurement(26.5, "tonne"))));
        assertThrows(IllegalArgumentException.class,
                () -> ShippingLimits.checkWeightKg(normaliser.toKilograms(new Measurement(58423, "lb"))));
        assertDoesNotThrow(() -> ShippingLimits.checkSideCm("Length",
                normaliser.toCentimetres(new Measurement(535, "inch"))));
        assertThrows(IllegalArgumentException.class, () -> ShippingLimits.checkSideCm("Length",
                normaliser.toCentimetres(new Measurement(536, "inch"))));
        assertDoesNotThrow(() -> ShippingLimits.checkSideCm("Length",
                normaliser.toCentimetres(new Measurement(13.6, "m"))));
    }

    @Test
    @DisplayName("A shipment holds 1 to 10 items")
    void parcelCount() {
        PackageDetails p = new PackageDetails(1, 10, 10, 10);
        assertDoesNotThrow(() -> new Shipment(Collections.nCopies(10, p), false, false));
        assertThrows(IllegalArgumentException.class, () -> new Shipment(Collections.nCopies(11, p), false, false));
        assertThrows(IllegalArgumentException.class, () -> new Shipment(Collections.emptyList(), false, false));
        assertThrows(IllegalArgumentException.class, () -> new Shipment(null, false, false));
    }

    @Test
    @DisplayName("The limits summary lists every transport mode")
    void summary() {
        String text = ShippingLimits.summary();
        assertTrue(text.contains("Courier: up to 70 kg, 150 x 150 x 150 cm"), text);
        assertTrue(text.contains("Sea freight: up to 26,500 kg, 1,200 x 239 x 235 cm"), text);
        assertEquals(TransportMode.SEA, TransportMode.fromCode(" sea "));
    }
}
