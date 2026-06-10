package dev.conjure.content.block;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A pre-registered block shell backed by a {@link SlotDefinition}. The archetype it was built
 * from (its {@link BlockBehaviour.Properties}) is fixed; texture, name, behavior and collision
 * shape are resolved at runtime. The matching {@link net.minecraft.world.item.BlockItem} is
 * registered alongside it in the item pool.
 */
public class ConjureBlock extends Block {

    public final BlockArchetype archetype;
    private final int slotIndex;

    public ConjureBlock(BlockArchetype archetype, int slotIndex, BlockBehaviour.Properties properties) {
        super(properties);
        this.archetype = archetype;
        this.slotIndex = slotIndex;
    }

    public SlotDefinition def() {
        return SlotRegistry.get(SlotKind.BLOCK, slotIndex);
    }

    // Collision-shape, interaction and tick overrides will read def() and delegate to the
    // scripting runtime in a later pass. Render type (cutout/translucent) for TRANSPARENT/
    // CUSTOM_SHAPE archetypes is driven by the generated block model JSON ("render_type").
}
