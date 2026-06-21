package dev.conjure.gen;

import java.io.IOException;

/**
 * Writes block loot tables to {@code data/conjure/loot_table/blocks/<slot>.json} so a generated
 * block drops itself when broken (without one, breaking it yields nothing). Schema matches vanilla
 * 1.21.1 simple-block tables: one pool, {@code survives_explosion} condition, a self-drop item entry.
 */
public final class LootTableTemplates {

    private LootTableTemplates() {}

    /** Self-drop table: breaking {@code blockId} (e.g. "conjure:block_slot_5") drops itself. */
    public static void writeSelfDrop(String slotId, String blockId) throws IOException {
        DynamicPackManager.write("data/conjure/loot_table/blocks/" + slotId + ".json",
                "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1.0,"
                + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}],"
                + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + blockId + "\"}]}],"
                + "\"random_sequence\":\"conjure:blocks/" + slotId + "\"}");
    }
}
