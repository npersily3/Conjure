package dev.conjure.gen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Encodes an ARGB pixel grid (the LLM's emitted pixel art) to a PNG file. */
public final class PixelTexture {

    /** @param argb row-major ARGB pixels, argb[y][x] */
    public static void writePng(int[][] argb, Path out) throws IOException {
        int height = argb.length;
        int width = argb[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb[y][x]);
            }
        }
        Files.createDirectories(out.getParent());
        try (OutputStream os = Files.newOutputStream(out)) {
            ImageIO.write(image, "PNG", os);
        }
    }

    /** Parses "#RRGGBB" or "#AARRGGBB" into a packed ARGB int. Defaults to transparent. */
    public static int parseColor(String hex) {
        if (hex == null) return 0;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        try {
            if (h.length() == 6) {
                return 0xFF000000 | (int) Long.parseLong(h, 16);
            } else if (h.length() == 8) {
                return (int) Long.parseLong(h, 16);
            }
        } catch (NumberFormatException ignored) {
            // fall through to transparent
        }
        return 0;
    }

    private PixelTexture() {}
}
