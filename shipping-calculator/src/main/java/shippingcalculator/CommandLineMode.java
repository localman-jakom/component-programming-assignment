package shippingcalculator;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import unitconverter.exceptions.UnitConverterException;

// One-shot, scriptable mode, e.g.
//   java -jar shipping-calculator-1.0.jar --from Mombasa --to Dubai --weight "2 tonne"
//        --length "2 m" --width "1.2 m" --height "1 m" --mode sea --save quote.pdf
// Repeat --weight/--length/--width/--height to add more items (one of each per item).
final class CommandLineMode {

    static final int OK = 0;
    static final int FAILED = 1;
    static final int USAGE = 2;

    private final PrintStream out;
    private final PrintStream err;
    private final ShippingDatabase database;
    private final UnitNormaliser normaliser = new UnitNormaliser();
    private final ShippingCalculator calculator = new ShippingCalculator();

    CommandLineMode(PrintStream out, PrintStream err, ShippingDatabase database) {
        this.out = out;
        this.err = err;
        this.database = database;
    }

    // Parsed options; lists keep the order the user typed them
    private static final class Options {
        String from;
        String to;
        String carrier;
        String mode;
        String save;
        boolean fragile;
        boolean express;
        boolean listRoutes;
        boolean help;
        final List<String> weights = new ArrayList<>();
        final List<String> lengths = new ArrayList<>();
        final List<String> widths = new ArrayList<>();
        final List<String> heights = new ArrayList<>();
    }

