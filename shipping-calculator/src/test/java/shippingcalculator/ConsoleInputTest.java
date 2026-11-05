package shippingcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Input handling with fake keyboard input.
class ConsoleInputTest {

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

    private ConsoleInput withInput(String text) {
        return new ConsoleInput(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(printed, true, StandardCharsets.UTF_8));
    }

    private String printed() {
        return printed.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Reads a number and unit, with or without a space")
    void parsesMeasurements() {
        Measurement a = withInput("2.5 kg\n").readMeasurement("? ");
        assertEquals(2.5, a.value(), 1e-12);
        assertEquals("kg", a.unit());

        Measurement b = withInput("  10lb  \n").readMeasurement("? ");
        assertEquals(10.0, b.value(), 1e-12);
        assertEquals("lb", b.unit());
        assertEquals("10 lb", b.toString());
    }

    @Test
    @DisplayName("Keeps asking after bad lines (text, no unit, zero, negative, huge)")
    void skipsBadInput() {
        Measurement m = withInput("hello\n5\n0 kg\n-3 kg\n1" + "0".repeat(400) + " kg\n7 oz\n").readMeasurement("? ");
        assertEquals(7.0, m.value(), 1e-12);
        assertEquals("oz", m.unit());
        assertTrue(printed().contains("greater than zero"));
        assertTrue(printed().contains("too large"));
    }

    @Test
    @DisplayName("Yes/no accepts y, yes, n, no in any case and re-asks otherwise")
    void yesNo() {
        assertTrue(withInput("Y\n").readYesNo("? "));
        assertTrue(withInput("maybe\nyes\n").readYesNo("? "));
        assertFalse(withInput("n\n").readYesNo("? "));
        assertFalse(withInput("NO\n").readYesNo("? "));
    }

    @Test
    @DisplayName("Menu choice must be a whole number in range")
    void choice() {
        assertEquals(3, withInput("0\nfour\n9\n3\n").readChoice("? ", 5));
        assertTrue(printed().contains("from 1 to 5"));
    }

    @Test
    @DisplayName("Free text must not be empty")
    void text() {
        assertEquals("quote.csv", withInput("\n   \nquote.csv\n").readText("? "));
    }

    @Test
    @DisplayName("Running out of input ends the question loop")
    void endOfInput() {
        assertThrows(NoSuchElementException.class, () -> withInput("").readMeasurement("? "));
        assertThrows(NoSuchElementException.class, () -> withInput("").readChoice("? ", 2));
    }

    @Test
    @DisplayName("Measurement.parse explains what is wrong")
    void parseMessages() {
        assertEquals("Please type a number followed by a unit, e.g. 2.5 kg",
                assertThrows(IllegalArgumentException.class, () -> Measurement.parse("kg")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> Measurement.parse(null));
        assertEquals(0.5, Measurement.parse(".5 m").value(), 1e-12);
    }
}
