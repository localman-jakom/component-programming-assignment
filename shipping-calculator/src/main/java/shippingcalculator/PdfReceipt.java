package shippingcalculator;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

// Writes a one-page A4 PDF quote with the company logo, using only the JDK.
// A PDF is a list of numbered objects plus a table of their byte offsets; the page is a
// "content stream" of drawing commands (text, lines, filled boxes, the logo image).
// Text uses the PDF standard fonts; numbers use Courier so they can be right-aligned exactly.
final class PdfReceipt {

    private static final float PAGE_W = 595;
    private static final float PAGE_H = 842;
    private static final float LEFT = 40;
    private static final float RIGHT = 555;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.UK);

    private static final float[] ORANGE = {0.949f, 0.420f, 0.114f};
    private static final float[] DARK = {0.165f, 0.165f, 0.180f};
    private static final float[] GREY = {0.42f, 0.44f, 0.47f};
    private static final float[] LIGHT = {0.957f, 0.957f, 0.965f};
    private static final float[] LINE = {0.85f, 0.86f, 0.88f};

    private final StringBuilder page = new StringBuilder();

    private PdfReceipt() {
    }

    static byte[] render(ShipmentQuote quote, LocalDateTime when) {
        PdfReceipt pdf = new PdfReceipt();
        byte[] logo = jpeg(Brand.logo());
        float[] logoSize = logo == null ? null : fitLogo(Brand.logo(), 210, 64);
        pdf.drawPage(quote, when, logoSize);
        return pdf.assemble(logo, Brand.logo(), when);
    }

    // ------------------------------------------------------------------ page layout

    private void drawPage(ShipmentQuote quote, LocalDateTime when, float[] logoSize) {
        Route route = quote.route();
        Shipment shipment = quote.shipment();
        float top = PAGE_H - 40;

        // Header: logo on the left, title and date on the right
        if (logoSize != null) {
            page.append(String.format(Locale.US, "q %.2f 0 0 %.2f %.2f %.2f cm /Logo Do Q\n",
                    logoSize[0], logoSize[1], LEFT, top - logoSize[1]));
        } else {
            text("F2", 18, LEFT, top - 24, DARK, Brand.NAME);
            text("F1", 10, LEFT, top - 40, GREY, Brand.TAGLINE);
        }
        text("F2", 16, 345, top - 22, DARK, "SHIPPING QUOTATION");
        text("F1", 10, 345, top - 40, GREY, "Issued " + when.format(WHEN));
        text("F1", 10, 345, top - 54, GREY, "Valid for 7 days");
        fill(LEFT, top - 78, RIGHT - LEFT, 3, ORANGE);

        // Shipment summary in two columns
        float y = top - 104;
        pair(LEFT, y, "Carrier", route.carrier().name() + " (" + route.carrier().code() + ")");
        pair(310, y, "Transport", route.mode().label());
        y -= 30;
        pair(LEFT, y, "Route", route.path());
        pair(310, y, "Service", quote.serviceName() + ", delivery in " + Format.days(quote.deliveryDays()));
        y -= 30;
        pair(LEFT, y, "Items", String.valueOf(shipment.parcels().size()));
        pair(310, y, "Options", options(shipment));

        // Items table
        y -= 40;
        float[] rightEdges = {56, 112, 0, 272, 326, 384, 432, 486, RIGHT - 6};
        String[] headers = {"#", "Actual kg", "Size L x W x H cm", "Volum. kg", "Charge. kg",
                "Base", "Fragile", "Express", "Total"};
        fill(LEFT, y - 6, RIGHT - LEFT, 20, LIGHT);
        for (int c = 0; c < headers.length; c++) {
            if (c == 2) {
                text("F4", 7.5f, 120, y, DARK, headers[c]);
            } else {
                rightText("F4", 7.5f, rightEdges[c], y, DARK, headers[c]);
            }
        }
        y -= 20;
        for (int i = 0; i < quote.parcels().size(); i++) {
            CostBreakdown cost = quote.parcels().get(i);
            PackageDetails item = shipment.parcels().get(i);
            String[] cells = {String.valueOf(i + 1), kg(cost.actualWeightKg()),
                    size(item), kg(cost.volumetricWeightKg()), kg(cost.chargeableWeightKg()),
                    Format.amount(cost.baseCost()), Format.amount(cost.fragileSurcharge()),
                    Format.amount(cost.expressSurcharge()), Format.amount(cost.total())};
            for (int c = 0; c < cells.length; c++) {
                if (c == 2) {
                    text("F3", 7.5f, 120, y, DARK, cells[c]);
                } else {
                    rightText(c == 8 ? "F4" : "F3", 7.5f, rightEdges[c], y, DARK, cells[c]);
                }
            }
            line(LEFT, y - 6, RIGHT, y - 6, LINE);
            y -= 18;
        }

        // Totals box on the right
        y -= 14;
        float boxLeft = 330;
        y = totalLine(boxLeft, y, "Chargeable weight", Format.kg(quote.chargeableWeightKg()));
        y = totalLine(boxLeft, y, "Base rate", Format.money(quote.baseCost()));
        if (shipment.fragile()) {
            y = totalLine(boxLeft, y, "Fragile surcharge", Format.money(quote.fragileSurcharge()));
        }
        if (shipment.express()) {
            y = totalLine(boxLeft, y, "Express surcharge (" + route.carrier().expressPercent() + ")",
                    Format.money(quote.expressSurcharge()));
        }
        fill(boxLeft, y - 10, RIGHT - boxLeft, 28, ORANGE);
        text("F2", 13, boxLeft + 10, y, new float[] {1, 1, 1}, "TOTAL");
        rightText("F4", 13, RIGHT - 10, y, new float[] {1, 1, 1}, Format.money(quote.total()));

        // How the price was worked out
        y -= 50;
        text("F2", 9, LEFT, y, DARK, "How this price is calculated");
        y -= 14;
        for (String note : new String[] {
                "Volumetric weight = length x width x height (cm) / " + route.mode().volumetricDivisor()
                        + " for " + route.mode().label().toLowerCase(Locale.ROOT) + ".",
                "Chargeable weight is the greater of actual and volumetric weight, rounded up to the next 0.5 kg.",
                "Base rate comes from the " + route.tariffName() + " tariff of " + route.carrier().name() + ".",
                "All amounts are in Kenya shillings (KES). Measurements were converted with unitconverter-1.0.jar."}) {
            text("F1", 8.5f, LEFT, y, GREY, note);
            y -= 12;
        }

        // Footer
        line(LEFT, 52, RIGHT, 52, LINE);
        text("F2", 8, LEFT, 38, ORANGE, Brand.NAME);
        text("F1", 8, LEFT + 112, 38, GREY, Brand.TAGLINE + "  |  Shipping quotation system");
    }

    private void pair(float x, float y, String label, String value) {
        text("F1", 8.5f, x, y + 9, GREY, label.toUpperCase(Locale.ROOT));
        text("F2", 10.5f, x, y - 3, DARK, value);
    }

    private float totalLine(float left, float y, String label, String amount) {
        text("F1", 10, left + 10, y, DARK, label);
        rightText("F3", 10, RIGHT - 10, y, DARK, amount);
        line(left, y - 7, RIGHT, y - 7, LINE);
        return y - 20;
    }

    private static String options(Shipment shipment) {
        if (shipment.fragile() && shipment.express()) {
            return "Fragile handling, Express";
        }
        return shipment.fragile() ? "Fragile handling" : shipment.express() ? "Express" : "None";
    }

    private static String kg(double kg) {
        return String.format(Locale.US, "%,.2f", kg);
    }

    private static String size(PackageDetails item) {
        return dim(item.lengthCm()) + " x " + dim(item.widthCm()) + " x " + dim(item.heightCm());
    }

    private static String dim(double cm) {
        return BigDecimal.valueOf(cm).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ drawing commands

    private void text(String font, float size, float x, float y, float[] rgb, String value) {
        page.append(String.format(Locale.US, "BT %.3f %.3f %.3f rg /%s %.1f Tf %.2f %.2f Td (%s) Tj ET\n",
                rgb[0], rgb[1], rgb[2], font, size, x, y, escape(value)));
    }

    // Only for the Courier fonts (F3, F4): every character is 0.6 x the font size wide
    private void rightText(String font, float size, float right, float y, float[] rgb, String value) {
        text(font, size, right - value.length() * size * 0.6f, y, rgb, value);
    }

    private void fill(float x, float y, float w, float h, float[] rgb) {
        page.append(String.format(Locale.US, "%.3f %.3f %.3f rg %.2f %.2f %.2f %.2f re f\n",
                rgb[0], rgb[1], rgb[2], x, y, w, h));
    }

    private void line(float x1, float y1, float x2, float y2, float[] rgb) {
        page.append(String.format(Locale.US, "%.3f %.3f %.3f RG 0.6 w %.2f %.2f m %.2f %.2f l S\n",
                rgb[0], rgb[1], rgb[2], x1, y1, x2, y2));
    }

    // PDF strings need \, ( and ) escaped; anything outside plain ASCII becomes '?'
    static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (ch == '\\' || ch == '(' || ch == ')') {
                out.append('\\').append(ch);
            } else if (ch < 32 || ch > 126) {
                out.append('?');
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ file structure

    private byte[] assemble(byte[] logo, BufferedImage logoImage, LocalDateTime when) {
        List<byte[]> objects = new ArrayList<>();
        byte[] content = page.toString().getBytes(StandardCharsets.US_ASCII);
        String resources = "/Font << /F1 5 0 R /F2 6 0 R /F3 7 0 R /F4 8 0 R >>"
                + (logo == null ? "" : " /XObject << /Logo 9 0 R >>");

        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) PAGE_W + " " + (int) PAGE_H
                + "] /Resources << " + resources + " >> /Contents 4 0 R >>"));
        objects.add(stream("<< /Length " + content.length + " >>", content));
        objects.add(font("Helvetica"));
        objects.add(font("Helvetica-Bold"));
        objects.add(font("Courier"));
        objects.add(font("Courier-Bold"));
        if (logo != null) {
            objects.add(stream("<< /Type /XObject /Subtype /Image /Width " + logoImage.getWidth()
                    + " /Height " + logoImage.getHeight() + " /ColorSpace /DeviceRGB /BitsPerComponent 8"
                    + " /Filter /DCTDecode /Length " + logo.length + " >>", logo));
        } else {
            objects.add(ascii("<< >>"));
        }
        objects.add(ascii("<< /Title (Shipping quotation) /Author (" + escape(Brand.NAME) + ") /Producer"
                + " (Shipping Quotation, unitconverter-1.0.jar) /CreationDate (D:"
                + when.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ") >>"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1));
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, ascii((i + 1) + " 0 obj\n"));
            write(out, objects.get(i));
            write(out, ascii("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format(Locale.US, "%010d 00000 n \n", offset));
        }
        table.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R /Info ")
                .append(objects.size()).append(" 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(out, ascii(table.toString()));
        return out.toByteArray();
    }

    private static byte[] font(String name) {
        return ascii("<< /Type /Font /Subtype /Type1 /BaseFont /" + name + " /Encoding /WinAnsiEncoding >>");
    }

    private static byte[] stream(String dictionary, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, ascii(dictionary + "\nstream\n"));
        write(out, data);
        write(out, ascii("\nendstream"));
        return out.toByteArray();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    // Logo size in points, keeping its proportions inside the given box
    private static float[] fitLogo(BufferedImage image, float maxW, float maxH) {
        float scale = Math.min(maxW / image.getWidth(), maxH / image.getHeight());
        return new float[] {image.getWidth() * scale, image.getHeight() * scale};
    }

    // PDF viewers can show JPEG data directly, so the logo is stored as a JPEG
    private static byte[] jpeg(BufferedImage image) {
        if (image == null) {
            return null;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.92f);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(rgb, null, null), params);
        } catch (IOException unexpected) {
            return null;
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
