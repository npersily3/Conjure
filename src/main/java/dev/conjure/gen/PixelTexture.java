package dev.conjure.gen;

import dev.conjure.ai.TextureKind;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/** Encodes an ARGB pixel grid (the LLM's emitted pixel art) to a PNG file. */
public final class PixelTexture {

    /**
     * Decodes a raw PNG byte array produced by an image-generation backend and scales it to
     * {@code targetSize}×{@code targetSize} using nearest-neighbour resampling (which preserves
     * the pixelated look for Minecraft textures).
     *
     * <p>For {@link TextureKind#ITEM}, the background is automatically made transparent: the
     * background colour is detected from the image corners and then flood-filled inward from
     * all four edges within a small colour tolerance, clearing those pixels to {@code #00000000}.
     * Interior pixels that happen to share the background colour are preserved (the fill is
     * edge-connected, not a global threshold). BLOCK and FLUID textures are left fully opaque.
     *
     * @param png        raw PNG bytes (e.g. from {@link dev.conjure.ai.ComfyUIProvider#generateTexture})
     * @param targetSize desired edge length in pixels (e.g. 16, 64, 128)
     * @param kind       texture kind — only {@link TextureKind#ITEM} receives background masking
     * @return ARGB pixel grid indexed {@code [y][x]}, always exactly {@code targetSize} rows/cols
     * @throws IOException if the bytes cannot be decoded as a valid PNG
     */
    public static int[][] fromPng(byte[] png, int targetSize, TextureKind kind) throws IOException {
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

        if (kind == TextureKind.ITEM) {
            maskItemBackground(argb);
        }
        return argb;
    }

    /**
     * Backward-compatible overload — keeps the image fully opaque (used for BLOCK path and
     * callers that pre-date the kind parameter).
     */
    public static int[][] fromPng(byte[] png, int targetSize) throws IOException {
        return fromPng(png, targetSize, TextureKind.BLOCK);
    }

    // -------------------------------------------------------------------------
    // Background masking — ITEM kind only
    // -------------------------------------------------------------------------

    /**
     * Colour tolerance (per channel, 0-255) used when deciding whether a border-connected pixel
     * belongs to the background. A value of 30 handles slight JPEG/diffusion artefacts around
     * solid-colour backgrounds while being tight enough to avoid eating into subject detail.
     */
    private static final int BG_TOLERANCE = 30;

    /**
     * Flood-fill inward from every edge pixel, clearing the background to fully-transparent.
     * The background colour is sampled from the four corners (majority-vote of the corner rgbs).
     * Only pixels that are edge-connected to the border AND whose RGB is within
     * {@link #BG_TOLERANCE} on each channel are cleared. Interior pixels are never touched.
     */
    static void maskItemBackground(int[][] argb) {
        int h = argb.length;
        int w = argb[0].length;

        // --- Sample background colour from the four corners (majority-vote) ---
        int[] corners = { rgb(argb[0][0]), rgb(argb[0][w - 1]), rgb(argb[h - 1][0]), rgb(argb[h - 1][w - 1]) };
        int bgRgb = majorityColor(corners);

        // --- Flood-fill from all border pixels ---
        boolean[][] visited = new boolean[h][w];
        Deque<int[]> queue = new ArrayDeque<>();

        // Seed the queue with every border pixel that matches the background
        for (int x = 0; x < w; x++) {
            if (withinTolerance(rgb(argb[0][x]), bgRgb)) enqueue(queue, visited, 0, x, h, w);
            if (withinTolerance(rgb(argb[h - 1][x]), bgRgb)) enqueue(queue, visited, h - 1, x, h, w);
        }
        for (int y = 1; y < h - 1; y++) {
            if (withinTolerance(rgb(argb[y][0]), bgRgb)) enqueue(queue, visited, y, 0, h, w);
            if (withinTolerance(rgb(argb[y][w - 1]), bgRgb)) enqueue(queue, visited, y, w - 1, h, w);
        }

        // 4-connected BFS inward
        int[] dy = { -1, 1, 0, 0 };
        int[] dx = { 0, 0, -1, 1 };
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            int cy = cell[0], cx = cell[1];
            argb[cy][cx] = 0x00000000; // clear to fully transparent
            for (int d = 0; d < 4; d++) {
                int ny = cy + dy[d], nx = cx + dx[d];
                if (ny >= 0 && ny < h && nx >= 0 && nx < w && !visited[ny][nx]
                        && withinTolerance(rgb(argb[ny][nx]), bgRgb)) {
                    enqueue(queue, visited, ny, nx, h, w);
                }
            }
        }
    }

    private static void enqueue(Deque<int[]> q, boolean[][] vis, int y, int x, int h, int w) {
        if (y >= 0 && y < h && x >= 0 && x < w && !vis[y][x]) {
            vis[y][x] = true;
            q.add(new int[]{ y, x });
        }
    }

    /** Extracts the RGB triplet (no alpha) from an ARGB int. */
    private static int rgb(int argb) { return argb & 0x00FFFFFF; }

    /** True if each channel of {@code a} and {@code b} differs by at most {@link #BG_TOLERANCE}. */
    private static boolean withinTolerance(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) <= BG_TOLERANCE
            && Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) <= BG_TOLERANCE
            && Math.abs((a & 0xFF) - (b & 0xFF)) <= BG_TOLERANCE;
    }

    /**
     * Returns the most common colour from {@code samples}, or the first element on a tie.
     * With four corners there are at most four distinct values; a simple linear scan is fine.
     */
    private static int majorityColor(int[] samples) {
        int best = samples[0], bestCount = 0;
        for (int i = 0; i < samples.length; i++) {
            int count = 0;
            for (int j = 0; j < samples.length; j++) {
                if (withinTolerance(samples[i], samples[j])) count++;
            }
            if (count > bestCount) { bestCount = count; best = samples[i]; }
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // ponytail: self-check — run with `java -ea PixelTexture`
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@link #maskItemBackground} clears border-connected background pixels and
     * leaves an interior blob fully opaque. Run with {@code java -ea} in the classpath that
     * includes this class.
     *
     * <p>Synthetic image: 8×8 grid, solid grey (#AAAAAA) background, 2×2 red blob at centre.
     */
    public static void selfCheck() {
        int BG   = 0xFFAAAAAA;
        int BLOB = 0xFFFF0000;
        int[][] img = new int[8][8];
        for (int[] row : img) java.util.Arrays.fill(row, BG);
        // Plant a 2×2 red blob at (3,3)
        img[3][3] = img[3][4] = img[4][3] = img[4][4] = BLOB;

        maskItemBackground(img);

        // Border pixels should be transparent
        assert (img[0][0] >>> 24) == 0 : "corner should be transparent";
        assert (img[7][7] >>> 24) == 0 : "corner should be transparent";
        assert (img[0][4] >>> 24) == 0 : "top-edge should be transparent";

        // Interior blob should be fully opaque and unchanged
        assert img[3][3] == BLOB : "blob pixel [3][3] should be untouched";
        assert img[4][4] == BLOB : "blob pixel [4][4] should be untouched";

        System.out.println("PixelTexture.selfCheck() passed.");
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
