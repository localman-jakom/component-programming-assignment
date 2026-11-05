package shippingcalculator;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

// Colours, fonts and flat widgets in the Nexus Globex style: charcoal header, orange accents,
// white rounded cards. Everything is drawn here, so the window looks the same on every computer.
final class Theme {

    static final Color ORANGE = new Color(0xF26B1D);
    static final Color ORANGE_DARK = new Color(0xD4570C);
    static final Color ORANGE_SOFT = new Color(0xFDE6D4);
    static final Color ORANGE_THUMB = new Color(0xF6B183);
    static final Color CHARCOAL = new Color(0x2A2A2E);
    static final Color CHARCOAL_LINE = new Color(0x45454B);
    static final Color INK = new Color(0x1F1F23);
    static final Color MUTED = new Color(0x6B6F76);
    static final Color ON_DARK = new Color(0xC4C6CB);
    static final Color BACKGROUND = new Color(0xF3F3F5);
    static final Color CARD = Color.WHITE;
    static final Color BORDER = new Color(0xE0E1E5);
    static final Color HEADER_ROW = new Color(0xF7F7F9);
    static final Color STRIPE = new Color(0xFAFAFB);
    static final Color DISABLED = new Color(0xE6E7EA);
    static final Color SUCCESS = new Color(0x1E7E34);
    static final Color ERROR = new Color(0xC62828);

    private static String fontFamily = Font.SANS_SERIF;

    private Theme() {
    }

    // ------------------------------------------------------------------ setup

