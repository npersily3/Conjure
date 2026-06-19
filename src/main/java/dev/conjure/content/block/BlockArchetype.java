package dev.conjure.content.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Property buckets for the block pool.
 *
 * <p>A block's render layer (opaque vs. cutout vs. translucent), whether it occludes light,
 * and similar facets are effectively baked at JVM registration and cannot be flipped at
 * runtime. So instead of 500 identical blanks we pre-register a *mix* of archetypes, and the
 * orchestrator assigns an AI request to the nearest-fitting bucket. Texture, behavior,
 * display name and collision shape stay runtime-configurable on top of the chosen archetype.
 */
public enum BlockArchetype {

    /** Standard full opaque cube. The workhorse bucket. */
    SOLID(200, () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .sound(SoundType.STONE)),

    /** Non-occluding, see-through (glass / ice / crystal styled). Uses a cutout/translucent model. */
    TRANSPARENT(80, () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NONE)
            .strength(0.6f)
            .sound(SoundType.GLASS)
            .noOcclusion()),

    /** Full strength block intended for non-full / custom collision shapes set at runtime. */
    CUSTOM_SHAPE(80, () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(1.5f)
            .sound(SoundType.WOOD)
            .noOcclusion()
            .dynamicShape()),

    /** Light-emitting block. Light level is read from the slot definition via the lightLevel lambda. */
    LIGHT(80, () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .strength(0.3f)
            .sound(SoundType.GLASS)
            .lightLevel(state -> 15)),

    /** "Machine" block — opaque, sturdy, intended to back a BlockEntity for tick logic later. */
    MACHINE(60, () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.5f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .pushReaction(PushReaction.BLOCK)),

    /**
     * Stateful block backed by {@link ConjureActivatableBlock}: carries a runtime-toggleable
     * {@code active} on/off state (doors, lamps, switches, safes). Appended LAST so adding it
     * never shifts the slot indices of earlier archetypes (which persisted blocks reference).
     * Non-occluding so "open" models with gaps render correctly; light tracks {@code active}.
     */
    ACTIVATABLE(80, () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.5f)
            .sound(SoundType.METAL)
            .noOcclusion()
            .lightLevel(state -> state.getBlock() instanceof ConjureActivatableBlock b
                    ? b.activeLight(state) : 0));

    public interface PropFactory {
        BlockBehaviour.Properties create();
    }

    public final int count;
    private final PropFactory factory;

    BlockArchetype(int count, PropFactory factory) {
        this.count = count;
        this.factory = factory;
    }

    public BlockBehaviour.Properties newProperties() {
        return factory.create();
    }

    /** Total pool size across all archetypes (== 500). */
    public static int totalPool() {
        int total = 0;
        for (BlockArchetype a : values()) total += a.count;
        return total;
    }
}
