package dev.conjure.ai.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;
import dev.conjure.gen.PixelTexture;

/**
 * Sub-agent responsible for producing a 16×16 pixel-art texture from a natural-language prompt.
 *
 * <p>The model is asked to emit a JSON object describing a palette and a 16-row pixel grid; this
 * class decodes the response into an ARGB int[][] that can be fed to {@link PixelTexture#writePng}.
 */
public final class TextureAgent {

    /** Edge length of the generated icon; the grid is normalized to exactly this many rows/cols. */
    private static final int SIZE = 16;

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
     * Calls the text model to generate pixel-art data for {@code prompt} and returns the decoded
     * ARGB pixel grid.
     *
     * @param prompt the item description (e.g. "a glowing emerald sword")
     * @return 16×16 ARGB int array, indexed [y][x]
     * @throws Exception if the model call fails or the JSON cannot be parsed/repaired
     */
    public int[][] generate(String prompt) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String raw = provider.complete(SYSTEM, "Design a pixel-art Minecraft item icon for: " + prompt);
        JsonObject obj = JsonHelper.extractAndParse(raw, SYSTEM, provider,
                "Design a pixel-art Minecraft item icon for: " + prompt);
        return decode(obj);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Decodes the model's palette/rows into a strict {@code SIZE}×{@code SIZE} ARGB grid.
     * Local models routinely ignore the "exactly 16×16" instruction and emit jagged rows or the
     * wrong count, so we normalize defensively: missing/short rows and cells default to transparent,
     * and any excess rows/columns are dropped. This guarantees a clean rectangle for
     * {@link PixelTexture#writePng} regardless of what the model returned.
     */
    private static int[][] decode(JsonObject obj) {
        JsonObject palette = obj.getAsJsonObject("palette");
        JsonArray rows = obj.getAsJsonArray("rows");

        int[][] argb = new int[SIZE][SIZE]; // initialized to 0 == transparent
        if (rows == null) return argb;

        for (int y = 0; y < SIZE && y < rows.size(); y++) {
            if (rows.get(y).isJsonNull()) continue;
            String row = rows.get(y).getAsString();
            for (int x = 0; x < SIZE && x < row.length(); x++) {
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
