package shippingcalculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

// The price of a whole shipment on one route: one CostBreakdown per parcel plus the totals.
record ShipmentQuote(Route route, Shipment shipment, List<CostBreakdown> parcels) {

    ShipmentQuote {
        parcels = List.copyOf(parcels);
    }

    BigDecimal baseCost() {
        return sum(CostBreakdown::baseCost);
    }

    BigDecimal fragileSurcharge() {
        return sum(CostBreakdown::fragileSurcharge);
    }

    BigDecimal expressSurcharge() {
        return sum(CostBreakdown::expressSurcharge);
    }

    BigDecimal total() {
        return sum(CostBreakdown::total);
    }

    double chargeableWeightKg() {
        return parcels.stream().mapToDouble(CostBreakdown::chargeableWeightKg).sum();
    }

    int deliveryDays() {
        return route.deliveryDays(shipment.express());
    }

    String serviceName() {
        return shipment.express() ? "Express" : "Standard";
    }

    private BigDecimal sum(Function<CostBreakdown, BigDecimal> field) {
        return parcels.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
