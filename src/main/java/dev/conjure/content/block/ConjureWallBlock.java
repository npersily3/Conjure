package dev.conjure.content.block;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A pre-registered wall shell backed by a {@link SlotKind#WALL} slot. Shape/behavior is the vanilla
 * wall; only texture and display name resolve at runtime. Spawned as part of a base block's
 * material family — never prompted directly.
 */
public class ConjureWallBlock extends WallBlock {

    public final int slotIndex;

    public ConjureWallBlock(int slotIndex, BlockBehaviour.Properties properties) {
        super(properties);
        this.slotIndex = slotIndex;
    }

    @Override
    public MutableComponent getName() {
        SlotDefinition d = SlotRegistry.get(SlotKind.WALL, slotIndex);
        return Component.literal(d.configured ? d.displayName : "Empty Wall Slot #" + slotIndex);
    }
}
