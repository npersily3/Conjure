package dev.conjure.gen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.conjure.ai.agents.RecipeAgent;

import java.io.IOException;
import java.util.List;

/**
 * Deterministic vanilla-pattern recipe JSON for a generated material family, plus builders for
 * all recipe types supported by BlockPipeline's varied obtainability path. The shapes (3-across
 * → 6 slabs, stairs pattern → 4, smelt → smooth, stonecutter 1→N) are fixed Minecraft conventions,
 * so they're built directly from slot ids — no AI needed. Recipes are written under
 * {@code data/conjure/recipe/<id>.json} (singular {@code recipe/} in 1.21.1) and take effect on a
 * datapack reload. JSON shapes copied from vanilla 1.21.1 recipes (result uses {@code id}; crafting
 * keys/ingredients use {@code {"item": …}}).
 *
 * <p>The {@code *Json} builders are pure (no IO) so they can be checked; see {@link #main} for a
 * runnable self-check of the recipe shapes.
 *
 * <h2>Additional recipe types (for BlockPipeline obtainability)</h2>
 * <ul>
 *   <li>{@link #writeShaped} — single-ingredient row-of-3 shaped crafting recipe.</li>
 *   <li>{@link #writeSmelting(String,String,String,int)} — furnace smelting (with count).</li>
 *   <li>{@link #writeBlasting} — blast furnace (faster smelting, half cookingtime).</li>
 *   <li>{@link #writeSmithing} — smithing table transform (base + addition + template).</li>
 * </ul>
 */
public final class RecipeTemplates {

    private static final String DIR = "data/conjure/recipe/";

    private RecipeTemplates() {}

    /**
     * Writes the standard building-block recipes for a material family. Any variant id may be
     * {@code null} (that variant wasn't generated) and is skipped. All variants are crafted and
     * stonecut from {@code baseId}, matching the user-facing "3 marble → 6 slabs / smelt → smooth"
     * expectation.
     *
     * @param baseId   the base cube block item id, e.g. {@code conjure:block_slot_5}
     * @param smoothId smelt result (a cube block), or null
     * @param bricksId 2×2 craft result (a cube block), or null
     * @param slabId   slab block item id, or null
     * @param stairsId stairs block item id, or null
     * @param wallId   wall block item id, or null
     */
    public static void writeFamily(String baseId, String smoothId, String bricksId,
                                   String slabId, String stairsId, String wallId) throws IOException {
        if (smoothId != null) {
            write(name(smoothId), smeltingJson(baseId, smoothId));
        }
        if (bricksId != null) {
            write(name(bricksId), shapedJson(baseId, new String[]{"##", "##"}, bricksId, 4));
            write(name(bricksId) + "_stonecutting", stonecuttingJson(baseId, bricksId, 1));
        }
        if (slabId != null) {
            write(name(slabId), shapedJson(baseId, new String[]{"###"}, slabId, 6));
            write(name(slabId) + "_stonecutting", stonecuttingJson(baseId, slabId, 2));
        }
        if (stairsId != null) {
            write(name(stairsId), shapedJson(baseId, new String[]{"#  ", "## ", "###"}, stairsId, 4));
            write(name(stairsId) + "_stonecutting", stonecuttingJson(baseId, stairsId, 1));
        }
        if (wallId != null) {
            write(name(wallId), shapedJson(baseId, new String[]{"###", "###"}, wallId, 6));
            write(name(wallId) + "_stonecutting", stonecuttingJson(baseId, wallId, 1));
        }
    }

    /** Writes a single stonecutting recipe (used for the base material's obtainability). */
    public static void writeStonecutting(String recipeId, String ingredientId, String resultId,
                                         int count) throws IOException {
        write(recipeId, stonecuttingJson(ingredientId, resultId, count));
    }

    /** Writes a shapeless crafting recipe: the given vanilla ingredients → {@code resultId}. */
    public static void writeShapeless(String recipeId, List<String> ingredientIds, String resultId,
                                      int count) throws IOException {
        write(recipeId, shapelessJson(ingredientIds, resultId, count));
    }

    /**
     * Writes a shaped crafting recipe (row of 3 identical items → {@code resultId}).
     * This is the simplest "shaped" form — a single row of 3 matching the {@code ###} pattern.
     */
    public static void writeShaped(String recipeId, String ingredientId, String resultId,
                                   int count) throws IOException {
        write(recipeId, shapedJson(ingredientId, new String[]{"###"}, resultId, count));
    }

    /**
     * Writes a furnace smelting recipe.
     *
     * @param cookingtime ticks to smelt (200 = 10 s, the vanilla default for blocks)
     * @param experience  XP awarded on completion
     */
    public static void writeSmelting(String recipeId, String ingredientId, String resultId,
                                     int cookingtime, float experience) throws IOException {
        write(recipeId, smeltingJson(ingredientId, resultId, cookingtime, experience));
    }

    /**
     * Writes a furnace smelting recipe with vanilla block defaults (200 ticks, 0.1 XP).
     * Overload kept so call-sites that only care about ingredient+result don't need to pass ticks.
     */
    public static void writeSmelting(String recipeId, String ingredientId, String resultId,
                                     int count) throws IOException {
        // count is ignored for smelting (always 1); kept for API symmetry.
        write(recipeId, smeltingJson(ingredientId, resultId, 200, 0.1f));
    }

