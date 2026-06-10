package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.content.structure.ConjureStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structure pool registry — 100 pre-allocated slots.
 *
 * <h2>Architecture</h2>
 * <p>In Minecraft 1.21+ (NeoForge), {@link net.minecraft.world.level.levelgen.structure.Structure}
 * entries live in a <em>datapack registry</em> ({@code Registries.STRUCTURE}) and are loaded
 * from JSON, not Java code.  Only the <em>type</em> of a structure
 * ({@link StructureType}) lives in the built-in JVM registry.  Therefore this class:
 *
 * <ol>
 *   <li>Registers a single {@link StructureType} — {@code conjure:conjure_structure} — whose
 *       codec describes a {@link ConjureStructure} (a minimal, do-nothing placeholder).</li>
 *   <li>Documents the naming convention for the 100 datapack entries: each runtime-generated
 *       structure JSON should use the type {@code conjure:conjure_structure} and carry the
 *       resource key {@code conjure:structure_slot_0} … {@code conjure:structure_slot_99}.</li>
 * </ol>
 *
 * <h2>Content lifecycle</h2>
 * <p>The Conjure AI layer generates a dynamic datapack at runtime that contains up to
 * {@value #STRUCTURE_POOL} structure JSON files (one per slot, on demand).  Each JSON
 * references real biome tags, jigsaw template pools, and placement config.  Because
 * structures are resolved during world generation, only <em>newly generated chunks</em>
 * are affected; already-explored terrain is never modified.
 *
 * <h2>Why no DeferredRegister for STRUCTURE?</h2>
 * <p>{@code Registries.STRUCTURE} is a datapack (world-gen) registry whose entries are
 * never registered by Java code — they come from JSON.  Attempting to use a
 * {@link DeferredRegister} for it would produce empty holders that the codec system
 * would reject.  The type registry ({@code BuiltInRegistries.STRUCTURE_TYPE}) is the
 * correct Java-side registration point.
 *
 * <p>TODO(structures): When the runtime datapack builder (gen package) is complete,
 * generate 100 structure JSON files under
 * {@code data/conjure/worldgen/structure/structure_slot_N.json} that reference
 * {@code "type": "conjure:conjure_structure"} and appropriate biome lists /
 * jigsaw start pools.
 */
public final class ConjureStructures {

    /** Total number of structure slots reserved in the naming convention. */
    public static final int STRUCTURE_POOL = 100;

    // ------------------------------------------------------------------ registries

    /**
     * {@link StructureType} registry — the only built-in Java-side registration needed.
     * We register ONE type that all 100 datapack entries share via
     * {@code "type": "conjure:conjure_structure"}.
     */
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Conjure.MODID);

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final DeferredHolder<StructureType<?>, StructureType<ConjureStructure>>
            CONJURE_STRUCTURE_TYPE = (DeferredHolder<StructureType<?>, StructureType<ConjureStructure>>)
            (DeferredHolder<?, ?>) STRUCTURE_TYPES.register("conjure_structure",
                    (java.util.function.Supplier<StructureType<ConjureStructure>>) () ->
                            (StructureType<ConjureStructure>) () -> ConjureStructure.CODEC);

    // ------------------------------------------------------------------ registration

    public static void register(IEventBus modBus) {
        // Publish the type bridge reference so ConjureStructure.type() can resolve.
        dev.conjure.content.structure.ConjureStructures.CONJURE_STRUCTURE_TYPE_REF = CONJURE_STRUCTURE_TYPE;

        STRUCTURE_TYPES.register(modBus);
    }

    private ConjureStructures() {}
}
