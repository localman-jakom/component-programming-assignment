package shippingcalculator;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

// Embedded H2 database holding carriers, tariffs, routes and saved quotes.
// Routes are read once when the database opens; quotes are written as they are saved.
// When the example data changes (SEED_VERSION), the carriers, tariffs and routes are
// reloaded and saved quotes are kept, including quotes from the first version of the program.
final class ShippingDatabase implements AutoCloseable {

    // Set -Dshipping.db=<path> to use a different database file
    static final String LOCATION_PROPERTY = "shipping.db";
    static final String SEED_VERSION = "2";

    private static final AtomicInteger MEMORY_COUNTER = new AtomicInteger();

    private final Connection connection;
    private final String location;
    private final boolean temporary;
    private final List<Route> routes;

    // A saved quote as listed in the history; mode is null for quotes saved by version 1
    record SavedQuote(long id, LocalDateTime createdAt, String carrierName, TransportMode mode, String origin,
                      String destination, int parcelCount, BigDecimal chargeableKg,
                      boolean fragile, boolean express, BigDecimal total, String receipt) {

        String options() {
            if (fragile && express) {
                return "Fragile, Express";
            }
            return fragile ? "Fragile" : express ? "Express" : "-";
        }

        String modeLabel() {
            return mode == null ? "-" : mode.label();
        }
    }

    // Unchecked wrapper so callers can show one friendly message for any database problem
    static final class DatabaseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private ShippingDatabase(Connection connection, String location, boolean temporary) throws SQLException {
        this.connection = connection;
        this.location = location;
        this.temporary = temporary;
        createAndSeed();
        this.routes = List.copyOf(loadRoutes());
    }

    static ShippingDatabase open(String jdbcUrl, String location) {
        return open(jdbcUrl, location, false);
    }

    private static ShippingDatabase open(String jdbcUrl, String location, boolean temporary) {
        try {
            return new ShippingDatabase(DriverManager.getConnection(jdbcUrl, "sa", ""), location, temporary);
        } catch (SQLException e) {
            throw new DatabaseException("Could not open the database at " + location + ": "
                    + firstLine(e.getMessage()), e);
        }
    }

