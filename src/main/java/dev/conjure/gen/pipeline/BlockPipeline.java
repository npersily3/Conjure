package dev.conjure.gen.pipeline;

import dev.conjure.Conjure;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.TextureAgent;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.block.BlockArchetype;
import dev.conjure.gen.DynamicPackManager;

import java.util.function.Consumer;

/**
 * Block generation. Phase-0 baseline: texture + placeable (no behavior). The interactive-blocks
 * lane expands this to discern plain / script-driven / machine blocks and wire BlockEntity + menu.
 */
public final class BlockPipeline implements GenerationPipeline {

    @Override
    public void run(String prompt, Consumer<String> feedback) throws Exception {
        // SOLID is the first archetype bucket (slots 0 .. SOLID.count-1); restricting to it
        // guarantees an opaque block that renders with the standard cube_all model.
        int slot = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
        if (slot < 0) {
            feedback.accept("All " + BlockArchetype.SOLID.count + " solid block slots are full.");
            return;
        }
        Conjure.LOGGER.info("Conjure: generating block slot {} via {} for prompt: {}",
                slot, ProviderFactory.text().id(), prompt);

        feedback.accept("§7[Conjure] Generating texture…");
        int[][] argb = new TextureAgent().generate(prompt);
        DynamicPackManager.writeBlockTexture(slot, argb);
        DynamicPackManager.writeBlockModel(slot);
        DynamicPackManager.writeBlockState(slot);
        DynamicPackManager.writeBlockItemModel(slot);

        feedback.accept("§7[Conjure] Generating name…");
        DataAgent.Result data = new DataAgent().generate(prompt);

        SlotDefinition def = new SlotDefinition(SlotKind.BLOCK, slot);
        def.displayName      = data.displayName();
        def.sourcePrompt     = prompt;
        def.texturePath      = "conjure:block/block_slot_" + slot;
        def.behaviorScriptId = "";
        def.strings.put("description", data.description());

        PipelineSupport.commit(def);

        feedback.accept("Conjured '" + data.displayName() + "' → block slot #" + slot
                + ". Try: /give @s conjure:block_slot_" + slot);
    }
}
