package dev.conjure.ai.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-agent that decides how a generated block or building material is <em>obtained</em> in
 * survival. For plain/scripted/stateful blocks it picks the recipe type AND ingredients via
 * {@link #chooseRecipe(String)}, returning a {@link ObtainabilityResult}. For material families
 * the legacy {@link #generate(String)} path (stonecutting source) is still available.
 *
 * <h2>Supported recipe types</h2>
 * <ul>
 *   <li>{@code SHAPELESS}    — crafting table, no shape, 1-9 vanilla ingredients.</li>
 *   <li>{@code SHAPED}       — crafting table, single row pattern, one vanilla ingredient.</li>
 *   <li>{@code SMELTING}     — furnace, one vanilla input.</li>
 *   <li>{@code BLASTING}     — blast furnace, one vanilla input (ores/metals/stone).</li>
 *   <li>{@code SMITHING}     — smithing table: base + addition + template.</li>
 *   <li>{@code STONECUTTING} — stonecutter, one vanilla stone-family input.</li>
 * </ul>
 *
 * <p>The model is lightly biased toward routing obtainability through conjured machine/workbench
 * blocks when thematically appropriate, but all types remain valid picks. Validation always
 * produces a usable fallback.
 */
public final class RecipeAgent {

    // -------------------------------------------------------------------------
    // Varied obtainability — new primary path for plain/scripted/stateful blocks
    // -------------------------------------------------------------------------

    /** All recipe types the pipeline can dispatch. */
    public enum RecipeType {
        SHAPELESS, SHAPED, SMELTING, BLASTING, SMITHING, STONECUTTING
    }

    /**
     * Structured result of {@link #chooseRecipe(String)}.
     *
     * @param type         the chosen recipe type
     * @param ingredients  vanilla (or conjure:) item ids used as inputs:
     *                     shapeless  → 1-9 ids;
     *                     shaped     → exactly 1 id (pattern is a 3-wide single row);
     *                     smelting   → exactly 1 id;
     *                     blasting   → exactly 1 id;
     *                     smithing   → exactly 3 ids: [base, addition, template];
     *                     stonecutting → exactly 1 id.
     * @param outputCount  how many outputs the recipe produces (≥ 1)
     */
    public record ObtainabilityResult(RecipeType type, List<String> ingredients, int outputCount) {}

    private static final String OBTAIN_SYSTEM = """
            You decide how a custom Minecraft block is obtained in survival mode.
            Choose the MOST THEMATICALLY FITTING recipe type from this list:
              shapeless    — craft at a crafting table, no fixed shape, 1-9 vanilla ingredients
              shaped       — craft at a crafting table with a pattern (single row of 3 identical items)
              smelting     — smelt a vanilla block/item in a furnace
              blasting     — smelt faster in a blast furnace (best for ores, metals, stone-type blocks)
              smithing     — combine base + addition + smithing_template at the smithing table
              stonecutting — cut a stone-type vanilla block in a stonecutter (always 1 output)

            BIAS: if this block sounds like it belongs in a specialized conjured workshop or arcane
            machine, pick "shapeless" and include at least one conjured machine block in the
            ingredients list (use the id "conjure:block_slot_440" as a stand-in for any machine
            block). Do this ONLY if it fits the block's theme naturally. Most blocks use vanilla
            ingredients only.

            INGREDIENT RULES:
            - Only use "minecraft:<item>" ids that exist in vanilla 1.21.1.
            - For shapeless: list 1-9 ids (repeat an id to require multiple copies of it).
            - For shaped: list exactly 1 ingredient id; a row of 3 will be generated automatically.
            - For smelting / blasting: list exactly 1 ingredient id.
            - For smithing: list exactly 3 ids: [base_item, addition_item, smithing_template_item].
              Use "minecraft:netherite_upgrade_smithing_template" as template unless another fits.
            - For stonecutting: list exactly 1 stone-family vanilla block id.

            Reply with ONLY a JSON object, no prose, no fences:
            { "type": "<one of the six types above>", "ingredients": ["minecraft:...", ...], "output_count": <integer 1-4> }
            """;

    /**
     * Asks the model to choose a recipe type + ingredients for the block described by
     * {@code prompt}. Always returns a valid, usable result; falls back to shapeless+stone on any
     * model/parse failure.
     */
    public ObtainabilityResult chooseRecipe(String prompt) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String userMsg = "Choose the survival obtainability recipe for this Minecraft block: " + prompt;
        String raw = provider.complete(OBTAIN_SYSTEM, userMsg);
        JsonObject obj = JsonHelper.extractAndParse(raw, OBTAIN_SYSTEM, provider, userMsg);

        RecipeType type = parseType(obj);
        List<String> ingredients = parseIngredients(obj, type);
        int outputCount;
        try {
            outputCount = Math.max(1, Math.min(4,
                    obj.has("output_count") ? obj.get("output_count").getAsInt() : 1));
        } catch (Exception e) {
            outputCount = 1;
        }
        return new ObtainabilityResult(type, ingredients, outputCount);
    }

    /** Parses the "type" field; falls back to SHAPELESS on any bad value. */
    private static RecipeType parseType(JsonObject obj) {
        if (!obj.has("type")) return RecipeType.SHAPELESS;
        try {
            return RecipeType.valueOf(obj.get("type").getAsString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return RecipeType.SHAPELESS;
        }
    }

    /**
     * Parses and validates the ingredients list for the given type. Ensures minimum/maximum count
     * per type and that every id is a valid {@code minecraft:…} or {@code conjure:…} id. Always
     * returns at least one element.
     */
    private static List<String> parseIngredients(JsonObject obj, RecipeType type) {
        List<String> raw = new ArrayList<>();
        if (obj.has("ingredients") && obj.get("ingredients").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("ingredients");
            for (int i = 0; i < arr.size() && raw.size() < 9; i++) {
                try {
                    String id = arr.get(i).getAsString().trim();
                    // Accept minecraft: or conjure: ids; drop anything else.
                    if ((id.startsWith("minecraft:") || id.startsWith("conjure:"))
                            && id.length() > id.indexOf(':') + 1) {
                        raw.add(id);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Enforce per-type cardinality:
        return switch (type) {
            case SHAPELESS -> raw.isEmpty() ? List.of("minecraft:stone") : raw;
            case SHAPED, SMELTING, BLASTING, STONECUTTING -> {
                // Exactly one ingredient; must be vanilla (conjure ids don't belong in vanilla
                // furnaces/stonecutters — sanitise back to stone if the model snuck one in).
                String first = raw.isEmpty() ? "minecraft:stone" : raw.get(0);
                if (!first.startsWith("minecraft:")) first = "minecraft:stone";
                yield List.of(first);
            }
            case SMITHING -> {
                // Need exactly 3: base, addition, template. Pad with stone then netherite upgrade.
                List<String> padded = new ArrayList<>();
                for (String id : raw) {
                    if (padded.size() >= 3) break;
                    padded.add(id);
                }
                if (padded.isEmpty()) padded.add("minecraft:stone");
                if (padded.size() < 2) padded.add("minecraft:netherite_ingot");
                if (padded.size() < 3) padded.add("minecraft:netherite_upgrade_smithing_template");
                yield List.copyOf(padded.subList(0, 3));
            }
        };
    }

    // -------------------------------------------------------------------------
    // Legacy path — stonecutting source for material families (unchanged)
    // -------------------------------------------------------------------------

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

    /**
     * Legacy method: maps the building material to the closest vanilla stonecutting source. Still
     * used by the material family path in BlockPipeline.
     */
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

    // -------------------------------------------------------------------------
    // Shapeless crafting — kept for ItemPipeline compatibility
    // -------------------------------------------------------------------------

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
