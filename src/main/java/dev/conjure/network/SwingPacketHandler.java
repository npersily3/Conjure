package dev.conjure.network;

import com.mojang.logging.LogUtils;
import dev.conjure.Conjure;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.item.ConjureItem;
import dev.conjure.script.ScriptContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * Registers the {@link SwingPayload} and handles it on the server side.
 *
 * <p>Uses {@code @EventBusSubscriber(Bus.MOD)} so this class self-registers
 * into the NeoForge payload system without any changes to {@code Conjure.java}.
 *
 * <p>On receipt of a {@link SwingPayload} the handler checks that the player is
 * holding a configured {@link ConjureItem} with a behavior script, then runs that
 * script with a no-target {@link ScriptContext} whose trigger is {@code "swing"}.
 */
@EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SwingPacketHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SwingPacketHandler() {}

    /** Called during mod setup — registers the swing payload with the NeoForge network layer. */
    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
             .playToServer(SwingPayload.TYPE, SwingPayload.CODEC, SwingPacketHandler::handleSwing);
    }

    /**
     * Server-side handler for {@link SwingPayload}.
     *
     * <p>Validates that:
     * <ul>
     *   <li>The sender is a {@link ServerPlayer} (always true in the play phase, but checked for safety).</li>
     *   <li>The main-hand item is a configured {@link ConjureItem} with a behavior script.</li>
     * </ul>
     * Then runs the item's behavior script with trigger {@code "swing"} and no block/entity target.
     */
    private static void handleSwing(SwingPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer)) return;

        // context.player() is guaranteed to be on the main thread by NeoForge's default
        // payload handler wrapping, so we can touch world state directly.
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(held.getItem() instanceof ConjureItem conjureItem)) return;

        SlotDefinition def = conjureItem.slotDef();
        if (!def.configured || def.behaviorScriptId == null || def.behaviorScriptId.isBlank()) return;

        ScriptContext ctx = new ScriptContext(
                player.level(), player, InteractionHand.MAIN_HAND, "swing");

        LOGGER.debug("[Conjure] swing trigger for item slot, scriptId='{}'", def.behaviorScriptId);
        conjureItem.runBehavior(player, ctx);
    }
}