    // A fresh private database that disappears when closed: used by tests and as a fallback
    static ShippingDatabase openInMemory() {
        String name = "shipping" + MEMORY_COUNTER.incrementAndGet();
        return open("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "temporary in-memory database", true);
    }

    static Path defaultFile() {
        String override = System.getProperty(LOCATION_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.home"), ".shipping-calculator", "shipping");
    }

    // Opens the database file; if that fails (e.g. another copy of the program has it open)
    // the program still works with an in-memory copy and says so.
    static ShippingDatabase openDefault(PrintStream warnings) {
        Path file = defaultFile();
        String url = "jdbc:h2:file:" + file.toString().replace('\\', '/') + ";TRACE_LEVEL_FILE=0";
        try {
            return open(url, file.toString());
        } catch (DatabaseException problem) {
            warnings.println("Warning: " + problem.getMessage());
            warnings.println("Using a temporary in-memory database; saved quotes will not be kept.");
            return openInMemory();
        }
    }

    String location() {
        return location;
    }

    // True when quotes will be lost on exit (in-memory fallback)
    boolean isTemporary() {
        return temporary;
    }

    // ------------------------------------------------------------------ routes

    List<Route> routes() {
        return routes;
    }

    List<String> origins() {
        TreeSet<String> names = new TreeSet<>();
        routes.forEach(r -> names.add(r.origin()));
        return List.copyOf(names);
    }

    List<String> destinationsFrom(String origin) {
        TreeSet<String> names = new TreeSet<>();
        routes.stream().filter(r -> r.origin().equalsIgnoreCase(origin))
                .forEach(r -> names.add(r.destination()));
        return List.copyOf(names);
    }

    List<Route> routesBetween(String origin, String destination) {
        return routes.stream()
                .filter(r -> r.origin().equalsIgnoreCase(origin)
                        && r.destination().equalsIgnoreCase(destination))
                .toList();
    }

    Optional<Route> findRoute(String carrierCode, String origin, String destination) {
        return routesBetween(origin, destination).stream()
                .filter(r -> r.carrier().code().equalsIgnoreCase(carrierCode))
                .findFirst();
    }

    long carrierCount() {
        return routes.stream().map(r -> r.carrier().code()).distinct().count();
    }

    // The transport modes offered between two cities (all modes when either city is null)
    Set<TransportMode> modes(String origin, String destination) {
        Set<TransportMode> modes = EnumSet.noneOf(TransportMode.class);
        for (Route r : origin == null || destination == null ? routes : routesBetween(origin, destination)) {
            modes.add(r.mode());
        }
        return modes;
    }

    // ------------------------------------------------------------------ quotes

    long saveQuote(ShipmentQuote quote) {
        String sql = "INSERT INTO quote (created_at, carrier_code, carrier_name, mode, origin, destination,"
                + " parcel_count, chargeable_kg, fragile, express, total, receipt)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Route route = quote.route();
        try (PreparedStatement insert = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().withNano(0)));
            insert.setString(2, route.carrier().code());
            insert.setString(3, route.carrier().name());
            insert.setString(4, route.mode().name());
            insert.setString(5, route.origin());
            insert.setString(6, route.destination());
            insert.setInt(7, quote.parcels().size());
            insert.setBigDecimal(8, BigDecimal.valueOf(quote.chargeableWeightKg()).setScale(2, RoundingMode.HALF_UP));
            insert.setBoolean(9, quote.shipment().fragile());
            insert.setBoolean(10, quote.shipment().express());
            insert.setBigDecimal(11, quote.total());
            insert.setString(12, ReceiptPrinter.receipt(quote));
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not save the quote: " + firstLine(e.getMessage()), e);
        }
    }

