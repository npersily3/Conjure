package dev.conjure.registry;

// PARENT: add to Conjure.java constructor:
//   ConjureMenus.MENUS.register(modBus);

import dev.conjure.Conjure;
import dev.conjure.content.block.ConjureMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers ONE shared {@link MenuType} for all ConjureBlock machine containers.
 *
 * <p>PARENT: call {@code ConjureMenus.MENUS.register(modBus)} in Conjure.java.
 */
public final class ConjureMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Conjure.MODID);

    /**
     * Shared menu type for every machine-archetype ConjureBlock.
     * Uses NeoForge's {@code IMenuTypeExtension.create} so the menu can receive the block position
     * in the extra data buffer.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<ConjureMenu>> MACHINE_MENU =
            MENUS.register("machine_menu",
                    () -> IMenuTypeExtension.create(
                            (containerId, playerInv, extraData) -> {
                                // Decode the BlockPos sent by ConjureBlock.openMenu
                                net.minecraft.core.BlockPos pos = extraData.readBlockPos();
                                return new ConjureMenu(containerId, playerInv, pos, playerInv.player.level());
                            }));

    private ConjureMenus() {}
}
