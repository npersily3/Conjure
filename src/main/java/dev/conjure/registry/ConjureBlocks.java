package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.block.BlockArchetype;
import dev.conjure.content.block.ConjureBlock;
import dev.conjure.content.item.ConjureBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * The block pool: 500 pre-registered {@link ConjureBlock} shells spread across
 * {@link BlockArchetype} buckets, each paired with a {@link BlockItem} in the item registry.
 */
public final class ConjureBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Conjure.MODID);

    public static final List<DeferredHolder<Block, ConjureBlock>> BLOCK_SLOTS = new ArrayList<>();

    static {
        int idx = 0;
        for (BlockArchetype archetype : BlockArchetype.values()) {
            for (int j = 0; j < archetype.count; j++) {
                final int slot = idx;
                final BlockArchetype a = archetype;
                SlotRegistry.init(SlotKind.BLOCK, slot);

                DeferredHolder<Block, ConjureBlock> blockHolder = BLOCKS.register(
                        "block_slot_" + slot,
                        () -> new ConjureBlock(a, slot, a.newProperties()));
                BLOCK_SLOTS.add(blockHolder);

                // Matching ConjureBlockItem (shares the slot id, lives in the ITEM registry).
                // ConjureBlockItem overrides getName() so the item shows the AI-generated display name.
                final int blockSlot = slot;
                ConjureItems.BLOCK_ITEMS.add(ConjureItems.ITEMS.register(
                        "block_slot_" + slot,
                        () -> new ConjureBlockItem(
                                blockHolder.get(), SlotKind.BLOCK, blockSlot, new Item.Properties())));
                idx++;
            }
        }
    }

    private ConjureBlocks() {}
}