    List<SavedQuote> recentQuotes(int limit) {
        String sql = "SELECT id, created_at, carrier_name, mode, origin, destination, parcel_count,"
                + " chargeable_kg, fragile, express, total, receipt FROM quote ORDER BY id DESC LIMIT ?";
        List<SavedQuote> result = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setInt(1, limit);
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    String mode = rs.getString(4);
                    result.add(new SavedQuote(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                            rs.getString(3), mode == null ? null : TransportMode.fromCode(mode),
                            rs.getString(5), rs.getString(6), rs.getInt(7), rs.getBigDecimal(8),
                            rs.getBoolean(9), rs.getBoolean(10), rs.getBigDecimal(11), rs.getString(12)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not read the quote history: " + firstLine(e.getMessage()), e);
        }
        return result;
    }

    int clearHistory() {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate("DELETE FROM quote");
        } catch (SQLException e) {
            throw new DatabaseException("Could not clear the history: " + firstLine(e.getMessage()), e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do while shutting down
        }
    }

    // ------------------------------------------------------------------ setup

    private void createAndSeed() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            String version = tableExists("APP_INFO") ? seedVersion(statement) : null;
            boolean reseed = !SEED_VERSION.equals(version);
            boolean legacyQuotes = columnExists("QUOTE", "ROUTE_ID");

            if (legacyQuotes) {
                // Version 1 linked quotes to route ids; copy the names out before the routes are replaced
                statement.execute("CREATE TABLE quote_legacy AS SELECT q.id, q.created_at,"
                        + " c.code AS carrier_code, c.name AS carrier_name,"
                        + " CASE WHEN c.code IN ('SWIFT', 'SAVX') THEN 'COURIER' WHEN c.code = 'KILI' THEN 'AIR'"
                        + " WHEN c.code = 'COAST' AND r.destination IN ('Dar es Salaam', 'Zanzibar') THEN 'SEA'"
                        + " ELSE 'ROAD' END AS mode, r.origin, r.destination, q.parcel_count, q.chargeable_kg,"
                        + " q.fragile, q.express, q.total, q.receipt"
                        + " FROM quote q JOIN route r ON r.id = q.route_id JOIN carrier c ON c.code = r.carrier_code");
                statement.execute("DROP TABLE quote");
            }
            if (reseed) {
                for (String table : new String[] {"route", "tariff_band", "tariff", "carrier"}) {
                    statement.execute("DROP TABLE IF EXISTS " + table + " CASCADE");
                }
            }

            statement.execute("RUNSCRIPT FROM 'classpath:/db/schema.sql'");

            if (legacyQuotes) {
                statement.execute("INSERT INTO quote (id, created_at, carrier_code, carrier_name, mode, origin,"
                        + " destination, parcel_count, chargeable_kg, fragile, express, total, receipt)"
                        + " SELECT * FROM quote_legacy");
                statement.execute("DROP TABLE quote_legacy");
                try (ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM quote")) {
                    rs.next();
                    statement.execute("ALTER TABLE quote ALTER COLUMN id RESTART WITH " + rs.getLong(1));
                }
            }
            if (reseed) {
                statement.execute("RUNSCRIPT FROM 'classpath:/db/seed.sql'");
                statement.execute("MERGE INTO app_info (info_name, info_value) KEY (info_name) VALUES ('seed_version', '"
                        + SEED_VERSION + "')");
            }
        }
    }

    private static String seedVersion(Statement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT info_value FROM app_info WHERE info_name = 'seed_version'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private boolean tableExists(String table) throws SQLException {
        return count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'"
                + " AND TABLE_NAME = ?", table) > 0;
    }

    private boolean columnExists(String table, String column) throws SQLException {
        return count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC'"
                + " AND TABLE_NAME = ? AND COLUMN_NAME = '" + column + "'", table) > 0;
    }

    private int count(String sql, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, table);
            try (ResultSet rs = query.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<Route> loadRoutes() throws SQLException {
        Map<String, List<RateTable.Band>> bands = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT tariff_code, up_to_kg, price FROM tariff_band ORDER BY tariff_code, up_to_kg")) {
            while (rs.next()) {
                bands.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                        .add(new RateTable.Band(rs.getBigDecimal(2).doubleValue(), rs.getBigDecimal(3)));
            }
        }

        String sql = "SELECT r.id, r.mode, r.origin, r.destination, r.transit_days, r.express_days,"
                + " c.code, c.name, c.fragile_fee, c.express_rate,"
                + " t.code, t.description, t.extra_per_kg"
                + " FROM route r JOIN carrier c ON c.code = r.carrier_code JOIN tariff t ON t.code = r.tariff_code"
                + " ORDER BY r.origin, r.destination, r.mode, c.name";
        Map<String, Carrier> carriers = new HashMap<>();
        Map<String, RateTable> tariffs = new HashMap<>();
        List<Route> loaded = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String carrierCode = rs.getString(7);
                Carrier carrier = carriers.get(carrierCode);
                if (carrier == null) {
                    carrier = new Carrier(carrierCode, rs.getString(8), rs.getBigDecimal(9), rs.getBigDecimal(10));
                    carriers.put(carrierCode, carrier);
                }
                String tariffCode = rs.getString(11);
                RateTable rates = tariffs.get(tariffCode);
                if (rates == null) {
                    List<RateTable.Band> tariffBands = bands.get(tariffCode);
                    if (tariffBands == null) {
                        throw new SQLException("Tariff " + tariffCode + " has no weight bands");
                    }
                    rates = new RateTable(tariffBands, rs.getBigDecimal(13));
                    tariffs.put(tariffCode, rates);
                }
                int expressDays = rs.getInt(6);
                Integer express = rs.wasNull() ? null : expressDays;
                loaded.add(new Route(rs.getInt(1), carrier, TransportMode.fromCode(rs.getString(2)),
                        rs.getString(3), rs.getString(4), rs.getString(12), rs.getInt(5), express, rates));
            }
        }
        return loaded;
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        int end = message.indexOf('\n');
        return (end < 0 ? message : message.substring(0, end)).trim();
    }
}
