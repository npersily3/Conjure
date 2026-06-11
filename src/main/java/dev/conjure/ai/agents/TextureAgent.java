package dev.conjure.ai.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.conjure.Conjure;
import dev.conjure.Config;
import dev.conjure.ai.ImageModelProvider;
import dev.conjure.ai.ImageQuality;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;
import dev.conjure.gen.PixelTexture;

/**
 * Sub-agent responsible for producing a pixel-art texture from a natural-language prompt.
 *
 * <p>Primary path: if a local image backend (A1111) is configured, delegate to
 * {@link ProviderFactory#image()} and decode the returned PNG via {@link PixelTexture#fromPng}.
 *
 * <p>Fallback path: the text model is asked to emit a JSON palette + 16-row grid; this class
 * decodes the response into an ARGB {@code int[][]} that can be fed to {@link PixelTexture#writePng}.
 * The fallback is always used if the image provider is {@code null} or throws.
 */
public final class TextureAgent {

    /** Edge length used by the LLM pixel-art fallback path. */
    private static final int FALLBACK_SIZE = 16;

    private static final String SYSTEM = """
            You design Minecraft item icons as 16x16 pixel art and respond with ONLY a JSON object,
            no prose, no markdown fences. Schema:
            {
              "palette": { "0": "#00000000", "1": "#RRGGBB", "2": "#RRGGBBAA", ... },
              "rows": ["................", ... exactly 16 strings of exactly 16 chars each]
            }
            Each character in a row is a key into the palette. Use "0" mapped to "#00000000" for
            transparent background pixels. Keep a clear, recognisable silhouette. Use at most 8
            palette entries. Exactly 16 rows. Every row exactly 16 chars.
            """;

    /**
     * Generates a pixel-art texture for {@code prompt}.
     *
     * <p>First tries the configured image provider (A1111). If no provider is available or
     * it fails for any reason, falls back to the LLM pixel-art strategy. Never throws — on
     * total failure returns a transparent grid rather than breaking generation.
     *
     * @param prompt the item description (e.g. "a glowing emerald sword")
     * @return ARGB pixel grid, indexed [y][x]; size varies by quality tier (image path) or is
     *         always 16×16 (fallback path)
     */
    public int[][] generate(String prompt) throws Exception {
        // Determine target size from config
        ImageQuality quality = Config.IMAGE_QUALITY.get();
        int targetSize = (quality == ImageQuality.HIGH)
                ? Config.IMAGE_HIGH_SIZE.get()
                : Config.IMAGE_FAST_SIZE.get();

        // Primary path: local image backend
        try {
            ImageModelProvider imageProvider = ProviderFactory.image();
            if (imageProvider != null) {
                byte[] pngBytes = imageProvider.generateTexture(prompt, targetSize);
                return PixelTexture.fromPng(pngBytes, targetSize);
            }
        } catch (Exception e) {
            Conjure.LOGGER.warn(
                    "[TextureAgent] Image provider failed for '{}', falling back to LLM pixel-art: {}",
                    prompt, e.getMessage());
        }

        // Fallback path: ask the text model to emit a pixel-art JSON grid
        return llmFallback(prompt);
    }

    // -------------------------------------------------------------------------
    // LLM pixel-art fallback
    // -------------------------------------------------------------------------

    /**
     * Original LLM-based pixel-art path. Calls the text model and decodes the JSON
     * palette+rows response into a 16×16 ARGB grid.
     */
    private static int[][] llmFallback(String prompt) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String raw = provider.complete(SYSTEM, "Design a pixel-art Minecraft item icon for: " + prompt);
        JsonObject obj = JsonHelper.extractAndParse(raw, SYSTEM, provider,
                "Design a pixel-art Minecraft item icon for: " + prompt);
        return decode(obj);
    }

    /**
     * Decodes the model's palette/rows into a strict {@code FALLBACK_SIZE}×{@code FALLBACK_SIZE}
     * ARGB grid. Local models routinely ignore the "exactly 16×16" instruction and emit jagged rows
     * or the wrong count, so we normalize defensively: missing/short rows and cells default to
     * transparent, and any excess rows/columns are dropped. This guarantees a clean rectangle for
     * {@link PixelTexture#writePng} regardless of what the model returned.
     */
    private static int[][] decode(JsonObject obj) {
        JsonObject palette = obj.getAsJsonObject("palette");
        JsonArray rows = obj.getAsJsonArray("rows");

        int[][] argb = new int[FALLBACK_SIZE][FALLBACK_SIZE]; // initialized to 0 == transparent
        if (rows == null) return argb;

        for (int y = 0; y < FALLBACK_SIZE && y < rows.size(); y++) {
            if (rows.get(y).isJsonNull()) continue;
            String row = rows.get(y).getAsString();
            for (int x = 0; x < FALLBACK_SIZE && x < row.length(); x++) {
                String key = String.valueOf(row.charAt(x));
                String hex = (palette != null && palette.has(key))
                        ? palette.get(key).getAsString()
                        : "#00000000";
                argb[y][x] = PixelTexture.parseColor(hex);
            }
        }
        return argb;
    }

    public TextureAgent() {}
}
