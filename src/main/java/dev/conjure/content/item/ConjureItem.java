package dev.conjure.content.item;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A pre-registered item shell whose identity (name, behavior) is resolved at runtime from
 * the {@link SlotRegistry}. One instance is registered per item slot at startup; the AI
 * never creates new items, it only fills in the {@link SlotDefinition} behind one of these.
 */
public class ConjureItem extends Item {

    private final int slotIndex;

    public ConjureItem(int slotIndex, Properties properties) {
        super(properties);
        this.slotIndex = slotIndex;
    }

    private SlotDefinition def() {
        return SlotRegistry.get(SlotKind.ITEM, slotIndex);
    }

    @Override
    public Component getName(ItemStack stack) {
        SlotDefinition d = def();
        return Component.literal(d.configured ? d.displayName : "Empty Item Slot #" + slotIndex);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        SlotDefinition d = def();
        if (!d.configured) {
            return super.use(level, player, hand);
        }
        // TODO(scripting): dispatch d.behaviorScriptId to the embedded Rhino runtime here.
        // For now this is the integration point where right-click behavior will be evaluated.
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
