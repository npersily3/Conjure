package dev.conjure.ai.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.conjure.Conjure;
import dev.conjure.Config;
import dev.conjure.ai.ImageModelProvider;
import dev.conjure.ai.ImageQuality;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;
import dev.conjure.ai.TextureKind;
import dev.conjure.gen.PixelTexture;

/**
 * Sub-agent responsible for producing a pixel-art texture from a natural-language prompt.
 *
 * <p>Primary path: if a local image backend (ComfyUI) is configured, delegate to
 * {@link ProviderFactory#image()} and decode the returned PNG via
 * {@link PixelTexture#fromPng(byte[], int, TextureKind)} which applies kind-specific
 * post-processing (block tiling + quantize, item background masking).
 *
 * <p>Fallback path: the text model is asked to emit a JSON palette + 16-row grid; this class
 * decodes the response into an ARGB {@code int[][]} that can be fed to {@link PixelTexture#writePng}.
 * The fallback is always used if the image provider is {@code null} or throws.
 *
 * <h2>Overloads</h2>
 * <ul>
 *   <li>{@link #generate(String, TextureKind)} — basic; prompt is used as-is.</li>
 *   <li>{@link #generate(String, String, TextureKind)} — enriched; a {@code visualIntent} string
 *       is prepended to the prompt for both the image backend and the LLM fallback. This lets the
 *       block pipeline supply additional visual hints (material, color, style) without the callers
 *       having to manually concatenate strings.</li>
 * </ul>
 */
public final class TextureAgent {

    /** Edge length used by the LLM pixel-art fallback path. */
    private static final int FALLBACK_SIZE = 16;

    private static final String SCHEMA = """
            Respond with ONLY a JSON object, no prose, no markdown fences. Schema:
            {
              "palette": { "0": "#00000000", "1": "#RRGGBB", "2": "#RRGGBBAA", ... },
              "rows": ["................", ... exactly 16 strings of exactly 16 chars each]
            }
            Each character in a row is a key into the palette. Use at most 8 palette entries.
            Exactly 16 rows. Every row exactly 16 chars.
            """;

    private static final String ITEM_SYSTEM =
            "You design Minecraft item icons as 16x16 pixel art. Use \"0\" mapped to \"#00000000\" "
            + "for transparent background pixels around a clear, recognisable centered silhouette.\n"
            + SCHEMA;

    /** Blocks are a full opaque surface, NOT a centered icon — fill every pixel, no transparency. */
    private static final String BLOCK_SYSTEM =
            "You design Minecraft block surface textures as 16x16 pixel art: a full, opaque surface "
            + "(like stone, planks, or ore) that fills the WHOLE 16x16 grid edge to edge and tiles "
            + "seamlessly when repeated. Do NOT leave a transparent background and do NOT draw a "
            + "centered object — every one of the 256 pixels is part of the surface and must be "
            + "opaque (no \"#00000000\").\n"
            + SCHEMA;

    /** Fluids are a tileable, seamless, top-down liquid surface — never a centered icon. */
    private static final String FLUID_SYSTEM =
            "You design Minecraft fluid surface textures as 16x16 pixel art: a tileable, seamless, "
            + "top-down liquid surface that fills the WHOLE 16x16 grid edge to edge with no gaps. "
            + "Use subtle ripple or wave patterns. Do NOT draw a centered icon, do NOT leave any "
            + "transparent pixels, do NOT draw a border or background — every pixel is part of the "
            + "liquid surface and must be fully opaque (no \"#00000000\").\n"
            + SCHEMA;

    private static String systemFor(TextureKind kind) {
        return switch (kind) {
            case BLOCK -> BLOCK_SYSTEM;
            case FLUID -> FLUID_SYSTEM;
            default    -> ITEM_SYSTEM;
        };
    }

    // -------------------------------------------------------------------------
    // Public generate overloads
    // -------------------------------------------------------------------------

    /**
     * Generates a pixel-art texture for {@code prompt}.
     *
     * <p>First tries the configured image provider (ComfyUI). If no provider is available or
     * it fails for any reason, falls back to the LLM pixel-art strategy. Never throws — on
     * total failure returns a transparent grid rather than breaking generation.
     *
     * @param prompt the item/block description (e.g. "a glowing emerald sword")
     * @param kind   what kind of texture to produce; steers prompts and post-processing
     * @return ARGB pixel grid, indexed [y][x]; 16×16 on the fallback path; size depends on config
     *         and kind on the image-backend path (BLOCK is always 16×16 after post-processing)
     */
    public int[][] generate(String prompt, TextureKind kind) throws Exception {
        return generate(prompt, null, kind);
    }

    /**
     * Generates a pixel-art texture with an optional {@code visualIntent} enrichment.
     *
     * <p>When {@code visualIntent} is non-null and non-blank it is prepended to {@code prompt}
     * before the image backend (or LLM fallback) is called. This is the recommended call site for
     * the block pipeline, which can supply a visual hint such as "dark grey stone with orange veins"
     * to steer the texture without changing the block's display name.
     *
     * <p>This overload is the contract that other lanes/pipelines must call when they have visual
     * enrichment data; the 2-arg overload delegates here with {@code visualIntent = null}.
     *
     * @param prompt       the item/block description
     * @param visualIntent optional visual-style hint (may be null or blank)
     * @param kind         what kind of texture to produce
     * @return ARGB pixel grid
     */
    public int[][] generate(String prompt, String visualIntent, TextureKind kind) throws Exception {
        String enriched = (visualIntent != null && !visualIntent.isBlank())
                ? visualIntent + ", " + prompt
                : prompt;

        // Determine target size from config
        ImageQuality quality = Config.IMAGE_QUALITY.get();
        int targetSize = (quality == ImageQuality.HIGH)
                ? Config.IMAGE_HIGH_SIZE.get()
                : Config.IMAGE_FAST_SIZE.get();

        // Primary path: local image backend
        try {
            ImageModelProvider imageProvider = ProviderFactory.image();
            if (imageProvider != null) {
                byte[] pngBytes = imageProvider.generateTexture(enriched, targetSize, kind);
                // Use kind-aware fromPng so blocks get tiling+quantize, items get BG masking
                return PixelTexture.fromPng(pngBytes, targetSize, kind);
            }
        } catch (Exception e) {
            Conjure.LOGGER.warn(
                    "[TextureAgent] Image provider failed for '{}', falling back to LLM pixel-art: {}",
                    enriched, e.getMessage());
        }

        // Fallback path: ask the text model to emit a pixel-art JSON grid
        return llmFallback(enriched, kind);
    }

    // -------------------------------------------------------------------------
    // LLM pixel-art fallback
    // -------------------------------------------------------------------------

    /**
     * Original LLM-based pixel-art path. Calls the text model and decodes the JSON
     * palette+rows response into a 16×16 ARGB grid.
     */
    private static int[][] llmFallback(String prompt, TextureKind kind) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String system = systemFor(kind);
        String user = switch (kind) {
            case BLOCK -> "Design a tileable 16x16 Minecraft block surface texture for: " + prompt;
            case FLUID -> "Design a tileable 16x16 Minecraft fluid surface texture for: " + prompt;
            default    -> "Design a pixel-art Minecraft item icon for: " + prompt;
        };
        String raw = provider.complete(system, user);
        JsonObject obj = JsonHelper.extractAndParse(raw, system, provider, user);
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
