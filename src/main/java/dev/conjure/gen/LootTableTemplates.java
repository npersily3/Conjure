package dev.conjure.gen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

/**
 * Writes block loot tables under {@code data/conjure/loot_table/blocks/<id>.json} via
 * {@link DynamicPackManager#write}. Each Conjure block must have a loot table so it drops
 * something when broken in survival; without one, breaking the block yields nothing.
 *
 * <h2>JSON schema (verified against vanilla 1.21.1)</h2>
 * <pre>
 * {
 *   "type": "minecraft:block",
 *   "pools": [{
 *     "rolls": 1.0,
 *     "bonus_rolls": 0.0,
 *     "conditions": [{ "condition": "minecraft:survives_explosion" }],
 *     "entries": [{
 *       "type": "minecraft:item",
 *       "name": "<item id>"
 *     }]
 *   }],
 *   "random_sequence": "conjure:blocks/<id>"
 * }
 * </pre>
 * The {@code survives_explosion} condition matches vanilla's simple block loot tables (planks,
 * sand, etc. from {@code neoforge-21.1.93-client-extra-aka-minecraft-resources.jar}). Ore-style
 * silk-touch alternatives are not generated here — drops are always self-drops.
 *
 * <p>The {@code *Json} builders are pure (no IO) for testability; {@link #main} runs a
 * self-check on the generated JSON shapes.
 */
public final class LootTableTemplates {

    private static final String DIR = "data/conjure/loot_table/blocks/";

    private LootTableTemplates() {}

    /**
     * Writes a self-drop loot table for a block slot: breaking {@code blockId} drops
     * {@code blockId} itself (with explosion survivability). This is the default for all generated
     * blocks — the block drops itself as an item, just like vanilla planks or stone bricks.
     *
     * @param slotId   the slot name, e.g. {@code block_slot_5} (used as the file name and
     *                 {@code random_sequence} path suffix)
     * @param blockId  the fully-qualified item id of the block, e.g. {@code conjure:block_slot_5}
     */
    public static void writeSelfDrop(String slotId, String blockId) throws IOException {
        write(slotId, blockDropJson(blockId, blockId));
    }

    /**
     * Writes a themed alternate drop loot table: breaking the block drops {@code dropId} instead
     * of the block itself. Useful when the AI assigns a thematically different drop (e.g. breaking
     * a "salt crystal block" could drop "minecraft:sugar" as a stand-in).
     *
     * @param slotId the slot name (file name and sequence suffix)
     * @param dropId fully-qualified item id of what is dropped, e.g. {@code minecraft:amethyst_shard}
     */
    public static void writeAlternateDrop(String slotId, String dropId) throws IOException {
        write(slotId, blockDropJson("conjure:" + slotId, dropId));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void write(String slotId, String json) throws IOException {
        DynamicPackManager.write(DIR + slotId + ".json", json);
    }

    /**
     * Pure JSON builder for a simple block loot table that drops {@code dropItemId} when broken
     * (with explosion survivability, matching vanilla conventions for simple blocks like planks).
     *
     * @param blockId    the block being broken (used as {@code random_sequence} path)
     * @param dropItemId the item that drops
     */
    static String blockDropJson(String blockId, String dropItemId) {
        // Extract the path portion for random_sequence (e.g. "conjure:block_slot_5" → "block_slot_5")
        int colon = blockId.indexOf(':');
        String seqPath = colon >= 0 ? blockId.substring(colon + 1) : blockId;
        return "{"
                + "\"type\":\"minecraft:block\","
                + "\"pools\":[{"
                + "\"rolls\":1.0,"
                + "\"bonus_rolls\":0.0,"
                + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}],"
                + "\"entries\":[{"
                + "\"type\":\"minecraft:item\","
                + "\"name\":\"" + dropItemId + "\""
                + "}]"
                + "}],"
                + "\"random_sequence\":\"conjure:blocks/" + seqPath + "\""
                + "}";
    }

    /**
     * Self-check: both loot table shapes must parse as valid JSON with the expected type and entry.
     * Run with {@code -ea} to enable assertions.
     *
     * <p>ponytail: assert that blockDropJson produces a well-formed 1.21.1 loot table.
     */
    public static void main(String[] args) {
        // ponytail: self-drop table must parse and match 1.21.1 loot table schema
        JsonObject selfDrop = JsonParser.parseString(
                blockDropJson("conjure:block_slot_5", "conjure:block_slot_5")).getAsJsonObject();
        assert "minecraft:block".equals(selfDrop.get("type").getAsString())
                : "type must be minecraft:block";
        assert selfDrop.has("pools") : "must have pools";
        assert selfDrop.getAsJsonArray("pools").size() == 1 : "must have exactly one pool";
        JsonObject pool = selfDrop.getAsJsonArray("pools").get(0).getAsJsonObject();
        assert pool.get("rolls").getAsDouble() == 1.0 : "rolls must be 1.0";
        assert pool.getAsJsonArray("conditions").size() == 1 : "must have survives_explosion";
        assert "minecraft:survives_explosion".equals(
                pool.getAsJsonArray("conditions").get(0).getAsJsonObject()
                        .get("condition").getAsString());
        assert pool.getAsJsonArray("entries").size() == 1 : "must have one entry";
        JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
        assert "minecraft:item".equals(entry.get("type").getAsString()) : "entry type must be minecraft:item";
        assert "conjure:block_slot_5".equals(entry.get("name").getAsString());
        assert "conjure:blocks/block_slot_5".equals(selfDrop.get("random_sequence").getAsString());

        // ponytail: alternate-drop table must use the specified drop id
        JsonObject altDrop = JsonParser.parseString(
                blockDropJson("conjure:block_slot_7", "minecraft:amethyst_shard")).getAsJsonObject();
        assert "minecraft:block".equals(altDrop.get("type").getAsString());
        JsonObject altEntry = altDrop.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();
        assert "minecraft:amethyst_shard".equals(altEntry.get("name").getAsString());

        System.out.println("LootTableTemplates self-check OK (run with -ea)");
    }
}
