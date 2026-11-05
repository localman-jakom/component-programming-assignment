package shippingcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import unitconverter.exceptions.IncompatibleUnitException;
import unitconverter.exceptions.UnknownUnitException;

// Proves the client gets its conversions from the library jar
// (these tests only pass if unitconverter-1.0.jar is on the classpath).
class UnitNormaliserTest {

    private static final double DELTA = 1e-9;
    private final UnitNormaliser normaliser = new UnitNormaliser();

    @ParameterizedTest(name = "{0} {1} = {2} kg")
    @CsvSource({
            "1,    kg,  1.0",
            "500,  g,   0.5",
            "10,   lb,  4.5359237",
            "16,   oz,  0.45359237",
            "2,    lbs, 0.90718474",
            "1.5,  tonne, 1500.0"
    })
    void weightsBecomeKilograms(double value, String unit, double expectedKg) {
        assertEquals(expectedKg, normaliser.toKilograms(new Measurement(value, unit)), DELTA);
    }

    @ParameterizedTest(name = "{0} {1} = {2} cm")
    @CsvSource({
            "40,   cm,   40.0",
            "1,    m,    100.0",
            "10,   inch, 25.4",
            "2,    foot, 60.96",
            "1,    ft,   30.48"
    })
    void lengthsBecomeCentimetres(double value, String unit, double expectedCm) {
        assertEquals(expectedCm, normaliser.toCentimetres(new Measurement(value, unit)), DELTA);
    }

    @Test
    @DisplayName("A length unit typed for weight is rejected by the library")
    void wrongKindOfUnit() {
        assertThrows(IncompatibleUnitException.class,
                () -> normaliser.toKilograms(new Measurement(5, "cm")));
    }

    @Test
    @DisplayName("A made-up unit is rejected by the library")
    void unknownUnit() {
        assertThrows(UnknownUnitException.class,
                () -> normaliser.toCentimetres(new Measurement(5, "furlongz")));
    }
}
