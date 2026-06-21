package dev.conjure.gen.pipeline;

import dev.conjure.Conjure;
import dev.conjure.ai.TextureKind;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.TextureAgent;
import dev.conjure.ai.agents.WorldgenAgent;
import dev.conjure.content.IntentTooltip;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.block.BlockArchetype;
import dev.conjure.gen.DynamicPackManager;

import java.util.function.Consumer;

/**
 * World-gen injection: generates a plain ore block, then writes the three datapack JSON files
 * (configured feature + placed feature + NeoForge biome modifier) that make it spawn in the world.
 *
 * <p>Worldgen registries (CONFIGURED_FEATURE / PLACED_FEATURE) are datapack registries that freeze
 * at world load, so a {@code DeferredRegister} can't add to them at runtime — instead we write JSON
 * into the dynamic pack (same mechanism as recipes) and the new ore appears in chunks generated
 * after the next world rejoin. The three files are named by the owning block slot.
 */
public final class WorldgenPipeline implements GenerationPipeline {

    @Override
    public void run(String prompt, Consumer<String> feedback) throws Exception {
        int slot = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
        if (slot < 0) { feedback.accept("All solid block slots are full."); return; }

        feedback.accept("§7[Conjure] Generating ore texture…");
        int[][] argb = new TextureAgent().generate(prompt + " ore rock mineral vein", TextureKind.BLOCK);
        DynamicPackManager.writeBlockTexture(slot, argb);
        DynamicPackManager.writeBlockModel(slot);
        DynamicPackManager.writeBlockState(slot);
        DynamicPackManager.writeBlockItemModel(slot);

        feedback.accept("§7[Conjure] Generating name + worldgen parameters…");
        DataAgent.Result data = new DataAgent().generate(prompt, SlotKind.BLOCK);
        String blockId = "conjure:block_slot_" + slot;
        WorldgenAgent.Result wg = new WorldgenAgent().generate(prompt, blockId);

        String name = "worldgen_block_" + slot;
        writeWorldgenJson(name, blockId, wg);

        SlotDefinition def = new SlotDefinition(SlotKind.BLOCK, slot);
        def.displayName  = data.displayName();
        def.sourcePrompt = prompt;
        def.texturePath  = "conjure:block/block_slot_" + slot;
        def.behaviorScriptId = "";
        def.strings.put("description", data.description());
        def.strings.put("interaction", "plain");
        def.strings.put(IntentTooltip.WORLDGEN, wg.worldgenIntent());

        dev.conjure.gen.GenerationContext gc = dev.conjure.gen.GenerationContext.current();
        if (gc != null) gc.setCreatedId("conjure:block_slot_" + slot);

        PipelineSupport.commit(def);
        PipelineSupport.reloadData();

        Conjure.LOGGER.info("Conjure: worldgen block slot {} ({}) for: {}", slot, wg.worldgenIntent(), prompt);
        feedback.accept("Conjured ore '" + data.displayName() + "' → block slot #" + slot
                + " (" + wg.worldgenIntent() + ").");
        feedback.accept("§e[Conjure] §fRejoin the world to see it spawn in new chunks.");
    }

    /** Writes the configured-feature, placed-feature, and biome-modifier JSON for one ore. */
    private static void writeWorldgenJson(String name, String blockId, WorldgenAgent.Result wg) throws Exception {
        DynamicPackManager.write("data/conjure/worldgen/configured_feature/" + name + ".json",
                "{\"type\":\"minecraft:ore\",\"config\":{\"size\":" + wg.veinSize()
                + ",\"discard_chance_on_air_exposure\":0.0,\"targets\":[{"
                + "\"state\":{\"Name\":\"" + blockId + "\"},"
                + "\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:stone_ore_replaceables\"}}]}}");

        DynamicPackManager.write("data/conjure/worldgen/placed_feature/" + name + ".json",
                "{\"feature\":\"conjure:" + name + "\",\"placement\":["
                + "{\"type\":\"minecraft:count\",\"count\":" + wg.veinsPerChunk() + "},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:uniform\","
                + "\"min_inclusive\":{\"absolute\":" + wg.minY() + "},\"max_inclusive\":{\"absolute\":" + wg.maxY() + "}}},"
                + "{\"type\":\"minecraft:biome\"}]}");

        DynamicPackManager.write("data/conjure/neoforge/biome_modifier/" + name + ".json",
                "{\"type\":\"neoforge:add_features\",\"biomes\":\"" + wg.biomeTag() + "\","
                + "\"features\":\"conjure:" + name + "\",\"step\":\"underground_ores\"}");
    }
}
