package shippingcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The one-shot command-line mode: options in, quote out, exit code back.
class CommandLineModeTest {

    @TempDir
    Path tempDir;

    private final ShippingDatabase db = ShippingDatabase.openInMemory();
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @AfterEach
    void close() {
        db.close();
    }

    private int run(String... args) {
        return new CommandLineMode(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8), db).run(args);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private static String[] parcel(String... extra) {
        String[] base = {"--from", "Nairobi", "--to", "Mombasa",
                "--weight", "2 kg", "--length", "30 cm", "--width", "20 cm", "--height", "10 cm"};
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    @Test
    @DisplayName("--help and --list-routes")
    void helpAndRoutes() {
        assertEquals(CommandLineMode.OK, run("--help"));
        assertTrue(out().contains("Usage:"));
        assertEquals(CommandLineMode.OK, run("--list-routes"));
        assertTrue(out().contains("Kilimanjaro Air Freight"));
        assertTrue(out().contains("Nairobi -> London"));
    }

    @Test
    @DisplayName("With --carrier: just that carrier's quote")
    void chosenCarrier() {
        assertEquals(CommandLineMode.OK, run(parcel("--carrier", "swift")));
        assertTrue(out().contains(" TOTAL:                 KES 900.00"), out());
        assertFalse(out().contains("cheapest"));
    }

    @Test
    @DisplayName("Without --carrier: comparison plus the cheapest quote in full")
    void comparison() {
        assertEquals(CommandLineMode.OK, run(parcel("--fragile", "--express")));
        assertTrue(out().contains("<- cheapest"));
        assertTrue(out().contains("SwiftParcel Kenya (SWIFT)"));
        assertTrue(out().contains("KES 1,650.00"));
        assertTrue(out().contains("Road freight"));
    }

    @Test
    @DisplayName("--mode sea and a heavy item: only ships are quoted, saved as PDF")
    void seaFreight() throws Exception {
        Path pdf = tempDir.resolve("sea.pdf");
        assertEquals(CommandLineMode.OK, run("--from", "Nairobi", "--to", "London", "--mode", "sea",
                "--weight", "2 tonne", "--length", "2 m", "--width", "1.2 m", "--height", "1 m", "--save", pdf.toString()));
        assertTrue(out().contains("Indian Ocean Container Lines (IOCL)"), out());
        assertTrue(out().contains("Sea freight"));
        assertTrue(Files.readString(pdf, StandardCharsets.ISO_8859_1).startsWith("%PDF"));

        assertEquals(CommandLineMode.USAGE, run("--from", "Nairobi", "--to", "London", "--mode", "rail",
                "--weight", "1 kg", "--length", "1 cm", "--width", "1 cm", "--height", "1 cm"));
        assertTrue(err().contains("Unknown --mode rail"));
        assertEquals(CommandLineMode.USAGE, run("--from", "Nairobi", "--to", "London", "--mode", "road",
                "--weight", "1 kg", "--length", "1 cm", "--width", "1 cm", "--height", "1 cm"));
        assertTrue(err().contains("No road freight on Nairobi -> London"));
    }

    @Test
    @DisplayName("A courier cannot take a 100 kg crate, and says why")
    void carrierCannotTakeIt() {
        assertEquals(CommandLineMode.USAGE, run("--from", "Nairobi", "--to", "Mombasa", "--carrier", "SWIFT",
                "--weight", "100 kg", "--length", "1 m", "--width", "1 m", "--height", "1 m"));
        assertTrue(err().contains("Carrier SWIFT cannot take this shipment"), err());
        assertTrue(err().contains("Courier takes items up to 70 kg"), err());
    }

    @Test
    @DisplayName("Repeated options make a multi-parcel shipment")
    void twoParcels() {
        assertEquals(CommandLineMode.OK, run(parcel("--weight", "1 kg", "--length", "50 cm", "--width", "40 cm",
                "--height", "30 cm", "--carrier", "SWIFT")));
        assertTrue(out().contains(" Items:                 2"));
        assertTrue(out().contains(" TOTAL:                 KES 3,400.00"), out());
    }

    @Test
    @DisplayName("--save writes the file; a folder instead of a file gives exit code 1")
    void save() {
        Path file = tempDir.resolve("q.csv");
        assertEquals(CommandLineMode.OK, run(parcel("--carrier", "SWIFT", "--save", file.toString())));
        assertTrue(Files.exists(file));
        assertEquals(CommandLineMode.FAILED, run(parcel("--carrier", "SWIFT", "--save", tempDir.toString())));
        assertTrue(err().contains("is a folder"));
    }

    @Test
    @DisplayName("Bad input gives exit code 2 and a clear message")
    void usageErrors() {
        assertEquals(CommandLineMode.USAGE, run("--from", "Nairobi"));
        assertTrue(err().contains("--from and --to are required"));

        assertEquals(CommandLineMode.USAGE, run("--bogus"));
        assertTrue(err().contains("Unknown option: --bogus"));

        assertEquals(CommandLineMode.USAGE, run("--weight"));
        assertTrue(err().contains("--weight needs a value"));

        assertEquals(CommandLineMode.USAGE, run("--from", "Nairobi", "--to", "Mombasa"));
        assertTrue(err().contains("Give at least one item"));

        assertEquals(CommandLineMode.USAGE, run(parcel("--weight", "1 kg")));
        assertTrue(err().contains("Each item needs one"));

        assertEquals(CommandLineMode.USAGE, run("--from", "Nairobi", "--to", "Atlantis", "--weight", "1 kg"));
        assertTrue(err().contains("No carrier serves Nairobi -> Atlantis"));

        assertEquals(CommandLineMode.USAGE, run(parcel("--carrier", "KILI")));
        assertTrue(err().contains("Choose one of:"));
    }

    @Test
    @DisplayName("Unit and limit problems are reported per parcel")
    void unitProblems() {
        assertEquals(CommandLineMode.USAGE, run("--from", "Nairobi", "--to", "Mombasa", "--weight", "2 cm",
                "--length", "1 cm", "--width", "1 cm", "--height", "1 cm"));
        assertTrue(err().contains("Cannot convert cm"));

        assertEquals(CommandLineMode.USAGE, run(parcel("--weight", "1 kg", "--length", "14 m",
                "--width", "1 cm", "--height", "1 cm")));
        assertTrue(err().contains("Item 2: Length 1,400.00 cm is over the 1,360 cm limit"), err());
    }

    @Test
    @DisplayName("Express on a route with no express service is refused")
    void noExpress() {
        assertEquals(CommandLineMode.USAGE, run("--from", "Nairobi", "--to", "Nakuru", "--express",
                "--weight", "1 kg", "--length", "1 cm", "--width", "1 cm", "--height", "1 cm"));
        assertTrue(err().contains("No carrier on Nairobi -> Nakuru can take this shipment"), err());
        assertTrue(err().contains("no express service"));
    }
}
