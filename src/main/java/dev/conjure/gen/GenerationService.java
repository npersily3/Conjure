package dev.conjure.gen;

import dev.conjure.Conjure;
import dev.conjure.ai.agents.RouterAgent;
import dev.conjure.content.SlotKind;
import dev.conjure.gen.pipeline.BlockPipeline;
import dev.conjure.gen.pipeline.EntityPipeline;
import dev.conjure.gen.pipeline.FluidPipeline;
import dev.conjure.gen.pipeline.GenerationPipeline;
import dev.conjure.gen.pipeline.ItemPipeline;
import dev.conjure.gen.pipeline.PipelineSupport;
import dev.conjure.gen.pipeline.StructurePipeline;
import dev.conjure.content.block.BlockArchetype;
import dev.conjure.registry.ConjureEntities;
import dev.conjure.registry.ConjureFluids;
import dev.conjure.registry.ConjureItems;
import dev.conjure.registry.ConjureStructures;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Generation dispatcher behind {@code /conjure new <prompt>}.
 *
 * <p>A {@link RouterAgent} classifies the prompt into a {@link SlotKind}; the matching
 * {@link GenerationPipeline} (registered in {@link #PIPELINES}) then allocates a slot, runs its
 * agents, writes assets, persists, and reloads. Adding a content kind = implement a new pipeline
 * and register it here — existing pipelines are untouched.
 *
 * <p>All work runs on a single background thread so the game thread is never blocked; callers post
 * {@code feedback} back to the server thread themselves.
 */
public final class GenerationService {

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "conjure-gen");
        t.setDaemon(true);
        return t;
    });

    /** The item pipeline is also reused directly by {@link #regenerateItem}. */
    private static final ItemPipeline ITEM_PIPELINE = new ItemPipeline();

    /**
     * Kind → pipeline. All five kinds (ITEM, BLOCK, FLUID, ENTITY, STRUCTURE) are mapped; the
     * dispatcher reports a clear message for any unmapped kind.
     */
    private static final Map<SlotKind, GenerationPipeline> PIPELINES = new EnumMap<>(SlotKind.class);

    static {
        PIPELINES.put(SlotKind.ITEM, ITEM_PIPELINE);
        PIPELINES.put(SlotKind.BLOCK, new BlockPipeline());
        PIPELINES.put(SlotKind.FLUID, new FluidPipeline());
        PIPELINES.put(SlotKind.STRUCTURE, new StructurePipeline());
        PIPELINES.put(SlotKind.ENTITY, new EntityPipeline());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Single entry point: route {@code prompt} to a content kind and run that pipeline.
     *
     * @param prompt   user-supplied description
     * @param feedback progress + result callback (invoked on the generation thread)
     */
    public static void generate(String prompt, Consumer<String> feedback) {
        POOL.submit(() -> {
            try {
                feedback.accept("§7[Conjure] Deciding what to build…");
                SlotKind kind = new RouterAgent().classify(prompt);

                GenerationPipeline pipeline = PIPELINES.get(kind);
                if (pipeline == null) {
                    feedback.accept("§7[Conjure] " + kind + " generation isn't available yet.");
                    return;
                }
                feedback.accept("§7[Conjure] Interpreting this as a " + kind.name().toLowerCase() + ".");
                pipeline.run(prompt, feedback);
            } catch (Exception e) {
                Conjure.LOGGER.error("Conjure generation failed", e);
                feedback.accept("Generation failed: " + PipelineSupport.describe(e));
            }
        });
    }

    /**
     * Re-runs the item pipeline for an existing item slot, preserving its index so existing item
     * stacks remain valid.
     */
    public static void regenerateItem(int slotIndex, String prompt, Consumer<String> feedback) {
        POOL.submit(() -> {
            try {
                if (slotIndex < 0 || slotIndex >= ConjureItems.ITEM_POOL) {
                    feedback.accept("Invalid slot index " + slotIndex
                            + " (pool size: " + ConjureItems.ITEM_POOL + ").");
                    return;
                }
                ITEM_PIPELINE.runForSlot(slotIndex, prompt, feedback);
            } catch (Exception e) {
                Conjure.LOGGER.error("Conjure regeneration failed for slot {}", slotIndex, e);
                feedback.accept("Regeneration failed: " + PipelineSupport.describe(e));
            }
        });
    }

    /**
     * Re-runs the pipeline for {@code kind} against an existing slot, preserving its index so
     * content already placed in the world stays valid. Backs {@code /conjure regenerate}.
     */
    public static void regenerate(SlotKind kind, int slotIndex, String prompt, Consumer<String> feedback) {
        POOL.submit(() -> {
            try {
                GenerationPipeline pipeline = PIPELINES.get(kind);
                if (pipeline == null) {
                    feedback.accept("§7[Conjure] " + kind + " regeneration isn't available yet.");
                    return;
                }
                int pool = poolSize(kind);
                if (slotIndex < 0 || slotIndex >= pool) {
                    feedback.accept("Invalid " + kind.name().toLowerCase() + " slot index "
                            + slotIndex + " (pool size: " + pool + ").");
                    return;
                }
                pipeline.runForSlot(slotIndex, prompt, feedback);
            } catch (Exception e) {
                Conjure.LOGGER.error("Conjure regeneration failed for {} slot {}", kind, slotIndex, e);
                feedback.accept("Regeneration failed: " + PipelineSupport.describe(e));
            }
        });
    }

    /** Pre-registered pool size for a kind (used to bounds-check slot indices). */
    public static int poolSize(SlotKind kind) {
        return switch (kind) {
            case ITEM      -> ConjureItems.ITEM_POOL;
            case BLOCK     -> BlockArchetype.totalPool();
            case FLUID     -> ConjureFluids.FLUID_POOL;
            case ENTITY    -> ConjureEntities.totalPool();
            case STRUCTURE -> ConjureStructures.STRUCTURE_POOL;
            default        -> 0;
        };
    }

    private GenerationService() {}
}
