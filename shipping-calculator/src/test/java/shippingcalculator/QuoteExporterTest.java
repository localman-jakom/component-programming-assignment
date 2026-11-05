package shippingcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Saving quotes to text and CSV files, including the error cases.
class QuoteExporterTest {

    @TempDir
    Path tempDir;

    private final ShipmentQuote quote = new ShippingCalculator().quote(new Shipment(List.of(
            new PackageDetails(2, 30, 20, 10),
            new PackageDetails(1, 50, 40, 30)), false, true), TestData.route(true));

    @Test
    @DisplayName("A .txt file holds the printed receipt")
    void textFile() throws Exception {
        Path written = QuoteExporter.export(quote, tempDir.resolve("quote.txt"));
        String text = Files.readString(written, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("Quote generated "));
        assertTrue(text.contains("NEXUS GLOBEX TRANSIT - Global Delivery Systems"));
        assertTrue(text.contains("TOTAL:                 KES 5,100.00"), text);
    }

    @Test
    @DisplayName("A .csv file has a header, one row per parcel and a total row")
    void csvFile() throws Exception {
        Path written = QuoteExporter.export(quote, tempDir.resolve("QUOTE.CSV"));
        List<String> lines = Files.readAllLines(written, StandardCharsets.UTF_8);
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).startsWith("carrier,transport,origin,destination"));
        assertEquals("SwiftParcel Kenya,Courier,Nairobi,Mombasa,Express,1,1,2.000,1.200,2.000,900.00,0.00,450.00,"
                + "1350.00", lines.get(1));
        assertTrue(lines.get(3).endsWith(",TOTAL,,,14.000,3400.00,0.00,1700.00,5100.00"), lines.get(3));
    }

    @Test
    @DisplayName("A .pdf file is a valid one-page PDF with the logo and the quote figures")
    void pdfFile() throws Exception {
        Path written = QuoteExporter.export(quote, tempDir.resolve("quote.PDF"));
        byte[] bytes = Files.readAllBytes(written);
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);

        assertTrue(raw.startsWith("%PDF-1.4"));
        assertTrue(raw.trim().endsWith("%%EOF"));
        assertTrue(raw.contains("/Type /Page "));
        assertTrue(raw.contains("/Subtype /Image") && raw.contains("/DCTDecode"), "logo image embedded");
        assertTrue(raw.contains("(SHIPPING QUOTATION)"));
        assertTrue(raw.contains("(KES 5,100.00)"));
        assertTrue(raw.contains("(SwiftParcel Kenya \\(SWIFT\\))"));

        // The cross-reference table must point at the real byte offsets of every object
        int startxref = Integer.parseInt(raw.substring(raw.lastIndexOf("startxref") + 9, raw.lastIndexOf("%%EOF")).trim());
        assertTrue(raw.startsWith("xref", startxref));
        String[] entries = raw.substring(startxref).split("\n");
        int objects = Integer.parseInt(entries[1].split(" ")[1]);
        for (int i = 1; i < objects; i++) {
            int offset = Integer.parseInt(entries[2 + i].substring(0, 10));
            assertTrue(raw.startsWith(i + " 0 obj", offset), "object " + i);
        }
    }

    @Test
    @DisplayName("PDF text escapes brackets and backslashes and replaces other characters")
    void pdfEscaping() {
        assertEquals("a\\(b\\)c\\\\d?", PdfReceipt.escape("a(b)c\\d\u00d7"));
    }

    @Test
    @DisplayName("Missing folders are created")
    void createsFolders() throws Exception {
        Path written = QuoteExporter.export(quote, tempDir.resolve("a/b/quote.txt"));
        assertTrue(Files.exists(written));
    }

    @Test
    @DisplayName("A folder name instead of a file name is refused with a clear message")
    void folderIsRefused() {
        QuoteExporter.ExportException e = assertThrows(QuoteExporter.ExportException.class,
                () -> QuoteExporter.export(quote, tempDir));
        assertTrue(e.getMessage().contains("is a folder"));
    }

    @Test
    @DisplayName("Writing inside a file (not a folder) fails with a clear message")
    void parentIsAFile() throws Exception {
        Path file = Files.writeString(tempDir.resolve("plain.txt"), "x");
        QuoteExporter.ExportException e = assertThrows(QuoteExporter.ExportException.class,
                () -> QuoteExporter.export(quote, file.resolve("quote.txt")));
        assertTrue(e.getMessage().startsWith("Could not save the quote"), e.getMessage());
    }

    @Test
    @DisplayName("CSV cells with commas or quotes are escaped")
    void csvEscaping() {
        assertEquals("plain", QuoteExporter.cell("plain"));
        assertEquals("\"Smith, Jones\"", QuoteExporter.cell("Smith, Jones"));
        assertEquals("\"say \"\"hi\"\"\"", QuoteExporter.cell("say \"hi\""));
    }
}
