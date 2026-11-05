package shippingcalculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// A tariff: a flat price per weight band plus a per-kilogram charge above the last band.
// Band limits are inclusive, so exactly 1.0 kg costs the "up to 1 kg" price.
final class RateTable {

    record Band(double upToKg, BigDecimal price) {
    }

    private final List<Band> bands;
    private final BigDecimal extraPerKg;

    RateTable(List<Band> bands, BigDecimal extraPerKg) {
        if (bands == null || bands.isEmpty()) {
            throw new IllegalArgumentException("A rate table needs at least one weight band");
        }
        double previous = 0;
        for (Band band : bands) {
            if (band.upToKg() <= previous) {
                throw new IllegalArgumentException("Weight bands must be in increasing order");
            }
            if (band.price().signum() <= 0) {
                throw new IllegalArgumentException("Band prices must be positive");
            }
            previous = band.upToKg();
        }
        this.bands = List.copyOf(bands);
        this.extraPerKg = extraPerKg;
    }

    BigDecimal baseCost(double chargeableKg) {
        for (Band band : bands) {
            if (chargeableKg <= band.upToKg()) {
                return band.price();
            }
        }
        // Heavier than every band: last band's price plus a charge per extra kg (part kgs round up)
        Band last = bands.get(bands.size() - 1);
        long extraKg = (long) Math.ceil(chargeableKg - last.upToKg() - 1e-9);
        return last.price().add(extraPerKg.multiply(BigDecimal.valueOf(extraKg)));
    }

    List<Band> bands() {
        return bands;
    }

    BigDecimal extraPerKg() {
        return extraPerKg;
    }

    // e.g. "<=1kg 500 | <=5kg 900 | +120/kg over 20kg"
    String summary() {
        List<String> parts = new ArrayList<>();
        for (Band band : bands) {
            parts.add("<=" + Format.number(band.upToKg()) + "kg "
                    + band.price().stripTrailingZeros().toPlainString());
        }
        parts.add("+" + extraPerKg.stripTrailingZeros().toPlainString() + "/kg over "
                + Format.number(bands.get(bands.size() - 1).upToKg()) + "kg");
        return String.join(" | ", parts);
    }
}
