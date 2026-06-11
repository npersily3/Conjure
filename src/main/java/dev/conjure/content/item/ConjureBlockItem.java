package dev.conjure.content.item;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.block.ConjureBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A {@link BlockItem} whose display name is resolved at runtime from the backing
 * {@link SlotDefinition}, matching the pattern used by {@link ConjureItem}.
 *
 * <p>One {@link ConjureBlockItem} is registered per block slot in {@link dev.conjure.registry.ConjureBlocks},
 * replacing the previous plain {@code BlockItem} so that block-items show AI-generated names
 * rather than the internal {@code block_slot_N} translation key.
 */
public class ConjureBlockItem extends BlockItem {

    private final int slotIndex;

    public ConjureBlockItem(ConjureBlock block, int slotIndex, Item.Properties properties) {
        super(block, properties);
        this.slotIndex = slotIndex;
    }

    @Override
    public Component getName(ItemStack stack) {
        SlotDefinition d = SlotRegistry.get(SlotKind.BLOCK, slotIndex);
        return Component.literal(d.configured ? d.displayName : "Empty Block Slot #" + slotIndex);
    }
}
