package shippingcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static shippingcalculator.TestData.kes;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// The pure pricing logic: no console, no database, no unit library.
class ShippingCalculatorTest {

    private static final double DELTA = 1e-9;
    private final ShippingCalculator calculator = new ShippingCalculator();
    private final Route route = TestData.route(true);

    private CostBreakdown price(PackageDetails parcel, boolean fragile, boolean express) {
        return calculator.priceParcel(parcel, route, fragile, express);
    }

    @Test
    @DisplayName("Actual weight wins: 2 kg, 30x20x10 cm -> KES 900")
    void actualWeightWins() {
        CostBreakdown c = price(new PackageDetails(2, 30, 20, 10), false, false);
        assertEquals(1.2, c.volumetricWeightKg(), DELTA);
        assertEquals(2.0, c.chargeableWeightKg(), DELTA);
        assertFalse(c.volumetricWeightApplies());
        assertEquals(kes("900"), c.total());
    }

    @Test
    @DisplayName("Volumetric weight wins: 1 kg, 50x40x30 cm -> 12 kg -> KES 2500")
    void volumetricWeightWins() {
        CostBreakdown c = price(new PackageDetails(1, 50, 40, 30), false, false);
        assertEquals(12.0, c.volumetricWeightKg(), DELTA);
        assertEquals(12.0, c.chargeableWeightKg(), DELTA);
        assertTrue(c.volumetricWeightApplies());
        assertEquals(kes("2500"), c.total());
    }

    @Test
    @DisplayName("Fragile adds 300; express adds 50% of base; both together")
    void surcharges() {
        PackageDetails p = new PackageDetails(2, 30, 20, 10);
        assertEquals(kes("1200"), price(p, true, false).total());
        assertEquals(kes("1350"), price(p, false, true).total());
        CostBreakdown both = price(p, true, true);
        assertEquals(kes("300"), both.fragileSurcharge());
        assertEquals(kes("450"), both.expressSurcharge());
        assertEquals(kes("1650"), both.total());
    }

    @ParameterizedTest(name = "{0} kg rounds up to {1} kg")
    @CsvSource({
            "0.1,                0.5",
            "0.5,                0.5",
            "0.51,               1.0",
            "1.0,                1.0",
            "1.0000000000000002, 1.0",   // floating-point noise must not cost an extra half kilo
            "1.2,                1.5",
            "4.6,                5.0",
            "20.01,              20.5"
    })
    void chargeableWeightRoundsUpToHalfKilo(double kg, double expected) {
        assertEquals(expected, ShippingCalculator.roundUpToStep(kg), DELTA);
    }

    @Test
    @DisplayName("Rounding moves a parcel into the next band: 5.01 kg -> 5.5 kg -> KES 1500")
    void roundingCanChangeTheBand() {
        CostBreakdown c = price(new PackageDetails(5.01, 10, 10, 10), false, false);
        assertEquals(5.5, c.chargeableWeightKg(), DELTA);
        assertEquals(kes("1500"), c.baseCost());
    }

    @ParameterizedTest(name = "{0} kg chargeable -> KES {1}")
    @CsvSource({
            "0.5,   500",
            "1.0,   500",     // band limit is inclusive
            "1.5,   900",
            "5.0,   900",
            "5.5,   1500",
            "10.0,  1500",
            "10.5,  2500",
            "20.0,  2500",
            "20.5,  2620",    // 1 extra kg (part kilos round up) x 120
            "21.0,  2620",
            "25.0,  3100"     // 5 extra kg x 120
    })
    void rateBands(double chargeableKg, String expectedPrice) {
        assertEquals(kes(expectedPrice), TestData.standardRates().baseCost(chargeableKg));
    }

    @Test
    @DisplayName("Money is exact to the cent: 166.665 rounds half-up to 166.67, and totals never drift")
    void bigDecimalMoneyIsExact() {
        RateTable oddPrices = new RateTable(List.of(new RateTable.Band(70, new BigDecimal("333.33"))), kes("0"));
        Route r = TestData.route(2, TestData.SWIFT, oddPrices, 2);
        PackageDetails p = new PackageDetails(1, 10, 10, 10);
        ShipmentQuote q = calculator.quote(new Shipment(List.of(p, p, p), false, true), r);

        assertEquals(kes("166.67"), q.parcels().get(0).expressSurcharge());
        assertEquals(kes("500.00"), q.parcels().get(0).total());
        assertEquals(kes("1500.00"), q.total());
    }

    @Test
    @DisplayName("Multi-parcel shipment: each parcel priced on its own, totals added up")
    void multiParcelShipment() {
        Shipment shipment = new Shipment(List.of(
                new PackageDetails(2, 30, 20, 10),      // 2 kg -> KES 900
                new PackageDetails(1, 50, 40, 30)),     // 12 kg volumetric -> KES 2500
                true, false);
        ShipmentQuote q = calculator.quote(shipment, route);

        assertEquals(2, q.parcels().size());
        assertEquals(kes("3400"), q.baseCost());
        assertEquals(kes("600"), q.fragileSurcharge());
        assertEquals(kes("0"), q.expressSurcharge());
        assertEquals(kes("4000"), q.total());
        assertEquals(14.0, q.chargeableWeightKg(), DELTA);
        assertEquals(3.0, shipment.totalActualWeightKg(), DELTA);
        assertEquals(2, q.deliveryDays());
    }

