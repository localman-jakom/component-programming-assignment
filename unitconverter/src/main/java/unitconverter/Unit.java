package unitconverter;

import java.util.function.DoubleUnaryOperator;

// Converts to/from base units via functions rather than pure factors to support offset scales like temperature.
final class Unit {

    private final String name;
    private final Category category;
    private final DoubleUnaryOperator toBase;
    private final DoubleUnaryOperator fromBase;

    private Unit(String name, Category category,
                 DoubleUnaryOperator toBase, DoubleUnaryOperator fromBase) {
        this.name = name;
        this.category = category;
        this.toBase = toBase;
        this.fromBase = fromBase;
    }

    static Unit linear(String name, Category category, double factor) {
        return new Unit(name, category, v -> v * factor, b -> b / factor);
    }

    static Unit custom(String name, Category category,
                       DoubleUnaryOperator toBase, DoubleUnaryOperator fromBase) {
        return new Unit(name, category, toBase, fromBase);
    }

    String name() {
        return name;
    }

    Category category() {
        return category;
    }

    double toBase(double value) {
        return toBase.applyAsDouble(value);
    }

    double fromBase(double baseValue) {
        return fromBase.applyAsDouble(baseValue);
    }
}
