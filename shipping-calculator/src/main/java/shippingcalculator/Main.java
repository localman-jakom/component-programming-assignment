package shippingcalculator;

import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.DoubleConsumer;
import java.util.function.ToDoubleFunction;

// The library's exception base class: used to show friendly messages
import unitconverter.exceptions.UnitConverterException;

// Entry point and the interactive console session.
//   ConsoleInput -> UnitNormaliser (library) -> ShippingCalculator -> ReceiptPrinter
//   (ask)           (kg and cm)                 (price per route)     (show)
// Streams and database are passed in so a whole session can run inside a test.
public final class Main {

    private final ConsoleInput input;
    private final PrintStream out;
    private final ShippingDatabase database;
    private final UnitNormaliser normaliser = new UnitNormaliser();
    private final ShippingCalculator calculator = new ShippingCalculator();

    Main(InputStream in, PrintStream out, ShippingDatabase database) {
        this.input = new ConsoleInput(in, out);
        this.out = out;
        this.database = database;
    }

    // No arguments: window (or console when there is no screen). --console: text version.
    // Anything else: one-shot command-line mode, see CommandLineMode.
    public static void main(String[] args) {
        boolean noArgs = args.length == 0;
        boolean wantsConsole = args.length == 1 && "--console".equalsIgnoreCase(args[0]);
        boolean wantsGui = args.length == 1 && "--gui".equalsIgnoreCase(args[0]);

        if ((noArgs || wantsGui) && !GraphicsEnvironment.isHeadless()) {
            ShippingGui.launch(ShippingDatabase.openDefault(System.err));
        } else if (noArgs || wantsConsole || wantsGui) {
            try (ShippingDatabase db = ShippingDatabase.openDefault(System.out)) {
                new Main(System.in, System.out, db).run();
            }
        } else {
            int status;
            try (ShippingDatabase db = ShippingDatabase.openDefault(System.err)) {
                status = new CommandLineMode(System.out, System.err, db).run(args);
            }
            System.exit(status);
        }
    }

    void run() {
        out.println("=== " + Brand.NAME + " | Shipping Quotation (powered by unitconverter-1.0.jar) ===");
        out.println(database.routes().size() + " routes from " + database.carrierCount()
                + " carriers by courier, road, air and sea"
                + (database.isTemporary() ? " (temporary database: saved quotes are not kept)" : ""));
        out.println("Weight units: kg, g, lb, oz, tonne     Size units: cm, m, inch, foot");
        out.println("Limits per item by transport:");
        for (String line : ShippingLimits.summary().split("\n")) {
            out.println("  " + line);
        }

        try {
            do {
                quoteOneShipment();
            } while (input.readYesNo("\nQuote another shipment? (y/n): "));
        } catch (NoSuchElementException endOfInput) {
            out.println("\nInput ended.");
        }
        out.println("Goodbye.");
    }

    private void quoteOneShipment() {
        // 1. ROUTE: pick from the cities in the database
        String origin = choose("\nWhere from?", database.origins());
        String destination = choose("Where to?", database.destinationsFrom(origin));
        List<Route> routes = database.routesBetween(origin, destination);
        out.println("  Available: " + modeList(origin, destination));

        // 2. ITEMS: each answer is converted to kg or cm by the library, then checked against the limits
        int count = input.readChoice("How many items? (1-" + ShippingLimits.MAX_PARCELS + "): ",
                ShippingLimits.MAX_PARCELS);
        List<PackageDetails> parcels = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            if (count > 1) {
                out.println("-- Item " + i + " of " + count + " --");
            }
            parcels.add(readParcel());
        }
        boolean fragile = input.readYesNo("Fragile handling? (y/n): ");
        boolean express = input.readYesNo("Express delivery? (y/n): ");

