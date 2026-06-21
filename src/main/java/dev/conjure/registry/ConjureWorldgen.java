package dev.conjure.registry;

import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.neoforged.bus.api.IEventBus;

/**
 * Worldgen pool registry — 32 pre-allocated worldgen slots.
 *
 * <h2>Design note: why no DeferredRegister for ConfiguredFeature / PlacedFeature</h2>
 * <p>In NeoForge 1.21.1, {@code Registries.CONFIGURED_FEATURE} and {@code Registries.PLACED_FEATURE}
 * are <em>datapack registries</em> (bootstrap / dynamic registries).  They are loaded from JSON
 * during world load — not from Java {@code DeferredRegister} entries.  A {@code DeferredRegister}
 * can only be used with {@code BuiltInRegistries}; attempting to create one for a datapack registry
 * does not compile (no matching {@code create} overload accepts a datapack registry key with a live
 * {@code IEventBus}).
 *
 * <p>The correct runtime approach (and the one Conjure already uses for recipes) is to write valid
 * JSON files into the dynamic data pack directory ({@code <gamedir>/conjure/generated/}) so the
 * integrated server picks them up on the next world load / {@code reloadResources}.  This is exactly
 * what {@link dev.conjure.gen.pipeline.WorldgenPipeline} does.
 *
 * <h2>What this class does do</h2>
 * <p>Initialises {@value #WORLDGEN_POOL} worldgen slots in the {@link SlotRegistry} using
 * {@link SlotKind#BLOCK} kind (worldgen always targets an existing conjure block).  Slot indices
 * are independent of the {@link ConjureBlocks} pool: the registry holds a separate range under the
 * synthetic key prefix "worldgen_slot_N" tracked only in the strings map.  The actual BLOCK slot
 * index that owns the worldgen config is stored at {@code strings.get("worldgenBlockSlot")}.
 *
 * <p>The {@code modBus} parameter is accepted for call-site API parity with other pools; no bus
 * listeners are registered here.
 */
public final class ConjureWorldgen {

    /** Total number of worldgen configuration slots pre-allocated in the slot registry. */
    public static final int WORLDGEN_POOL = 32;

    /**
     * Initialises all {@value #WORLDGEN_POOL} worldgen tracking slots in the {@link SlotRegistry}.
     * Must be called during mod construction (from {@code Conjure.java}) so slots are ready before
     * any command or generation runs.
     *
     * @param modBus the mod event bus (accepted for API compatibility; no bus listeners here)
     */
    public static void register(IEventBus modBus) {
        for (int i = 0; i < WORLDGEN_POOL; i++) {
            SlotRegistry.init(SlotKind.BLOCK, worldgenSlotIndex(i));
        }
    }

    /**
     * Returns the logical slot-registry index for worldgen slot {@code n}.
     * These are placed above the full block pool to avoid collisions:
     * {@code blockPoolSize + n}.
     *
     * @param n worldgen pool position (0 ≤ n < {@link #WORLDGEN_POOL})
     */
    public static int worldgenSlotIndex(int n) {
        // Place worldgen tracking slots after the entire block pool
        // (ConjureBlocks total = BlockArchetype.totalPool(), typically 500).
        // We use a high offset (1000) to be safe regardless of pool size.
        return 1000 + n;
    }

    /**
     * Returns the first unconfigured worldgen slot index (in the worldgen pool),
     * or {@code -1} if all {@value #WORLDGEN_POOL} slots are occupied.
     */
    public static int firstFree() {
        for (int i = 0; i < WORLDGEN_POOL; i++) {
            if (!SlotRegistry.get(SlotKind.BLOCK, worldgenSlotIndex(i)).configured) {
                return i;
            }
        }
        return -1;
    }

    private ConjureWorldgen() {}
}
