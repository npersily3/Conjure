package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.item.ConjureItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The item pool: {@value #ITEM_POOL} pre-registered {@link ConjureItem} shells, plus the
 * {@link BlockItem}s for every block slot (added here by {@link ConjureBlocks}).
 */
public final class ConjureItems {

    public static final int ITEM_POOL = 500;

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Conjure.MODID);

    public static final List<DeferredHolder<Item, ConjureItem>> ITEM_SLOTS = new ArrayList<>();
    /** BlockItems for the block pool, populated from {@link ConjureBlocks}'s static init. */
    public static final List<DeferredHolder<Item, BlockItem>> BLOCK_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < ITEM_POOL; i++) {
            final int idx = i;
            SlotRegistry.init(SlotKind.ITEM, idx);
            ITEM_SLOTS.add(ITEMS.register("item_slot_" + idx,
                    () -> new ConjureItem(idx, new Item.Properties())));
        }
    }

    private ConjureItems() {}
}
