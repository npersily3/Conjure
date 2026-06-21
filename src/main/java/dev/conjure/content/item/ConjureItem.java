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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
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

    /** The live slot definition behind this item (used by behavior scripts for cross-object reads). */
    public SlotDefinition slotDef() {
        return def();
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

    /** Right-click in the air: runs the behavior script with trigger "use". */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        SlotDefinition d = def();
        if (!d.configured) {
            return super.use(level, player, hand);
        }
        if (level.isClientSide || !hasScript(d)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        boolean ok = runBehavior(player, new ScriptContext(level, player, hand, "use"));
        ItemStack held = player.getItemInHand(hand);
        return ok ? InteractionResultHolder.sidedSuccess(held, false)
                  : InteractionResultHolder.fail(held);
    }

    /**
     * Right-click on a block: runs the behavior script with trigger "useOnBlock".
     * Returns CONSUME so the interaction does not also fall through to {@link #use}.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        SlotDefinition d = def();
        if (!d.configured || player == null || !hasScript(d)) {
            return super.useOn(context);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ScriptContext ctx = new ScriptContext(level, player, context.getHand(), "useOnBlock",
                context.getClickedPos(), context.getClickedFace());
        return runBehavior(player, ctx) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** Hit a mob: runs the behavior script with trigger "hitEntity". */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        SlotDefinition d = def();
        if (attacker instanceof Player player && !attacker.level().isClientSide && hasScript(d)) {
            runBehavior(player, new ScriptContext(attacker.level(), player, InteractionHand.MAIN_HAND,
                    "hitEntity", target));
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    private static boolean hasScript(SlotDefinition d) {
        return d.configured && d.behaviorScriptId != null && !d.behaviorScriptId.isBlank();
    }

    /**
     * Runs the slot's behavior script with {@code ctx}; logs + surfaces any error, returns success.
     * Public so the swing packet handler in {@code dev.conjure.network} can invoke it.
     */
    public boolean runBehavior(Player player, ScriptContext ctx) {
        String scriptId = def().behaviorScriptId;
        try {
            ScriptRuntime.get().run(scriptId, ctx);
            return true;
        } catch (ScriptException e) {
            LOGGER.error("[Conjure] Script error for item slot {} (scriptId='{}'): {}",
                    slotIndex, scriptId, e.getMessage(), e);
            player.displayClientMessage(
                    Component.literal("[Conjure] Script error: " + e.getMessage()), false);
            return false;
        }
    }
}
