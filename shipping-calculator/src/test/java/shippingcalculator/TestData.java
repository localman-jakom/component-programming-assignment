package shippingcalculator;

import java.math.BigDecimal;
import java.util.List;

// Fixed example data for unit tests, independent of the database.
// The SWIFT carrier and its standard tariff are the original assignment prices.
final class TestData {

    static final Carrier SWIFT = new Carrier("SWIFT", "SwiftParcel Kenya", kes("300"), new BigDecimal("0.50"));

    private TestData() {
    }

    static BigDecimal kes(String amount) {
        return new BigDecimal(amount).setScale(2);
    }

    static RateTable standardRates() {
        return new RateTable(List.of(
                new RateTable.Band(1.0, kes("500")),
                new RateTable.Band(5.0, kes("900")),
                new RateTable.Band(10.0, kes("1500")),
                new RateTable.Band(20.0, kes("2500"))), kes("120"));
    }

    // Courier route (volumetric divisor 5000) with the original prices
    static Route route(boolean expressAvailable) {
        return new Route(1, SWIFT, TransportMode.COURIER, "Nairobi", "Mombasa", "Standard domestic", 2,
                expressAvailable ? 1 : null, standardRates());
    }

    static Route route(int id, Carrier carrier, RateTable rates, int days) {
        return route(id, carrier, TransportMode.COURIER, rates, days);
    }

    static Route route(int id, Carrier carrier, TransportMode mode, RateTable rates, int days) {
        return new Route(id, carrier, mode, "Nairobi", "Mombasa", "Test", days, days > 1 ? days - 1 : null, rates);
    }
}
