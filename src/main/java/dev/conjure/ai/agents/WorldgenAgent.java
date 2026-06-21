package dev.conjure.ai.agents;

import com.google.gson.JsonObject;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;

/**
 * Sub-agent that decides world-generation parameters for a conjured ore/block.
 *
 * <p>Given the prompt and the target conjured block's registry id it produces:
 * <ul>
 *   <li>{@link Result#veinSize()} — blocks per vein (clamped 1–64)</li>
 *   <li>{@link Result#veinsPerChunk()} — average veins per chunk (clamped 1–16)</li>
 *   <li>{@link Result#minY()} / {@link Result#maxY()} — Y-range (min ≥ -64, max ≤ 256)</li>
 *   <li>{@link Result#biomeTag()} — a biome tag or id to add the feature to
 *       (e.g. {@code "#minecraft:is_overworld"})</li>
 *   <li>{@link Result#worldgenIntent()} — human-readable summary for the tooltip overlay,
 *       e.g. "ore veins of 4-8 in stone, Y -32..48, in plains/forest biomes"</li>
 * </ul>
 *
 * <p>On any model/parse failure the agent returns sensible defaults so generation is never blocked.
 */
public final class WorldgenAgent {

    private static final String SYSTEM = """
            You are designing world-generation parameters for a new Minecraft ore or block.
            Reply with ONLY a JSON object — no prose, no markdown fences. Schema:
            {
              "veinSize":       <int 1-64, blocks per ore vein>,
              "veinsPerChunk":  <int 1-16, average veins spawned per chunk>,
              "minY":           <int -64 to 255, minimum Y level>,
              "maxY":           <int minY+1 to 256, maximum Y level>,
              "biomeTag":       "<biome id or tag — use '#minecraft:is_overworld' for overworld,
                                  '#minecraft:is_nether' for nether, or a specific tag/id>",
              "worldgenIntent": "<one short sentence summarising spawn behaviour for the tooltip,
                                  e.g. 'rare veins of 3-5 deep underground in all overworld biomes'>"
            }
            Guidelines:
            - Common ores (iron, coal): veinSize 6-12, veinsPerChunk 8-14, Y -30..60, overworld
            - Rare/deep ores (gold, diamond-like): veinSize 3-8, veinsPerChunk 1-5, Y -64..0, overworld
            - Magical/surface ores: veinSize 2-6, veinsPerChunk 2-8, Y 0..80, forest/plains biomes
            - Nether ores: veinSize 4-10, veinsPerChunk 3-8, nether biomes
            - Make worldgenIntent a SHORT summary (≤ 20 words) mentioning vein size, Y range, and biomes.
            """;

    /**
     * Ore/block world-generation parameters.
     *
     * @param veinSize       blocks per ore vein
     * @param veinsPerChunk  average veins per chunk
     * @param minY           minimum Y generation level (inclusive)
     * @param maxY           maximum Y generation level (inclusive)
     * @param biomeTag       biome tag or id for the biome modifier
     * @param worldgenIntent human-readable summary for the tooltip overlay
     */
    public record Result(int veinSize, int veinsPerChunk, int minY, int maxY,
                         String biomeTag, String worldgenIntent) {}

    /**
     * Calls the text model to determine worldgen parameters for {@code prompt}.
     * Falls back to a common-ore configuration on any error so the pipeline always completes.
     *
     * @param prompt         the original user-supplied description
     * @param targetBlockId  the block's registry id (e.g. {@code "conjure:block_slot_3"})
     * @return a {@link Result} with all worldgen parameters
     */
    public Result generate(String prompt, String targetBlockId) {
        try {
            TextModelProvider provider = ProviderFactory.text();
            String userMsg = "Design world-generation parameters for this Minecraft block: "
                    + prompt + " (block id: " + targetBlockId + ")";
            String raw = provider.complete(SYSTEM, userMsg);
            JsonObject obj = JsonHelper.extractAndParse(raw, SYSTEM, provider, userMsg);

            int veinSize       = clamp(getInt(obj, "veinSize",       8),   1,  64);
            int veinsPerChunk  = clamp(getInt(obj, "veinsPerChunk",  8),   1,  16);
            int minY           = clamp(getInt(obj, "minY",          -32), -64, 255);
            int maxY           = clamp(getInt(obj, "maxY",           48), minY + 1, 256);
            String biomeTag    = getString(obj, "biomeTag", "#minecraft:is_overworld");
            String intent      = getString(obj, "worldgenIntent",
                    "ore veins of ~" + veinSize + " in stone, Y " + minY + ".." + maxY);

            return new Result(veinSize, veinsPerChunk, minY, maxY, biomeTag, intent);

        } catch (Exception e) {
            // Fallback: sensible common-ore defaults — never let a model failure block generation.
            return new Result(
                    8,                         // veinSize
                    8,                         // veinsPerChunk
                    -32,                       // minY
                    48,                        // maxY
                    "#minecraft:is_overworld", // biomeTag
                    "ore veins of ~8 in stone, Y -32..48, in all overworld biomes"
            );
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try { return obj.get(key).getAsInt(); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static String getString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            String v = obj.get(key).getAsString().trim();
            if (!v.isBlank()) return v;
        }
        return fallback;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public WorldgenAgent() {}
}
