package shippingcalculator;

import java.util.Locale;

// The absolute limits for one item: the largest any transport mode accepts
// (a loaded 40 ft container by weight, a 13.6 m truck trailer by length).
// Each route then checks the tighter limits of its own mode, see TransportMode.
final class ShippingLimits {

    static final double MAX_WEIGHT_KG = TransportMode.SEA.maxWeightKg();
    static final double MAX_SIDE_CM = 1_360.0;
    static final int MAX_PARCELS = 10;

    // Absorbs floating-point noise from unit conversion, e.g. 59.0551 inch -> 150.00000 cm
    private static final double TOLERANCE = 1e-6;

    private ShippingLimits() {
    }

    static void checkWeightKg(double weightKg) {
        if (weightKg > MAX_WEIGHT_KG + TOLERANCE) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "Weight %,.2f kg is over the %,.0f kg limit for one item (a full 40 ft container)."
                            + " Split it into smaller items.", weightKg, MAX_WEIGHT_KG));
        }
    }

    static void checkSideCm(String side, double sideCm) {
        if (sideCm > MAX_SIDE_CM + TOLERANCE) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "%s %,.2f cm is over the %,.0f cm limit for any side (a 13.6 m truck trailer).",
                    side, sideCm, MAX_SIDE_CM));
        }
    }

    static void checkParcelCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("A shipment needs at least one item.");
        }
        if (count > MAX_PARCELS) {
            throw new IllegalArgumentException(
                    "A shipment can have at most " + MAX_PARCELS + " items (you have " + count + ").");
        }
    }

    // One line for the screens, e.g. "Courier 70 kg, 150 x 150 x 150 cm | Air ..."
    static String summary() {
        StringBuilder text = new StringBuilder();
        for (TransportMode mode : TransportMode.values()) {
            if (text.length() > 0) {
                text.append("\n");
            }
            text.append(mode.label()).append(": up to ").append(mode.limitsText());
        }
        return text.toString();
    }
}
