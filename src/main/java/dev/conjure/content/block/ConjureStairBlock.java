package dev.conjure.content.block;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A pre-registered stair shell backed by a {@link SlotKind#STAIRS} slot. {@link StairBlock} needs a
 * "base state" for incidental host behavior (sounds, etc.); we feed it a neutral
 * {@code minecraft:stone} state since the slot supplies the real texture and name at runtime.
 * Spawned as part of a base block's material family — never prompted directly.
 */
public class ConjureStairBlock extends StairBlock {

    public final int slotIndex;

    public ConjureStairBlock(int slotIndex, BlockBehaviour.Properties properties) {
        super(Blocks.STONE.defaultBlockState(), properties);
        this.slotIndex = slotIndex;
    }

    @Override
    public MutableComponent getName() {
        SlotDefinition d = SlotRegistry.get(SlotKind.STAIRS, slotIndex);
        return Component.literal(d.configured ? d.displayName : "Empty Stairs Slot #" + slotIndex);
    }
}
