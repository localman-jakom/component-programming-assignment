package unitconverter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import unitconverter.exceptions.IncompatibleUnitException;
import unitconverter.exceptions.InvalidValueException;
import unitconverter.exceptions.UnknownUnitException;

// Converts values between physical units of measurement across supported categories
public final class UnitConverter {

    private static final UnitRegistry REGISTRY = new UnitRegistry();

    public UnitConverter() {
    }

    // Converts a value from one unit to another (case-insensitive)
    public double convert(double value, String fromUnit, String toUnit) {
        ValueValidator.checkFinite(value);

        Unit from = REGISTRY.find(fromUnit);
        if (from == null) {
            throw new UnknownUnitException(fromUnit);
        }
        Unit to = REGISTRY.find(toUnit);
        if (to == null) {
            throw new UnknownUnitException(toUnit);
        }

        if (from.category() != to.category()) {
            throw new IncompatibleUnitException(
                    from.name(), from.category().name(),
                    to.name(), to.category().name());
        }

        double base = from.toBase(value);

        // Temperatures cannot drop below absolute zero (0 K)
        if (from.category() == Category.TEMPERATURE) {
            ValueValidator.checkAboveAbsoluteZero(base);
            base = Math.max(base, 0.0);
        }

        double result = to.fromBase(base);

        if (Double.isInfinite(result)) {
            throw new InvalidValueException("Result is too large to represent");
        }
        return result;
    }

    // Lists all supported unit names for a category
    public List<String> getSupportedUnits(Category category) {
        Objects.requireNonNull(category, "category must not be null");
        return REGISTRY.unitNames(category);
    }

    // Lists all physical categories that the library can convert
    public List<Category> getCategories() {
        return Collections.unmodifiableList(List.of(Category.values()));
    }
}