    @Test
    @DisplayName("Express is refused on a route without an express service")
    void expressNotAvailable() {
        Shipment express = Shipment.single(new PackageDetails(2, 30, 20, 10), false, true);
        assertThrows(IllegalArgumentException.class, () -> calculator.quote(express, TestData.route(false)));
        ShippingCalculator.Comparison none = calculator.compare(express, List.of(TestData.route(false)));
        assertTrue(none.quotes().isEmpty());
        assertEquals(List.of("SwiftParcel Kenya (courier): no express service"), none.skipped());
    }

    @Test
    @DisplayName("Comparison lists cheapest first, then fastest")
    void comparisonOrder() {
        Carrier cheap = new Carrier("CHEAP", "Cheap Co", kes("0"), new BigDecimal("0.5"));
        RateTable lower = new RateTable(List.of(new RateTable.Band(70, kes("400"))), kes("0"));
        Route slowSwift = TestData.route(2, TestData.SWIFT, TestData.standardRates(), 3);
        Route fastSwift = TestData.route(3, TestData.SWIFT, TestData.standardRates(), 2);
        Route cheapest = TestData.route(4, cheap, lower, 5);

        Shipment s = Shipment.single(new PackageDetails(2, 30, 20, 10), false, false);
        List<ShipmentQuote> quotes = calculator.compare(s, List.of(slowSwift, fastSwift, cheapest)).quotes();

        assertEquals(4, quotes.get(0).route().id());
        assertEquals(3, quotes.get(1).route().id());
        assertEquals(2, quotes.get(2).route().id());
        assertEquals("Standard", quotes.get(0).serviceName());
    }

    @ParameterizedTest(name = "invalid package: {0}, {1}, {2}, {3}")
    @CsvSource({
            "0,   10, 10, 10",
            "-1,  10, 10, 10",
            "5,   0,  10, 10",
            "5,   10, -3, 10",
            "5,   10, 10, 0",
            "NaN, 10, 10, 10",
            "5,   Infinity, 10, 10"
    })
    void invalidPackagesAreRejected(double w, double l, double wd, double h) {
        assertThrows(IllegalArgumentException.class, () -> new PackageDetails(w, l, wd, h));
    }

    @Test
    @DisplayName("Rate tables must have increasing, positive bands")
    void badRateTables() {
        assertThrows(IllegalArgumentException.class, () -> new RateTable(List.of(), kes("1")));
        assertThrows(IllegalArgumentException.class, () -> new RateTable(List.of(
                new RateTable.Band(5, kes("10")), new RateTable.Band(1, kes("20"))), kes("1")));
        assertThrows(IllegalArgumentException.class, () -> new RateTable(List.of(
                new RateTable.Band(5, kes("0"))), kes("1")));
    }

    @Test
    @DisplayName("Carriers and routes reject impossible settings")
    void badCarriersAndRoutes() {
        assertThrows(IllegalArgumentException.class,
                () -> new Carrier("X", "X", kes("-1"), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new Route(1, TestData.SWIFT, TransportMode.ROAD,
                "A", "B", "T", 0, null, TestData.standardRates()));
        assertEquals("50%", TestData.SWIFT.expressPercent());
    }

    @Test
    @DisplayName("Each transport mode uses its own volumetric divisor: courier 5000, air 6000, road 3000, sea 1000")
    void divisorsByMode() {
        PackageDetails box = new PackageDetails(1, 60, 50, 40);   // 120,000 cm3
        RateTable flat = new RateTable(List.of(new RateTable.Band(30_000, kes("100"))), kes("1"));
        double[] expected = {24.0, 20.0, 40.0, 120.0};
        TransportMode[] modes = {TransportMode.COURIER, TransportMode.AIR, TransportMode.ROAD, TransportMode.SEA};
        for (int i = 0; i < modes.length; i++) {
            Route r = TestData.route(i, TestData.SWIFT, modes[i], flat, 3);
            assertEquals(expected[i], calculator.priceParcel(box, r, false, false).volumetricWeightKg(), DELTA);
        }
    }

    @Test
    @DisplayName("Heavy goods: courier is skipped with a reason, road freight is quoted")
    void heavyGoodsSkipCourier() {
        RateTable freight = new RateTable(List.of(new RateTable.Band(1000, kes("16000"))), kes("7"));
        Route courier = TestData.route(true);
        Route truck = TestData.route(2, TestData.SWIFT, TransportMode.ROAD, freight, 2);
        Shipment pallet = Shipment.single(new PackageDetails(1500, 120, 100, 150), false, false);

        ShippingCalculator.Comparison result = calculator.compare(pallet, List.of(courier, truck));
        assertEquals(1, result.quotes().size());
        assertEquals(TransportMode.ROAD, result.quotes().get(0).route().mode());
        assertEquals(kes("19500"), result.quotes().get(0).total());   // 16,000 + 500 kg x 7
        assertEquals(List.of("SwiftParcel Kenya (courier): Courier takes items up to 70 kg"), result.skipped());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> calculator.quote(pallet, courier));
        assertTrue(e.getMessage().contains("Courier takes items up to 70 kg"), e.getMessage());
    }

    @Test
    @DisplayName("In a multi-item shipment the reason names the item that does not fit")
    void reasonNamesTheItem() {
        Shipment mixed = new Shipment(List.of(new PackageDetails(2, 30, 20, 10),
                new PackageDetails(5, 200, 40, 40)), false, false);
        assertEquals("item 2: Courier takes items up to 150 x 150 x 150 cm",
                ShippingCalculator.whyRouteCannotCarry(mixed, TestData.route(true)));
    }
}
