package dev.conjure.gen.pipeline;

import dev.conjure.Conjure;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextureKind;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.TextureAgent;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.gen.assets.FluidAssets;
import dev.conjure.registry.ConjureFluids;

import java.util.function.Consumer;

/**
 * Fluid generation pipeline: generates a still texture (reused/variant for flow), writes assets,
 * names the fluid, fills the {@link SlotKind#FLUID} slot, persists, and triggers a client reload.
 *
 * <p>The still texture is generated from the prompt; the flowing texture reuses the same ARGB
 * data for v1 simplicity. Both PNGs are written under {@code assets/conjure/textures/block/} so
 * the block atlas stitcher picks them up on the next {@code reloadResourcePacks()} call.
 */
public final class FluidPipeline implements GenerationPipeline {

    @Override
    public void run(String prompt, Consumer<String> feedback) throws Exception {
        int slot = SlotRegistry.firstFree(SlotKind.FLUID, ConjureFluids.FLUID_POOL);
        if (slot < 0) {
            feedback.accept("All " + ConjureFluids.FLUID_POOL + " fluid slots are full.");
            return;
        }
        runForSlot(slot, prompt, feedback);
    }

    /**
     * Runs the fluid pipeline against a specific slot index. Used for new generation and for
     * {@code /conjure regenerate fluid <index>} (which preserves the slot id so existing fluid
     * references stay valid).
     */
    @Override
    public void runForSlot(int slot, String prompt, Consumer<String> feedback) throws Exception {
        Conjure.LOGGER.info("Conjure: generating fluid slot {} via {} for prompt: {}",
                slot, ProviderFactory.text().id(), prompt);

        feedback.accept("§7[Conjure] Generating fluid texture…");
        int[][] stillArgb = new TextureAgent().generate(prompt + " liquid fluid", TextureKind.FLUID);
        // v1: flow texture reuses the still sprite (same pixel data)
        int[][] flowArgb = stillArgb;

        FluidAssets.writeStillTexture(slot, stillArgb);
        FluidAssets.writeFlowTexture(slot, flowArgb);
        // Bucket icon + model are hardcoded from a formula (tinted to the fluid colour), not AI.
        FluidAssets.writeBucketAssets(slot, stillArgb);

        feedback.accept("§7[Conjure] Generating fluid name…");
        DataAgent.Result data = new DataAgent().generate(prompt + " liquid", SlotKind.FLUID);

        SlotDefinition def = new SlotDefinition(SlotKind.FLUID, slot);
        def.displayName  = data.displayName();
        def.sourcePrompt = prompt;
        def.texturePath  = "conjure:block/fluid_still_slot_" + slot;
        def.strings.put("description",    data.description());
        def.strings.put("still_texture",  "conjure:block/fluid_still_slot_" + slot);
        def.strings.put("flow_texture",   "conjure:block/fluid_flow_slot_" + slot);

        PipelineSupport.commit(def);

        feedback.accept("Conjured '" + data.displayName() + "' → fluid slot #" + slot
                + ". Try: /give @s conjure:bucket_slot_" + slot);
    }
}