        // 3. PRICE on every carrier and transport mode that can take the shipment
        Shipment shipment = new Shipment(parcels, fragile, express);
        ShippingCalculator.Comparison comparison = calculator.compare(shipment, routes);
        if (comparison.quotes().isEmpty() && express) {
            out.println("No carrier can take this shipment by express on " + origin + " -> " + destination
                    + ". Trying standard delivery.");
            shipment = new Shipment(parcels, fragile, false);
            comparison = calculator.compare(shipment, routes);
        }
        if (comparison.quotes().isEmpty()) {
            out.println("No carrier on " + origin + " -> " + destination + " can take this shipment:");
            comparison.skipped().forEach(reason -> out.println("  - " + reason));
            return;
        }
        List<ShipmentQuote> quotes = comparison.quotes();
        out.println("\nCarriers for " + origin + " -> " + destination + " (cheapest first):");
        out.print(ReceiptPrinter.comparison(quotes));
        comparison.skipped().forEach(reason -> out.println("  Not available: " + reason));

        // 4. OUTPUT the chosen carrier's full quote
        ShipmentQuote chosen = quotes.size() == 1 ? quotes.get(0)
                : quotes.get(input.readChoice("Full quote for which carrier? (1-" + quotes.size() + "): ",
                        quotes.size()) - 1);
        out.println();
        out.print(ReceiptPrinter.receipt(chosen));
        offerToKeep(chosen);
    }

    private String modeList(String origin, String destination) {
        List<String> labels = new ArrayList<>();
        database.modes(origin, destination).forEach(m -> labels.add(m.label()));
        return String.join(", ", labels);
    }

    private PackageDetails readParcel() {
        while (true) {
            double weightKg = askUntilValid("Weight (e.g. 2.5 kg): ", "kg, g, lb, oz or tonne",
                    normaliser::toKilograms, ShippingLimits::checkWeightKg);
            double lengthCm = askUntilValid("Length (e.g. 40 cm): ", "cm, m, inch or foot",
                    normaliser::toCentimetres, cm -> ShippingLimits.checkSideCm("Length", cm));
            double widthCm = askUntilValid("Width  (e.g. 30 cm): ", "cm, m, inch or foot",
                    normaliser::toCentimetres, cm -> ShippingLimits.checkSideCm("Width", cm));
            double heightCm = askUntilValid("Height (e.g. 20 cm): ", "cm, m, inch or foot",
                    normaliser::toCentimetres, cm -> ShippingLimits.checkSideCm("Height", cm));
            try {
                return new PackageDetails(weightKg, lengthCm, widthCm, heightCm);
            } catch (IllegalArgumentException problem) {
                // Should not happen after the checks above, but never crash on bad input
                out.println("  " + problem.getMessage() + " Please enter the item again.");
            }
        }
    }

    // Asks until the library can convert the answer and it is within the business limits
    private double askUntilValid(String prompt, String allowedUnits,
                                 ToDoubleFunction<Measurement> conversion, DoubleConsumer limitCheck) {
        while (true) {
            Measurement typed = input.readMeasurement(prompt);
            try {
                double converted = conversion.applyAsDouble(typed);
                limitCheck.accept(converted);
                return converted;
            } catch (UnitConverterException problem) {
                out.println("  Could not use that: " + problem.getMessage());
                out.println("  Please use one of: " + allowedUnits);
            } catch (IllegalArgumentException overLimit) {
                out.println("  " + overLimit.getMessage());
            }
        }
    }

    private String choose(String title, List<String> options) {
        out.println(title);
        for (int i = 0; i < options.size(); i++) {
            out.println("  " + (i + 1) + ") " + options.get(i));
        }
        return options.get(input.readChoice("Choose 1-" + options.size() + ": ", options.size()) - 1);
    }

    private void offerToKeep(ShipmentQuote quote) {
        if (input.readYesNo("Save this quote to the history? (y/n): ")) {
            try {
                out.println("  Saved as quote #" + database.saveQuote(quote) + ".");
            } catch (ShippingDatabase.DatabaseException problem) {
                out.println("  " + problem.getMessage());
            }
        }
        while (input.readYesNo("Export this quote to a file? (y/n): ")) {
            String name = input.readText("File name (ending .pdf, .txt or .csv): ");
            try {
                Path file = Paths.get(name);
                out.println("  Written to " + QuoteExporter.export(quote, file));
                return;
            } catch (QuoteExporter.ExportException | java.nio.file.InvalidPathException problem) {
                out.println("  " + problem.getMessage());
            }
        }
    }
}
