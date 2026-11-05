package shippingcalculator;

// One parcel after normalisation: weight in kilograms, sides in centimetres.
// The calculation code only ever sees this record, so it never deals with pounds or inches.
record PackageDetails(double weightKg, double lengthCm, double widthCm, double heightCm) {

    PackageDetails {
        requirePositive(weightKg, "weight");
        requirePositive(lengthCm, "length");
        requirePositive(widthCm, "width");
        requirePositive(heightCm, "height");
        ShippingLimits.checkWeightKg(weightKg);
        ShippingLimits.checkSideCm("Length", lengthCm);
        ShippingLimits.checkSideCm("Width", widthCm);
        ShippingLimits.checkSideCm("Height", heightCm);
    }

    double longestSideCm() {
        return Math.max(lengthCm, Math.max(widthCm, heightCm));
    }

    String describe() {
        return Format.kg(weightKg) + ", " + Format.cm(lengthCm) + " x " + Format.cm(widthCm)
                + " x " + Format.cm(heightCm) + " cm";
    }

    private static void requirePositive(double value, String name) {
        if (!(value > 0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("The " + name + " must be a positive, finite number");
        }
    }
}
