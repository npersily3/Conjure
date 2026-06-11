package dev.conjure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.conjure.Conjure;
import dev.conjure.content.block.ConjureMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import dev.conjure.registry.ConjureMenus;

/**
 * GUI screen for {@link ConjureMenu} (machine block entities).
 *
 * <p>Uses the vanilla furnace background texture since it already shows three slots in a
 * compatible layout. A progress arrow is drawn when the block entity has an active recipe.
 *
 * <p>The inner {@link ScreenRegistrar} class subscribes to {@link RegisterMenuScreensEvent} on
 * the mod bus (client-only), wiring this screen to {@link ConjureMenus#MACHINE_MENU}.
 */
@OnlyIn(Dist.CLIENT)
public class ConjureScreen extends AbstractContainerScreen<ConjureMenu> {

    /** Vanilla furnace GUI texture — 176×166, slots at positions compatible with our layout. */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");

    public ConjureScreen(ConjureMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Standard 176×166 container dimensions (same as furnace)
        this.imageWidth  = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.leftPos;
        int y = this.topPos;

        // Draw the furnace background (176×166 sheet, UV origin at 0,0)
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Draw the progress arrow if processing
        int progress    = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0 && progress > 0) {
            int arrowWidth = (int) (24.0 * progress / maxProgress);
            // Vanilla furnace arrow is at UV (176, 14) → 24×16 px, drawn at (x+79, y+34)
            guiGraphics.blit(TEXTURE, x + 79, y + 34, 176, 14, arrowWidth, 16);
        }
    }

    // ------------------------------------------------------------------
    // Registration helper (client mod-bus subscriber)
    // ------------------------------------------------------------------

    /**
     * Registers {@link ConjureScreen} for {@link ConjureMenus#MACHINE_MENU} on the mod bus.
     * This class is referenced from {@link ConjureClientPack} (client-side mod bus subscriber)
     * so NeoForge discovers it without touching Conjure.java.
     */
    @EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ScreenRegistrar {

        @SubscribeEvent
        public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
            event.register(ConjureMenus.MACHINE_MENU.get(), ConjureScreen::new);
        }
    }
}
