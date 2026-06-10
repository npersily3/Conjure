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

    private static int[][] decode(JsonObject obj) {
        JsonObject palette = obj.getAsJsonObject("palette");
        JsonArray rows = obj.getAsJsonArray("rows");

        int height = rows.size();
        int[][] argb = new int[height][];
        for (int y = 0; y < height; y++) {
            String row = rows.get(y).getAsString();
            argb[y] = new int[row.length()];
            for (int x = 0; x < row.length(); x++) {
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
