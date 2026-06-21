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
import dev.conjure.gen.pipeline.WorldgenPipeline;
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

    /** Used for mod-economy "resource" pieces (an ore that also spawns in the world). */
    private static final WorldgenPipeline WORLDGEN_PIPELINE = new WorldgenPipeline();

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
            GenerationStatus.begin();
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
            } finally {
                GenerationStatus.end();
            }
        });
    }

    /**
     * Generates one piece of a whole-mod economy <em>synchronously</em> (blocks until done) with a
     * {@link GenerationContext} carrying the resolved ingredient ids, and returns the
     * {@code conjure:...} id the pipeline created so the next piece can be crafted from it. Runs on
     * the same single generation thread, so it stays serial with everything else.
     *
     * @param worldgenResource true for a "resource" piece — generate an ore that also spawns in world
     * @return the created content id, or {@code null} if the piece failed
     */
    public static String generateForMod(String prompt, SlotKind kindHint, boolean worldgenResource,
                                        java.util.List<String> ingredientIds, boolean smelt,
                                        Consumer<String> feedback) {
        try {
            return POOL.submit(() -> {
                GenerationStatus.begin();
                GenerationContext.set(new GenerationContext(ingredientIds, smelt));
                try {
                    SlotKind kind = kindHint != null ? kindHint : new RouterAgent().classify(prompt);
                    GenerationPipeline pipeline = worldgenResource
                            ? WORLDGEN_PIPELINE : PIPELINES.getOrDefault(kind, ITEM_PIPELINE);
                    pipeline.run(prompt, feedback);
                    GenerationContext gc = GenerationContext.current();
                    return gc == null ? null : gc.createdId();
                } finally {
                    GenerationContext.clear();
                    GenerationStatus.end();
                }
            }).get();
        } catch (Exception e) {
            Conjure.LOGGER.error("Conjure mod-piece generation failed: {}", prompt, e);
            feedback.accept("Piece failed: " + PipelineSupport.describe(e));
            return null;
        }
    }

    /**
     * Re-runs the item pipeline for an existing item slot, preserving its index so existing item
     * stacks remain valid.
     */
    public static void regenerateItem(int slotIndex, String prompt, Consumer<String> feedback) {
        POOL.submit(() -> {
            GenerationStatus.begin();
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
            } finally {
                GenerationStatus.end();
            }
        });
    }

    /**
     * Re-runs the pipeline for {@code kind} against an existing slot, preserving its index so
     * content already placed in the world stays valid. Backs {@code /conjure regenerate}.
     */
    public static void regenerate(SlotKind kind, int slotIndex, String prompt, Consumer<String> feedback) {
        POOL.submit(() -> {
            GenerationStatus.begin();
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
            } finally {
                GenerationStatus.end();
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
