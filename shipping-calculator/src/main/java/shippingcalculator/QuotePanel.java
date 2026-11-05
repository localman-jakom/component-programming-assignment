package shippingcalculator;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;

import unitconverter.exceptions.UnitConverterException;

// The "New Quote" page: route and items on the left, carrier comparison and the quote on the right.
// Uses exactly the same parts as the console: UnitNormaliser -> ShippingCalculator -> ReceiptPrinter.
final class QuotePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String[] WEIGHT_UNITS = {"kg", "g", "lb", "oz", "tonne"};
    private static final String[] LENGTH_UNITS = {"cm", "m", "inch", "foot"};
    private static final String EMPTY_RECEIPT =
            "Choose a route, add your items and press \"Compare Carriers\".\n\n"
            + "Every carrier and transport mode that can take the shipment\n"
            + "is priced side by side; click a row to see its full quote here.\n\n"
            + "Heavy or large goods go by road (trucks, up to 24 t) or by sea\n"
            + "(containers, up to 26.5 t) where the cities are separated by sea.";

    private final transient ShippingDatabase database;
    private final transient Runnable onQuoteSaved;
    private final transient UnitNormaliser normaliser = new UnitNormaliser();
    private final transient ShippingCalculator calculator = new ShippingCalculator();

    // Route
    final JComboBox<String> originBox = Theme.combo(new String[0]);
    final JComboBox<String> destinationBox = Theme.combo(new String[0]);
    private final JLabel routeInfo = Theme.label(" ", Font.PLAIN, 12, Theme.ORANGE_DARK);

    // Item entry
    final JTextField weightField = Theme.textField(6);
    final JComboBox<String> weightUnit = Theme.combo(WEIGHT_UNITS);
    final JTextField lengthField = Theme.textField(4);
    final JTextField widthField = Theme.textField(4);
    final JTextField heightField = Theme.textField(4);
    final JComboBox<String> sizeUnit = Theme.combo(LENGTH_UNITS);
    private final transient List<PackageDetails> parcels = new ArrayList<>();
    private final DefaultTableModel parcelModel = new DefaultTableModel(
            new Object[] {"#", "Weight", "Size L x W x H (cm)", "Entered as"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable parcelTable = new JTable(parcelModel);

    // Options and actions
    final JCheckBox fragileBox = Theme.checkBox("Fragile handling");
    final JCheckBox expressBox = Theme.checkBox("Express delivery");
    final JButton addButton = Theme.button("Add Item", Theme.ButtonStyle.PRIMARY);
    final JButton compareButton = Theme.button("Compare Carriers", Theme.ButtonStyle.PRIMARY);
    final JLabel statusLabel = new JLabel(" ");

    // Results
    final QuoteTableModel quoteModel = new QuoteTableModel();
    final JTable quoteTable = new JTable(quoteModel);
    private final JLabel skippedLabel = Theme.mutedLabel(" ");
    final JLabel totalLabel = Theme.label("—", Font.BOLD, 30, Theme.INK);
    private final JLabel summaryLabel = Theme.mutedLabel("No quote yet");
    private final JLabel receiptState = Theme.mutedLabel("Draft");
    final JTextArea receiptArea = new JTextArea(EMPTY_RECEIPT);
    final JButton saveButton = Theme.button("Save to History", Theme.ButtonStyle.SECONDARY);
    final JButton exportButton = Theme.button("Export…", Theme.ButtonStyle.SECONDARY);
    private transient ShipmentQuote selected;

    QuotePanel(ShippingDatabase database, Runnable onQuoteSaved) {
        super(new BorderLayout(16, 0));
        this.database = database;
        this.onQuoteSaved = onQuoteSaved;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        add(buildForm(), BorderLayout.WEST);
        add(buildResults(), BorderLayout.CENTER);

        database.origins().forEach(originBox::addItem);
        originBox.setSelectedItem(database.origins().contains("Nairobi") ? "Nairobi" : originBox.getItemAt(0));
        refreshDestinations();

        originBox.addActionListener(e -> {
            refreshDestinations();
            invalidateResults();
        });
        destinationBox.addActionListener(e -> {
            updateRouteInfo();
            invalidateResults();
        });
        fragileBox.addItemListener(e -> invalidateResults());
        expressBox.addItemListener(e -> invalidateResults());
        addButton.addActionListener(e -> onAddParcel());
        compareButton.addActionListener(e -> onCompare());
        saveButton.addActionListener(e -> onSave());
        exportButton.addActionListener(e -> onExport());
        showQuote(null);
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildForm() {
        // Fixed width, height follows the content (so a two-line status message never squashes the cards)
        JPanel column = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(456, super.getPreferredSize().height);
            }
        };
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JPanel route = Theme.transparent(new BorderLayout(0, 8));
        route.add(Theme.row(Theme.label("From", Font.PLAIN, 13, Theme.MUTED), originBox,
                Theme.label("→", Font.BOLD, 15, Theme.INK),
                Theme.label("To", Font.PLAIN, 13, Theme.MUTED), destinationBox), BorderLayout.NORTH);
        route.add(routeInfo, BorderLayout.SOUTH);
        column.add(Theme.card("Route", route));
        column.add(Box.createVerticalStrut(12));
        column.add(Theme.card("Items", buildParcelEntry()));
        column.add(Box.createVerticalStrut(12));

        JPanel options = Theme.transparent(new GridBagLayout());
        GridBagConstraints o = formConstraints();
        o.gridx = 0;
        o.gridy = 0;
        options.add(fragileBox, o);
        o.gridx = 1;
        o.weightx = 1;
        options.add(Theme.mutedLabel("carrier's fixed fee per item"), o);
        o.gridx = 0;
        o.gridy = 1;
        o.weightx = 0;
        options.add(expressBox, o);
        o.gridx = 1;
        o.weightx = 1;
        options.add(Theme.mutedLabel("faster delivery, % of base rate"), o);
        column.add(Theme.card("Options", options));
        column.add(Box.createVerticalStrut(14));

        compareButton.setFont(Theme.font(Font.BOLD, 15));
        compareButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        compareButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        compareButton.setPreferredSize(new Dimension(400, 42));
        column.add(compareButton);
        column.add(Box.createVerticalStrut(8));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setFont(Theme.font(Font.PLAIN, 13));
        column.add(statusLabel);
        column.add(Box.createVerticalGlue());

        column.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        // Scrolls instead of squashing the form when the window is short
        JScrollPane scroll = new JScrollPane(column, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
            private static final long serialVersionUID = 1L;

            // Room for the scroll bar is always kept, so it never covers the cards
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(column.getPreferredSize().width + 12, super.getPreferredSize().height);
            }
        };
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        Theme.styleScrollBars(scroll);
        return scroll;
    }

    private JComponent buildParcelEntry() {
        JPanel panel = Theme.transparent(new GridBagLayout());
        GridBagConstraints c = formConstraints();

        addRow(panel, c, 0, "Weight", Theme.row(weightField, weightUnit));
        addRow(panel, c, 1, "Size", Theme.row(lengthField, Theme.mutedLabel("×"), widthField,
                Theme.mutedLabel("×"), heightField, sizeUnit));
        lengthField.setToolTipText("Length");
        widthField.setToolTipText("Width");
        heightField.setToolTipText("Height");

        JButton remove = Theme.button("Remove Selected", Theme.ButtonStyle.LINK);
        JButton clear = Theme.button("Clear All", Theme.ButtonStyle.LINK);
        remove.addActionListener(e -> onRemoveParcel());
        clear.addActionListener(e -> onClearParcels());
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        panel.add(Theme.row(addButton, remove, clear), c);

        Theme.styleTable(parcelTable);
        parcelTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        parcelTable.getColumnModel().getColumn(0).setMaxWidth(30);
        JScrollPane scroll = Theme.scroll(parcelTable);
        scroll.setPreferredSize(new Dimension(340, 116));
        c.gridy = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(scroll, c);

        c.gridy = 4;
        JLabel limits = Theme.mutedLabel("Max per item: courier 70 kg · air 1 t · road 24 t · sea 26.5 t");
        limits.setToolTipText(Theme.wrap(ShippingLimits.summary(), 320));
        panel.add(limits, c);
        return panel;
    }

    private JComponent buildResults() {
        JPanel column = Theme.transparent(new BorderLayout(0, 12));

        Theme.styleTable(quoteTable);
        quoteTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        quoteTable.getColumnModel().getColumn(3).setCellRenderer(Theme.cellRenderer(SwingConstants.RIGHT));
        quoteTable.getColumnModel().getColumn(4).setCellRenderer(Theme.moneyRenderer());
        quoteTable.getColumnModel().getColumn(5).setCellRenderer(Theme.pillRenderer());
        int[] widths = {225, 105, 70, 100, 125, 130};
        for (int i = 0; i < widths.length; i++) {
            quoteTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        quoteTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = quoteTable.getSelectedRow();
                showQuote(row < 0 ? null : quoteModel.quoteAt(quoteTable.convertRowIndexToModel(row)));
            }
        });
        JScrollPane tableScroll = Theme.scroll(quoteTable);
        tableScroll.setPreferredSize(new Dimension(600, 150));
        JPanel comparison = Theme.transparent(new BorderLayout(0, 6));
        comparison.add(tableScroll, BorderLayout.CENTER);
        comparison.add(skippedLabel, BorderLayout.SOUTH);
        column.add(Theme.card("Carrier Comparison", comparison), BorderLayout.NORTH);

        JPanel detail = Theme.transparent(new BorderLayout(0, 10));
        JPanel headline = Theme.transparent(new BorderLayout());
        headline.add(totalLabel, BorderLayout.NORTH);
        headline.add(summaryLabel, BorderLayout.SOUTH);
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(saveButton);
        actions.add(exportButton);
        headline.add(actions, BorderLayout.EAST);
        detail.add(headline, BorderLayout.NORTH);
        detail.add(receiptDocument(), BorderLayout.CENTER);

        JPanel quoteCard = new Theme.RoundPanel(Theme.CARD, Theme.BORDER, 12) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
        };
        quoteCard.setLayout(new BorderLayout(0, 6));
        quoteCard.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        quoteCard.add(Theme.label("Quote", Font.BOLD, 16, Theme.INK), BorderLayout.NORTH);
        quoteCard.add(detail, BorderLayout.CENTER);
        column.add(quoteCard, BorderLayout.CENTER);
        return column;
    }

    // The receipt shown like a printed document: logo header, then the quote text
    private JComponent receiptDocument() {
        JPanel letterhead = new JPanel(new BorderLayout());
        letterhead.setBackground(Theme.CARD);
        letterhead.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, Theme.ORANGE),
                BorderFactory.createEmptyBorder(10, 12, 8, 14)));
        if (Brand.logo() != null) {
            letterhead.add(new JLabel(new ImageIcon(ShippingGui.scale(Brand.logo(), 46))), BorderLayout.WEST);
        } else {
            letterhead.add(Theme.label(Brand.NAME, Font.BOLD, 16, Theme.INK), BorderLayout.WEST);
        }
        JPanel stamp = Theme.transparent(null);
        stamp.setLayout(new BoxLayout(stamp, BoxLayout.Y_AXIS));
        JLabel title = Theme.label("SHIPPING QUOTE", Font.BOLD, 14, Theme.INK);
        title.setAlignmentX(1f);
        receiptState.setAlignmentX(1f);
        stamp.add(Box.createVerticalGlue());
        stamp.add(title);
        stamp.add(receiptState);
        stamp.add(Box.createVerticalGlue());
        letterhead.add(stamp, BorderLayout.EAST);

        receiptArea.setEditable(false);
        receiptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        receiptArea.setForeground(Theme.INK);
        receiptArea.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel document = new JPanel(new BorderLayout());
        document.setBackground(Theme.CARD);
        document.add(letterhead, BorderLayout.NORTH);
        document.add(receiptArea, BorderLayout.CENTER);
        return Theme.scroll(document);
    }

    // ------------------------------------------------------------------ actions

    void onAddParcel() {
        clearStatus();
        try {
            addParcelFromFields();
            invalidateResults();
        } catch (InputProblem problem) {
            showProblem(problem);
        }
    }

    void onCompare() {
        clearStatus();
        try {
            // Fields that are filled in count as one more item, so one item needs no "Add" click
            if (parcels.isEmpty() || !allBlank(weightField, lengthField, widthField, heightField)) {
                addParcelFromFields();
            }
            String origin = (String) originBox.getSelectedItem();
            String destination = (String) destinationBox.getSelectedItem();
            if (origin == null || destination == null) {
                throw new InputProblem(originBox, "Please choose where the shipment goes from and to.");
            }
            Shipment shipment = new Shipment(parcels, fragileBox.isSelected(), expressBox.isSelected());
            ShippingCalculator.Comparison comparison =
                    calculator.compare(shipment, database.routesBetween(origin, destination));
            quoteModel.setQuotes(comparison.quotes());
            skippedLabel.setText(comparison.skipped().isEmpty() ? " "
                    : Theme.wrap("Not available: " + String.join("; ", comparison.skipped()), 560));
            if (comparison.quotes().isEmpty()) {
                showQuote(null);
                throw new InputProblem(expressBox.isSelected() ? expressBox : weightField,
                        "No carrier on " + origin + " -> " + destination + " can take this shipment"
                        + (expressBox.isSelected() ? " by express. Untick Express or check the sizes." : ".")
                        + " See the reasons under the comparison table.");
            }
            quoteTable.setRowSelectionInterval(0, 0);
            ShipmentQuote best = comparison.quotes().get(0);
            int count = comparison.quotes().size();
            showSuccess(count + (count == 1 ? " option" : " options") + " quoted. Best price: "
                    + best.route().carrier().name() + " by " + best.route().mode().label().toLowerCase(Locale.ROOT)
                    + ", " + Format.money(best.total()) + ".");
        } catch (InputProblem problem) {
            showProblem(problem);
        } catch (IllegalArgumentException problem) {
            showError(problem.getMessage());
        }
    }

    private void onRemoveParcel() {
        int row = parcelTable.getSelectedRow();
        if (row < 0 && !parcels.isEmpty()) {
            row = parcels.size() - 1;
        }
        if (row >= 0) {
            parcels.remove(row);
            parcelModel.removeRow(row);
            renumberParcels();
            invalidateResults();
        }
    }

    private void onClearParcels() {
        parcels.clear();
        parcelModel.setRowCount(0);
        invalidateResults();
        weightField.requestFocusInWindow();
    }

    void onSave() {
        if (selected == null) {
            return;
        }
        try {
            long id = database.saveQuote(selected);
            saveButton.setEnabled(false);
            receiptState.setText("Saved as quote #" + id);
            showSuccess("Saved as quote #" + id + ". See the History tab.");
            onQuoteSaved.run();
        } catch (ShippingDatabase.DatabaseException problem) {
            showError(problem.getMessage());
        }
    }

    private void onExport() {
        if (selected == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        FileNameExtensionFilter pdf = new FileNameExtensionFilter("PDF document (*.pdf)", "pdf");
        FileNameExtensionFilter text = new FileNameExtensionFilter("Text receipt (*.txt)", "txt");
        FileNameExtensionFilter csv = new FileNameExtensionFilter("Spreadsheet (*.csv)", "csv");
        chooser.addChoosableFileFilter(pdf);
        chooser.addChoosableFileFilter(text);
        chooser.addChoosableFileFilter(csv);
        chooser.setFileFilter(pdf);
        chooser.setSelectedFile(new File(suggestedFileName(selected)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") && !name.endsWith(".txt") && !name.endsWith(".csv")) {
            String extension = chooser.getFileFilter() == csv ? ".csv" : chooser.getFileFilter() == text ? ".txt" : ".pdf";
            file = new File(file.getParentFile(), file.getName() + extension);
        }
        if (file.exists() && JOptionPane.showConfirmDialog(this, file.getName() + " already exists. Replace it?",
                "Export quote", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Path written = QuoteExporter.export(selected, file.toPath());
            showSuccess("Exported to " + written.getFileName() + ".");
        } catch (QuoteExporter.ExportException | InvalidPathException problem) {
            JOptionPane.showMessageDialog(this, problem.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void addParcelFromFields() {
        if (parcels.size() >= ShippingLimits.MAX_PARCELS) {
            throw new InputProblem(weightField, "A shipment can have at most " + ShippingLimits.MAX_PARCELS
                    + " items.");
        }
        Measurement weight = read(weightField, (String) weightUnit.getSelectedItem(), "Weight");
        Measurement length = read(lengthField, (String) sizeUnit.getSelectedItem(), "Length");
        Measurement width = read(widthField, (String) sizeUnit.getSelectedItem(), "Width");
        Measurement height = read(heightField, (String) sizeUnit.getSelectedItem(), "Height");

        double weightKg = convert(weightField, () -> {
            double kg = normaliser.toKilograms(weight);
            ShippingLimits.checkWeightKg(kg);
            return kg;
        });
        double lengthCm = convertSide(lengthField, length, "Length");
        double widthCm = convertSide(widthField, width, "Width");
        double heightCm = convertSide(heightField, height, "Height");

        PackageDetails parcel = new PackageDetails(weightKg, lengthCm, widthCm, heightCm);
        parcels.add(parcel);
        parcelModel.addRow(new Object[] {parcels.size(), Format.kg(weightKg),
                Format.cm(lengthCm) + " × " + Format.cm(widthCm) + " × " + Format.cm(heightCm),
                weight + "; " + Format.number(length.value()) + " × " + Format.number(width.value())
                        + " × " + Format.number(height.value()) + " " + length.unit()});
        for (JTextField f : new JTextField[] {weightField, lengthField, widthField, heightField}) {
            f.setText("");
        }
        weightField.requestFocusInWindow();
    }

    private double convertSide(JTextField field, Measurement side, String name) {
        return convert(field, () -> {
            double cm = normaliser.toCentimetres(side);
            ShippingLimits.checkSideCm(name, cm);
            return cm;
        });
    }

    private interface Conversion {
        double run();
    }

    // Runs a library conversion plus limit check, attaching any problem to the field it came from
    private static double convert(JTextField field, Conversion conversion) {
        try {
            return conversion.run();
        } catch (UnitConverterException | IllegalArgumentException problem) {
            throw new InputProblem(field, problem.getMessage());
        }
    }

    private static Measurement read(JTextField field, String unit, String name) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            throw new InputProblem(field, name + " is empty. Please enter a number.");
        }
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException notANumber) {
            throw new InputProblem(field, name + " must be a number (you typed \"" + text + "\").");
        }
        if (!(value > 0) || Double.isInfinite(value)) {
            throw new InputProblem(field, name + " must be greater than zero.");
        }
        return new Measurement(value, unit);
    }

    private void refreshDestinations() {
        destinationBox.removeAllItems();
        String origin = (String) originBox.getSelectedItem();
        if (origin != null) {
            database.destinationsFrom(origin).forEach(destinationBox::addItem);
        }
        updateRouteInfo();
    }

    private void updateRouteInfo() {
        String origin = (String) originBox.getSelectedItem();
        String destination = (String) destinationBox.getSelectedItem();
        if (origin == null || destination == null) {
            routeInfo.setText(" ");
            return;
        }
        List<Route> routes = database.routesBetween(origin, destination);
        long carriers = routes.stream().map(r -> r.carrier().code()).distinct().count();
        long express = routes.stream().filter(Route::expressAvailable).count();
        List<String> modes = new ArrayList<>();
        database.modes(origin, destination).forEach(m -> modes.add(m.label().toLowerCase(Locale.ROOT)));
        routeInfo.setText(carriers + (carriers == 1 ? " carrier" : " carriers") + " on this route ("
                + String.join(", ", modes) + "), " + express + " with express");
    }

    private void showQuote(ShipmentQuote quote) {
        selected = quote;
        saveButton.setEnabled(quote != null);
        exportButton.setEnabled(quote != null);
        receiptState.setText(quote == null ? " " : "Draft, not saved");
        if (quote == null) {
            totalLabel.setText("—");
            summaryLabel.setText("No quote yet");
            receiptArea.setText(EMPTY_RECEIPT);
        } else {
            int count = quote.parcels().size();
            totalLabel.setText(Format.money(quote.total()));
            summaryLabel.setText(quote.route().carrier().name() + "  •  " + quote.route().mode().label()
                    + "  •  " + quote.serviceName() + ", " + Format.days(quote.deliveryDays()) + "  •  "
                    + count + (count == 1 ? " item, " : " items, ") + Format.kg(quote.chargeableWeightKg())
                    + " chargeable");
            receiptArea.setText(ReceiptPrinter.body(quote));
        }
        receiptArea.setCaretPosition(0);
    }

    private void invalidateResults() {
        if (quoteModel.getRowCount() > 0) {
            quoteModel.setQuotes(List.of());
            skippedLabel.setText(" ");
            showQuote(null);
            showInfo("Details changed. Press \"Compare Carriers\" to update the prices.");
        }
    }

    private void renumberParcels() {
        for (int row = 0; row < parcelModel.getRowCount(); row++) {
            parcelModel.setValueAt(row + 1, row, 0);
        }
    }

    private static boolean allBlank(JTextField... fields) {
        for (JTextField f : fields) {
            if (!f.getText().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String suggestedFileName(ShipmentQuote quote) {
        String raw = "quote-" + quote.route().carrier().code() + "-" + quote.route().origin() + "-"
                + quote.route().destination() + ".pdf";
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]+", "-");
    }

    private static GridBagConstraints formConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 10);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridy = row;
        c.gridwidth = 1;
        c.gridx = 0;
        panel.add(Theme.label(label, Font.PLAIN, 13, Theme.MUTED), c);
        c.gridx = 1;
        panel.add(field, c);
    }

    private void clearStatus() {
        statusLabel.setText(" ");
    }

    private void showProblem(InputProblem problem) {
        showError(problem.getMessage());
        problem.field.requestFocusInWindow();
        if (problem.field instanceof JTextField text) {
            text.selectAll();
        }
    }

    private void showError(String message) {
        statusLabel.setForeground(Theme.ERROR);
        statusLabel.setText(Theme.wrap(message, 340));
    }

    private void showSuccess(String message) {
        statusLabel.setForeground(Theme.SUCCESS);
        statusLabel.setText(Theme.wrap(message, 340));
    }

    private void showInfo(String message) {
        statusLabel.setForeground(Theme.MUTED);
        statusLabel.setText(Theme.wrap(message, 340));
    }

    String statusText() {
        return statusLabel.getText().replaceAll("<[^>]+>", "");
    }

    String skippedText() {
        return skippedLabel.getText().replaceAll("<[^>]+>", "");
    }

    int parcelCount() {
        return parcels.size();
    }

    ShipmentQuote selectedQuote() {
        return selected;
    }

    // A problem with what the user typed, tied to the field to focus
    private static final class InputProblem extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient JComponent field;

        InputProblem(JComponent field, String message) {
            super(message);
            this.field = field;
        }
    }

    // Rows of the carrier comparison table
    static final class QuoteTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {"Carrier", "Transport", "Delivery", "Chargeable", "Total", ""};
        private transient List<ShipmentQuote> quotes = List.of();
        private int fastestDays;

        void setQuotes(List<ShipmentQuote> newQuotes) {
            quotes = List.copyOf(newQuotes);
            fastestDays = quotes.stream().mapToInt(ShipmentQuote::deliveryDays).min().orElse(0);
            fireTableDataChanged();
        }

        ShipmentQuote quoteAt(int row) {
            return quotes.get(row);
        }

        @Override
        public int getRowCount() {
            return quotes.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 4 ? BigDecimal.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            ShipmentQuote q = quotes.get(row);
            return switch (column) {
                case 0 -> q.route().carrier().name();
                case 1 -> q.route().mode().label();
                case 2 -> Format.days(q.deliveryDays());
                case 3 -> String.format(Locale.US, "%,.1f kg", q.chargeableWeightKg());
                case 4 -> q.total();
                default -> badge(row, q);
            };
        }

        private String badge(int row, ShipmentQuote q) {
            boolean fastest = q.deliveryDays() == fastestDays && quotes.size() > 1;
            if (row == 0) {
                return fastest ? "Best Price, Fastest" : "Best Price";
            }
            return fastest ? "Fastest" : "";
        }
    }
}
