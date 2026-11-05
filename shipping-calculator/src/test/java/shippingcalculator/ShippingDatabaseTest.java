package shippingcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static shippingcalculator.TestData.kes;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The embedded database: seed data, route look-ups, quote history and upgrading old files.
class ShippingDatabaseTest {

    @TempDir
    Path tempDir;

    private final ShippingDatabase db = ShippingDatabase.openInMemory();

    @AfterEach
    void close() {
        db.close();
    }

    private ShipmentQuote quoteSwift() {
        Route swift = db.findRoute("swift", "Nairobi", "Mombasa").orElseThrow();
        return new ShippingCalculator().quote(
                Shipment.single(new PackageDetails(2, 30, 20, 10), true, false), swift);
    }

    private String fileUrl(String name) {
        return "jdbc:h2:file:" + tempDir.resolve(name).toString().replace('\\', '/');
    }

    @Test
    @DisplayName("Seed data: 6 carriers, 48 routes over four transport modes")
    void seeded() {
        assertEquals(6, db.carrierCount());
        assertEquals(48, db.routes().size());
        assertEquals(List.of("Eldoret", "Kisumu", "Mombasa", "Nairobi"), db.origins());
        assertEquals(Set.of(TransportMode.values()), db.modes(null, null));
        assertTrue(db.isTemporary());
    }

    @Test
    @DisplayName("Route look-ups ignore letter case")
    void lookups() {
        assertTrue(db.destinationsFrom("nairobi").containsAll(List.of("Mombasa", "London", "Rotterdam", "Shanghai")));
        assertEquals(4, db.routesBetween("NAIROBI", "mombasa").size());
        assertTrue(db.findRoute("KILI", "Nairobi", "London").isPresent());
        assertFalse(db.findRoute("KILI", "Nairobi", "Mombasa").isPresent());
    }

    @Test
    @DisplayName("Cities separated by sea are served by ships; Nairobi -> London has air and sea")
    void seaRoutes() {
        assertEquals(Set.of(TransportMode.SEA), db.modes("Mombasa", "Rotterdam"));
        assertEquals(Set.of(TransportMode.AIR, TransportMode.SEA), db.modes("Nairobi", "London"));
        assertEquals(Set.of(TransportMode.COURIER, TransportMode.ROAD), db.modes("Nairobi", "Mombasa"));
        Route ship = db.findRoute("IOCL", "Mombasa", "Dubai").orElseThrow();
        assertEquals(TransportMode.SEA, ship.mode());
        assertFalse(ship.expressAvailable());
        assertEquals(14, ship.transitDays());
    }

    @Test
    @DisplayName("Tariffs are loaded from the database: SwiftParcel standard keeps the original prices")
    void tariffsLoaded() {
        Route swift = db.findRoute("SWIFT", "Nairobi", "Mombasa").orElseThrow();
        assertEquals(kes("900"), swift.rates().baseCost(2.0));
        assertEquals(kes("2620"), swift.rates().baseCost(20.5));
        assertEquals(TransportMode.COURIER, swift.mode());
        assertEquals(Integer.valueOf(1), swift.expressDays());

        Route nakuru = db.findRoute("SWIFT", "Nairobi", "Nakuru").orElseThrow();
        assertFalse(nakuru.expressAvailable());
        Route truck = db.findRoute("RIFT", "Nairobi", "Mombasa").orElseThrow();
        assertEquals(TransportMode.ROAD, truck.mode());
        assertEquals(kes("1500"), truck.rates().baseCost(3));                 // minimum charge
        assertEquals(kes("170000"), truck.rates().baseCost(23_000));          // 16,000 + 22,000 kg x 7
    }

    @Test
    @DisplayName("Quotes are saved, listed newest first and cleared")
    void history() {
        long first = db.saveQuote(quoteSwift());
        long second = db.saveQuote(quoteSwift());
        assertTrue(second > first);

        List<ShippingDatabase.SavedQuote> saved = db.recentQuotes(10);
        assertEquals(2, saved.size());
        ShippingDatabase.SavedQuote latest = saved.get(0);
        assertEquals(second, latest.id());
        assertEquals("SwiftParcel Kenya", latest.carrierName());
        assertEquals(TransportMode.COURIER, latest.mode());
        assertEquals("Courier", latest.modeLabel());
        assertEquals("Nairobi", latest.origin());
        assertEquals(kes("1200"), latest.total());
        assertEquals("Fragile", latest.options());
        assertTrue(latest.receipt().contains("NEXUS GLOBEX TRANSIT"));
        assertTrue(latest.receipt().contains("TOTAL:"));

        assertEquals(1, db.recentQuotes(1).size());
        assertEquals(2, db.clearHistory());
        assertTrue(db.recentQuotes(10).isEmpty());
    }

    @Test
    @DisplayName("A database file keeps its quotes and is not seeded twice")
    void fileDatabasePersists() {
        String url = fileUrl("shipping");
        try (ShippingDatabase file = ShippingDatabase.open(url, "test file")) {
            file.saveQuote(new ShippingCalculator().quote(Shipment.single(new PackageDetails(1, 10, 10, 10),
                    false, false), file.findRoute("SWIFT", "Nairobi", "Mombasa").orElseThrow()));
        }
        try (ShippingDatabase reopened = ShippingDatabase.open(url, "test file")) {
            assertEquals(48, reopened.routes().size());
            assertEquals(1, reopened.recentQuotes(10).size());
            assertEquals("test file", reopened.location());
            assertFalse(reopened.isTemporary());
        }
    }

