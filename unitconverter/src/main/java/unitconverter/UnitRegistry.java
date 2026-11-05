package unitconverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Internal registry managing units, aliases, and category mappings
final class UnitRegistry {

    private static final double KELVIN_OFFSET = 273.15;

    private final Map<String, Unit> byName = new HashMap<>();
    private final Map<Category, List<Unit>> byCategory = new EnumMap<>(Category.class);

    UnitRegistry() {
        for (Category c : Category.values()) {
            byCategory.put(c, new ArrayList<>());
        }
        registerLength();
        registerMass();
        registerTemperature();
        registerVolume();
        registerSpeed();
    }

    // Case-insensitive lookup supporting standard names and aliases
    Unit find(String name) {
        if (name == null) {
            return null;
        }
        return byName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    // Unmodifiable list of canonical unit names registered under a category
    List<String> unitNames(Category category) {
        List<String> names = new ArrayList<>();
        for (Unit u : byCategory.get(category)) {
            names.add(u.name());
        }
        return Collections.unmodifiableList(names);
    }

    private void add(Unit unit, String... aliases) {
        byCategory.get(unit.category()).add(unit);
        byName.put(unit.name().toLowerCase(Locale.ROOT), unit);
        for (String alias : aliases) {
            byName.put(alias.toLowerCase(Locale.ROOT), unit);
        }
    }

    // Base unit: metre (m)
    private void registerLength() {
        Category c = Category.LENGTH;
        add(Unit.linear("m", c, 1.0), "metre", "metres", "meter", "meters");
        add(Unit.linear("cm", c, 0.01), "centimetre", "centimetres", "centimeter", "centimeters");
        add(Unit.linear("mm", c, 0.001), "millimetre", "millimetres", "millimeter", "millimeters");
        add(Unit.linear("km", c, 1000.0), "kilometre", "kilometres", "kilometer", "kilometers");
        add(Unit.linear("inch", c, 0.0254), "inches", "in");
        add(Unit.linear("foot", c, 0.3048), "feet", "ft");
        add(Unit.linear("yard", c, 0.9144), "yards", "yd");
        add(Unit.linear("mile", c, 1609.344), "miles", "mi");
    }

    // Base unit: kilogram (kg)
    private void registerMass() {
        Category c = Category.MASS;
        add(Unit.linear("kg", c, 1.0), "kilogram", "kilograms", "kilo", "kilos");
        add(Unit.linear("g", c, 0.001), "gram", "grams");
        add(Unit.linear("mg", c, 0.000001), "milligram", "milligrams");
        add(Unit.linear("lb", c, 0.45359237), "lbs", "pound", "pounds");
        add(Unit.linear("oz", c, 0.028349523125), "ounce", "ounces");
        add(Unit.linear("tonne", c, 1000.0), "tonnes", "t", "metric ton");
    }

    // Base unit: kelvin (K)
    private void registerTemperature() {
        Category c = Category.TEMPERATURE;
        add(Unit.custom("K", c, v -> v, b -> b), "kelvin");
        add(Unit.custom("C", c,
                v -> v + KELVIN_OFFSET,
                b -> b - KELVIN_OFFSET), "celsius", "\u00B0c", "degc");
        add(Unit.custom("F", c,
                v -> (v - 32.0) * 5.0 / 9.0 + KELVIN_OFFSET,
                b -> (b - KELVIN_OFFSET) * 9.0 / 5.0 + 32.0), "fahrenheit", "\u00B0f", "degf");
    }

    // Base unit: litre (l)
    private void registerVolume() {
        Category c = Category.VOLUME;
        add(Unit.linear("l", c, 1.0), "litre", "litres", "liter", "liters");
        add(Unit.linear("ml", c, 0.001), "millilitre", "millilitres", "milliliter", "milliliters");
        add(Unit.linear("gallon", c, 3.785411784), "gallons", "gal");
        add(Unit.linear("cup", c, 0.2365882365), "cups");
        add(Unit.linear("m3", c, 1000.0), "cubic metre", "cubic meter");
    }

    // Base unit: metres per second (m/s)
    private void registerSpeed() {
        Category c = Category.SPEED;
        add(Unit.linear("m/s", c, 1.0), "mps");
        add(Unit.linear("km/h", c, 1000.0 / 3600.0), "kmh", "kph");
        add(Unit.linear("mph", c, 0.44704), "mi/h");
        add(Unit.linear("knot", c, 1852.0 / 3600.0), "knots", "kn", "kt");
    }
}