    static void apply() {
        String[] installed = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String preferred : new String[] {"Segoe UI", "Inter", "Noto Sans", "DejaVu Sans"}) {
            if (Arrays.asList(installed).contains(preferred)) {
                fontFamily = preferred;
                break;
            }
        }
        try {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception unavailable) {
            // Keep the default look; the program works the same
        }
        Font base = font(Font.PLAIN, 13);
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (key.toString().endsWith(".font")) {
                UIManager.put(key, base);
            }
        }
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", INK);
        UIManager.put("Table.selectionBackground", ORANGE_SOFT);
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TextField.selectionBackground", ORANGE_SOFT);
        UIManager.put("TextArea.selectionBackground", ORANGE_SOFT);
        UIManager.put("ComboBox.selectionBackground", ORANGE_SOFT);
        UIManager.put("ComboBox.selectionForeground", INK);
        UIManager.put("List.selectionBackground", ORANGE_SOFT);
        UIManager.put("List.selectionForeground", INK);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("ToolTip.background", CHARCOAL);
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    static Font font(int style, float size) {
        return new Font(fontFamily, style, Math.round(size));
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    // ------------------------------------------------------------------ containers

    // White rounded card with a bold title
    static JPanel card(String title, JComponent content) {
        RoundPanel card = new RoundPanel(CARD, BORDER, 12);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        if (title != null) {
            JLabel heading = new JLabel(title);
            heading.setFont(font(Font.BOLD, 16));
            heading.setForeground(INK);
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            card.add(heading);
        }
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(content);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    // A panel that paints a rounded background; never taller than its content inside a column
    static class RoundPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color fill;
        private final Color outline;
        private final int radius;

        RoundPanel(Color fill, Color outline, int radius) {
            this.fill = fill;
            this.outline = outline;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (outline != null) {
                g2.setColor(outline);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
        }
    }

    // Lays components out left to right with small gaps
    static JPanel row(JComponent... parts) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        for (JComponent part : parts) {
            row.add(part);
        }
        return row;
    }

    static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static JScrollPane scroll(JComponent view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(new RoundBorder(BORDER, 8, 1));
        scroll.getViewport().setBackground(CARD);
        styleScrollBars(scroll);
        return scroll;
    }

    static void styleScrollBars(JScrollPane scroll) {
        scroll.getVerticalScrollBar().setUI(new FlatScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new FlatScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
    }

    // ------------------------------------------------------------------ labels

    static JLabel label(String text, int style, float size, Color colour) {
        JLabel label = new JLabel(text);
        label.setFont(font(style, size));
        label.setForeground(colour);
        return label;
    }

    static JLabel mutedLabel(String text) {
        return label(text, Font.PLAIN, 12, MUTED);
    }

    // Lets a JLabel wrap long messages instead of being cut off
    static String wrap(String message, int widthPx) {
        String safe = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        return "<html><div style='width:" + widthPx + "px'>" + safe + "</div></html>";
    }

    // ------------------------------------------------------------------ inputs

    enum ButtonStyle { PRIMARY, SECONDARY, LINK }

    static JButton button(String text, ButtonStyle style) {
        return new FlatButton(text, style);
    }

    // A button painted as a flat rounded shape: orange (primary), outlined (secondary) or text-only (link)
    static final class FlatButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final ButtonStyle style;

        FlatButton(String text, ButtonStyle style) {
            super(text);
            this.style = style;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(font(style == ButtonStyle.PRIMARY ? Font.BOLD : Font.PLAIN, 13));
            setBorder(BorderFactory.createEmptyBorder(7, style == ButtonStyle.LINK ? 4 : 16, 7,
                    style == ButtonStyle.LINK ? 4 : 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            boolean hover = getModel().isRollover() && isEnabled();
            boolean pressed = getModel().isPressed();
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            Color text;
            if (style == ButtonStyle.PRIMARY) {
                g2.setColor(!isEnabled() ? DISABLED : pressed || hover ? ORANGE_DARK : ORANGE);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
                text = isEnabled() ? Color.WHITE : MUTED;
            } else if (style == ButtonStyle.SECONDARY) {
                g2.setColor(!isEnabled() ? DISABLED : hover ? HEADER_ROW : CARD);
                g2.fillRoundRect(0, 0, w, h, 10, 10);
                g2.setColor(hover ? ORANGE : new Color(0xC9CBD0));
                g2.drawRoundRect(0, 0, w, h, 10, 10);
                text = isEnabled() ? INK : MUTED;
            } else {
                text = !isEnabled() ? MUTED : hover ? ORANGE_DARK : INK;
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(text);
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    static JTextField textField(int columns) {
        JTextField field = new JTextField(columns);
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        field.setForeground(INK);
        field.setBackground(CARD);
        field.setCaretColor(ORANGE);
        RoundBorder normal = new RoundBorder(new Color(0xC9CBD0), 8, 1);
        RoundBorder focused = new RoundBorder(ORANGE, 8, 2);
        field.setBorder(BorderFactory.createCompoundBorder(normal, BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(focused, BorderFactory.createEmptyBorder(5, 8, 5, 8)));
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(normal, BorderFactory.createEmptyBorder(5, 8, 5, 8)));
            }
        });
        return field;
    }

    static <T> JComboBox<T> combo(T[] items) {
        JComboBox<T> box = new JComboBox<>(items);
        styleCombo(box);
        return box;
    }

    static <T> void styleCombo(JComboBox<T> box) {
        box.setUI(new FlatComboUI());
        box.setBackground(CARD);
        box.setForeground(INK);
        box.setBorder(new RoundBorder(ORANGE, 8, 1));
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ListCellRenderer<Object> padded = new BasicComboBoxRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                c.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                c.setBackground(selected ? ORANGE_SOFT : CARD);
                c.setForeground(INK);
                return c;
            }
        };
        @SuppressWarnings("unchecked")
        ListCellRenderer<? super T> renderer = (ListCellRenderer<? super T>) (ListCellRenderer<?>) padded;
        box.setRenderer(renderer);
    }

    // Combo box without the bevelled Metal look: white field and a small chevron
    static final class FlatComboUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton arrow = new JButton() {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    smooth(g2);
                    g2.setColor(INK);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2 - 2;
                    int cy = getHeight() / 2;
                    g2.drawPolyline(new int[] {cx - 4, cx, cx + 4}, new int[] {cy - 2, cy + 2, cy - 2}, 3);
                    g2.dispose();
                }
            };
            arrow.setBorder(BorderFactory.createEmptyBorder());
            arrow.setContentAreaFilled(false);
            arrow.setFocusPainted(false);
            arrow.setOpaque(false);
            arrow.setPreferredSize(new Dimension(24, 24));
            return arrow;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            g.setColor(CARD);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        protected Insets getInsets() {
            return new Insets(4, 8, 4, 2);
        }
    }

    static JCheckBox checkBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setForeground(INK);
        box.setIcon(new CheckIcon(false));
        box.setSelectedIcon(new CheckIcon(true));
        box.setIconTextGap(8);
        box.setFocusPainted(false);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return box;
    }

    // Rounded square, orange with a white tick when selected
    private static final class CheckIcon implements Icon {
        private final boolean checked;

        CheckIcon(boolean checked) {
            this.checked = checked;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            if (checked) {
                g2.setColor(ORANGE);
                g2.fillRoundRect(x, y, 15, 15, 5, 5);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[] {x + 4, x + 7, x + 12}, new int[] {y + 8, y + 11, y + 5}, 3);
            } else {
                g2.setColor(CARD);
                g2.fillRoundRect(x, y, 15, 15, 5, 5);
                g2.setColor(new Color(0xA9ACB2));
                g2.drawRoundRect(x, y, 15, 15, 5, 5);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    // Rounded outline used by fields, combos and scroll panes
    static final class RoundBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;
        private final Color colour;
        private final int radius;
        private final int thickness;

        RoundBorder(Color colour, int radius, int thickness) {
            this.colour = colour;
            this.radius = radius;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(colour);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 1, thickness + 1, thickness + 1, thickness + 1);
        }
    }

    // Thin scroll bar with a rounded orange thumb and no arrow buttons
    static final class FlatScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = ORANGE_THUMB;
            trackColor = CARD;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(CARD);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(isDragging || isThumbRollover() ? ORANGE : ORANGE_THUMB);
            boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            int arc = vertical ? r.width - 4 : r.height - 4;
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, arc, arc);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ tables

    static void styleTable(JTable table) {
        table.setRowHeight(30);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBackground(CARD);
        table.setForeground(INK);
        table.setSelectionBackground(ORANGE_SOFT);
        table.setSelectionForeground(INK);
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, cellRenderer(SwingConstants.LEFT));
        table.setDefaultRenderer(Number.class, cellRenderer(SwingConstants.LEFT));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
        table.getTableHeader().setPreferredSize(new Dimension(0, 30));
    }

    static DefaultTableCellRenderer cellRenderer(int alignment) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, false, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                if (!selected) {
                    setBackground(row % 2 == 0 ? CARD : STRIPE);
                    setForeground(INK);
                }
                return this;
            }
        };
        renderer.setHorizontalAlignment(alignment);
        return renderer;
    }

    static DefaultTableCellRenderer moneyRenderer() {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, false, row, column);
                setText(value instanceof BigDecimal amount ? Format.money(amount) : String.valueOf(value));
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 10));
                if (!selected) {
                    setBackground(row % 2 == 0 ? CARD : STRIPE);
                    setForeground(INK);
                }
                return this;
            }
        };
        renderer.setHorizontalAlignment(SwingConstants.RIGHT);
        return renderer;
    }

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private static final Border LINE = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER), BorderFactory.createEmptyBorder(0, 8, 0, 8));

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setFont(font(Font.BOLD, 12));
            setForeground(INK);
            setBackground(HEADER_ROW);
            setBorder(LINE);
            return this;
        }
    }

    // "Best Price" as a filled orange pill, "Fastest" as an outlined pill
    static TableCellRenderer pillRenderer() {
        return new PillRenderer();
    }

    private static final class PillRenderer extends JPanel implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private String text = "";
        private boolean filled;

        PillRenderer() {
            super(new BorderLayout());
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            text = value == null ? "" : value.toString();
            filled = text.startsWith("Best");
            setBackground(selected ? ORANGE_SOFT : row % 2 == 0 ? CARD : STRIPE);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (text.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setFont(font(Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(text) + 16;
            int h = 20;
            int x = 6;
            int y = (getHeight() - h) / 2;
            if (filled) {
                g2.setColor(ORANGE);
                g2.fillRoundRect(x, y, w, h, 8, 8);
                g2.setColor(Color.WHITE);
            } else {
                g2.setColor(ORANGE);
                g2.drawRoundRect(x, y, w, h, 8, 8);
                g2.setColor(ORANGE_DARK);
            }
            g2.drawString(text, x + 8, y + (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
