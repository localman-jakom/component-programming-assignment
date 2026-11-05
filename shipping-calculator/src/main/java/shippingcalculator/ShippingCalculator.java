package shippingcalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// The pricing logic only: no input, no printing, no unit conversion.
// Per item:
//   volumetric weight = length x width x height / divisor of the route's transport mode
//   chargeable weight = greater of actual and volumetric, rounded up to the next 0.5 kg
//   base cost         = route tariff lookup on the chargeable weight
//   fragile           = carrier's fixed fee per item
//   express           = carrier's percentage of the base cost
final class ShippingCalculator {

    static final double WEIGHT_STEP_KG = 0.5;

    // The quotes that could be made, cheapest first, plus why other routes were left out
    record Comparison(List<ShipmentQuote> quotes, List<String> skipped) {

        Comparison {
            quotes = List.copyOf(quotes);
            skipped = List.copyOf(skipped);
        }
    }

    static double roundUpToStep(double kg) {
        // The tiny tolerance stops 1.0000000000000002 (floating-point noise) from becoming 1.5
        return Math.ceil(kg / WEIGHT_STEP_KG - 1e-9) * WEIGHT_STEP_KG;
    }

    CostBreakdown priceParcel(PackageDetails parcel, Route route, boolean fragile, boolean express) {
        Carrier carrier = route.carrier();
        double volumetricKg = parcel.lengthCm() * parcel.widthCm() * parcel.heightCm()
                / route.mode().volumetricDivisor();
        double chargeableKg = roundUpToStep(Math.max(parcel.weightKg(), volumetricKg));

        BigDecimal base = money(route.rates().baseCost(chargeableKg));
        BigDecimal fragileFee = fragile ? money(carrier.fragileFee()) : zero();
        BigDecimal expressFee = express ? money(base.multiply(carrier.expressRate())) : zero();

        return new CostBreakdown(parcel.weightKg(), volumetricKg, chargeableKg,
                base, fragileFee, expressFee);
    }

    // null when the route can carry the shipment, otherwise the reason it cannot
    static String whyRouteCannotCarry(Shipment shipment, Route route) {
        if (shipment.express() && !route.expressAvailable()) {
            return "no express service";
        }
        for (int i = 0; i < shipment.parcels().size(); i++) {
            String reason = route.mode().whyCannotCarry(shipment.parcels().get(i));
            if (reason != null) {
                return shipment.parcels().size() > 1 ? "item " + (i + 1) + ": " + reason : reason;
            }
        }
        return null;
    }

    ShipmentQuote quote(Shipment shipment, Route route) {
        String reason = whyRouteCannotCarry(shipment, route);
        if (reason != null) {
            throw new IllegalArgumentException(route.carrier().name() + " cannot take this shipment on "
                    + route.path() + " (" + reason + ").");
        }
        List<CostBreakdown> lines = new ArrayList<>();
        for (PackageDetails parcel : shipment.parcels()) {
            lines.add(priceParcel(parcel, route, shipment.fragile(), shipment.express()));
        }
        return new ShipmentQuote(route, shipment, lines);
    }

    // Quotes every route that can carry the shipment, cheapest first (then fastest)
    Comparison compare(Shipment shipment, List<Route> routes) {
        List<ShipmentQuote> quotes = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Route route : routes) {
            String reason = whyRouteCannotCarry(shipment, route);
            if (reason == null) {
                quotes.add(quote(shipment, route));
            } else {
                skipped.add(route.carrier().name() + " (" + route.mode().label().toLowerCase(Locale.ROOT) + "): " + reason);
            }
        }
        quotes.sort(Comparator.comparing(ShipmentQuote::total)
                .thenComparingInt(ShipmentQuote::deliveryDays)
                .thenComparing(q -> q.route().carrier().name()));
        return new Comparison(quotes, skipped);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }
}
