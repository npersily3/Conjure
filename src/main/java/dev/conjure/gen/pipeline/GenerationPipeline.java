package dev.conjure.gen.pipeline;

import java.util.function.Consumer;

/**
 * One content-kind's end-to-end generation pipeline (item, block, fluid, entity, structure).
 *
 * <p>Implementations own the whole flow for their kind: allocate a free slot from their pool,
 * call the relevant AI agents, write assets, fill + persist the {@link dev.conjure.content.SlotDefinition},
 * and trigger a client reload. They report progress and the final result through {@code feedback}.
 *
 * <p>{@link dev.conjure.gen.GenerationService} maps a {@link dev.conjure.content.SlotKind} to its
 * pipeline and invokes {@link #run} on the shared generation thread. Shared helpers live in
 * {@link PipelineSupport}. New kinds are added by implementing this interface and registering the
 * implementation in {@code GenerationService} — no edits to existing pipelines.
 */
public interface GenerationPipeline {

    /**
     * Allocates a slot and runs the full pipeline for {@code prompt}. Must report a clear message
     * via {@code feedback} on success, pool-exhaustion, or a handled early-out. Thrown exceptions
     * are logged and surfaced by the caller.
     */
    void run(String prompt, Consumer<String> feedback) throws Exception;

    /**
     * Re-runs the pipeline against a specific, already-allocated slot index instead of allocating
     * a fresh one — preserving the slot's JVM registry id so existing item stacks / placed blocks
     * stay valid. Used by {@code /conjure regenerate} (and {@code /conjure edit} for items).
     *
     * <p>Default throws {@link UnsupportedOperationException}; each concrete pipeline overrides it.
     * This keeps the interface back-compatible so adding a pipeline never forces a regenerate impl.
     */
    default void runForSlot(int slot, String prompt, Consumer<String> feedback) throws Exception {
        throw new UnsupportedOperationException("regenerate not supported for this pipeline");
    }
}
