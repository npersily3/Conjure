package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.block.ConjureStairBlock;
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
 * The stairs pool: {@value #STAIRS_POOL} pre-registered {@link ConjureStairBlock} shells, each
 * paired with a {@link ConjureBlockItem}. Filled by material-family generation. Stone-like.
 */
public final class ConjureStairs {

    public static final int STAIRS_POOL = 64;

    public static final DeferredRegister<Block> STAIRS =
            DeferredRegister.create(Registries.BLOCK, Conjure.MODID);

    public static final List<DeferredHolder<Block, ConjureStairBlock>> STAIR_SLOTS = new ArrayList<>();
    public static final List<DeferredHolder<Item, BlockItem>> STAIR_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < STAIRS_POOL; i++) {
            final int slot = i;
            SlotRegistry.init(SlotKind.STAIRS, slot);
            DeferredHolder<Block, ConjureStairBlock> holder = STAIRS.register(
                    "stairs_slot_" + slot, () -> new ConjureStairBlock(slot, props()));
            STAIR_SLOTS.add(holder);
            STAIR_ITEMS.add(ConjureItems.ITEMS.register("stairs_slot_" + slot,
                    () -> new ConjureBlockItem(holder.get(), SlotKind.STAIRS, slot, new Item.Properties())));
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
        STAIRS.register(modBus);
    }

    private ConjureStairs() {}
}
