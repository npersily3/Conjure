package dev.conjure.gen.pipeline;

import dev.conjure.Conjure;
import dev.conjure.ai.ProviderFactory;
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
import dev.conjure.registry.ConjureWorldgen;

import java.util.function.Consumer;

/**
 * World-generation injection pipeline.
 *
 * <p>This pipeline does two things in one shot when called via {@code run}:
 * <ol>
 *   <li>Generates a new {@link SlotKind#BLOCK} slot (texture, name, description) — same as
 *       a plain-block generation but restricted to the SOLID archetype range so the resulting
 *       block is a simple cube that can act as an ore vein.</li>
 *   <li>Writes three JSON files into the dynamic data pack so the block spawns in the world
 *       on the next world rejoin:
 *       <ul>
 *         <li>{@code data/conjure/worldgen/configured_feature/worldgen_slot_N.json}
 *             — {@code minecraft:ore} type targeting {@code minecraft:stone_ore_replaceables}</li>
 *         <li>{@code data/conjure/worldgen/placed_feature/worldgen_slot_N.json}
 *             — count + height_range + in_square + biome placement modifiers</li>
 *         <li>{@code data/conjure/neoforge/biome_modifier/worldgen_slot_N.json}
 *             — {@code neoforge:add_features} modifier pointing at the placed feature</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>Why JSON, not DeferredRegister</h2>
 * <p>{@code Registries.CONFIGURED_FEATURE} and {@code Registries.PLACED_FEATURE} are
 * <em>datapack registries</em> in 1.21.1 — they are not {@code BuiltInRegistries} and cannot
 * accept entries from a {@code DeferredRegister} at mod-load time.  The JSON approach is the
 * canonical runtime injection mechanism and exactly mirrors how vanilla/NeoForge handles custom
 * worldgen in mods.  The dynamic pack is already registered as a {@code SERVER_DATA} pack via
 * {@link dev.conjure.registry.ConjureDataPack}.
 *
 * <h2>Restart / rejoin requirement</h2>
 * <p>Worldgen registries freeze when the world loads.  Writing the JSON files after generation
 * means the new ore will appear in <em>newly generated chunks</em> starting from the next world
 * load.  The user is always informed of this in the feedback message.
 *
 * <h2>Pool</h2>
 * <p>Worldgen slots are tracked in {@link ConjureWorldgen} (32 slots).  Each slot maps to one
 * block slot index + one worldgen JSON triple.
 */
public final class WorldgenPipeline implements GenerationPipeline {

    // -------------------------------------------------------------------------
    // GenerationPipeline impl
    // -------------------------------------------------------------------------

    /**
     * Allocates a new SOLID block slot, generates its texture + name, calls
     * {@link WorldgenAgent} for ore parameters, writes the worldgen JSON, and stores the
     * {@link IntentTooltip#WORLDGEN} key so the tooltip overlay shows what was generated.
     */
    @Override
    public void run(String prompt, Consumer<String> feedback) throws Exception {
        // Check worldgen pool capacity
        int worldgenPoolSlot = ConjureWorldgen.firstFree();
        if (worldgenPoolSlot < 0) {
            feedback.accept("All " + ConjureWorldgen.WORLDGEN_POOL
                    + " worldgen slots are full.");
            return;
        }

        // Allocate a SOLID block slot for the physical ore block
        int blockSlot = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
        if (blockSlot < 0) {
            feedback.accept("All solid block slots are full — cannot create an ore block.");
            return;
        }

        Conjure.LOGGER.info("Conjure: generating worldgen slot {} (block slot {}) via {} for: {}",
                worldgenPoolSlot, blockSlot, ProviderFactory.text().id(), prompt);

        // 1. Generate block texture + name
        feedback.accept("§7[Conjure] Generating ore texture…");
        int[][] argb = new TextureAgent().generate(prompt + " ore rock mineral vein", TextureKind.BLOCK);
        DynamicPackManager.writeBlockTexture(blockSlot, argb);
        DynamicPackManager.writeBlockModel(blockSlot);
        DynamicPackManager.writeBlockState(blockSlot);
        DynamicPackManager.writeBlockItemModel(blockSlot);

        feedback.accept("§7[Conjure] Generating name…");
        DataAgent.Result data = new DataAgent().generate(prompt);

        // 2. Ask WorldgenAgent for spawn parameters
        feedback.accept("§7[Conjure] Generating worldgen parameters…");
        String blockId = "conjure:block_slot_" + blockSlot;
        WorldgenAgent.Result wg = new WorldgenAgent().generate(prompt, blockId);

        // 3. Write worldgen JSON files
        String slotName = "worldgen_slot_" + worldgenPoolSlot;
        writeConfiguredFeature(slotName, blockId, wg);
        writePlacedFeature(slotName, wg);
        writeBiomeModifier(slotName, wg);

        // 4. Build and commit block SlotDefinition
        SlotDefinition def = new SlotDefinition(SlotKind.BLOCK, blockSlot);
        def.displayName  = data.displayName();
        def.sourcePrompt = prompt;
        def.texturePath  = "conjure:block/block_slot_" + blockSlot;
        def.behaviorScriptId = "";
        def.strings.put("description",   data.description());
        def.strings.put("interaction",    "plain");
        def.strings.put(IntentTooltip.WORLDGEN, wg.worldgenIntent());
        def.strings.put("worldgenSlot",   String.valueOf(worldgenPoolSlot));

        // 5. Mark worldgen tracking slot as configured
        SlotDefinition wgTracking = SlotRegistry.get(SlotKind.BLOCK, ConjureWorldgen.worldgenSlotIndex(worldgenPoolSlot));
        wgTracking.displayName  = "Worldgen: " + data.displayName();
        wgTracking.sourcePrompt = prompt;
        wgTracking.strings.put("worldgenBlockSlot", String.valueOf(blockSlot));
        wgTracking.strings.put(IntentTooltip.WORLDGEN, wg.worldgenIntent());
        SlotRegistry.put(wgTracking);

        PipelineSupport.commit(def);

        // Reload datapack so the worldgen JSON is registered for the next world load
        PipelineSupport.reloadData();

        feedback.accept("Conjured ore '" + data.displayName() + "' → block slot #" + blockSlot
                + ". Try: /give @s conjure:block_slot_" + blockSlot);
        feedback.accept("§e[Conjure] §fWorldgen config written ("
                + wg.worldgenIntent() + "). §7Rejoin the world to see it spawn in new chunks.");
    }