    int run(String[] args) {
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException problem) {
            return usageError(problem.getMessage());
        }
        if (options.help) {
            out.print(usage());
            return OK;
        }
        if (options.listRoutes) {
            out.print(ReceiptPrinter.routes(database.routes()));
            return OK;
        }
        try {
            return quote(options);
        } catch (IllegalArgumentException | UnitConverterException problem) {
            return usageError(problem.getMessage());
        }
    }

    private int quote(Options options) {
        if (options.from == null || options.to == null) {
            throw new IllegalArgumentException("--from and --to are required (see --list-routes).");
        }
        List<Route> routes = database.routesBetween(options.from, options.to);
        if (routes.isEmpty()) {
            throw new IllegalArgumentException("No carrier serves " + options.from + " -> " + options.to
                    + ". Use --list-routes to see the routes.");
        }
        String path = routes.get(0).path();
        if (options.mode != null) {
            TransportMode mode = parseMode(options.mode);
            routes = routes.stream().filter(r -> r.mode() == mode).toList();
            if (routes.isEmpty()) {
                throw new IllegalArgumentException("No " + mode.label().toLowerCase(Locale.ROOT) + " on " + path + ".");
            }
        }

        Shipment shipment = new Shipment(readParcels(options), options.fragile, options.express);
        ShipmentQuote chosen;
        if (options.carrier != null) {
            List<Route> carrierRoutes = routes.stream()
                    .filter(r -> r.carrier().code().equalsIgnoreCase(options.carrier)).toList();
            if (carrierRoutes.isEmpty()) {
                List<String> codes = routes.stream().map(r -> r.carrier().code()).distinct().toList();
                throw new IllegalArgumentException("Carrier " + options.carrier + " does not serve "
                        + path + ". Choose one of: " + String.join(", ", codes));
            }
            ShippingCalculator.Comparison carrierQuotes = calculator.compare(shipment, carrierRoutes);
            if (carrierQuotes.quotes().isEmpty()) {
                throw new IllegalArgumentException("Carrier " + options.carrier.toUpperCase(Locale.ROOT)
                        + " cannot take this shipment: " + String.join("; ", carrierQuotes.skipped()));
            }
            chosen = carrierQuotes.quotes().get(0);
        } else {
            ShippingCalculator.Comparison comparison = calculator.compare(shipment, routes);
            if (comparison.quotes().isEmpty()) {
                throw new IllegalArgumentException("No carrier on " + path + " can take this shipment: "
                        + String.join("; ", comparison.skipped()));
            }
            out.println("Carriers for " + path + " (cheapest first):");
            out.print(ReceiptPrinter.comparison(comparison.quotes()));
            comparison.skipped().forEach(reason -> out.println("  Not available: " + reason));
            out.println();
            chosen = comparison.quotes().get(0);
        }
        out.print(ReceiptPrinter.receipt(chosen));

        if (options.save != null) {
            try {
                out.println("Written to " + QuoteExporter.export(chosen, Paths.get(options.save)));
            } catch (QuoteExporter.ExportException | InvalidPathException problem) {
                err.println("Error: " + problem.getMessage());
                return FAILED;
            }
        }
        return OK;
    }

    private List<PackageDetails> readParcels(Options o) {
        int count = o.weights.size();
        if (count == 0) {
            throw new IllegalArgumentException("Give at least one item: --weight, --length, --width and --height.");
        }
        if (o.lengths.size() != count || o.widths.size() != count || o.heights.size() != count) {
            throw new IllegalArgumentException("Each item needs one --weight, --length, --width and --height"
                    + " (got " + count + ", " + o.lengths.size() + ", " + o.widths.size() + ", "
                    + o.heights.size() + ").");
        }
        ShippingLimits.checkParcelCount(count);
        List<PackageDetails> parcels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String which = count > 1 ? "Item " + (i + 1) + ": " : "";
            try {
                parcels.add(new PackageDetails(
                        normaliser.toKilograms(Measurement.parse(o.weights.get(i))),
                        normaliser.toCentimetres(Measurement.parse(o.lengths.get(i))),
                        normaliser.toCentimetres(Measurement.parse(o.widths.get(i))),
                        normaliser.toCentimetres(Measurement.parse(o.heights.get(i)))));
            } catch (IllegalArgumentException problem) {
                throw new IllegalArgumentException(which + problem.getMessage(), problem);
            }
        }
        return parcels;
    }

    private static Options parse(String[] args) {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            switch (arg) {
                case "--fragile" -> o.fragile = true;
                case "--express" -> o.express = true;
                case "--list-routes" -> o.listRoutes = true;
                case "--help", "-h" -> o.help = true;
                case "--from" -> o.from = value(args, ++i, arg);
                case "--to" -> o.to = value(args, ++i, arg);
                case "--carrier" -> o.carrier = value(args, ++i, arg);
                case "--mode" -> o.mode = value(args, ++i, arg);
                case "--save" -> o.save = value(args, ++i, arg);
                case "--weight" -> o.weights.add(value(args, ++i, arg));
                case "--length" -> o.lengths.add(value(args, ++i, arg));
                case "--width" -> o.widths.add(value(args, ++i, arg));
                case "--height" -> o.heights.add(value(args, ++i, arg));
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        return o;
    }

    private static TransportMode parseMode(String text) {
        try {
            return TransportMode.fromCode(text);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown --mode " + text + ". Use courier, road, air or sea.");
        }
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " needs a value.");
        }
        return args[index];
    }

    private int usageError(String message) {
        err.println("Error: " + message);
        err.println("Run with --help for usage.");
        return USAGE;
    }

    static String usage() {
        return """
                Usage:
                  java -jar shipping-calculator-1.0.jar                 open the window
                  java -jar shipping-calculator-1.0.jar --console       interactive text version
                  java -jar shipping-calculator-1.0.jar --list-routes   show carriers, routes and tariffs
                  java -jar shipping-calculator-1.0.jar --from CITY --to CITY
                       --weight "2 kg" --length "40 cm" --width "30 cm" --height "20 cm"
                       [--carrier CODE] [--mode courier|road|air|sea] [--fragile] [--express]
                       [--save FILE.pdf|FILE.txt|FILE.csv]

                Repeat --weight/--length/--width/--height for each extra item (up to 10).
                Heavy or large items are offered only by transport that can carry them:
                courier up to 70 kg, air up to 1 t, road (truck) up to 24 t, sea (container) up to 26.5 t.
                Without --carrier every carrier on the route is compared and the cheapest is shown in full.
                Exit codes: 0 success, 1 could not save the file, 2 bad input.
                """;
    }
}
