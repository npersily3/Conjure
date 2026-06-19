package dev.conjure.content.item;

import com.mojang.logging.LogUtils;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.script.ScriptContext;
import dev.conjure.script.ScriptException;
import dev.conjure.script.ScriptRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.List;

/**
 * A pre-registered item shell whose identity (name, behavior) is resolved at runtime from
 * the {@link SlotRegistry}. One instance is registered per item slot at startup; the AI
 * never creates new items, it only fills in the {@link SlotDefinition} behind one of these.
 */
public class ConjureItem extends Item {

    private static final Logger LOGGER = LogUtils.getLogger();

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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        String desc = def().str("description", "");
        if (!desc.isBlank()) {
            tooltip.add(Component.literal(desc).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        SlotDefinition d = def();

        if (!d.configured) {
            return super.use(level, player, hand);
        }

        // Only run the script server-side; the client side is a no-op pass.
        if (level.isClientSide) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        String scriptId = d.behaviorScriptId;
        if (scriptId == null || scriptId.isBlank()) {
            // Configured slot but no script assigned yet — nothing to do.
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        ScriptContext ctx = new ScriptContext(level, player, hand);
        try {
            ScriptRuntime.get().run(scriptId, ctx);
        } catch (ScriptException e) {
            LOGGER.error("[Conjure] Script error for item slot {} (scriptId='{}'): {}",
                    slotIndex, scriptId, e.getMessage(), e);
            player.displayClientMessage(
                    Component.literal("[Conjure] Script error: " + e.getMessage()),
                    false
            );
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
