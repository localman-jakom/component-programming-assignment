package shippingcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Runs whole console sessions: typed input -> library conversion -> pricing -> printed quote,
// plus saving to the database and exporting to a file.
class MainEndToEndTest {

    @TempDir
    Path tempDir;

    private final ShippingDatabase db = ShippingDatabase.openInMemory();

    @AfterEach
    void close() {
        db.close();
    }

    private String session(String... lines) {
        String typed = String.join("\n", lines) + "\n";
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        new Main(new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(printed, true, StandardCharsets.UTF_8), db).run();
        return printed.toString(StandardCharsets.UTF_8);
    }

    private String origin(String city) {
        return String.valueOf(db.origins().indexOf(city) + 1);
    }

    private String destination(String from, String to) {
        return String.valueOf(db.destinationsFrom(from).indexOf(to) + 1);
    }

    @Test
    @DisplayName("Happy path: one parcel Nairobi -> Mombasa, pick the cheapest, save and export")
    void fullSession() throws Exception {
        Path csv = tempDir.resolve("quote.csv");
        String out = session(
                origin("Nairobi"), destination("Nairobi", "Mombasa"),
                "1",                                  // one item
                "2 kg", "30 cm", "20 cm", "10 cm",
                "n", "n",                             // not fragile, not express
                "1",                                  // cheapest: SwiftParcel courier
                "y",                                  // save to history
                "y", csv.toString(),                  // export
                "n");                                 // no more shipments

        assertTrue(out.contains("Available: Courier, Road freight"), out);
        assertTrue(out.contains("Carriers for Nairobi -> Mombasa"), out);
        assertTrue(out.contains(" 1   SwiftParcel Kenya            Courier      Standard  2 days"), out);
        assertTrue(out.contains("KES 900.00  <- cheapest"), out);
        assertTrue(out.contains("Rift Valley Road Freight     Road freight"), out);
        assertTrue(out.contains("NEXUS GLOBEX TRANSIT - Global Delivery Systems"), out);
        assertTrue(out.contains(" TOTAL:                 KES 900.00"), out);
        assertTrue(out.contains("Saved as quote #1."), out);
        assertTrue(out.contains("Written to"), out);
        assertTrue(out.trim().endsWith("Goodbye."));

        assertEquals(1, db.recentQuotes(10).size());
        assertTrue(Files.readString(csv).contains("SwiftParcel Kenya,Courier,Nairobi,Mombasa,Standard,2,1,"));
    }

    @Test
    @DisplayName("Bad answers are re-asked: text, over-limit weight, wrong kind of unit, too-long side")
    void recoversFromBadInput() {
        String out = session(
                origin("Nairobi"), destination("Nairobi", "Mombasa"),
                "one", "1",
                "abc", "30 tonne", "5 cm", "4.4 lb",  // ends as 4.4 lb = 1.996 kg
                "14 m", "40 cm", "30 cm", "20 cm",    // 14 m is longer than a truck trailer
                "y", "y",                             // fragile and express
                "1",
                "n", "n", "n");

        assertTrue(out.contains("Please enter a number from 1 to 10."), out);
        assertTrue(out.contains("Please type a number followed by a unit"), out);
        assertTrue(out.contains("Weight 30,000.00 kg is over the 26,500 kg limit"), out);
        assertTrue(out.contains("Could not use that: Cannot convert cm"), out);
        assertTrue(out.contains("Length 1,400.00 cm is over the 1,360 cm limit"), out);
        // 40x30x20 / 5000 = 4.8 kg -> 5.0 kg; SwiftParcel: 900 + 300 fragile + 50% express (450) = 1,650
        assertTrue(out.contains(" TOTAL:                 KES 1,650.00"), out);
    }

    @Test
    @DisplayName("Heavy machinery Mombasa -> Rotterdam goes by sea; a pallet Nairobi -> Mombasa only by truck")
    void heavyFreight() {
        String out = session(
                origin("Mombasa"), destination("Mombasa", "Rotterdam"),
                "1", "12 tonne", "6 m", "2.2 m", "2.2 m", "n", "n",
                "n", "n",
                "y",
                origin("Nairobi"), destination("Nairobi", "Mombasa"),
                "1", "1.5 tonne", "120 cm", "100 cm", "150 cm", "n", "n",
                "1",
                "n", "n", "n");

        assertTrue(out.contains("Indian Ocean Container Lines (IOCL)"), out);
        assertTrue(out.contains(" Transport:             Sea freight"), out);
        // 600 x 220 x 220 / 1000 = 29,040 kg volumetric: 230,000 + 24,040 kg x 33 = 1,023,320
        assertTrue(out.contains(" TOTAL:                 KES 1,023,320.00"), out);
        assertTrue(out.contains("Not available: SwiftParcel Kenya (courier): Courier takes items up to 70 kg"), out);
        assertTrue(out.contains(" Transport:             Road freight"), out);
    }

    @Test
    @DisplayName("Two items with no express service on the route fall back to standard delivery")
    void multiParcelWithoutExpress() {
        String out = session(
                origin("Nairobi"), destination("Nairobi", "Nakuru"),
                "2",
                "3 kg", "30 cm", "20 cm", "10 cm",
                "10 lb", "60 cm", "40 cm", "40 cm",
                "n", "y",
                "1",
                "n", "n", "n");

        assertTrue(out.contains("No carrier can take this shipment by express on Nairobi -> Nakuru"), out);
        assertTrue(out.contains("-- Item 2 of 2 --"), out);
        assertTrue(out.contains(" Items:                 2"), out);
        assertTrue(out.contains("Item total:"), out);
    }

    @Test
    @DisplayName("A failed export is reported and can be retried; a PDF can then be written")
    void exportFailureIsReported() {
        Path pdf = tempDir.resolve("quote.pdf");
        String out = session(
                origin("Nairobi"), destination("Nairobi", "London"),
                "1", "500 g", "20 cm", "15 cm", "5 cm", "n", "n",
                "1",                                  // air is cheapest for a small parcel
                "n",
                "y", tempDir.toString(),              // a folder, not a file
                "y", pdf.toString(),
                "n");
        assertTrue(out.contains("is a folder"), out);
        assertTrue(out.contains("Kilimanjaro Air Freight (KILI)"), out);
        assertTrue(out.contains(" TOTAL:                 KES 4,500.00"), out);
        assertTrue(Files.exists(pdf));
    }

    @Test
    @DisplayName("Input ending mid-session finishes cleanly")
    void inputEnds() {
        String out = session(origin("Mombasa"));
        assertTrue(out.contains("Input ended."));
        assertTrue(out.trim().endsWith("Goodbye."));
    }
}
