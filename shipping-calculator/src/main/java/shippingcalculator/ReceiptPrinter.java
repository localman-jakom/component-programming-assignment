package shippingcalculator;

import java.util.List;
import java.util.Locale;

// Output formatting only. Returns plain text so the console, CLI, GUI and file export share it.
final class ReceiptPrinter {

    private static final String RULE = "-".repeat(62);
    private static final String DOUBLE_RULE = "=".repeat(62);

    private ReceiptPrinter() {
    }

    // Full receipt with the company name on top (console, text files, saved history)
    static String receipt(ShipmentQuote quote) {
        return DOUBLE_RULE + "\n" + centre(Brand.NAME + " - " + Brand.TAGLINE) + "\n" + body(quote);
    }

    // The receipt without the company line, for the window where the logo is shown above it
    static String body(ShipmentQuote quote) {
        Route route = quote.route();
        Carrier carrier = route.carrier();
        Shipment shipment = quote.shipment();
        StringBuilder text = new StringBuilder();

        text.append("======================= SHIPPING QUOTE =======================\n");
        line(text, " Carrier:", carrier.name() + " (" + carrier.code() + ")");
        line(text, " Transport:", route.mode().label());
        line(text, " Route:", route.path());
        line(text, " Service:", quote.serviceName() + ", delivery in " + Format.days(quote.deliveryDays()));
        line(text, " Items:", String.valueOf(shipment.parcels().size()));
        text.append(RULE).append('\n');

        for (int i = 0; i < quote.parcels().size(); i++) {
            CostBreakdown cost = quote.parcels().get(i);
            PackageDetails parcel = shipment.parcels().get(i);
            text.append(" Item ").append(i + 1).append(": ").append(parcel.describe()).append('\n');
            line(text, "   Volumetric weight:", Format.kg(cost.volumetricWeightKg())
                    + "  (L x W x H / " + route.mode().volumetricDivisor() + ")");
            line(text, "   Chargeable weight:", Format.kg(cost.chargeableWeightKg())
                    + "  (" + (cost.volumetricWeightApplies() ? "volumetric" : "actual")
                    + ", rounded up to 0.5 kg)");
            line(text, "   Base rate:", Format.money(cost.baseCost()));
            if (shipment.fragile()) {
                line(text, "   Fragile surcharge:", Format.money(cost.fragileSurcharge()));
            }
            if (shipment.express()) {
                line(text, "   Express surcharge:", Format.money(cost.expressSurcharge())
                        + "  (" + carrier.expressPercent() + " of base)");
            }
            if (quote.parcels().size() > 1) {
                line(text, "   Item total:", Format.money(cost.total()));
            }
        }

        text.append(RULE).append('\n');
        line(text, " Chargeable weight:", Format.kg(quote.chargeableWeightKg()));
        line(text, " Base rate:", Format.money(quote.baseCost()));
        if (shipment.fragile()) {
            line(text, " Fragile surcharge:", Format.money(quote.fragileSurcharge()));
        }
        if (shipment.express()) {
            line(text, " Express surcharge:", Format.money(quote.expressSurcharge()));
        }
        text.append(RULE).append('\n');
        line(text, " TOTAL:", Format.money(quote.total()));
        text.append(DOUBLE_RULE).append('\n');
        return text.toString();
    }

    static String comparison(List<ShipmentQuote> quotes) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.US, " %-3s %-28s %-12s %-9s %-9s %14s %17s\n",
                "#", "Carrier", "Transport", "Service", "Delivery", "Chargeable", "Total"));
        for (int i = 0; i < quotes.size(); i++) {
            ShipmentQuote q = quotes.get(i);
            text.append(String.format(Locale.US, " %-3d %-28s %-12s %-9s %-9s %14s %17s%s\n",
                    i + 1, q.route().carrier().name(), q.route().mode().label(), q.serviceName(),
                    Format.days(q.deliveryDays()), Format.kg(q.chargeableWeightKg()), Format.money(q.total()),
                    i == 0 ? "  <- cheapest" : ""));
        }
        return text.toString();
    }

    static String routes(List<Route> routes) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.US, " %-6s %-28s %-12s %-30s %-8s %-8s %s\n",
                "Code", "Carrier", "Transport", "Route", "Standard", "Express", "Tariff"));
        for (Route r : routes) {
            text.append(String.format(Locale.US, " %-6s %-28s %-12s %-30s %-8s %-8s %s\n",
                    r.carrier().code(), r.carrier().name(), r.mode().label(), r.path(),
                    Format.days(r.transitDays()), r.expressAvailable() ? Format.days(r.expressDays()) : "-",
                    r.rates().summary()));
        }
        return text.toString();
    }

    private static String centre(String value) {
        int pad = Math.max(0, (DOUBLE_RULE.length() - value.length()) / 2);
        return " ".repeat(pad) + value;
    }

    private static void line(StringBuilder text, String label, String value) {
        text.append(String.format(Locale.US, "%-23s %s\n", label, value));
    }
}
