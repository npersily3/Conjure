package dev.conjure.client;

import dev.conjure.Conjure;
import dev.conjure.content.block.ConjureMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import dev.conjure.registry.ConjureMenus;

/**
 * GUI screen for {@link ConjureMenu} (workbench block entities).
 *
 * <p>The slot layout is dynamic (it depends on the recipe's ingredient count), so instead of a
 * fixed background image this screen draws everything programmatically: a flat vanilla-style panel,
 * a pocket behind every slot the menu created (workbench cells <em>and</em> the player inventory),
 * and a progress bar that fills as the recipe processes. No per-recipe textures required.
 */
@OnlyIn(Dist.CLIENT)
public class ConjureScreen extends AbstractContainerScreen<ConjureMenu> {

    // Vanilla GUI palette.
    private static final int PANEL   = 0xFFC6C6C6;
    private static final int LIGHT   = 0xFFFFFFFF;
    private static final int DARK    = 0xFF555555;
    private static final int POCKET  = 0xFF8B8B8B;
    private static final int POCKET_HI = 0xFFFFFFFF;
    private static final int POCKET_LO = 0xFF373737;
    private static final int BAR_BG   = 0xFF8B8B8B;
    private static final int BAR_FILL = 0xFF3FB23F;

    public ConjureScreen(ConjureMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;

        // Outer panel.
        bevel(g, x, y, imageWidth, imageHeight, PANEL, LIGHT, DARK);

        // A pocket behind every slot (workbench + player inventory) — fully dynamic.
        for (Slot slot : this.menu.slots) {
            pocket(g, x + slot.x - 1, y + slot.y - 1);
        }

        // Progress bar between the input area and the output slot.
        int maxProgress = menu.getMaxProgress();
        int progress    = menu.getProgress();
        int barX = x + 104, barY = y + 38, barW = 24, barH = 8;
        g.fill(barX, barY, barX + barW, barY + barH, BAR_BG);
        if (maxProgress > 0 && progress > 0) {
            int filled = (int) ((long) barW * progress / maxProgress);
            g.fill(barX, barY, barX + filled, barY + barH, BAR_FILL);
        }
    }

    /** A 3D-bevelled filled rectangle (light top/left, dark bottom/right). */
    private static void bevel(GuiGraphics g, int x, int y, int w, int h, int fill, int light, int dark) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, light);              // top
        g.fill(x, y, x + 1, y + h, light);              // left
        g.fill(x, y + h - 1, x + w, y + h, dark);       // bottom
        g.fill(x + w - 1, y, x + w, y + h, dark);       // right
    }

    /** An 18×18 vanilla-style inset slot pocket with its top-left corner at (x, y). */
    private static void pocket(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, POCKET);
        g.fill(x, y, x + 18, y + 1, POCKET_LO);         // top (inset → dark)
        g.fill(x, y, x + 1, y + 18, POCKET_LO);         // left
        g.fill(x, y + 17, x + 18, y + 18, POCKET_HI);   // bottom (inset → light)
        g.fill(x + 17, y, x + 18, y + 18, POCKET_HI);   // right
    }

    // ------------------------------------------------------------------
    // Registration helper (client mod-bus subscriber)
    // ------------------------------------------------------------------

    @EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ScreenRegistrar {

        @SubscribeEvent
        public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
            event.register(ConjureMenus.MACHINE_MENU.get(), ConjureScreen::new);
        }
    }
}
