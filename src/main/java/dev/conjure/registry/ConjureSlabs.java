package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.block.ConjureSlabBlock;
import dev.conjure.content.item.ConjureBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The slab pool: {@value #SLAB_POOL} pre-registered {@link ConjureSlabBlock} shells, each paired
 * with a {@link ConjureBlockItem} in the item registry. Filled by material-family generation when a
 * base block expands into variants (see the block pipeline). Stone-like properties throughout.
 */
public final class ConjureSlabs {

    public static final int SLAB_POOL = 64;

    public static final DeferredRegister<Block> SLABS =
            DeferredRegister.create(Registries.BLOCK, Conjure.MODID);

    public static final List<DeferredHolder<Block, ConjureSlabBlock>> SLAB_SLOTS = new ArrayList<>();
    public static final List<DeferredHolder<Item, BlockItem>> SLAB_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < SLAB_POOL; i++) {
            final int slot = i;
            SlotRegistry.init(SlotKind.SLAB, slot);
            DeferredHolder<Block, ConjureSlabBlock> holder = SLABS.register(
                    "slab_slot_" + slot, () -> new ConjureSlabBlock(slot, props()));
            SLAB_SLOTS.add(holder);
            SLAB_ITEMS.add(ConjureItems.ITEMS.register("slab_slot_" + slot,
                    () -> new ConjureBlockItem(holder.get(), SlotKind.SLAB, slot, new Item.Properties())));
        }
    }

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE);
    }

    public static void register(IEventBus modBus) {
        SLABS.register(modBus);
    }

    private ConjureSlabs() {}
}