    /**
     * Adds worldgen configuration to an existing block slot (e.g. after a block was already
     * generated by {@link BlockPipeline}). Does NOT re-generate the block itself — only
     * writes the three worldgen JSON files and updates the slot's {@link IntentTooltip#WORLDGEN}
     * string so the tooltip overlay reflects the change.
     *
     * @param blockSlot the existing BLOCK slot index to add worldgen for
     * @param prompt    the original prompt (used to generate ore parameters)
     * @param feedback  progress + result callback
     */
    public void runForSlot(int blockSlot, String prompt, Consumer<String> feedback) throws Exception {
        SlotDefinition existing = SlotRegistry.get(SlotKind.BLOCK, blockSlot);
        if (!existing.configured) {
            feedback.accept("§cBlock slot #" + blockSlot + " is not configured. Generate a block first.");
            return;
        }

        int worldgenPoolSlot = ConjureWorldgen.firstFree();
        if (worldgenPoolSlot < 0) {
            feedback.accept("All " + ConjureWorldgen.WORLDGEN_POOL + " worldgen slots are full.");
            return;
        }

        Conjure.LOGGER.info("Conjure: adding worldgen to block slot {} (worldgen pool {}) for: {}",
                blockSlot, worldgenPoolSlot, prompt);

        feedback.accept("§7[Conjure] Generating worldgen parameters…");
        String blockId = "conjure:block_slot_" + blockSlot;
        WorldgenAgent.Result wg = new WorldgenAgent().generate(prompt, blockId);

        String slotName = "worldgen_slot_" + worldgenPoolSlot;
        writeConfiguredFeature(slotName, blockId, wg);
        writePlacedFeature(slotName, wg);
        writeBiomeModifier(slotName, wg);

        // Update slot definition with worldgen intent (rebuild rather than mutate in-place)
        SlotDefinition updated = new SlotDefinition(SlotKind.BLOCK, blockSlot);
        updated.displayName      = existing.displayName;
        updated.sourcePrompt     = existing.sourcePrompt;
        updated.texturePath      = existing.texturePath;
        updated.behaviorScriptId = existing.behaviorScriptId;
        updated.numbers.putAll(existing.numbers);
        updated.strings.putAll(existing.strings);
        updated.strings.put(IntentTooltip.WORLDGEN, wg.worldgenIntent());
        updated.strings.put("worldgenSlot", String.valueOf(worldgenPoolSlot));

        SlotDefinition wgTracking = SlotRegistry.get(SlotKind.BLOCK, ConjureWorldgen.worldgenSlotIndex(worldgenPoolSlot));
        wgTracking.displayName  = "Worldgen: " + existing.displayName;
        wgTracking.sourcePrompt = prompt;
        wgTracking.strings.put("worldgenBlockSlot", String.valueOf(blockSlot));
        wgTracking.strings.put(IntentTooltip.WORLDGEN, wg.worldgenIntent());
        SlotRegistry.put(wgTracking);

        PipelineSupport.commit(updated);
        PipelineSupport.reloadData();

        feedback.accept("Added worldgen config to '" + existing.displayName
                + "' (block slot #" + blockSlot + "): " + wg.worldgenIntent());
        feedback.accept("§e[Conjure] §fRejoin the world to see it spawn in new chunks.");
    }

    // -------------------------------------------------------------------------
    // JSON writers
    // -------------------------------------------------------------------------

