package shippingcalculator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A number plus the unit the user typed, e.g. 2.5 and "kg". Conversion is the library's job.
record Measurement(double value, String unit) {

    // A number, optional spaces, then a unit such as kg, lb, inch or m3
    private static final Pattern FORMAT =
            Pattern.compile("^\\s*(-?(?:\\d+(?:\\.\\d+)?|\\.\\d+))\\s*([A-Za-z][A-Za-z0-9/]*)\\s*$");

    static Measurement parse(String text) {
        Matcher matcher = FORMAT.matcher(text == null ? "" : text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Please type a number followed by a unit, e.g. 2.5 kg");
        }
        double value = Double.parseDouble(matcher.group(1));
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("That number is too large.");
        }
        if (value <= 0) {
            throw new IllegalArgumentException("The value must be greater than zero.");
        }
        return new Measurement(value, matcher.group(2));
    }

    @Override
    public String toString() {
        return Format.number(value) + " " + unit;
    }
}
