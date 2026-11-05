package shippingcalculator;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

// The main window: branded header, a tab bar and three pages (New Quote, Routes & Rates, History).
final class ShippingGui extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private ShippingGui(ShippingDatabase database) {
        super("Nexus Globex Transit — Shipping Quotation");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                database.close();
                System.exit(0);
            }
        });
        if (Brand.globe() != null) {
            setIconImage(Brand.globe());
        }
        setContentPane(buildContent(database));
        setMinimumSize(new Dimension(1100, 700));
        pack();
        setSize(Math.max(getWidth(), 1280), Math.max(getHeight(), 840));
        setLocationRelativeTo(null);
    }

    static void launch(ShippingDatabase database) {
        SwingUtilities.invokeLater(() -> {
            Theme.apply();
            new ShippingGui(database).setVisible(true);
        });
    }

    // Separate from the frame so the whole screen can be built (and tested) without a display
    static JComponent buildContent(ShippingDatabase database) {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);

        HistoryTab history = new HistoryTab(database);
        JPanel pages = new JPanel(new CardLayout());
        pages.setOpaque(false);
        pages.add(new QuotePanel(database, history::refresh), "New Quote");
        pages.add(routesTab(database), "Routes & Rates");
        pages.add(history, "History");

        JPanel top = new JPanel(new BorderLayout());
        top.add(header(database), BorderLayout.NORTH);
        top.add(new TabBar(pages, "New Quote", "Routes & Rates", "History"), BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);
        root.add(pages, BorderLayout.CENTER);
        return root;
    }

    // ------------------------------------------------------------------ header

    private static JComponent header(ShippingDatabase database) {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(Theme.CHARCOAL);
        header.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 22));

        // Brand: globe mark plus the name in two colours
        JPanel brand = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        if (Brand.globe() != null) {
            brand.add(new JLabel(new ImageIcon(scale(Brand.globe(), 54))));
        }
        JPanel name = Theme.transparent(null);
        name.setLayout(new BoxLayout(name, BoxLayout.Y_AXIS));
        name.add(Theme.label("NEXUS", Font.BOLD, 24, Color.WHITE));
        name.add(Theme.label("GLOBEX", Font.BOLD, 24, Theme.ORANGE));
        brand.add(name);
        brand.add(Box.createHorizontalStrut(10));
        brand.add(new Divider());
        brand.add(Box.createHorizontalStrut(10));

        JPanel titles = Theme.transparent(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(Theme.label("SHIPPING QUOTATION", Font.BOLD, 22, Color.WHITE));
        titles.add(Box.createVerticalStrut(2));
        titles.add(Theme.label("Compare shipping lines across Kenya and beyond by courier, road, air and sea."
                + "  Powered by unitconverter-1.0.jar", Font.PLAIN, 13, Theme.ON_DARK));
        brand.add(titles);
        header.add(brand, BorderLayout.WEST);

        // Right: what the database offers (never the file location)
        JPanel stats = Theme.transparent(null);
        stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
        JLabel counts = Theme.label(database.carrierCount() + " CARRIERS  •  " + database.routes().size()
                + " ROUTES", Font.BOLD, 15, Theme.ORANGE);
        counts.setAlignmentX(1f);
        List<String> modes = new ArrayList<>();
        database.modes(null, null).forEach(m -> modes.add(m.label().replace(" freight", "")));
        JLabel modeLine = Theme.label(String.join("  ·  ", modes), Font.PLAIN, 13, Theme.ON_DARK);
        modeLine.setAlignmentX(1f);
        stats.add(Box.createVerticalGlue());
        stats.add(counts);
        stats.add(Box.createVerticalStrut(4));
        stats.add(modeLine);
        if (database.isTemporary()) {
            JLabel warning = Theme.label("Temporary database: saved quotes are not kept", Font.PLAIN, 12,
                    Theme.ORANGE_THUMB);
            warning.setAlignmentX(1f);
            stats.add(warning);
        }
        stats.add(Box.createVerticalGlue());
        header.add(stats, BorderLayout.EAST);
        return header;
    }

    private static final class Divider extends JComponent {
        private static final long serialVersionUID = 1L;

        Divider() {
            setPreferredSize(new Dimension(1, 48));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.CHARCOAL_LINE);
            g.fillRect(0, 0, 1, getHeight());
        }
    }

    static Image scale(BufferedImage image, int height) {
        int width = Math.round(image.getWidth() * (height / (float) image.getHeight()));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return out;
    }

    // ------------------------------------------------------------------ tab bar

    // Text tabs with an orange underline under the selected one
    private static final class TabBar extends JPanel {
        private static final long serialVersionUID = 1L;
        private final transient List<Tab> tabs = new ArrayList<>();

        TabBar(JPanel pages, String... names) {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            setBackground(Theme.CARD);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(0, 10, 0, 0)));
            for (String name : names) {
                Tab tab = new Tab(name);
                tab.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        ((CardLayout) pages.getLayout()).show(pages, name);
                        tabs.forEach(t -> t.setSelected(t == tab));
                    }
                });
                tabs.add(tab);
                add(tab);
            }
            tabs.get(0).setSelected(true);
        }
    }

    private static final class Tab extends JLabel {
        private static final long serialVersionUID = 1L;
        private boolean selected;

        Tab(String text) {
            super(text);
            setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setSelected(false);
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            setFont(Theme.font(selected ? Font.BOLD : Font.PLAIN, 14));
            setForeground(selected ? Theme.INK : Theme.MUTED);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (selected) {
                g.setColor(Theme.ORANGE);
                g.fillRect(8, getHeight() - 3, getWidth() - 16, 3);
            }
        }
    }

    // ------------------------------------------------------------------ routes tab

    private static JComponent routesTab(ShippingDatabase database) {
        String[] columns = {"Carrier", "Code", "Transport", "From", "To", "Tariff", "Standard", "Express",
                "Express fee", "Fragile (KES)"};
        DefaultTableModel model = readOnlyModel(columns);
        List<Route> routes = database.routes();
        for (Route r : routes) {
            Carrier c = r.carrier();
            model.addRow(new Object[] {c.name(), c.code(), r.mode().label(), r.origin(), r.destination(),
                    r.tariffName(), Format.days(r.transitDays()),
                    r.expressAvailable() ? Format.days(r.expressDays()) : "—",
                    r.expressAvailable() ? c.expressPercent() : "—", Format.amount(c.fragileFee())});
        }
        JTable table = new JTable(model);
        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] widths = {200, 55, 100, 85, 105, 260, 75, 70, 80, 90};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        JTextField filter = Theme.textField(18);
        filter.setHorizontalAlignment(JTextField.LEFT);
        String[] modeNames = new String[TransportMode.values().length + 1];
        modeNames[0] = "All transport";
        for (int i = 0; i < TransportMode.values().length; i++) {
            modeNames[i + 1] = TransportMode.values()[i].label();
        }
        JComboBox<String> modeFilter = Theme.combo(modeNames);
        Runnable update = () -> {
            List<RowFilter<Object, Object>> filters = new ArrayList<>();
            String text = filter.getText().trim();
            if (!text.isEmpty()) {
                filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
            }
            if (modeFilter.getSelectedIndex() > 0) {
                filters.add(RowFilter.regexFilter("^" + Pattern.quote((String) modeFilter.getSelectedItem()) + "$", 2));
            }
            sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        };
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update.run();
            }
        });
        modeFilter.addActionListener(e -> update.run());

        // Full price list and limits of the selected route
        JLabel prices = new JLabel(" ");
        prices.setFont(Theme.font(Font.PLAIN, 13));
        prices.setForeground(Theme.INK);
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            prices.setText(row < 0 ? " " : priceList(routes.get(table.convertRowIndexToModel(row))));
        });
        if (!routes.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }

        JPanel top = Theme.row(Theme.label("Search", Font.PLAIN, 13, Theme.MUTED), filter, modeFilter,
                Theme.mutedLabel("  Select a row to see its full price list and size limits."));
        JPanel body = Theme.transparent(new BorderLayout(0, 12));
        body.add(top, BorderLayout.NORTH);
        body.add(Theme.scroll(table), BorderLayout.CENTER);
        body.add(prices, BorderLayout.SOUTH);
        return padded(fullHeightCard("Shipping Lines, Routes and Tariffs", body));
    }

    static String priceList(Route route) {
        StringBuilder html = new StringBuilder("<html><b>" + route.carrier().name() + "</b>, "
                + route.mode().label().toLowerCase(java.util.Locale.ROOT) + ", " + route.tariffName()
                + " (" + route.path() + ")<br>");
        for (RateTable.Band band : route.rates().bands()) {
            html.append("up to ").append(Format.number(band.upToKg())).append(" kg: ")
                    .append(Format.money(band.price())).append(" &nbsp;·&nbsp; ");
        }
        html.append("then ").append(Format.money(route.rates().extraPerKg())).append(" per extra kg<br>")
                .append("<span style='color:#6B6F76'>Each item up to ").append(route.mode().limitsText())
                .append(". Volumetric weight = L x W x H (cm) / ").append(route.mode().volumetricDivisor())
                .append(".</span></html>");
        return html.toString();
    }

    // ------------------------------------------------------------------ history tab

    static final class HistoryTab extends JPanel {
        private static final long serialVersionUID = 1L;
        private final transient ShippingDatabase database;
        private final DefaultTableModel model = readOnlyModel(new String[] {"#", "Saved", "Carrier", "Transport",
                "Route", "Items", "Chargeable kg", "Options", "Total"});
        private final JTable table = new JTable(model);
        private transient List<ShippingDatabase.SavedQuote> rows = List.of();

        HistoryTab(ShippingDatabase database) {
            super(new BorderLayout());
            this.database = database;
            setOpaque(false);
            Theme.styleTable(table);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            int[] widths = {40, 140, 210, 110, 200, 60, 110, 120, 130};
            for (int i = 0; i < widths.length; i++) {
                table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            }
            table.getColumnModel().getColumn(8).setCellRenderer(Theme.moneyRenderer());
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        viewSelected();
                    }
                }
            });

            JButton view = Theme.button("View Receipt", Theme.ButtonStyle.PRIMARY);
            JButton refresh = Theme.button("Refresh", Theme.ButtonStyle.SECONDARY);
            JButton clear = Theme.button("Clear History", Theme.ButtonStyle.LINK);
            view.addActionListener(e -> viewSelected());
            refresh.addActionListener(e -> refresh());
            clear.addActionListener(e -> clearAll());

            JPanel body = Theme.transparent(new BorderLayout(0, 12));
            body.add(Theme.row(view, refresh, clear,
                    Theme.mutedLabel("  Quotes saved from New Quote or the console. Double-click to open.")),
                    BorderLayout.NORTH);
            body.add(Theme.scroll(table), BorderLayout.CENTER);
            add(padded(fullHeightCard("Saved Quotes", body)), BorderLayout.CENTER);
            refresh();
        }

        void refresh() {
            try {
                rows = database.recentQuotes(500);
            } catch (ShippingDatabase.DatabaseException problem) {
                rows = List.of();
                JOptionPane.showMessageDialog(this, problem.getMessage(), "History", JOptionPane.ERROR_MESSAGE);
            }
            model.setRowCount(0);
            for (ShippingDatabase.SavedQuote q : rows) {
                model.addRow(new Object[] {q.id(), q.createdAt().format(WHEN), q.carrierName(), q.modeLabel(),
                        q.origin() + " -> " + q.destination(), q.parcelCount(), q.chargeableKg().toPlainString(),
                        q.options(), q.total()});
            }
        }

        int rowCount() {
            return model.getRowCount();
        }

        private void viewSelected() {
            int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            ShippingDatabase.SavedQuote quote = rows.get(table.convertRowIndexToModel(row));
            JTextArea text = new JTextArea(quote.receipt());
            text.setEditable(false);
            text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            text.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            JScrollPane scroll = Theme.scroll(text);
            scroll.setPreferredSize(new Dimension(600, 460));
            JOptionPane.showMessageDialog(this, scroll, "Quote #" + quote.id() + " — "
                    + quote.createdAt().format(WHEN), JOptionPane.PLAIN_MESSAGE);
        }

        private void clearAll() {
            if (rows.isEmpty()) {
                return;
            }
            if (JOptionPane.showConfirmDialog(this, "Delete all " + rows.size() + " saved quotes?",
                    "Clear history", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                try {
                    database.clearHistory();
                } catch (ShippingDatabase.DatabaseException problem) {
                    JOptionPane.showMessageDialog(this, problem.getMessage(), "History", JOptionPane.ERROR_MESSAGE);
                }
                refresh();
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static DefaultTableModel readOnlyModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // A card that fills the page instead of shrinking to its content
    private static JComponent fullHeightCard(String title, JComponent body) {
        JPanel card = new Theme.RoundPanel(Theme.CARD, Theme.BORDER, 12) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
        };
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        card.add(Theme.label(title, Font.BOLD, 16, Theme.INK), BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private static JComponent padded(JComponent content) {
        JPanel wrapper = Theme.transparent(new BorderLayout());
        wrapper.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }
}
