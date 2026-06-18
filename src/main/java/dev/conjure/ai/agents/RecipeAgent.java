package dev.conjure.ai.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-agent that decides how a generated building material is <em>obtained</em> in survival: it
 * maps the material to the closest common vanilla block, so the pipeline can emit a stonecutting
 * recipe ({@code vanilla → base}). This is the only AI part of recipe generation — the family
 * recipes (slab/stairs/wall/smooth) are deterministic (see
 * {@link dev.conjure.gen.RecipeTemplates}).
 *
 * <p>Contract is intentionally tiny (one field) so it's hard to get wrong; the result is always a
 * usable vanilla id, defaulting to {@code minecraft:stone}.
 */
public final class RecipeAgent {

    private static final String SYSTEM = """
            You map a custom Minecraft building material to the single closest VANILLA block a player
            could stonecut it from in survival. Reply with ONLY a JSON object, no prose, no fences:
            { "source": "minecraft:<block>" }
            Pick a common, always-craftable/minable vanilla block (e.g. minecraft:stone,
            minecraft:deepslate, minecraft:sandstone, minecraft:blackstone). If unsure, use
            minecraft:stone.
            """;

    /**
     * @param sourceBlock vanilla item id the base material is stonecut from (always {@code minecraft:…})
     */
    public record Result(String sourceBlock) {}

    public Result generate(String prompt) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String userMsg = "Closest vanilla source block for this material: " + prompt;
        String raw = provider.complete(SYSTEM, userMsg);
        JsonObject obj = JsonHelper.extractAndParse(raw, SYSTEM, provider, userMsg);

        String source = obj.has("source") ? obj.get("source").getAsString().trim() : "";
        // Guard: only accept a vanilla namespaced id; otherwise fall back to stone.
        if (!source.startsWith("minecraft:") || source.length() <= "minecraft:".length()) {
            source = "minecraft:stone";
        }
        return new Result(source);
    }

    private static final String CRAFT_SYSTEM = """
            You design a simple survival CRAFTING recipe for a custom Minecraft block, using ONLY
            common vanilla ingredients. Reply with ONLY a JSON object, no prose, no fences:
            { "ingredients": ["minecraft:<item>", ... up to 9] }
            List 1-9 vanilla item ids that thematically match the block (repeat an id to require
            multiples — these go into a shapeless recipe). Use only minecraft: items that exist.
            """;

    /**
     * Picks vanilla ingredients for a shapeless crafting recipe that yields the block. Used for any
     * block (material or not) so generated content is obtainable in survival. Always returns at
     * least one valid {@code minecraft:} id (defaults to a single {@code minecraft:stone}); invalid
     * or non-vanilla entries are dropped and the list is capped at 9.
     */
    public List<String> craftIngredients(String prompt) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String userMsg = "Vanilla crafting ingredients for this block: " + prompt;
        String raw = provider.complete(CRAFT_SYSTEM, userMsg);
        JsonObject obj = JsonHelper.extractAndParse(raw, CRAFT_SYSTEM, provider, userMsg);

        List<String> out = new ArrayList<>();
        if (obj.has("ingredients") && obj.get("ingredients").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("ingredients");
            for (int i = 0; i < arr.size() && out.size() < 9; i++) {
                String id = arr.get(i).getAsString().trim();
                if (id.startsWith("minecraft:") && id.length() > "minecraft:".length()) {
                    out.add(id);
                }
            }
        }
        if (out.isEmpty()) out.add("minecraft:stone"); // guarantee a usable recipe
        return out;
    }

    public RecipeAgent() {}
}