    /**
     * Writes a blast-furnace recipe (half the cookingtime of a regular furnace, same category).
     * Verified against {@code data/minecraft/recipe/coal_from_blasting_coal_ore.json} in 1.21.1.
     */
    public static void writeBlasting(String recipeId, String ingredientId, String resultId,
                                     int count) throws IOException {
        write(recipeId, blastingJson(ingredientId, resultId, count));
    }

    /**
     * Writes a smithing-table transform recipe.
     * Verified against {@code data/minecraft/recipe/netherite_sword_smithing.json} in 1.21.1.
     * The type is {@code minecraft:smithing_transform}.
     *
     * @param baseId     the item being upgraded
     * @param additionId the item added (e.g. minecraft:netherite_ingot)
     * @param templateId the smithing template (e.g. minecraft:netherite_upgrade_smithing_template)
     */
    public static void writeSmithing(String recipeId, String baseId, String additionId,
                                     String templateId, String resultId, int count) throws IOException {
        write(recipeId, smithingJson(baseId, additionId, templateId, resultId, count));
    }

    /**
     * Dispatches a {@link RecipeAgent.ObtainabilityResult} to the matching builder above — the single
     * place both ItemPipeline and BlockPipeline share for "write whatever recipe the agent chose".
     */
    public static void writeChosen(String recipeId, String resultId,
                                   RecipeAgent.ObtainabilityResult choice) throws IOException {
        List<String> ing = choice.ingredients();
        int count = choice.outputCount();
        switch (choice.type()) {
            case SHAPELESS    -> writeShapeless(recipeId, ing, resultId, count);
            case SHAPED       -> writeShaped(recipeId, ing.get(0), resultId, count);
            case SMELTING     -> writeSmelting(recipeId, ing.get(0), resultId, count);
            case BLASTING     -> writeBlasting(recipeId, ing.get(0), resultId, count);
            case SMITHING     -> writeSmithing(recipeId, ing.get(0), ing.get(1), ing.get(2), resultId, count);
            case STONECUTTING -> writeStonecutting(recipeId, ing.get(0), resultId, count);
        }
    }

    /**
     * Mod-economy recipe: craft (or smelt) {@code resultId} from the supplied already-generated
     * {@code ingredientIds}. A single ingredient with {@code smelt} true becomes a furnace recipe
     * (ore → ingot); otherwise a shapeless craft consuming all ingredients.
     */
    public static void writeChain(String recipeId, String resultId, List<String> ingredientIds,
                                  boolean smelt) throws IOException {
        if (smelt && ingredientIds.size() == 1) {
            writeSmelting(recipeId, ingredientIds.get(0), resultId, 1);
        } else {
            writeShapeless(recipeId, ingredientIds, resultId, 1);
        }
    }

    private static void write(String recipeId, String json) throws IOException {
        DynamicPackManager.write(DIR + recipeId + ".json", json);
    }

    // ---- pure JSON builders (no IO) -----------------------------------------

    static String shapedJson(String ingredientId, String[] pattern, String resultId, int count) {
        StringBuilder pat = new StringBuilder("[");
        for (int i = 0; i < pattern.length; i++) {
            if (i > 0) pat.append(',');
            pat.append('"').append(pattern[i]).append('"');
        }
        pat.append(']');
        return "{\"type\":\"minecraft:crafting_shaped\",\"category\":\"building\","
                + "\"key\":{\"#\":{\"item\":\"" + ingredientId + "\"}},"
                + "\"pattern\":" + pat + ","
                + "\"result\":{\"count\":" + count + ",\"id\":\"" + resultId + "\"}}";
    }

    static String smeltingJson(String ingredientId, String resultId) {
        return smeltingJson(ingredientId, resultId, 200, 0.1f);
    }

    static String smeltingJson(String ingredientId, String resultId, int cookingtime, float experience) {
        return "{\"type\":\"minecraft:smelting\",\"category\":\"blocks\","
                + "\"cookingtime\":" + cookingtime + ",\"experience\":" + experience + ","
                + "\"ingredient\":{\"item\":\"" + ingredientId + "\"},"
                + "\"result\":{\"id\":\"" + resultId + "\"}}";
    }

    /**
     * Blast-furnace recipe. Cookingtime is half of smelting (100 ticks). Category "blocks" matches
     * vanilla stone-type blasting recipes. Verified against 1.21.1 blasting recipe JSON shape.
     */
    static String blastingJson(String ingredientId, String resultId, int count) {
        return "{\"type\":\"minecraft:blasting\",\"category\":\"blocks\","
                + "\"cookingtime\":100,\"experience\":0.1,"
                + "\"ingredient\":{\"item\":\"" + ingredientId + "\"},"
                + "\"result\":{\"count\":" + count + ",\"id\":\"" + resultId + "\"}}";
    }

