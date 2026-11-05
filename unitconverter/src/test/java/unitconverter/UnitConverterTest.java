
package unitconverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import unitconverter.exceptions.IncompatibleUnitException;
import unitconverter.exceptions.InvalidValueException;
import unitconverter.exceptions.UnitConverterException;
import unitconverter.exceptions.UnknownUnitException;

// Automated unit tests verifying conversion accuracy, boundaries, round trips, and exceptions
class UnitConverterTest {

    private static final double KNOWN_DELTA = 1e-4;
    private static final double TIGHT_DELTA = 1e-9;

    private UnitConverter converter;

    @BeforeEach
    void setUp() {
        converter = new UnitConverter();
    }

    // Compares numbers with relative tolerance for very large or tiny magnitudes
    private static void assertClose(double expected, double actual, double relativeTolerance) {
        assertEquals(expected, actual, Math.abs(expected) * relativeTolerance);
    }

    // 1. KNOWN VALUES
    @Nested
    @DisplayName("Known values")
    class KnownValues {

        @Test
        @DisplayName("0 C = 32 F (freezing point of water)")
        void freezingPointCelsiusToFahrenheit() {
            assertEquals(32.0, converter.convert(0, "C", "F"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("100 C = 212 F (boiling point of water)")
        void boilingPointCelsiusToFahrenheit() {
            assertEquals(212.0, converter.convert(100, "C", "F"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("-40 C = -40 F (the scales meet)")
        void minusFortyIsSameInBothScales() {
            assertEquals(-40.0, converter.convert(-40, "C", "F"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("0 C = 273.15 K")
        void celsiusToKelvin() {
            assertEquals(273.15, converter.convert(0, "C", "K"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("212 F = 100 C")
        void fahrenheitToCelsius() {
            assertEquals(100.0, converter.convert(212, "F", "C"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("1 kg = 2.20462 lb")
        void kilogramToPound() {
            assertEquals(2.20462, converter.convert(1, "kg", "lb"), KNOWN_DELTA);
        }

        @Test
        @DisplayName("16 oz = 1 lb")
        void sixteenOuncesIsOnePound() {
            assertEquals(1.0, converter.convert(16, "oz", "lb"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("1000 g = 1 kg")
        void gramsToKilograms() {
            assertEquals(1.0, converter.convert(1000, "g", "kg"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("1 inch = 2.54 cm")
        void inchToCentimetre() {
            assertEquals(2.54, converter.convert(1, "inch", "cm"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("1 foot = 12 inches")
        void footToInches() {
            assertEquals(12.0, converter.convert(1, "foot", "inch"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("1 mile = 1609.344 m")
        void mileToMetres() {
            assertEquals(1609.344, converter.convert(1, "mile", "m"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("1 US gallon = 3.785411784 l")
        void gallonToLitres() {
            assertEquals(3.785411784, converter.convert(1, "gallon", "l"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("1 m3 = 1000 l")
        void cubicMetreToLitres() {
            assertEquals(1000.0, converter.convert(1, "m3", "l"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("36 km/h = 10 m/s")
        void kmhToMetresPerSecond() {
            assertEquals(10.0, converter.convert(36, "km/h", "m/s"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("1 knot = 1.852 km/h")
        void knotToKmh() {
            assertEquals(1.852, converter.convert(1, "knot", "km/h"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("Unit names ignore case, spaces and accept aliases")
        void lookupIsCaseInsensitiveAndAcceptsAliases() {
            double expected = converter.convert(1, "kg", "lb");
            assertEquals(expected, converter.convert(1, "KG", "LB"), TIGHT_DELTA);
            assertEquals(expected, converter.convert(1, "  kg ", "pounds"), TIGHT_DELTA);
            assertEquals(expected, converter.convert(1, "Kilogram", "lbs"), TIGHT_DELTA);
            assertEquals(12.0, converter.convert(1, "ft", "inches"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("Converting a unit to itself returns the same value")
        void sameUnitReturnsSameValue() {
            assertEquals(5.0, converter.convert(5, "m", "m"), TIGHT_DELTA);
            assertEquals(25.0, converter.convert(25, "C", "C"), TIGHT_DELTA);
        }
    }

    // 2. ROUND TRIPS
    @Nested
    @DisplayName("Round-trip conversions")
    class RoundTrips {

        // Validates round-trip conversion for every ordered pair of units in a category
        @ParameterizedTest(name = "every unit pair in {0}")
        @EnumSource(Category.class)
        void everyPairRoundTrips(Category category) {
            // 300 is valid across all scales including temperature above absolute zero
            double[] samples = {300.0, 0.0, 123.456};
            List<String> units = converter.getSupportedUnits(category);

            for (String a : units) {
                for (String b : units) {
                    for (double v : samples) {
                        if (category == Category.TEMPERATURE && v != 300.0) {
                            continue; // Avoid inputs that may fall below absolute zero
                        }
                        double there = converter.convert(v, a, b);
                        double back = converter.convert(there, b, a);
                        assertEquals(v, back, TIGHT_DELTA,
                                v + " " + a + " -> " + b + " -> " + a);
                    }
                }
            }
        }

        @ParameterizedTest(name = "{0} {1} -> {2} -> {1}")
        @CsvSource({
                "72.5, kg, lb",
                "5.5,  foot, cm",
                "98.6, F, C",
                "20,   C, K",
                "3.7,  gallon, ml",
                "60,   mph, km/h"
        })
        void typicalRoundTrips(double value, String from, String to) {
            double back = converter.convert(converter.convert(value, from, to), to, from);
            assertEquals(value, back, TIGHT_DELTA);
        }
    }

    // 3. ZERO, NEGATIVE AND EXTREME VALUES
    @Nested
    @DisplayName("Zero, negative and extreme values")
    class ExtremeValues {

        @Test
        @DisplayName("Zero stays zero for scaled units")
        void zeroStaysZero() {
            assertEquals(0.0, converter.convert(0, "kg", "lb"), TIGHT_DELTA);
            assertEquals(0.0, converter.convert(0, "m", "mile"), TIGHT_DELTA);
            assertEquals(0.0, converter.convert(0, "mph", "km/h"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("Negative values convert correctly (e.g. a displacement)")
        void negativeValuesWork() {
            assertEquals(-500.0, converter.convert(-5, "m", "cm"), TIGHT_DELTA);
            assertEquals(-10.0, converter.convert(-36, "km/h", "m/s"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("Negative temperatures above absolute zero are fine")
        void negativeTemperaturesWork() {
            assertEquals(-40.0, converter.convert(-40, "F", "C"), TIGHT_DELTA);
            assertEquals(233.15, converter.convert(-40, "C", "K"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("Very large values keep their relative accuracy")
        void veryLargeValues() {
            assertClose(1e18, converter.convert(1e15, "km", "m"), 1e-12);
            assertClose(1e305, converter.convert(1e308, "m", "km"), 1e-12);
        }

        @Test
        @DisplayName("Very small values keep their relative accuracy")
        void verySmallValues() {
            assertClose(1e-297, converter.convert(1e-300, "m", "mm"), 1e-12);
        }

        @Test
        @DisplayName("Absolute zero is allowed in every scale")
        void absoluteZeroIsAllowed() {
            assertEquals(0.0, converter.convert(0, "K", "K"), TIGHT_DELTA);
            assertEquals(0.0, converter.convert(-273.15, "C", "K"), TIGHT_DELTA);
            assertEquals(0.0, converter.convert(-459.67, "F", "K"), TIGHT_DELTA);
            assertEquals(-273.15, converter.convert(0, "K", "C"), TIGHT_DELTA);
        }

        @Test
        @DisplayName("Tiny rounding error just below 0 K is tolerated")
        void toleranceNearAbsoluteZero() {
            assertEquals(-273.15, converter.convert(-1e-12, "K", "C"), TIGHT_DELTA);
        }
    }

    // 4. EXCEPTIONS
    @Nested
    @DisplayName("Exceptions")
    class Exceptions {

        // UnknownUnitException tests
        @Test
        @DisplayName("Unknown 'from' unit")
        void unknownFromUnit() {
            UnknownUnitException e = assertThrows(UnknownUnitException.class,
                    () -> converter.convert(1, "furlongz", "m"));
            assertEquals("furlongz", e.getUnitName());
        }

        @Test
        @DisplayName("Unknown 'to' unit")
        void unknownToUnit() {
            UnknownUnitException e = assertThrows(UnknownUnitException.class,
                    () -> converter.convert(1, "m", "parsec"));
            assertEquals("parsec", e.getUnitName());
        }

        @Test
        @DisplayName("Null unit name")
        void nullUnit() {
            assertThrows(UnknownUnitException.class, () -> converter.convert(1, null, "m"));
            assertThrows(UnknownUnitException.class, () -> converter.convert(1, "m", null));
        }

        @Test
        @DisplayName("Empty and blank unit names")
        void emptyUnit() {
            assertThrows(UnknownUnitException.class, () -> converter.convert(1, "", "m"));
            assertThrows(UnknownUnitException.class, () -> converter.convert(1, "m", "   "));
        }

        // IncompatibleUnitException tests
        @Test
        @DisplayName("kg to metres is incompatible")
        void massToLength() {
            IncompatibleUnitException e = assertThrows(IncompatibleUnitException.class,
                    () -> converter.convert(1, "kg", "m"));
            assertTrue(e.getMessage().contains("MASS"));
            assertTrue(e.getMessage().contains("LENGTH"));
        }

        @ParameterizedTest(name = "{0} -> {1} is incompatible")
        @CsvSource({
                "C,   m",
                "l,   kg",
                "mph, l",
                "m,   km/h",
                "K,   g"
        })
        void otherCategoryMixes(String from, String to) {
            assertThrows(IncompatibleUnitException.class,
                    () -> converter.convert(1, from, to));
        }

        // InvalidValueException tests
        @Test
        @DisplayName("NaN is rejected")
        void nanRejected() {
            assertThrows(InvalidValueException.class,
                    () -> converter.convert(Double.NaN, "m", "cm"));
        }

        @Test
        @DisplayName("Positive and negative infinity are rejected")
        void infinityRejected() {
            assertThrows(InvalidValueException.class,
                    () -> converter.convert(Double.POSITIVE_INFINITY, "m", "cm"));
            assertThrows(InvalidValueException.class,
                    () -> converter.convert(Double.NEGATIVE_INFINITY, "kg", "lb"));
        }

        @ParameterizedTest(name = "{0} {1} is below absolute zero")
        @CsvSource({
                "-0.001,  K",
                "-1,      K",
                "-273.16, C",
                "-459.68, F",
                "-1000,   C"
        })
        void belowAbsoluteZeroRejected(double value, String unit) {
            assertThrows(InvalidValueException.class,
                    () -> converter.convert(value, unit, "K"));
        }

        @Test
        @DisplayName("Result overflow is rejected (1e308 km -> mm)")
        void overflowRejected() {
            assertThrows(InvalidValueException.class,
                    () -> converter.convert(1e308, "km", "mm"));
        }

        // Base exception polymorphic catch tests
        @Test
        @DisplayName("All library errors can be caught with the base exception")
        void allExtendBaseException() {
            assertThrows(UnitConverterException.class, () -> converter.convert(1, "x", "m"));
            assertThrows(UnitConverterException.class, () -> converter.convert(1, "kg", "m"));
            assertThrows(UnitConverterException.class,
                    () -> converter.convert(Double.NaN, "m", "cm"));
        }
    }

    // 5. DISCOVERY METHODS
    @Nested
    @DisplayName("getCategories / getSupportedUnits")
    class Discovery {

        @Test
        @DisplayName("All five categories are listed")
        void categories() {
            assertEquals(List.of(Category.LENGTH, Category.MASS, Category.TEMPERATURE,
                    Category.VOLUME, Category.SPEED), converter.getCategories());
        }

        @Test
        @DisplayName("Category list cannot be modified")
        void categoriesUnmodifiable() {
            assertThrows(UnsupportedOperationException.class,
                    () -> converter.getCategories().add(Category.MASS));
        }

        @ParameterizedTest(name = "{0} has {1} units")
        @CsvSource({
                "LENGTH,      8",
                "MASS,        6",
                "TEMPERATURE, 3",
                "VOLUME,      5",
                "SPEED,       4"
        })
        void unitCounts(Category category, int expectedCount) {
            assertEquals(expectedCount, converter.getSupportedUnits(category).size());
        }

        @Test
        @DisplayName("Mass units include the ones the shipping client needs")
        void massUnitsContent() {
            List<String> mass = converter.getSupportedUnits(Category.MASS);
            assertTrue(mass.containsAll(List.of("kg", "g", "lb", "oz")));
            assertFalse(mass.contains("m"));
        }

        @Test
        @DisplayName("Length units include the ones the shipping client needs")
        void lengthUnitsContent() {
            assertTrue(converter.getSupportedUnits(Category.LENGTH)
                    .containsAll(List.of("cm", "m", "inch", "foot")));
        }

        @Test
        @DisplayName("Unit list cannot be modified")
        void unitsUnmodifiable() {
            assertThrows(UnsupportedOperationException.class,
                    () -> converter.getSupportedUnits(Category.LENGTH).add("furlong"));
        }

        @Test
        @DisplayName("Null category is rejected")
        void nullCategory() {
            assertThrows(NullPointerException.class, () -> converter.getSupportedUnits(null));
        }
    }
}