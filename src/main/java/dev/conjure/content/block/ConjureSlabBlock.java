package dev.conjure.content.block;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A pre-registered slab shell backed by a {@link SlotKind#SLAB} slot. Like {@link ConjureBlock}
 * the shape/behavior is fixed (a vanilla slab); only texture and display name resolve at runtime.
 * Spawned as part of a base block's material family — never prompted directly.
 */
public class ConjureSlabBlock extends SlabBlock {

    public final int slotIndex;

    public ConjureSlabBlock(int slotIndex, BlockBehaviour.Properties properties) {
        super(properties);
        this.slotIndex = slotIndex;
    }

    @Override
    public MutableComponent getName() {
        SlotDefinition d = SlotRegistry.get(SlotKind.SLAB, slotIndex);
        return Component.literal(d.configured ? d.displayName : "Empty Slab Slot #" + slotIndex);
    }
}
