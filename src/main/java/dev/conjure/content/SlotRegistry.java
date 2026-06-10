package dev.conjure.content;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store mapping each pre-registered slot to its current {@link SlotDefinition}.
 *
 * <p>This is the bridge between the frozen JVM registries (which can never grow) and the
 * AI layer (which produces new content at runtime). The item/block/fluid/entity classes
 * registered at startup are thin shells; their actual identity lives here and can change
 * any time without touching the JVM registry.
 *
 * <p>Thread-safety: the command/AI layer writes from a worker thread, the game reads from
 * the main thread, so the backing map is concurrent. Definitions themselves should be
 * swapped wholesale (build a new one, then put) rather than mutated in place once live.
 */
public final class SlotRegistry {

    private static final ConcurrentHashMap<Long, SlotDefinition> SLOTS = new ConcurrentHashMap<>();

    private SlotRegistry() {}

    private static long key(SlotKind kind, int index) {
        return ((long) kind.ordinal() << 32) | (index & 0xFFFFFFFFL);
    }

    /** Ensures an (empty) definition exists for a freshly registered slot. Called at startup. */
    public static SlotDefinition init(SlotKind kind, int index) {
        return SLOTS.computeIfAbsent(key(kind, index), k -> new SlotDefinition(kind, index));
    }

    public static SlotDefinition get(SlotKind kind, int index) {
        return SLOTS.computeIfAbsent(key(kind, index), k -> new SlotDefinition(kind, index));
    }

    /** Replaces a slot's definition with an AI-produced one and marks it configured. */
    public static void put(SlotDefinition def) {
        def.configured = true;
        SLOTS.put(key(def.kind, def.slotIndex), def);
    }

    /** Finds the lowest unconfigured slot of a kind, or -1 if the pool is exhausted. */
    public static int firstFree(SlotKind kind, int poolSize) {
        for (int i = 0; i < poolSize; i++) {
            if (!get(kind, i).configured) return i;
        }
        return -1;
    }
}
