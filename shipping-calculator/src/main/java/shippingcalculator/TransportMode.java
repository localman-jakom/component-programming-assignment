package shippingcalculator;

import java.util.Arrays;
import java.util.Locale;

// How a route moves goods. Each mode has its own size and weight limits per item
// and its own industry-standard volumetric divisor (cm3 per chargeable kg).
//   COURIER: parcel networks, 70 kg and 150 cm per side
//   AIR:     air cargo pallets, 1,000 kg, 300 x 240 x 160 cm
//   ROAD:    a 13.6 m truck trailer, 24 t, 1,360 x 270 x 245 cm
//   SEA:     a 40 ft container, 26.5 t, 1,200 x 239 x 235 cm
enum TransportMode {

    COURIER("Courier", 5000, 70, 150, 150, 150),
    AIR("Air freight", 6000, 1_000, 300, 240, 160),
    ROAD("Road freight", 3000, 24_000, 1_360, 270, 245),
    SEA("Sea freight", 1000, 26_500, 1_200, 239, 235);

    private final String label;
    private final int volumetricDivisor;
    private final double maxWeightKg;
    // Sorted largest first, so any orientation of the item can be tried
    private final double[] maxSidesCm;

    TransportMode(String label, int volumetricDivisor, double maxWeightKg,
                  double longestCm, double middleCm, double shortestCm) {
        this.label = label;
        this.volumetricDivisor = volumetricDivisor;
        this.maxWeightKg = maxWeightKg;
        this.maxSidesCm = new double[] {longestCm, middleCm, shortestCm};
    }

    String label() {
        return label;
    }

    int volumetricDivisor() {
        return volumetricDivisor;
    }

    double maxWeightKg() {
        return maxWeightKg;
    }

    // e.g. "1,360 x 270 x 245 cm"
    String maxSize() {
        return String.format(Locale.US, "%,.0f x %,.0f x %,.0f cm", maxSidesCm[0], maxSidesCm[1], maxSidesCm[2]);
    }

    String limitsText() {
        return String.format(Locale.US, "%,.0f kg, %s", maxWeightKg, maxSize());
    }

    // null when the item fits, otherwise a short reason for the user
    String whyCannotCarry(PackageDetails item) {
        if (item.weightKg() > maxWeightKg + 1e-6) {
            return label + " takes items up to " + String.format(Locale.US, "%,.0f kg", maxWeightKg);
        }
        double[] sides = {item.lengthCm(), item.widthCm(), item.heightCm()};
        Arrays.sort(sides);
        for (int i = 0; i < 3; i++) {
            if (sides[2 - i] > maxSidesCm[i] + 1e-6) {
                return label + " takes items up to " + maxSize();
            }
        }
        return null;
    }

    static TransportMode fromCode(String code) {
        return valueOf(code.trim().toUpperCase(Locale.ROOT));
    }
}
