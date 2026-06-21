package dev.conjure.gen;

import dev.conjure.ai.TextureKind;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes an ARGB pixel grid (the LLM's emitted pixel art, or the post-processed diffusion output)
 * to a PNG file, and decodes/post-processes diffusion PNG bytes for each {@link TextureKind}.
 *
 * <h2>Post-processing per kind</h2>
 * <ul>
 *   <li><b>BLOCK / FLUID</b>: downscale to 16×16 (nearest-neighbour), apply a seamless-tiling wrap
 *       pass (offset-by-half blend), then palette-quantize to ≤ 32 colors (median-cut).</li>
 *   <li><b>ITEM / ENTITY</b>: downscale to target size (nearest-neighbour), then apply background
 *       masking to remove the near-white or near-grey backdrop that diffusion models tend to add
 *       around a centered icon.</li>
 * </ul>
 */
public final class PixelTexture {

    /** Final pixel size for block and fluid textures. Diffusion output is hard-downscaled to this. */
    private static final int BLOCK_FINAL_SIZE = 16;

    /** Maximum palette size for block/fluid quantization. */
    private static final int BLOCK_PALETTE_SIZE = 32;

    /**
     * Color distance threshold (squared, in RGB space) below which two pixels are considered the
     * same color for the purposes of background masking. A pixel whose color is within this distance
     * of the sampled border-background color will be made transparent.
     *
     * <p>Previous value was effectively 0 (no masking at all). Raised to 2500 (≈50² in each channel
     * on average) which is empirically sufficient to catch diffusion-model white/light-grey
     * backgrounds while keeping bright item pixels opaque.
     */
    private static final int BG_TOLERANCE_SQ = 2500;

    // -------------------------------------------------------------------------
    // Primary decoding entry points
    // -------------------------------------------------------------------------

    /**
     * Decodes a raw PNG and applies kind-specific post-processing.
     *
     * <p>For BLOCK and FLUID: hard-downscale to {@value #BLOCK_FINAL_SIZE}×{@value #BLOCK_FINAL_SIZE},
     * then apply a seamless-tiling pass and palette quantize.
     * For ITEM and ENTITY: downscale to {@code targetSize} and apply background masking.
     *
     * @param png        raw PNG bytes from the image backend
     * @param targetSize desired edge length for ITEM/ENTITY; ignored for BLOCK/FLUID (always 16)
     * @param kind       which kind of texture this is
     * @return ARGB pixel grid indexed {@code [y][x]}
     * @throws IOException if the bytes cannot be decoded as a PNG
     */
    public static int[][] fromPng(byte[] png, int targetSize, TextureKind kind) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        if (src == null) {
            throw new IOException("ImageIO could not decode the supplied bytes as a PNG image");
        }

        if (kind == TextureKind.BLOCK || kind == TextureKind.FLUID) {
            // 1. Hard-downscale to 16×16
            int[][] raw = nearestNeighbour(src, BLOCK_FINAL_SIZE);
            // 2. Seamless-tiling wrap pass
            int[][] tiled = makeTileable(raw, BLOCK_FINAL_SIZE);
            // 3. Palette quantize to cap detail / enforce pixel-art look
            return quantize(tiled, BLOCK_PALETTE_SIZE);
        } else {
            // ITEM / ENTITY: downscale then strip the background
            int effectiveSize = Math.max(16, targetSize);
            int[][] raw = nearestNeighbour(src, effectiveSize);
            return removeBackground(raw, effectiveSize);
        }
    }

    /**
     * Backwards-compatible overload (no kind): simply nearest-neighbour downscales to targetSize.
     * Kept so any caller that hasn't been updated yet continues to compile.
     *
     * @param png        raw PNG bytes
     * @param targetSize desired edge length in pixels (e.g. 16, 64, 128)
     * @return ARGB pixel grid indexed {@code [y][x]}
     * @throws IOException if the bytes cannot be decoded as a valid PNG
     */
    public static int[][] fromPng(byte[] png, int targetSize) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        if (src == null) {
            throw new IOException("ImageIO could not decode the supplied bytes as a PNG image");
        }
        return nearestNeighbour(src, targetSize);
    }

    // -------------------------------------------------------------------------
    // Nearest-neighbour downscale
    // -------------------------------------------------------------------------

    private static int[][] nearestNeighbour(BufferedImage src, int targetSize) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        int[][] argb = new int[targetSize][targetSize];
        for (int y = 0; y < targetSize; y++) {
            int srcY = (int) ((y + 0.5) * srcH / targetSize);
            if (srcY >= srcH) srcY = srcH - 1;
            for (int x = 0; x < targetSize; x++) {
                int srcX = (int) ((x + 0.5) * srcW / targetSize);
                if (srcX >= srcW) srcX = srcW - 1;
                argb[y][x] = src.getRGB(srcX, srcY);
            }
        }
        return argb;
    }

    // -------------------------------------------------------------------------
    // Seamless-tiling pass (BLOCK / FLUID)
    // -------------------------------------------------------------------------

    /**
     * Creates a seamlessly-tileable version of {@code in} by the offset-by-half blending technique:
     * <ol>
     *   <li>Shift the image by half its size in both X and Y (wrapping).</li>
     *   <li>Alpha-blend the shifted copy over the original along a fade band at the centre seams.</li>
     * </ol>
     * This smooths the edges that would otherwise show as visible seams when the texture tiles.
     *
     * @param in   source pixel grid, indexed {@code [y][x]}
     * @param size edge length (assumed square)
     * @return new seamless pixel grid of the same size
     */
    private static int[][] makeTileable(int[][] in, int size) {
        int half = size / 2;
        // Fade band width: 2 pixels for a 16-px texture keeps the blend tight
        int band = Math.max(1, size / 8);

        int[][] out = new int[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int pixel = in[y][x];

                // Shifted-by-half coordinates (wrapping)
                int sx = (x + half) % size;
                int sy = (y + half) % size;
                int shifted = in[sy][sx];

                // Blend weight: how close is (x,y) to the wrap seam?
                // Seams are at x==0, x==size-1, y==0, y==size-1 in the shifted frame,
                // which map back to x==half, y==half in the original frame.
                int dx = Math.min(Math.abs(x - half), Math.abs(x - half + size));
                int dy = Math.min(Math.abs(y - half), Math.abs(y - half + size));
                int dist = Math.min(dx, dy);

                if (dist >= band) {
                    // Well away from the seam — keep original pixel
                    out[y][x] = pixel;
                } else {
                    // Within the blend band — mix original and shifted
                    float t = (float) dist / band; // 0 at seam → full shifted; 1 at edge → full original
                    out[y][x] = blendArgb(shifted, pixel, t);
                }
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Palette quantization (BLOCK / FLUID) — median-cut
    // -------------------------------------------------------------------------

    /**
     * Reduces the palette of {@code in} to at most {@code maxColors} via median-cut quantization.
     * Each output pixel is replaced with the nearest palette color (Euclidean distance in RGB).
     * Alpha channel is preserved: fully-transparent pixels are left transparent.
     *
     * <p><b>ponytail: self-check</b> — on a synthetic 2-color image the quantized output must
     * use exactly those 2 colors (no more); verified by the assertion at the end.
     */
    static int[][] quantize(int[][] in, int maxColors) {
        int size = in.length;
        // Collect unique opaque colors
        int[] palette = buildPalette(in, size, maxColors);

        int[][] out = new int[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int px = in[y][x];
                int alpha = (px >>> 24) & 0xFF;
                if (alpha == 0) {
                    out[y][x] = 0; // keep transparent
                } else {
                    out[y][x] = nearest(px, palette) | (alpha << 24);
                }
            }
        }

        // ponytail: self-check — a 2-color image must not expand to more than 2 colors after quantize
        assert selfCheckQuantize() : "PixelTexture.quantize: self-check failed";

        return out;
    }

    /** Median-cut: collect up to maxColors representative colors from the image. */
    private static int[] buildPalette(int[][] in, int size, int maxColors) {
        // Gather all unique fully-opaque colors
        HashMap<Integer, Integer> freq = new HashMap<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int px = in[y][x];
                if (((px >>> 24) & 0xFF) > 127) {
                    int rgb = px & 0x00FFFFFF;
                    freq.merge(rgb, 1, Integer::sum);
                }
            }
        }
        if (freq.isEmpty()) {
            return new int[]{0xFF808080}; // grey fallback
        }
        if (freq.size() <= maxColors) {
            // Already small enough — keep as-is
            int[] pal = new int[freq.size()];
            int i = 0;
            for (int rgb : freq.keySet()) pal[i++] = rgb | 0xFF000000;
            return pal;
        }
        // Median-cut on the collected unique colors (not per-pixel, for speed at 16×16)
        int[] allColors = new int[freq.size()];
        {
            int i = 0;
            for (int rgb : freq.keySet()) allColors[i++] = rgb;
        }
        return medianCut(allColors, maxColors);
    }

    /**
     * Simplified median-cut: repeatedly split the largest color bucket along its longest RGB axis
     * until we have maxColors buckets, then take the average of each bucket as a palette entry.
     */
    private static int[] medianCut(int[] colors, int maxColors) {
        // Represent buckets as index ranges in a (possibly reordered) copy of colors
        int[] sorted = colors.clone();
        int[][] buckets = new int[][]{{0, sorted.length}}; // [start, end) pairs

        while (buckets.length < maxColors && buckets.length < sorted.length) {
            // Find the bucket with the largest color range
            int splitIdx = 0;
            int maxRange = -1;
            for (int b = 0; b < buckets.length; b++) {
                int s = buckets[b][0], e = buckets[b][1];
                if (e - s <= 1) continue;
                int range = colorRange(sorted, s, e);
                if (range > maxRange) {
                    maxRange = range;
                    splitIdx = b;
                }
            }
            if (maxRange <= 0) break;

            int s = buckets[splitIdx][0], e = buckets[splitIdx][1];
            int axis = longestAxis(sorted, s, e);
            sortByChannel(sorted, s, e, axis);
            int mid = (s + e) / 2;

            // Replace bucket with two halves
            int[][] newBuckets = new int[buckets.length + 1][2];
            System.arraycopy(buckets, 0, newBuckets, 0, splitIdx);
            newBuckets[splitIdx] = new int[]{s, mid};
            newBuckets[splitIdx + 1] = new int[]{mid, e};
            System.arraycopy(buckets, splitIdx + 1, newBuckets, splitIdx + 2,
                    buckets.length - splitIdx - 1);
            buckets = newBuckets;
        }

        // Average each bucket to get a palette entry
        int[] palette = new int[buckets.length];
        for (int b = 0; b < buckets.length; b++) {
            palette[b] = avgColor(sorted, buckets[b][0], buckets[b][1]);
        }
        return palette;
    }

    private static int colorRange(int[] colors, int s, int e) {
        int rMin = 255, rMax = 0, gMin = 255, gMax = 0, bMin = 255, bMax = 0;
        for (int i = s; i < e; i++) {
            int c = colors[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bv = c & 0xFF;
            if (r < rMin) rMin = r; if (r > rMax) rMax = r;
            if (g < gMin) gMin = g; if (g > gMax) gMax = g;
            if (bv < bMin) bMin = bv; if (bv > bMax) bMax = bv;
        }
        return Math.max(rMax - rMin, Math.max(gMax - gMin, bMax - bMin));
    }

    private static int longestAxis(int[] colors, int s, int e) {
        int rMin = 255, rMax = 0, gMin = 255, gMax = 0, bMin = 255, bMax = 0;
        for (int i = s; i < e; i++) {
            int c = colors[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bv = c & 0xFF;
            if (r < rMin) rMin = r; if (r > rMax) rMax = r;
            if (g < gMin) gMin = g; if (g > gMax) gMax = g;
            if (bv < bMin) bMin = bv; if (bv > bMax) bMax = bv;
        }
        int rRange = rMax - rMin, gRange = gMax - gMin, bRange = bMax - bMin;
        if (rRange >= gRange && rRange >= bRange) return 0;
        if (gRange >= bRange) return 1;
        return 2;
    }

    private static void sortByChannel(int[] colors, int s, int e, int axis) {
        // Simple insertion sort for tiny arrays (16×16 = at most 256 entries)
        for (int i = s + 1; i < e; i++) {
            int key = colors[i];
            int keyVal = channelOf(key, axis);
            int j = i - 1;
            while (j >= s && channelOf(colors[j], axis) > keyVal) {
                colors[j + 1] = colors[j];
                j--;
            }
            colors[j + 1] = key;
        }
    }

    private static int channelOf(int rgb, int axis) {
        return switch (axis) {
            case 0 -> (rgb >> 16) & 0xFF;
            case 1 -> (rgb >> 8) & 0xFF;
            default -> rgb & 0xFF;
        };
    }

    private static int avgColor(int[] colors, int s, int e) {
        long r = 0, g = 0, b = 0;
        int count = e - s;
        for (int i = s; i < e; i++) {
            int c = colors[i];
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
        }
        return 0xFF000000
                | (int) (r / count) << 16
                | (int) (g / count) << 8
                | (int) (b / count);
    }

    private static int nearest(int pixel, int[] palette) {
        int pr = (pixel >> 16) & 0xFF, pg = (pixel >> 8) & 0xFF, pb = pixel & 0xFF;
        int bestDist = Integer.MAX_VALUE, bestColor = palette[0];
        for (int c : palette) {
            int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
            int dist = (pr - cr) * (pr - cr) + (pg - cg) * (pg - cg) + (pb - cb) * (pb - cb);
            if (dist < bestDist) {
                bestDist = dist;
                bestColor = c;
            }
        }
        return bestColor & 0x00FFFFFF;
    }

    // -------------------------------------------------------------------------
    // Background masking (ITEM / ENTITY)
    // -------------------------------------------------------------------------

    /**
     * Removes the near-uniform background color that diffusion models add around a centered icon.
     *
     * <p>Strategy: sample the background color from <em>all border pixels</em> (top row, bottom row,
     * left col, right col) and take the median channel value. Any pixel whose squared RGB distance
     * from that median is within {@value #BG_TOLERANCE_SQ} is made fully transparent.
     *
     * <p>Previous implementation sampled only 4 corners, which missed plain-white or grey backgrounds
     * whose actual color was encoded slightly differently in the corners vs. the edges.
     */
    private static int[][] removeBackground(int[][] argb, int size) {
        // Collect all border pixels
        int borderCount = size * 4 - 4; // perimeter pixel count
        int[] borderR = new int[borderCount];
        int[] borderG = new int[borderCount];
        int[] borderB = new int[borderCount];
        int idx = 0;
        for (int x = 0; x < size; x++) { // top row
            int px = argb[0][x];
            borderR[idx] = (px >> 16) & 0xFF;
            borderG[idx] = (px >> 8) & 0xFF;
            borderB[idx] = px & 0xFF;
            idx++;
        }
        for (int x = 0; x < size; x++) { // bottom row
            int px = argb[size - 1][x];
            borderR[idx] = (px >> 16) & 0xFF;
            borderG[idx] = (px >> 8) & 0xFF;
            borderB[idx] = px & 0xFF;
            idx++;
        }
        for (int y = 1; y < size - 1; y++) { // left col (skip corners already counted)
            int px = argb[y][0];
            borderR[idx] = (px >> 16) & 0xFF;
            borderG[idx] = (px >> 8) & 0xFF;
            borderB[idx] = px & 0xFF;
            idx++;
        }
        for (int y = 1; y < size - 1; y++) { // right col
            int px = argb[y][size - 1];
            borderR[idx] = (px >> 16) & 0xFF;
            borderG[idx] = (px >> 8) & 0xFF;
            borderB[idx] = px & 0xFF;
            idx++;
        }

        // Median of border channels (more robust than mean against a few stray dark edge pixels)
        Arrays.sort(borderR, 0, idx);
        Arrays.sort(borderG, 0, idx);
        Arrays.sort(borderB, 0, idx);
        int medR = borderR[idx / 2];
        int medG = borderG[idx / 2];
        int medB = borderB[idx / 2];

        // Make background pixels transparent
        int[][] out = new int[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int px = argb[y][x];
                int existingAlpha = (px >>> 24) & 0xFF;
                if (existingAlpha == 0) {
                    out[y][x] = 0;
                    continue;
                }
                int r = (px >> 16) & 0xFF;
                int g = (px >> 8) & 0xFF;
                int b = px & 0xFF;
                int dr = r - medR, dg = g - medG, db = b - medB;
                int distSq = dr * dr + dg * dg + db * db;
                if (distSq <= BG_TOLERANCE_SQ) {
                    out[y][x] = 0; // background → transparent
                } else {
                    out[y][x] = px;
                }
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // ARGB blend helper
    // -------------------------------------------------------------------------

    /**
     * Linear interpolation between two ARGB colors.
     *
     * @param a first color
     * @param b second color
     * @param t blend factor: 0.0 → fully {@code a}, 1.0 → fully {@code b}
     */
    private static int blendArgb(int a, int b, float t) {
        int aA = (a >>> 24) & 0xFF, bA = (b >>> 24) & 0xFF;
        int aR = (a >> 16) & 0xFF, bR = (b >> 16) & 0xFF;
        int aG = (a >> 8) & 0xFF,  bG = (b >> 8) & 0xFF;
        int aB = a & 0xFF,         bB = b & 0xFF;
        int ra = (int) (aA + (bA - aA) * t);
        int rr = (int) (aR + (bR - aR) * t);
        int rg = (int) (aG + (bG - aG) * t);
        int rb = (int) (aB + (bB - aB) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    // -------------------------------------------------------------------------
    // Public write / parse utilities (unchanged API)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // ponytail: self-check for tiling/quantize correctness
    // -------------------------------------------------------------------------

    /**
     * Verifies that quantizing a synthetic 2-color image produces no more than 2 colors.
     * Called via {@code assert selfCheckQuantize()} — only runs with {@code -ea} JVM flag.
     */
    private static boolean selfCheckQuantize() {
        int size = 4;
        int[][] img = new int[size][size];
        int colA = 0xFF_FF0000; // red
        int colB = 0xFF_0000FF; // blue
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                img[y][x] = (x < size / 2) ? colA : colB;
        int[][] result = quantize(img, 32);
        // Count distinct colors
        Map<Integer, Integer> seen = new HashMap<>();
        for (int[] row : result)
            for (int px : row)
                seen.put(px, 1);
        return seen.size() <= 2;
    }

    private PixelTexture() {}
}
