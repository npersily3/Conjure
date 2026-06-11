package dev.conjure.gen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Encodes an ARGB pixel grid (the LLM's emitted pixel art) to a PNG file. */
public final class PixelTexture {

    /**
     * Decodes a raw PNG byte array produced by an image-generation backend and scales it to
     * {@code targetSize}×{@code targetSize} using nearest-neighbour resampling (which preserves
     * the pixelated look for Minecraft textures).
     *
     * @param png        raw PNG bytes (e.g. from {@link dev.conjure.ai.ComfyUIProvider#generateTexture})
     * @param targetSize desired edge length in pixels (e.g. 16, 64, 128)
     * @return ARGB pixel grid indexed {@code [y][x]}, always exactly {@code targetSize} rows/cols
     * @throws IOException if the bytes cannot be decoded as a valid PNG
     */
    public static int[][] fromPng(byte[] png, int targetSize) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        if (src == null) {
            throw new IOException("ImageIO could not decode the supplied bytes as a PNG image");
        }

        int srcW = src.getWidth();
        int srcH = src.getHeight();
        int[][] argb = new int[targetSize][targetSize];

        for (int y = 0; y < targetSize; y++) {
            // nearest-neighbour: map target pixel back to source pixel
            int srcY = (int) ((y + 0.5) * srcH / targetSize);
            if (srcY >= srcH) srcY = srcH - 1;
            for (int x = 0; x < targetSize; x++) {
                int srcX = (int) ((x + 0.5) * srcW / targetSize);
                if (srcX >= srcW) srcX = srcW - 1;
                // getRGB returns ARGB; BufferedImage.TYPE_INT_ARGB uses the same encoding
                argb[y][x] = src.getRGB(srcX, srcY);
            }
        }
        return argb;
    }

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
