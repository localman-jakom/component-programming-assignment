package shippingcalculator;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

// The company identity used on screens, receipts and PDF quotes.
// Images are optional: if a file is missing the program falls back to text.
final class Brand {

    static final String NAME = "NEXUS GLOBEX TRANSIT";
    static final String TAGLINE = "Global Delivery Systems";

    private static BufferedImage logo;
    private static BufferedImage globe;
    private static boolean loaded;

    private Brand() {
    }

    // Full logo (globe and name) on a white background
    static synchronized BufferedImage logo() {
        load();
        return logo;
    }

    // Just the orange globe mark, transparent background
    static synchronized BufferedImage globe() {
        load();
        return globe;
    }

    private static void load() {
        if (!loaded) {
            logo = read("/branding/logo.png");
            globe = read("/branding/globe.png");
            loaded = true;
        }
    }

    private static BufferedImage read(String resource) {
        try (InputStream in = Brand.class.getResourceAsStream(resource)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException unreadable) {
            return null;
        }
    }
}
