package dev.conjure.gen.pipeline;

import dev.conjure.Conjure;
import dev.conjure.Config;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextureKind;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.LogicAgent;
import dev.conjure.ai.agents.MachineAgent;
import dev.conjure.ai.agents.RecipeAgent;
import dev.conjure.ai.agents.TextureAgent;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.block.BlockArchetype;
import dev.conjure.content.block.WorkbenchRecipes;
import dev.conjure.gen.DynamicPackManager;
import dev.conjure.gen.RecipeTemplates;
import dev.conjure.registry.ConjureSlabs;
import dev.conjure.registry.ConjureStairs;
import dev.conjure.registry.ConjureWalls;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Block generation pipeline. Produces a textured, named block and — when
 * {@link Config#INTERACTIVITY_ENABLED} is on — classifies it as PLAIN, SCRIPT-driven, or
 * a MACHINE with a furnace-style recipe.
 *
 * <p>When the prompt is a building <em>material</em> (per {@link DataAgent}) and
 * {@link Config#RECIPES_ENABLED} is on, the block is instead expanded into a <b>family</b>: a base
 * cube plus the requested cube variants (smooth / bricks) and shaped variants (slab / stairs /
 * wall), all wired together with vanilla-pattern survival recipes (3-across → 6 slabs, smelt →
 * smooth, stonecutter, and a stonecut obtainability recipe from a vanilla block). See
 * {@link RecipeTemplates}.
 *
 * <h2>Archetype / slot range mapping</h2>
 * <pre>
 *   SOLID        slots  0 ..  199   (200 slots)
 *   TRANSPARENT  slots 200 ..  279   ( 80 slots)
 *   CUSTOM_SHAPE slots 280 ..  359   ( 80 slots)
 *   LIGHT        slots 360 ..  439   ( 80 slots)
 *   MACHINE      slots 440 ..  499   ( 60 slots)
 * </pre>
 * PLAIN, SCRIPT and all material cube variants are allocated from the SOLID range (opaque
 * cube_all model). MACHINE results are allocated from the MACHINE range.
 */
public final class BlockPipeline implements GenerationPipeline {

    /** Slot offset where the MACHINE archetype starts (SOLID+TRANSPARENT+CUSTOM_SHAPE+LIGHT). */
    private static final int MACHINE_OFFSET =
            BlockArchetype.SOLID.count
            + BlockArchetype.TRANSPARENT.count
            + BlockArchetype.CUSTOM_SHAPE.count
            + BlockArchetype.LIGHT.count;

    /** Slot offset where the ACTIVATABLE archetype starts (after MACHINE; it is the last bucket). */
    private static final int ACTIVATABLE_OFFSET = MACHINE_OFFSET + BlockArchetype.MACHINE.count;

    /**
     * First unconfigured slot in the MACHINE sub-range {@code [MACHINE_OFFSET, +MACHINE.count)},
     * or {@code -1} if the whole sub-range is full. {@link SlotRegistry#firstFree} only scans from
     * index 0, so it can never return a machine-range slot — we must scan the offset range here.
     */
    private static int firstFreeMachineSlot() {
        return firstFreeInRange(MACHINE_OFFSET, BlockArchetype.MACHINE.count);
    }

    /** First unconfigured slot in the ACTIVATABLE sub-range, or {@code -1} if full. */
    private static int firstFreeActivatableSlot() {
        return firstFreeInRange(ACTIVATABLE_OFFSET, BlockArchetype.ACTIVATABLE.count);
    }

    private static int firstFreeInRange(int offset, int count) {
        for (int i = offset; i < offset + count; i++) {
            if (!SlotRegistry.get(SlotKind.BLOCK, i).configured) return i;
        }
        return -1;
    }

    @Override
    public void run(String prompt, Consumer<String> feedback) throws Exception {

        // --- Texture + name (always generated first; same for all archetypes) ---
        feedback.accept("§7[Conjure] Generating texture…");
        int[][] argb = new TextureAgent().generate(prompt, TextureKind.BLOCK);

        feedback.accept("§7[Conjure] Generating name…");
        DataAgent.Result data = new DataAgent().generate(prompt);

        // --- Building material → expand into a family with survival recipes ---
        if (data.isMaterial() && Config.RECIPES_ENABLED.get()) {
            generateFamily(prompt, data, argb, feedback);
            return;
        }

        // --- Decide interaction kind (when interactivity is enabled) ---
        MachineAgent.Result machineResult;
        if (Config.INTERACTIVITY_ENABLED.get()) {
            feedback.accept("§7[Conjure] Deciding interaction kind…");
            machineResult = new MachineAgent().generate(prompt);
        } else {
            machineResult = MachineAgent.Result.plain();
        }

        // --- Allocate the correct slot based on interaction kind ---
        final int slot;
        if (machineResult.kind() == MachineAgent.Kind.WORKBENCH) {
            // Allocate from the MACHINE sub-range [MACHINE_OFFSET, MACHINE_OFFSET + MACHINE.count).
            int freeInMachine = firstFreeMachineSlot();
            if (freeInMachine < 0) {
                // All MACHINE slots taken; fall back to a SOLID slot as a plain block.
                int solidFree = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
                if (solidFree < 0) {
                    feedback.accept("All MACHINE and SOLID block slots are full.");
                    return;
                }
                slot = solidFree;
                // Downgrade to plain since we're using a non-machine slot
                machineResult = MachineAgent.Result.plain();
            } else {
                slot = freeInMachine;
            }
        } else if (machineResult.kind() == MachineAgent.Kind.STATEFUL) {
            int free = firstFreeActivatableSlot();
            if (free < 0) {
                // All stateful slots taken; fall back to a plain SOLID block.
                int solidFree = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
                if (solidFree < 0) { feedback.accept("All stateful and solid block slots are full."); return; }
                slot = solidFree;
                machineResult = MachineAgent.Result.plain();
            } else {
                slot = free;
            }
        } else {
            // PLAIN and SCRIPT go into the SOLID range
            int solidFree = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
            if (solidFree < 0) {
                feedback.accept("All " + BlockArchetype.SOLID.count + " solid block slots are full.");
                return;
            }
            slot = solidFree;
        }

        Conjure.LOGGER.info("Conjure: generating block slot {} ({}) via {} for prompt: {}",
                slot, machineResult.kind(), ProviderFactory.text().id(), prompt);

        // Write assets (stateful blocks get a second "on/open" texture + a variant blockstate)
        if (machineResult.kind() == MachineAgent.Kind.STATEFUL) {
            feedback.accept("§7[Conjure] Generating activated texture…");
            String activePrompt = data.activeVisual().isBlank()
                    ? prompt + " activated"
                    : data.activeVisual();
            int[][] onArgb = new TextureAgent().generate(activePrompt, TextureKind.BLOCK);
            DynamicPackManager.writeActivatableAssets(slot, argb, onArgb);
        } else {
            DynamicPackManager.writeBlockTexture(slot, argb);
            DynamicPackManager.writeBlockModel(slot);
            DynamicPackManager.writeBlockState(slot);
            DynamicPackManager.writeBlockItemModel(slot);
        }

        // --- Build SlotDefinition ---
        SlotDefinition def = new SlotDefinition(SlotKind.BLOCK, slot);
        def.displayName  = data.displayName();
        def.sourcePrompt = prompt;
        def.texturePath  = "conjure:block/block_slot_" + slot;
        def.strings.put("description", data.description());

        switch (machineResult.kind()) {
            case WORKBENCH -> {
                def.behaviorScriptId = "";
                WorkbenchRecipes.write(def, machineResult.inputs().size(), machineResult.inputs(),
                        machineResult.output(), machineResult.outputCount(),
                        machineResult.fuel(), machineResult.ticks());
            }
            case STATEFUL -> {
                def.strings.put("interaction", "stateful");
                feedback.accept("§7[Conjure] Generating stateful behavior…");
                String scriptId = "block_slot_" + slot;
                PipelineSupport.writeScript(scriptId, new LogicAgent().generate(prompt));
                def.behaviorScriptId = scriptId;
                def.strings.put("keyId", "conjure_key_" + slot);
            }
            case SCRIPT -> {
                feedback.accept("§7[Conjure] Generating behavior script…");
                String js       = new LogicAgent().generate(prompt);
                String scriptId = "block_slot_" + slot;
                PipelineSupport.writeScript(scriptId, js);
                def.behaviorScriptId = scriptId;
                def.strings.put("interaction", "script");
            }
            default -> {
                // PLAIN
                def.behaviorScriptId = "";
                def.strings.put("interaction", "plain");
            }
        }

        PipelineSupport.commit(def);

        // Survival obtainability: a shapeless crafting recipe from vanilla ingredients, for any
        // block (a "magic orb block" is craftable even though it isn't a building material).
        if (Config.RECIPES_ENABLED.get()) {
            writeCraftingRecipe("block_slot_" + slot, prompt, "conjure:block_slot_" + slot, feedback);
        }

        String kindLabel = switch (machineResult.kind()) {
            case WORKBENCH -> " [workbench]";
            case SCRIPT    -> " [scripted]";
            case STATEFUL  -> " [stateful]";
            default        -> "";
        };
        feedback.accept("Conjured '" + data.displayName() + "'" + kindLabel
                + " → block slot #" + slot
                + ". Try: /give @s conjure:block_slot_" + slot);
    }

    /**
     * Regenerates a single existing block slot in place (used by {@code /conjure regenerate block}).
     * Unlike {@link #run}, this never expands into a material family and never re-allocates: it keeps
     * the requested slot id so blocks already placed in the world keep working. The slot's archetype
     * is fixed, so a workbench recipe is only produced when the slot lives in the MACHINE sub-range
     * (the only range whose blocks carry a {@link dev.conjure.content.block.ConjureBlockEntity}).
     */
    @Override
    public void runForSlot(int slot, String prompt, Consumer<String> feedback) throws Exception {
        feedback.accept("§7[Conjure] Generating texture…");
        int[][] argb = new TextureAgent().generate(prompt, TextureKind.BLOCK);

        feedback.accept("§7[Conjure] Generating name…");
        DataAgent.Result data = new DataAgent().generate(prompt);

        boolean machineSlot = slot >= MACHINE_OFFSET
                && slot < MACHINE_OFFSET + BlockArchetype.MACHINE.count;
        // The slot's archetype is fixed: an ACTIVATABLE-range block MUST keep its stateful assets
        // (its block carries the `active` property, so its blockstate needs both variants).
        boolean activatableSlot = slot >= ACTIVATABLE_OFFSET
                && slot < ACTIVATABLE_OFFSET + BlockArchetype.ACTIVATABLE.count;

        MachineAgent.Result result = activatableSlot ? MachineAgent.Result.stateful()
                                                     : MachineAgent.Result.plain();
        if (!activatableSlot && Config.INTERACTIVITY_ENABLED.get()) {
            feedback.accept("§7[Conjure] Deciding interaction kind…");
            result = new MachineAgent().generate(prompt);
            // A non-machine slot has no block entity, so a workbench can't run there — fall back.
            if (result.kind() == MachineAgent.Kind.WORKBENCH && !machineSlot) {
                result = MachineAgent.Result.plain();
            }
            // A STATEFUL classification needs the `active` property, which only ACTIVATABLE slots have.
            if (result.kind() == MachineAgent.Kind.STATEFUL) {
                result = MachineAgent.Result.plain();
            }
        }

        Conjure.LOGGER.info("Conjure: regenerating block slot {} ({}) via {} for prompt: {}",
                slot, result.kind(), ProviderFactory.text().id(), prompt);

        if (activatableSlot) {
            feedback.accept("§7[Conjure] Generating activated texture…");
            String activePrompt = data.activeVisual().isBlank()
                    ? prompt + " activated"
                    : data.activeVisual();
            int[][] onArgb = new TextureAgent().generate(activePrompt, TextureKind.BLOCK);
            DynamicPackManager.writeActivatableAssets(slot, argb, onArgb);
        } else {
            DynamicPackManager.writeBlockTexture(slot, argb);
            DynamicPackManager.writeBlockModel(slot);
            DynamicPackManager.writeBlockState(slot);
            DynamicPackManager.writeBlockItemModel(slot);
        }

        SlotDefinition def = new SlotDefinition(SlotKind.BLOCK, slot);
        def.displayName  = data.displayName();
        def.sourcePrompt = prompt;
        def.texturePath  = "conjure:block/block_slot_" + slot;
        def.strings.put("description", data.description());

        switch (result.kind()) {
            case WORKBENCH -> {
                def.behaviorScriptId = "";
                WorkbenchRecipes.write(def, result.inputs().size(), result.inputs(),
                        result.output(), result.outputCount(), result.fuel(), result.ticks());
            }
            case STATEFUL -> {
                def.strings.put("interaction", "stateful");
                feedback.accept("§7[Conjure] Generating stateful behavior…");
                String scriptId = "block_slot_" + slot;
                PipelineSupport.writeScript(scriptId, new LogicAgent().generate(prompt));
                def.behaviorScriptId = scriptId;
                def.strings.put("keyId", "conjure_key_" + slot);
            }
            case SCRIPT -> {
                feedback.accept("§7[Conjure] Generating behavior script…");
                String scriptId = "block_slot_" + slot;
                PipelineSupport.writeScript(scriptId, new LogicAgent().generate(prompt));
                def.behaviorScriptId = scriptId;
                def.strings.put("interaction", "script");
            }
            default -> {
                def.behaviorScriptId = "";
                def.strings.put("interaction", "plain");
            }
        }

        PipelineSupport.commit(def);
        feedback.accept("Regenerated '" + data.displayName() + "' → block slot #" + slot + ".");
    }

    // -------------------------------------------------------------------------
    // Material-family expansion
    // -------------------------------------------------------------------------

    /**
     * Builds a base cube plus the requested variants and writes their recipes. All members are
     * committed quietly and a single asset + datapack reload fires at the end, so a family appears
     * atomically with one reload instead of one per piece.
     */
    private void generateFamily(String prompt, DataAgent.Result data, int[][] baseArgb,
                                Consumer<String> feedback) throws Exception {
        int base = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
        if (base < 0) {
            feedback.accept("All " + BlockArchetype.SOLID.count + " solid block slots are full.");
            return;
        }
        Conjure.LOGGER.info("Conjure: generating material family '{}' base slot {} via {}",
                data.displayName(), base, ProviderFactory.text().id());

        writeCubeAssets(base, baseArgb);
        commitCube(base, data.displayName(), prompt, data.description());
        String baseId = "conjure:block_slot_" + base;
        feedback.accept("§a[Conjure] §fMaterial '" + data.displayName() + "' → block slot #" + base);

        Set<String> v = data.variants();
        String smoothId = null, bricksId = null, slabId = null, stairsId = null, wallId = null;

        if (v.contains("smooth")) {
            int s = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
            if (s >= 0) {
                feedback.accept("§7[Conjure] Generating smooth variant…");
                writeCubeAssets(s, new TextureAgent().generate("polished smooth " + prompt, TextureKind.BLOCK));
                commitCube(s, "Smooth " + data.displayName(), prompt, "Polished " + data.displayName() + ".");
                smoothId = "conjure:block_slot_" + s;
            }
        }
        if (v.contains("bricks")) {
            int k = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
            if (k >= 0) {
                feedback.accept("§7[Conjure] Generating bricks variant…");
                writeCubeAssets(k, new TextureAgent().generate(prompt + " bricks brickwork", TextureKind.BLOCK));
                commitCube(k, data.displayName() + " Bricks", prompt, data.displayName() + " bricks.");
                bricksId = "conjure:block_slot_" + k;
            }
        }

        // Shaped variants reuse the base texture (no new texture generation).
        String baseTexRef = "conjure:block/block_slot_" + base;
        if (v.contains("slab")) {
            int sl = SlotRegistry.firstFree(SlotKind.SLAB, ConjureSlabs.SLAB_POOL);
            if (sl >= 0) {
                DynamicPackManager.writeSlabAssets(sl, baseTexRef);
                commitShaped(SlotKind.SLAB, sl, data.displayName() + " Slab", prompt, baseTexRef);
                slabId = "conjure:slab_slot_" + sl;
            }
        }
        if (v.contains("stairs")) {
            int st = SlotRegistry.firstFree(SlotKind.STAIRS, ConjureStairs.STAIRS_POOL);
            if (st >= 0) {
                DynamicPackManager.writeStairAssets(st, baseTexRef);
                commitShaped(SlotKind.STAIRS, st, data.displayName() + " Stairs", prompt, baseTexRef);
                stairsId = "conjure:stairs_slot_" + st;
            }
        }
        if (v.contains("wall")) {
            int wl = SlotRegistry.firstFree(SlotKind.WALL, ConjureWalls.WALL_POOL);
            if (wl >= 0) {
                DynamicPackManager.writeWallAssets(wl, baseTexRef);
                commitShaped(SlotKind.WALL, wl, data.displayName() + " Wall", prompt, baseTexRef);
                wallId = "conjure:wall_slot_" + wl;
            }
        }

        // Recipes: deterministic family + AI-chosen base obtainability (stonecut from vanilla).
        feedback.accept("§7[Conjure] Writing recipes…");
        RecipeTemplates.writeFamily(baseId, smoothId, bricksId, slabId, stairsId, wallId);
        try {
            String source = new RecipeAgent().generate(prompt).sourceBlock();
            RecipeTemplates.writeStonecutting("block_slot_" + base + "_from_vanilla", source, baseId, 1);
        } catch (Exception e) {
            Conjure.LOGGER.warn("[Conjure] base obtainability recipe skipped: {}", e.getMessage());
        }

        // One asset reload + one datapack reload for the whole family.
        PipelineSupport.reloadIfClient();
        PipelineSupport.reloadData();

        feedback.accept("Conjured the '" + data.displayName() + "' family → block slot #" + base
                + " (+ variants). Craft slabs/stairs/walls, smelt for smooth, or use a stonecutter.");
    }

    /**
     * Writes a shapeless crafting recipe (vanilla ingredients → the block) and reloads datapacks so
     * it applies live. Best-effort: a model/IO failure is logged and skipped, never failing the
     * block generation that already succeeded.
     */
    private static void writeCraftingRecipe(String recipeId, String prompt, String resultId,
                                            Consumer<String> feedback) {
        try {
            feedback.accept("§7[Conjure] Writing recipe…");
            java.util.List<String> ingredients = new RecipeAgent().craftIngredients(prompt);
            RecipeTemplates.writeShapeless(recipeId, ingredients, resultId, 1);
            PipelineSupport.reloadData();
        } catch (Exception e) {
            Conjure.LOGGER.warn("[Conjure] crafting recipe skipped for {}: {}", recipeId, e.getMessage());
        }
    }

    private static void writeCubeAssets(int slot, int[][] argb) throws Exception {
        DynamicPackManager.writeBlockTexture(slot, argb);
        DynamicPackManager.writeBlockModel(slot);
        DynamicPackManager.writeBlockState(slot);
        DynamicPackManager.writeBlockItemModel(slot);
    }

    private static void commitCube(int slot, String name, String prompt, String desc) throws Exception {
        SlotDefinition def = new SlotDefinition(SlotKind.BLOCK, slot);
        def.displayName  = name;
        def.sourcePrompt = prompt;
        def.texturePath  = "conjure:block/block_slot_" + slot;
        def.behaviorScriptId = "";
        def.strings.put("description", desc);
        def.strings.put("interaction", "plain");
        PipelineSupport.commitQuiet(def);
    }

    private static void commitShaped(SlotKind kind, int slot, String name, String prompt,
                                     String textureRef) throws Exception {
        SlotDefinition def = new SlotDefinition(kind, slot);
        def.displayName  = name;
        def.sourcePrompt = prompt;
        def.texturePath  = textureRef; // reuses the base cube's texture
        PipelineSupport.commitQuiet(def);
    }
}
