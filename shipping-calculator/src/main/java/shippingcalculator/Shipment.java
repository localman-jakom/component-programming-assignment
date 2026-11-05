package shippingcalculator;

import java.util.List;

// What the customer wants to send: one or more parcels plus the options that apply to all of them.
record Shipment(List<PackageDetails> parcels, boolean fragile, boolean express) {

    Shipment {
        if (parcels == null) {
            throw new IllegalArgumentException("A shipment needs at least one parcel.");
        }
        ShippingLimits.checkParcelCount(parcels.size());
        parcels = List.copyOf(parcels);
    }

    static Shipment single(PackageDetails parcel, boolean fragile, boolean express) {
        return new Shipment(List.of(parcel), fragile, express);
    }

    double totalActualWeightKg() {
        return parcels.stream().mapToDouble(PackageDetails::weightKg).sum();
    }
}
