package unitconverter;

import unitconverter.exceptions.InvalidValueException;

// Numeric sanity checks and boundary validations
final class ValueValidator {

    // Small epsilon to accommodate floating-point inaccuracies near zero boundaries
    static final double TOLERANCE = 1e-9;

    private ValueValidator() {
    }

    // Rejects non-numeric and unbounded values
    static void checkFinite(double value) {
        if (Double.isNaN(value)) {
            throw new InvalidValueException("Value is NaN (not a number)");
        }
        if (Double.isInfinite(value)) {
            throw new InvalidValueException("Value is infinite");
        }
    }

    // Rejects temperatures strictly below absolute zero in kelvin
    static void checkAboveAbsoluteZero(double kelvin) {
        if (kelvin < -TOLERANCE) {
            throw new InvalidValueException(
                    "Temperature is below absolute zero (0 K)");
        }
    }
}