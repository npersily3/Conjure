package dev.conjure.gen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.List;

/**
 * Deterministic vanilla-pattern recipe JSON for a generated material family. The shapes (3-across
 * → 6 slabs, stairs pattern → 4, smelt → smooth, stonecutter 1→N) are fixed Minecraft conventions,
 * so they're built directly from slot ids — no AI needed. Recipes are written under
 * {@code data/conjure/recipe/<id>.json} (singular {@code recipe/} in 1.21.1) and take effect on a
 * datapack reload. JSON shapes copied from vanilla 1.21.1 recipes (result uses {@code id}; crafting
 * keys/ingredients use {@code {"item": …}}).
 *
 * <p>The {@code *Json} builders are pure (no IO) so they can be checked; see {@link #main} for a
 * runnable self-check of the recipe shapes.
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
        return "{\"type\":\"minecraft:smelting\",\"category\":\"blocks\","
                + "\"cookingtime\":200,\"experience\":0.1,"
                + "\"ingredient\":{\"item\":\"" + ingredientId + "\"},"
                + "\"result\":{\"id\":\"" + resultId + "\"}}";
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

        System.out.println("RecipeTemplates self-check OK (run with -ea)");
    }
}
