package dev.conjure.registry;

import net.neoforged.bus.api.IEventBus;

/**
 * Entity (mob) pool — SCAFFOLD.
 *
 * <p>An {@code EntityType}'s hitbox ({@code EntityDimensions}) and tracking range are fixed at
 * registration, so the pool is split into size buckets. Attributes (health/speed/damage), AI
 * goals and drops are all runtime-configurable from the slot definition. Appearance is the
 * catch: vanilla entity models are Java, not data, so runtime-designed creatures need either
 * texture-swaps on a fixed set of rigs or a data-driven model library (GeckoLib).
 *
 * <p>Next implementation pass: pre-register an {@code EntityType} pool backed by a scriptable
 * {@code PathfinderMob} subclass, register attribute suppliers, and wire GeckoLib renderers.
 */
public final class ConjureEntities {

    public static final int SMALL = 48;   // ~0.6 wide hitbox
    public static final int MEDIUM = 48;  // ~0.9 wide
    public static final int LARGE = 32;   // ~1.4 wide

    public static int totalPool() {
        return SMALL + MEDIUM + LARGE;
    }

    public static void register(IEventBus modBus) {
        // TODO(entities): register EntityTypes per size bucket + attribute suppliers + renderers.
    }

    private ConjureEntities() {}
}
