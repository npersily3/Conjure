package dev.conjure.registry;

import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.neoforged.bus.api.IEventBus;

/**
 * Structure pool registry — 100 pre-allocated {@link SlotKind#STRUCTURE} slots.
 *
 * <h2>What register(IEventBus) now does</h2>
 * <p>The old worldgen-StructureType / ConjureStructure path has been removed entirely
 * (those files no longer exist). {@code register(modBus)} now does real work:
 * it calls {@link SlotRegistry#init(SlotKind, int)} for every index 0..STRUCTURE_POOL-1
 * so that all 100 structure slots are present in the registry at startup and can be
 * persisted, listed, and placed via {@code /conjure place <index>}.
 * The {@code modBus} parameter is kept so the signature stays compatible with the
 * {@code Conjure.java} call-site (no other bus listeners are registered here).
 *
 * <h2>Note for parent (Conjure.java log line)</h2>
 * <p>The existing startup log in {@code Conjure.java} already references
 * {@code ConjureStructures.STRUCTURE_POOL} — that constant still exists and equals 100,
 * so the log line continues to compile and prints the correct value without any edits.
 * The parent does NOT need to change the log line.
 *
 * <h2>Note for parent (GenerationService wiring)</h2>
 * <p>The parent must register {@code StructurePipeline} in {@code GenerationService.PIPELINES}:
 * <pre>
 *   PIPELINES.put(SlotKind.STRUCTURE, new StructurePipeline());
 * </pre>
 * No changes to {@code Conjure.java} are required beyond that wiring.
 */
public final class ConjureStructures {

    /** Total number of structure slots pre-allocated in the slot registry. */
    public static final int STRUCTURE_POOL = 100;

    /**
     * Initialises all {@value #STRUCTURE_POOL} structure slots in the {@link SlotRegistry}.
     * Must be called during mod construction (from {@code Conjure.java}) so slots are ready
     * before any command or generation runs.
     *
     * @param modBus the mod event bus (accepted for API compatibility; no bus listeners here)
     */
    public static void register(IEventBus modBus) {
        for (int i = 0; i < STRUCTURE_POOL; i++) {
            SlotRegistry.init(SlotKind.STRUCTURE, i);
        }
    }

    private ConjureStructures() {}
}
