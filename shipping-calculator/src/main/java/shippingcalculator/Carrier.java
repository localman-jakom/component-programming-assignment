package shippingcalculator;

import java.math.BigDecimal;
import java.util.Objects;

// A shipping line and the surcharges it applies on every route it serves.
record Carrier(String code, String name, BigDecimal fragileFee, BigDecimal expressRate) {

    Carrier {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (fragileFee.signum() < 0 || expressRate.signum() < 0) {
            throw new IllegalArgumentException("Surcharges cannot be negative for " + code);
        }
    }

    // Express rate as a whole percentage for display, e.g. 0.50 -> "50%"
    String expressPercent() {
        return expressRate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
