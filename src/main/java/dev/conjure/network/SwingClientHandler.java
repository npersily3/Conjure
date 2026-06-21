package dev.conjure.network;

import dev.conjure.Conjure;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.item.ConjureItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-only listener that detects a left-click swing in empty air while holding
 * a configured {@link ConjureItem} and sends a {@link SwingPayload} to the server.
 *
 * <p>{@link PlayerInteractEvent.LeftClickEmpty} fires on the client side when the
 * player's attack swing hits nothing (no entity, no block in range). That is the
 * canonical NeoForge signal for an empty-air left-click.
 *
 * <p>Uses {@code @EventBusSubscriber(Bus.GAME, Dist.CLIENT)} to subscribe to the
 * main NeoForge game event bus on the client only — no changes needed in {@code Conjure.java}.
 */
@EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SwingClientHandler {

    private SwingClientHandler() {}

    /**
     * Called client-side when the player swings in empty air.
     * If the main-hand item is a configured {@link ConjureItem} with a behavior script,
     * sends a {@link SwingPayload} to the server to run the "swing" trigger.
     */
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        // LeftClickEmpty always uses MAIN_HAND, but be explicit.
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(held.getItem() instanceof ConjureItem conjureItem)) return;

        SlotDefinition def = conjureItem.slotDef();
        if (!def.configured || def.behaviorScriptId == null || def.behaviorScriptId.isBlank()) return;

        // Guard: only send in the play phase (not in menus or loading screens).
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        PacketDistributor.sendToServer(new SwingPayload());
    }
}
