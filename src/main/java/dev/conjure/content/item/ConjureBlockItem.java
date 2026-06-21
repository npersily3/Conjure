package dev.conjure.content.item;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * A {@link BlockItem} whose display name is resolved at runtime from the backing
 * {@link SlotDefinition}, matching the pattern used by {@link ConjureItem}.
 *
 * <p>One {@link ConjureBlockItem} is registered per block slot. The {@link SlotKind} parameter
 * lets the same class back the cube pool ({@link SlotKind#BLOCK}) and the shaped variant pools
 * ({@link SlotKind#SLAB}/{@link SlotKind#STAIRS}/{@link SlotKind#WALL}) — so block-items show the
 * AI-generated name rather than the internal {@code *_slot_N} translation key.
 */
public class ConjureBlockItem extends BlockItem {

    private final SlotKind kind;
    private final int slotIndex;

    public ConjureBlockItem(Block block, SlotKind kind, int slotIndex, Item.Properties properties) {
        super(block, properties);
        this.kind = kind;
        this.slotIndex = slotIndex;
    }

    /** The live slot definition behind this block-item (used by behavior scripts for cross-object reads). */
    public SlotDefinition slotDef() {
        return SlotRegistry.get(kind, slotIndex);
    }

    @Override
    public Component getName(ItemStack stack) {
        SlotDefinition d = SlotRegistry.get(kind, slotIndex);
        return Component.literal(d.configured ? d.displayName : "Empty " + kind + " Slot #" + slotIndex);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        SlotDefinition d = SlotRegistry.get(kind, slotIndex);
        String desc = d.str("description", "");
        if (!desc.isBlank()) {
            tooltip.add(Component.literal(desc).withStyle(ChatFormatting.GRAY));
        }
        dev.conjure.content.IntentTooltip.append(d, tooltip);
    }
}
