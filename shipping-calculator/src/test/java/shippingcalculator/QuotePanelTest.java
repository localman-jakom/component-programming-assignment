package shippingcalculator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static shippingcalculator.TestData.kes;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Drives the window's quote form without showing it on screen.
class QuotePanelTest {

    private final ShippingDatabase db = ShippingDatabase.openInMemory();
    private final AtomicInteger saves = new AtomicInteger();
    private final QuotePanel panel = new QuotePanel(db, saves::incrementAndGet);

    @AfterEach
    void close() {
        db.close();
    }

    private void parcel(String weight, String length, String width, String height) {
        panel.weightField.setText(weight);
        panel.lengthField.setText(length);
        panel.widthField.setText(width);
        panel.heightField.setText(height);
    }

    @Test
    @DisplayName("Compare prices every carrier on the route and selects the cheapest")
    void compare() {
        assertEquals("Nairobi", panel.originBox.getSelectedItem());
        panel.destinationBox.setSelectedItem("Mombasa");
        parcel("2", "30", "20", "10");
        panel.onCompare();

        assertEquals(4, panel.quoteModel.getRowCount());
        assertEquals(1, panel.parcelCount());
        assertEquals(kes("900"), panel.selectedQuote().total());
        assertEquals("Best Price, Fastest", panel.quoteModel.getValueAt(0, 5));
        assertTrue(panel.receiptArea.getText().contains("TOTAL:"));
        assertTrue(panel.statusText().contains("4 options quoted"), panel.statusText());

        panel.onSave();
        assertEquals(1, saves.get());
        assertEquals(1, db.recentQuotes(5).size());
        assertTrue(!panel.saveButton.isEnabled());
    }

    @Test
    @DisplayName("Units chosen in the form go through the library: 4.4 lb and inches")
    void unitsAreConverted() {
        panel.destinationBox.setSelectedItem("Mombasa");
        panel.weightUnit.setSelectedItem("lb");
        panel.sizeUnit.setSelectedItem("inch");
        parcel("4.4", "12", "8", "4");
        panel.onCompare();
        assertEquals(1.99580643, panel.selectedQuote().shipment().parcels().get(0).weightKg(), 1e-6);
        assertEquals(30.48, panel.selectedQuote().shipment().parcels().get(0).lengthCm(), 1e-9);
    }

    @Test
    @DisplayName("Problems are shown in the status line")
    void problems() {
        panel.onCompare();
        assertTrue(panel.statusText().contains("Weight is empty"), panel.statusText());

        parcel("heavy", "1", "1", "1");
        panel.onAddParcel();
        assertTrue(panel.statusText().contains("must be a number"));

        parcel("30000", "1", "1", "1");
        panel.onAddParcel();
        assertTrue(panel.statusText().contains("26,500 kg limit"));

        parcel("1", "1400", "1", "1");
        panel.onAddParcel();
        assertTrue(panel.statusText().contains("1,360 cm limit"));

        parcel("-2", "1", "1", "1");
        panel.onAddParcel();
        assertTrue(panel.statusText().contains("greater than zero"));
        assertEquals(0, panel.parcelCount());
    }

    @Test
    @DisplayName("Express on a route with no express carrier explains what to do")
    void noExpress() {
        panel.destinationBox.setSelectedItem("Nakuru");
        panel.expressBox.setSelected(true);
        parcel("1", "10", "10", "10");
        panel.onCompare();
        assertTrue(panel.statusText().contains("Untick Express"), panel.statusText());
        assertTrue(panel.skippedText().contains("no express service"), panel.skippedText());
        assertEquals(0, panel.quoteModel.getRowCount());
    }

    @Test
    @DisplayName("A 1.5 tonne pallet is quoted by truck only; couriers are listed as not available")
    void heavyPallet() {
        panel.destinationBox.setSelectedItem("Mombasa");
        panel.weightUnit.setSelectedItem("tonne");
        panel.sizeUnit.setSelectedItem("m");
        parcel("1.5", "1.2", "1", "1.5");
        panel.onCompare();
        assertEquals(2, panel.quoteModel.getRowCount());
        assertEquals("Road freight", panel.quoteModel.getValueAt(0, 1));
        assertEquals(kes("19500"), panel.selectedQuote().total());
        assertTrue(panel.skippedText().contains("Courier takes items up to 70 kg"), panel.skippedText());
    }

    @Test
    @DisplayName("Changing an option clears stale prices")
    void staleResultsAreCleared() {
        panel.destinationBox.setSelectedItem("Kisumu");
        parcel("1", "10", "10", "10");
        panel.onAddParcel();
        parcel("2", "10", "10", "10");
        panel.onAddParcel();
        panel.onCompare();
        assertEquals(2, panel.parcelCount());
        assertEquals(3, panel.quoteModel.getRowCount());

        panel.fragileBox.setSelected(true);
        assertEquals(0, panel.quoteModel.getRowCount());
        assertTrue(panel.statusText().contains("Details changed"));
    }

    @Test
    @DisplayName("The whole window content builds without a screen")
    void wholeWindowBuilds() {
        assertDoesNotThrow(() -> ShippingGui.buildContent(db));
    }
}
