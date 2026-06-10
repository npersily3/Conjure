package dev.conjure.client;

import dev.conjure.Conjure;
import dev.conjure.gen.DynamicPackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Registers the runtime-generated resource pack as an always-active, top-priority client pack.
 * Discovered once at startup; thereafter {@code reloadResourcePacks()} re-reads its folder, so
 * files written by the generator between reloads are picked up.
 */
@EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ConjureClientPack {

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        Path root = DynamicPackManager.root();
        PackLocationInfo location = new PackLocationInfo(
                "conjure/generated",
                Component.literal("Conjure Generated"),
                PackSource.BUILT_IN,
                Optional.empty());

        Pack.ResourcesSupplier supplier =
                BuiltInPackSource.fromName(info -> new PathPackResources(info, root));

        Pack pack = Pack.readMetaAndCreate(
                location,
                supplier,
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, true));

        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        } else {
            Conjure.LOGGER.error("Conjure generated pack failed to load (bad pack.mcmeta?)");
        }
    }

    private ConjureClientPack() {}
}
