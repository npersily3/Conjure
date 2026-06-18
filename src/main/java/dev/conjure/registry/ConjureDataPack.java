package dev.conjure.registry;

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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Registers the runtime-generated pack (same folder as {@link dev.conjure.client.ConjureClientPack},
 * but as a {@link PackType#SERVER_DATA} pack) so generated recipes under
 * {@code data/conjure/recipe/} load. Always-on (required + fixed), so it stays in the server's
 * selected datapacks and a {@code reloadResources} re-reads new recipe files live.
 *
 * <p>Common-side (no {@code Dist} filter): the integrated server in singleplayer needs it.
 */
@EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ConjureDataPack {

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;

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
                PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, true));

        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        } else {
            Conjure.LOGGER.error("Conjure generated datapack failed to load (bad pack.mcmeta?)");
        }
    }

    private ConjureDataPack() {}
}
