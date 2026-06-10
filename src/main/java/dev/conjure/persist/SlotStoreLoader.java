package dev.conjure.persist;

import dev.conjure.Conjure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Hooks the server-startup lifecycle to restore persisted {@link dev.conjure.content.SlotDefinition}s
 * from disk into the {@link dev.conjure.content.SlotRegistry} before any player can interact with
 * slots.
 *
 * <p><b>Bus choice:</b> {@link ServerStartingEvent} fires on the NeoForge game bus
 * (i.e. {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}). The default
 * {@link EventBusSubscriber} annotation with no explicit {@code bus} parameter subscribes to
 * {@code Bus.GAME}, which is exactly the NeoForge game event bus. This event fires after the
 * server has booted its registries but before any world ticks or command processing, making it
 * the correct insertion point for loading persistent content metadata.
 *
 * <p>Texture / model PNGs and behavior {@code .js} files are already present on disk from the
 * previous session, so the resource-pack layer picks them up on its own without an explicit
 * reload here.
 */
@EventBusSubscriber(modid = Conjure.MODID)
public final class SlotStoreLoader {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Conjure.LOGGER.info("Conjure SlotStore: loading persisted slot definitions…");
        SlotStore.loadAll();
    }

    private SlotStoreLoader() {}
}
