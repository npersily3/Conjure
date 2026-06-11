package dev.conjure.gen.pipeline;

import dev.conjure.Conjure;
import dev.conjure.Config;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.LogicAgent;
import dev.conjure.ai.agents.MachineAgent;
import dev.conjure.ai.agents.TextureAgent;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.block.BlockArchetype;
import dev.conjure.gen.DynamicPackManager;

import java.util.function.Consumer;

/**
 * Block generation pipeline. Produces a textured, named block and — when
 * {@link Config#INTERACTIVITY_ENABLED} is on — classifies it as PLAIN, SCRIPT-driven, or
 * a MACHINE with a furnace-style recipe.
 *
 * <h2>Archetype / slot range mapping</h2>
 * <pre>
 *   SOLID        slots  0 ..  199   (200 slots)
 *   TRANSPARENT  slots 200 ..  279   ( 80 slots)
 *   CUSTOM_SHAPE slots 280 ..  359   ( 80 slots)
 *   LIGHT        slots 360 ..  439   ( 80 slots)
 *   MACHINE      slots 440 ..  499   ( 60 slots)
 * </pre>
 * PLAIN and SCRIPT results are allocated from the SOLID range (opaque cube_all model).
 * MACHINE results are allocated from the MACHINE range.
 */
public final class BlockPipeline implements GenerationPipeline {

    /** Slot offset where the MACHINE archetype starts (SOLID+TRANSPARENT+CUSTOM_SHAPE+LIGHT). */
    private static final int MACHINE_OFFSET =
            BlockArchetype.SOLID.count
            + BlockArchetype.TRANSPARENT.count
            + BlockArchetype.CUSTOM_SHAPE.count
            + BlockArchetype.LIGHT.count;

    @Override
    public void run(String prompt, Consumer<String> feedback) throws Exception {

        // --- Texture + name (always generated first; same for all archetypes) ---
        feedback.accept("§7[Conjure] Generating texture…");
        int[][] argb = new TextureAgent().generate(prompt);

        feedback.accept("§7[Conjure] Generating name…");
        DataAgent.Result data = new DataAgent().generate(prompt);

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
        if (machineResult.kind() == MachineAgent.Kind.MACHINE) {
            // Allocate from the MACHINE sub-range (MACHINE_OFFSET .. MACHINE_OFFSET + MACHINE.count)
            int freeInMachine = SlotRegistry.firstFree(SlotKind.BLOCK,
                    MACHINE_OFFSET + BlockArchetype.MACHINE.count);
            if (freeInMachine < MACHINE_OFFSET) {
                // All MACHINE slots taken; fallback to SOLID
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

        // Write assets
        DynamicPackManager.writeBlockTexture(slot, argb);
        DynamicPackManager.writeBlockModel(slot);
        DynamicPackManager.writeBlockState(slot);
        DynamicPackManager.writeBlockItemModel(slot);

        // --- Build SlotDefinition ---
        SlotDefinition def = new SlotDefinition(SlotKind.BLOCK, slot);
        def.displayName  = data.displayName();
        def.sourcePrompt = prompt;
        def.texturePath  = "conjure:block/block_slot_" + slot;
        def.strings.put("description", data.description());

        switch (machineResult.kind()) {
            case MACHINE -> {
                def.behaviorScriptId = "";
                def.strings.put("interaction",    "machine");
                def.strings.put("recipe_input",   machineResult.recipeInput());
                def.strings.put("recipe_output",  machineResult.recipeOutput());
                def.numbers.put("recipe_ticks",   (double) machineResult.recipeTicks());
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

        String kindLabel = switch (machineResult.kind()) {
            case MACHINE -> " [machine]";
            case SCRIPT  -> " [scripted]";
            default      -> "";
        };
        feedback.accept("Conjured '" + data.displayName() + "'" + kindLabel
                + " → block slot #" + slot
                + ". Try: /give @s conjure:block_slot_" + slot);
    }
}
