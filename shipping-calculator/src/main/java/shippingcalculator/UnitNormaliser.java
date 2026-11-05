package shippingcalculator;

// LIBRARY IMPORT: the only place the client imports the reusable component (unitconverter-1.0.jar)
import unitconverter.UnitConverter;

// Turns whatever units the user typed into kilograms and centimetres.
// All conversion is delegated to the library; this class holds no conversion factors.
// Library exceptions (unknown unit, wrong kind of unit, bad value) are left for the caller to report.
final class UnitNormaliser {

    static final String WEIGHT_TARGET = "kg";
    static final String LENGTH_TARGET = "cm";

    // LIBRARY OBJECT: one converter, reused for every conversion
    private final UnitConverter converter = new UnitConverter();

    double toKilograms(Measurement weight) {
        return converter.convert(weight.value(), weight.unit(), WEIGHT_TARGET);
    }

    double toCentimetres(Measurement length) {
        return converter.convert(length.value(), length.unit(), LENGTH_TARGET);
    }
}
