package dev.conjure.content.fluid;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * A pre-registered bucket shell whose display name is resolved at runtime from
 * the {@link SlotRegistry}. One instance is registered per fluid slot at startup;
 * the AI fills the {@link SlotDefinition} behind it — the bucket's name updates
 * automatically without any restart.
 */
public class ConjureBucketItem extends BucketItem {

    private final int slotIndex;

    public ConjureBucketItem(int slotIndex, Fluid fluid, Properties properties) {
        super(fluid, properties);
        this.slotIndex = slotIndex;
    }

    private SlotDefinition def() {
        return SlotRegistry.get(SlotKind.FLUID, slotIndex);
    }

    @Override
    public Component getName(ItemStack stack) {
        SlotDefinition d = def();
        if (d.configured) {
            return Component.literal(d.displayName + " Bucket");
        }
        return Component.literal("Empty Bucket Slot #" + slotIndex);
    }
}
