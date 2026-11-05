package shippingcalculator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Saves a quote to a file: PDF for .pdf, CSV for .csv, otherwise a plain-text receipt.
final class QuoteExporter {

    // Checked so every caller has to decide how to tell the user
    static final class ExportException extends Exception {
        private static final long serialVersionUID = 1L;

        ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private QuoteExporter() {
    }

    static Path export(ShipmentQuote quote, Path file) throws ExportException {
        Path target = file.toAbsolutePath().normalize();
        if (Files.isDirectory(target)) {
            throw new ExportException(target + " is a folder. Please give a file name.", null);
        }
        byte[] content = switch (format(target)) {
            case "pdf" -> PdfReceipt.render(quote, LocalDateTime.now());
            case "csv" -> toCsv(quote).getBytes(StandardCharsets.UTF_8);
            default -> toText(quote).getBytes(StandardCharsets.UTF_8);
        };
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, content);
            return target;
        } catch (AccessDeniedException denied) {
            throw new ExportException("Not allowed to write to " + target + ".", denied);
        } catch (IOException | SecurityException problem) {
            throw new ExportException("Could not save the quote to " + target + ": "
                    + problem.getMessage(), problem);
        }
    }

    // "pdf", "csv" or "txt", from the file name
    static String format(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return "pdf";
        }
        return name.endsWith(".csv") ? "csv" : "txt";
    }

    static String toText(ShipmentQuote quote) {
        return "Quote generated " + LocalDateTime.now().format(STAMP) + "\n\n" + ReceiptPrinter.receipt(quote);
    }

    static String toCsv(ShipmentQuote quote) {
        Route route = quote.route();
        StringBuilder csv = new StringBuilder();
        csv.append("carrier,transport,origin,destination,service,delivery_days,item,actual_kg,volumetric_kg,"
                + "chargeable_kg,base_kes,fragile_kes,express_kes,total_kes\n");
        for (int i = 0; i < quote.parcels().size(); i++) {
            CostBreakdown cost = quote.parcels().get(i);
            csv.append(String.join(",",
                    cell(route.carrier().name()), route.mode().label(), cell(route.origin()),
                    cell(route.destination()), quote.serviceName(), String.valueOf(quote.deliveryDays()),
                    String.valueOf(i + 1),
                    decimal(cost.actualWeightKg()), decimal(cost.volumetricWeightKg()),
                    decimal(cost.chargeableWeightKg()), cost.baseCost().toPlainString(),
                    cost.fragileSurcharge().toPlainString(), cost.expressSurcharge().toPlainString(),
                    cost.total().toPlainString())).append('\n');
        }
        csv.append(String.join(",",
                cell(route.carrier().name()), route.mode().label(), cell(route.origin()),
                cell(route.destination()), quote.serviceName(), String.valueOf(quote.deliveryDays()), "TOTAL",
                "", "", decimal(quote.chargeableWeightKg()), quote.baseCost().toPlainString(),
                quote.fragileSurcharge().toPlainString(), quote.expressSurcharge().toPlainString(),
                quote.total().toPlainString())).append('\n');
        return csv.toString();
    }

    // Quotes a CSV cell when it contains a comma, quote or line break
    static String cell(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String decimal(double value) {
        return String.format(Locale.US, "%.3f", value);
    }
}