    /**
     * Writes {@code data/conjure/worldgen/configured_feature/<slotName>.json}.
     *
     * <p>Uses the vanilla {@code minecraft:ore} feature type with a single target:
     * the conjured block replacing {@code #minecraft:stone_ore_replaceables}.
     * The {@code "state"} object uses the 1.21.1 block-state codec (with {@code "Name"} key).
     *
     * <pre>{@code
     * {
     *   "type": "minecraft:ore",
     *   "config": {
     *     "size": <veinSize>,
     *     "discard_chance_on_air_exposure": 0.0,
     *     "targets": [{
     *       "state": { "Name": "<blockId>" },
     *       "target": { "predicate_type": "minecraft:tag_match",
     *                   "tag": "minecraft:stone_ore_replaceables" }
     *     }]
     *   }
     * }
     * }</pre>
     */
    private static void writeConfiguredFeature(String slotName, String blockId,
                                               WorldgenAgent.Result wg) throws Exception {
        String json = "{\n"
                + "  \"type\": \"minecraft:ore\",\n"
                + "  \"config\": {\n"
                + "    \"size\": " + wg.veinSize() + ",\n"
                + "    \"discard_chance_on_air_exposure\": 0.0,\n"
                + "    \"targets\": [\n"
                + "      {\n"
                + "        \"state\": { \"Name\": \"" + blockId + "\" },\n"
                + "        \"target\": {\n"
                + "          \"predicate_type\": \"minecraft:tag_match\",\n"
                + "          \"tag\": \"minecraft:stone_ore_replaceables\"\n"
                + "        }\n"
                + "      }\n"
                + "    ]\n"
                + "  }\n"
                + "}\n";
        DynamicPackManager.write(
                "data/conjure/worldgen/configured_feature/" + slotName + ".json", json);
    }

    /**
     * Writes {@code data/conjure/worldgen/placed_feature/<slotName>.json}.
     *
     * <p>Placement modifiers (in order, per vanilla convention):
     * <ol>
     *   <li>{@code minecraft:count}  — veins per chunk</li>
     *   <li>{@code minecraft:in_square} — random XZ spread within chunk</li>
     *   <li>{@code minecraft:height_range} — uniform Y range</li>
     *   <li>{@code minecraft:biome} — only in biomes that declare this feature</li>
     * </ol>
     *
     * <pre>{@code
     * {
     *   "feature": "conjure:<slotName>",
     *   "placement": [
     *     { "type": "minecraft:count", "count": <veinsPerChunk> },
     *     { "type": "minecraft:in_square" },
     *     { "type": "minecraft:height_range", "height": {
     *         "type": "minecraft:uniform",
     *         "min_inclusive": { "absolute": <minY> },
     *         "max_inclusive": { "absolute": <maxY> }
     *     }},
     *     { "type": "minecraft:biome" }
     *   ]
     * }
     * }</pre>
     */
    private static void writePlacedFeature(String slotName, WorldgenAgent.Result wg) throws Exception {
        String json = "{\n"
                + "  \"feature\": \"conjure:" + slotName + "\",\n"
                + "  \"placement\": [\n"
                + "    { \"type\": \"minecraft:count\", \"count\": " + wg.veinsPerChunk() + " },\n"
                + "    { \"type\": \"minecraft:in_square\" },\n"
                + "    {\n"
                + "      \"type\": \"minecraft:height_range\",\n"
                + "      \"height\": {\n"
                + "        \"type\": \"minecraft:uniform\",\n"
                + "        \"min_inclusive\": { \"absolute\": " + wg.minY() + " },\n"
                + "        \"max_inclusive\": { \"absolute\": " + wg.maxY() + " }\n"
                + "      }\n"
                + "    },\n"
                + "    { \"type\": \"minecraft:biome\" }\n"
                + "  ]\n"
                + "}\n";
        DynamicPackManager.write(
                "data/conjure/worldgen/placed_feature/" + slotName + ".json", json);
    }

    /**
     * Writes {@code data/conjure/neoforge/biome_modifier/<slotName>.json}.
     *
     * <p>Uses the {@code neoforge:add_features} modifier type (documented in
     * {@link net.neoforged.neoforge.common.world.BiomeModifiers.AddFeaturesBiomeModifier}).
     * The modifier adds the placed feature at the {@code underground_ores} decoration step.
     *
     * <pre>{@code
     * {
     *   "type": "neoforge:add_features",
     *   "biomes": "<biomeTag>",
     *   "features": "conjure:<slotName>",
     *   "step": "underground_ores"
     * }
     * }</pre>
     */
    private static void writeBiomeModifier(String slotName, WorldgenAgent.Result wg) throws Exception {
        String json = "{\n"
                + "  \"type\": \"neoforge:add_features\",\n"
                + "  \"biomes\": \"" + wg.biomeTag() + "\",\n"
                + "  \"features\": \"conjure:" + slotName + "\",\n"
                + "  \"step\": \"underground_ores\"\n"
                + "}\n";
        DynamicPackManager.write(
                "data/conjure/neoforge/biome_modifier/" + slotName + ".json", json);
    }
}