    /**
     * Smithing-table transform recipe. Verified against {@code netherite_sword_smithing.json} in
     * 1.21.1: type {@code minecraft:smithing_transform}, fields template/base/addition/result.
     */
    static String smithingJson(String baseId, String additionId, String templateId,
                               String resultId, int count) {
        return "{\"type\":\"minecraft:smithing_transform\","
                + "\"base\":{\"item\":\"" + baseId + "\"},"
                + "\"addition\":{\"item\":\"" + additionId + "\"},"
                + "\"template\":{\"item\":\"" + templateId + "\"},"
                + "\"result\":{\"count\":" + count + ",\"id\":\"" + resultId + "\"}}";
    }

    static String shapelessJson(List<String> ingredientIds, String resultId, int count) {
        StringBuilder ing = new StringBuilder("[");
        for (int i = 0; i < ingredientIds.size(); i++) {
            if (i > 0) ing.append(',');
            ing.append("{\"item\":\"").append(ingredientIds.get(i)).append("\"}");
        }
        ing.append(']');
        return "{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\","
                + "\"ingredients\":" + ing + ","
                + "\"result\":{\"count\":" + count + ",\"id\":\"" + resultId + "\"}}";
    }

    static String stonecuttingJson(String ingredientId, String resultId, int count) {
        return "{\"type\":\"minecraft:stonecutting\","
                + "\"ingredient\":{\"item\":\"" + ingredientId + "\"},"
                + "\"result\":{\"count\":" + count + ",\"id\":\"" + resultId + "\"}}";
    }

    /** Recipe file/id base from an item id: {@code conjure:slab_slot_3} → {@code slab_slot_3}. */
    private static String name(String itemId) {
        int colon = itemId.indexOf(':');
        return colon >= 0 ? itemId.substring(colon + 1) : itemId;
    }

    /** Self-check: every builder yields valid JSON with the expected type and result id. */
    public static void main(String[] args) {
        JsonObject slab = JsonParser.parseString(
                shapedJson("conjure:block_slot_5", new String[]{"###"}, "conjure:slab_slot_0", 6)).getAsJsonObject();
        assert "minecraft:crafting_shaped".equals(slab.get("type").getAsString());
        assert slab.getAsJsonObject("result").get("count").getAsInt() == 6;
        assert "conjure:slab_slot_0".equals(slab.getAsJsonObject("result").get("id").getAsString());
        assert slab.getAsJsonArray("pattern").size() == 1;

        JsonObject smooth = JsonParser.parseString(
                smeltingJson("conjure:block_slot_5", "conjure:block_slot_6")).getAsJsonObject();
        assert "minecraft:smelting".equals(smooth.get("type").getAsString());
        assert "conjure:block_slot_6".equals(smooth.getAsJsonObject("result").get("id").getAsString());

        JsonObject cut = JsonParser.parseString(
                stonecuttingJson("conjure:block_slot_5", "conjure:slab_slot_0", 2)).getAsJsonObject();
        assert "minecraft:stonecutting".equals(cut.get("type").getAsString());
        assert cut.getAsJsonObject("result").get("count").getAsInt() == 2;

        JsonObject shapeless = JsonParser.parseString(shapelessJson(
                List.of("minecraft:amethyst_shard", "minecraft:glowstone_dust"),
                "conjure:block_slot_7", 1)).getAsJsonObject();
        assert "minecraft:crafting_shapeless".equals(shapeless.get("type").getAsString());
        assert shapeless.getAsJsonArray("ingredients").size() == 2;
        assert "conjure:block_slot_7".equals(shapeless.getAsJsonObject("result").get("id").getAsString());

        // ponytail: blasting builder must parse and match 1.21.1 schema
        JsonObject blasting = JsonParser.parseString(
                blastingJson("minecraft:iron_ore", "conjure:block_slot_8", 1)).getAsJsonObject();
        assert "minecraft:blasting".equals(blasting.get("type").getAsString());
        assert "blocks".equals(blasting.get("category").getAsString());
        assert blasting.get("cookingtime").getAsInt() == 100;
        assert "minecraft:iron_ore".equals(blasting.getAsJsonObject("ingredient").get("item").getAsString());
        assert "conjure:block_slot_8".equals(blasting.getAsJsonObject("result").get("id").getAsString());

        // ponytail: smithing builder must parse and match 1.21.1 smithing_transform schema
        JsonObject smithing = JsonParser.parseString(
                smithingJson("minecraft:diamond_pickaxe", "minecraft:netherite_ingot",
                        "minecraft:netherite_upgrade_smithing_template",
                        "conjure:block_slot_9", 1)).getAsJsonObject();
        assert "minecraft:smithing_transform".equals(smithing.get("type").getAsString());
        assert "minecraft:diamond_pickaxe".equals(smithing.getAsJsonObject("base").get("item").getAsString());
        assert "minecraft:netherite_ingot".equals(smithing.getAsJsonObject("addition").get("item").getAsString());
        assert "minecraft:netherite_upgrade_smithing_template".equals(smithing.getAsJsonObject("template").get("item").getAsString());
        assert "conjure:block_slot_9".equals(smithing.getAsJsonObject("result").get("id").getAsString());

        System.out.println("RecipeTemplates self-check OK (run with -ea)");
    }
}
