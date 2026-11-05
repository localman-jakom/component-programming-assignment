package shippingcalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

// Shared number formatting so the console, CLI, GUI and exports all look the same.
final class Format {

    static final String CURRENCY = "KES";

    private Format() {
    }

    static String money(BigDecimal amount) {
        return CURRENCY + " " + amount(amount);
    }

    static String amount(BigDecimal amount) {
        return String.format(Locale.US, "%,.2f", amount.setScale(2, RoundingMode.HALF_UP));
    }

    static String kg(double kg) {
        return String.format(Locale.US, "%,.3f kg", kg);
    }

    static String cm(double cm) {
        return String.format(Locale.US, "%.2f", cm);
    }

    // 2.5 -> "2.5", 10.0 -> "10"
    static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String days(int days) {
        return days == 1 ? "1 day" : days + " days";
    }
}
