package dev.conjure.content.structure;

/**
 * Holds the lazy reference to the registered {@link net.minecraft.world.level.levelgen.structure.StructureType}
 * so that {@link ConjureStructure#type()} can resolve it without a hard dependency on the
 * {@code registry} package.  Populated by {@link dev.conjure.registry.ConjureStructures}
 * during static initialisation.
 *
 * <p>This indirection keeps the content class free of registry-layer imports.
 */
public final class ConjureStructures {

    /** Set by {@code registry.ConjureStructures} before any structure instance is created. */
    public static net.neoforged.neoforge.registries.DeferredHolder<
            net.minecraft.world.level.levelgen.structure.StructureType<?>,
            net.minecraft.world.level.levelgen.structure.StructureType<ConjureStructure>>
            CONJURE_STRUCTURE_TYPE_REF = null;

    private ConjureStructures() {}
}
