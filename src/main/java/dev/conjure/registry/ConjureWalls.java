package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.block.ConjureWallBlock;
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
 * The wall pool: {@value #WALL_POOL} pre-registered {@link ConjureWallBlock} shells, each paired
 * with a {@link ConjureBlockItem}. Filled by material-family generation. Stone-like.
 */
public final class ConjureWalls {

    public static final int WALL_POOL = 64;

    public static final DeferredRegister<Block> WALLS =
            DeferredRegister.create(Registries.BLOCK, Conjure.MODID);

    public static final List<DeferredHolder<Block, ConjureWallBlock>> WALL_SLOTS = new ArrayList<>();
    public static final List<DeferredHolder<Item, BlockItem>> WALL_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < WALL_POOL; i++) {
            final int slot = i;
            SlotRegistry.init(SlotKind.WALL, slot);
            DeferredHolder<Block, ConjureWallBlock> holder = WALLS.register(
                    "wall_slot_" + slot, () -> new ConjureWallBlock(slot, props()));
            WALL_SLOTS.add(holder);
            WALL_ITEMS.add(ConjureItems.ITEMS.register("wall_slot_" + slot,
                    () -> new ConjureBlockItem(holder.get(), SlotKind.WALL, slot, new Item.Properties())));
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
        WALLS.register(modBus);
    }

    private ConjureWalls() {}
}
