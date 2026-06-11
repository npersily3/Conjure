package dev.conjure.content.fluid;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * A pre-registered liquid-block shell whose display name is resolved at runtime from
 * the {@link SlotRegistry}. One instance is registered per fluid slot at startup.
 *
 * <p>{@link #getName()} is the block-level override (no ItemStack argument); the bucket's
 * display name is handled separately by {@link ConjureBucketItem#getName}.
 */
public class ConjureLiquidBlock extends LiquidBlock {

    private final int slotIndex;

    public ConjureLiquidBlock(int slotIndex, FlowingFluid fluid, BlockBehaviour.Properties properties) {
        super(fluid, properties);
        this.slotIndex = slotIndex;
    }

    private SlotDefinition def() {
        return SlotRegistry.get(SlotKind.FLUID, slotIndex);
    }

    @Override
    public MutableComponent getName() {
        SlotDefinition d = def();
        if (d.configured) {
            return Component.literal(d.displayName);
        }
        return Component.literal("Empty Fluid Block Slot #" + slotIndex);
    }
}
