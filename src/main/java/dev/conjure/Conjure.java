package dev.conjure;

import com.mojang.logging.LogUtils;
import dev.conjure.content.block.BlockArchetype;
import dev.conjure.registry.ConjureBlocks;
import dev.conjure.registry.ConjureEntities;
import dev.conjure.registry.ConjureFluids;
import dev.conjure.registry.ConjureItems;
import dev.conjure.registry.ConjureStructures;
import dev.conjure.registry.ConjureTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Conjure entrypoint. At startup it pre-registers the configurable content pools (the trick
 * that lets AI-authored content appear without a relaunch, up to the pool sizes) and wires up
 * config. Commands, the scripting runtime, the AI agent team, and the dynamic resource/data
 * packs are layered on from here in subsequent passes.
 */
@Mod(Conjure.MODID)
public final class Conjure {

    public static final String MODID = "conjure";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Conjure(IEventBus modBus, ModContainer container) {
        ConjureItems.ITEMS.register(modBus);   // 500 item shells + every block's BlockItem
        ConjureBlocks.BLOCKS.register(modBus); // 500 block shells across archetype buckets
        ConjureFluids.register(modBus);        // 32 fluid sets (source+flowing+block+bucket)
        ConjureEntities.register(modBus);      // 128 mob slots across 3 size buckets
        ConjureStructures.register(modBus);    // 1 StructureType for 100 datapack structure slots
        ConjureTabs.register(modBus);          // creative-inventory tab for generated content

        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        LOGGER.info(
                "Conjure online — pools: {} items, {} blocks ({} archetypes), {} fluids, {} entities ({}S/{}M/{}L), {} structure slots",
                ConjureItems.ITEM_POOL,
                BlockArchetype.totalPool(),
                BlockArchetype.values().length,
                ConjureFluids.FLUID_POOL,
                ConjureEntities.totalPool(),
                ConjureEntities.SMALL,
                ConjureEntities.MEDIUM,
                ConjureEntities.LARGE,
                ConjureStructures.STRUCTURE_POOL);
    }
}
