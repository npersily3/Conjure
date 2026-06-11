package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A dedicated creative-inventory tab for Conjure content.
 *
 * <p>The pools pre-register hundreds of empty slots, so the tab's {@code displayItems} generator
 * only emits slots whose {@link dev.conjure.content.SlotDefinition} is {@code configured} — i.e.
 * the things you've actually generated. The display names come from each item's {@code getName}
 * override (so this tab shows the AI-generated names, not {@code *_slot_N}).
 */
public final class ConjureTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Conjure.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CONJURE_TAB =
            TABS.register("conjure", () -> CreativeModeTab.builder()
                    .title(Component.literal("Conjure"))
                    .icon(() -> new ItemStack(Items.NETHER_STAR))
                    .displayItems((params, output) -> {
                        // Generated items
                        for (int i = 0; i < ConjureItems.ITEM_SLOTS.size(); i++) {
                            if (SlotRegistry.get(SlotKind.ITEM, i).configured) {
                                output.accept(ConjureItems.ITEM_SLOTS.get(i).get());
                            }
                        }
                        // Generated blocks (their BlockItems)
                        for (int i = 0; i < ConjureItems.BLOCK_ITEMS.size(); i++) {
                            if (SlotRegistry.get(SlotKind.BLOCK, i).configured) {
                                output.accept(ConjureItems.BLOCK_ITEMS.get(i).get());
                            }
                        }
                        // Generated fluids (their buckets)
                        for (int i = 0; i < ConjureFluids.BUCKET_SLOTS.size(); i++) {
                            if (SlotRegistry.get(SlotKind.FLUID, i).configured) {
                                output.accept(ConjureFluids.BUCKET_SLOTS.get(i).get());
                            }
                        }
                    })
                    .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }

    private ConjureTabs() {}
}