    @Test
    @DisplayName("A database from the first version is upgraded: new routes, old quotes kept")
    void upgradesVersionOneDatabase() throws Exception {
        String url = fileUrl("old");
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE carrier (code VARCHAR(10) PRIMARY KEY, name VARCHAR(80) NOT NULL,"
                    + " volumetric_divisor INT NOT NULL, fragile_fee DECIMAL(10,2) NOT NULL,"
                    + " express_rate DECIMAL(5,4) NOT NULL)");
            s.execute("CREATE TABLE tariff (code VARCHAR(20) PRIMARY KEY, carrier_code VARCHAR(10) NOT NULL"
                    + " REFERENCES carrier(code), description VARCHAR(80) NOT NULL, extra_per_kg DECIMAL(10,2) NOT NULL)");
            s.execute("CREATE TABLE tariff_band (tariff_code VARCHAR(20) NOT NULL REFERENCES tariff(code),"
                    + " up_to_kg DECIMAL(6,2) NOT NULL, price DECIMAL(10,2) NOT NULL, PRIMARY KEY (tariff_code, up_to_kg))");
            s.execute("CREATE TABLE route (id INT AUTO_INCREMENT PRIMARY KEY, carrier_code VARCHAR(10) NOT NULL"
                    + " REFERENCES carrier(code), origin VARCHAR(40) NOT NULL, destination VARCHAR(40) NOT NULL,"
                    + " tariff_code VARCHAR(20) NOT NULL REFERENCES tariff(code), transit_days INT NOT NULL,"
                    + " express_days INT)");
            s.execute("CREATE TABLE quote (id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at TIMESTAMP NOT NULL,"
                    + " route_id INT NOT NULL REFERENCES route(id), parcel_count INT NOT NULL,"
                    + " chargeable_kg DECIMAL(10,2) NOT NULL, fragile BOOLEAN NOT NULL, express BOOLEAN NOT NULL,"
                    + " total DECIMAL(12,2) NOT NULL, receipt CLOB NOT NULL)");
            s.execute("INSERT INTO carrier VALUES ('KILI', 'Kilimanjaro Air Freight', 5000, 500, 0.4)");
            s.execute("INSERT INTO tariff VALUES ('KILI-INTL', 'KILI', 'International air', 1600)");
            s.execute("INSERT INTO tariff_band VALUES ('KILI-INTL', 10, 36000)");
            s.execute("INSERT INTO route VALUES (7, 'KILI', 'Nairobi', 'Dubai', 'KILI-INTL', 4, 2)");
            s.execute("INSERT INTO quote VALUES (5, TIMESTAMP '2026-11-05 21:00:00', 7, 1, 70, FALSE, TRUE,"
                    + " 184800, 'old receipt')");
        }

        try (ShippingDatabase upgraded = ShippingDatabase.open(url, "old file")) {
            assertEquals(48, upgraded.routes().size());
            List<ShippingDatabase.SavedQuote> quotes = upgraded.recentQuotes(10);
            assertEquals(1, quotes.size());
            ShippingDatabase.SavedQuote old = quotes.get(0);
            assertEquals(5, old.id());
            assertEquals("Kilimanjaro Air Freight", old.carrierName());
            assertEquals(TransportMode.AIR, old.mode());
            assertEquals("Dubai", old.destination());
            assertEquals(kes("184800"), old.total());
            assertEquals("old receipt", old.receipt());
            // New quotes continue after the old numbers
            assertEquals(6, upgraded.saveQuote(quoteFrom(upgraded)));
        }
    }

    private static ShipmentQuote quoteFrom(ShippingDatabase database) {
        return new ShippingCalculator().quote(Shipment.single(new PackageDetails(1, 10, 10, 10), false, false),
                database.findRoute("SWIFT", "Nairobi", "Mombasa").orElseThrow());
    }

    @Test
    @DisplayName("If the database file cannot be opened, an in-memory database is used and a warning shown")
    void fallsBackToMemory() throws Exception {
        Path notAFolder = Files.createFile(tempDir.resolve("blocker.txt"));
        String previous = System.getProperty(ShippingDatabase.LOCATION_PROPERTY);
        System.setProperty(ShippingDatabase.LOCATION_PROPERTY, notAFolder.resolve("shipping").toString());
        ByteArrayOutputStream warnings = new ByteArrayOutputStream();
        try (ShippingDatabase fallback = ShippingDatabase.openDefault(
                new PrintStream(warnings, true, StandardCharsets.UTF_8))) {
            assertEquals("temporary in-memory database", fallback.location());
            assertTrue(fallback.isTemporary());
            assertEquals(48, fallback.routes().size());
            assertTrue(warnings.toString(StandardCharsets.UTF_8).contains("Warning"));
        } finally {
            if (previous == null) {
                System.clearProperty(ShippingDatabase.LOCATION_PROPERTY);
            } else {
                System.setProperty(ShippingDatabase.LOCATION_PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("Default file lives in the user's home folder unless overridden")
    void defaultLocation() {
        String previous = System.getProperty(ShippingDatabase.LOCATION_PROPERTY);
        System.clearProperty(ShippingDatabase.LOCATION_PROPERTY);
        try {
            assertTrue(ShippingDatabase.defaultFile().toString().contains(".shipping-calculator"));
        } finally {
            if (previous != null) {
                System.setProperty(ShippingDatabase.LOCATION_PROPERTY, previous);
            }
        }
    }
}
