package dev.conjure.content.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Minimal placeholder Structure for the Conjure structure pool.
 *
 * <p><b>Design intent:</b> This class establishes the {@link StructureType} entry that the
 * JVM registry requires at startup.  Actual generation parameters — jigsaw template pools,
 * biome tags, structure sets, placements — are all data-driven and are supplied entirely
 * by a dynamic datapack that Conjure generates at runtime (see the {@code gen} package).
 * Because structures are purely data-driven in 1.21+, pre-registering the type here lets
 * Conjure use registry keys {@code conjure:structure_slot_0} … {@code conjure:structure_slot_99}
 * inside datapack JSON without those being unknown types.
 * These slots will only affect <em>newly generated chunks</em>; previously generated terrain is
 * never modified.
 *
 * <p>TODO(structures): Once the runtime datapack builder is complete, upgrade
 * {@link #findGenerationPoint} to read jigsaw pool references from the Conjure
 * structure-definition store and wire in proper placement/piece logic.
 */
public class ConjureStructure extends Structure {

    /** Codec registered under {@code conjure:conjure_structure} StructureType. */
    public static final MapCodec<ConjureStructure> CODEC = simpleCodec(ConjureStructure::new);

    public ConjureStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    /**
     * No-op generation point: this placeholder never places any pieces.
     * The real implementation is delegated to data-pack jigsaw pools.
     */
    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public StructureType<?> type() {
        // Resolved lazily via the content↔registry bridge in ConjureStructures.
        if (ConjureStructures.CONJURE_STRUCTURE_TYPE_REF != null) {
            return ConjureStructures.CONJURE_STRUCTURE_TYPE_REF.get();
        }
        throw new IllegalStateException("ConjureStructures.CONJURE_STRUCTURE_TYPE_REF not yet initialised");
    }
}
