package dev.conjure.client;

import dev.conjure.Conjure;
import dev.conjure.gen.GenerationStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Client-only "Conjure is thinking" indicator plus a focus-loss guard.
 *
 * <ul>
 *   <li><b>HUD layer</b> — while {@link GenerationStatus#isActive()} is true, draws a pulsing amber
 *       square + "Conjuring…" in the top-left corner so you can see generation is running.</li>
 *   <li><b>Background guard</b> — while generating, disables {@code pauseOnLostFocus} so you can
 *       alt-tab out of Minecraft and the game (and the generation results it applies) keep running.
 *       The compute itself already runs on a background thread in {@code GenerationService}; this
 *       only stops the singleplayer client from auto-pausing. The original setting is restored the
 *       moment generation finishes.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class ConjureHud {

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "generation_status");

    private static final int AMBER = 0xFFD24A; // RGB; alpha added per-frame

    private ConjureHud() {}

    /** Draws the thinking indicator; a no-op when nothing is generating. */
    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (!GenerationStatus.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        long now = System.currentTimeMillis();
        // Pulsing square (no texture asset needed): alpha breathes between ~0.45 and 1.0.
        double phase = (now % 1000) / 1000.0;
        int alpha = (int) (115 + 140 * Math.abs(Math.sin(phase * Math.PI)));
        g.fill(6, 6, 14, 14, (alpha << 24) | AMBER);

        // Animated "Conjuring…" with cycling dots; show a ×N badge when several tasks overlap.
        int dots = (int) ((now / 400) % 4);
        int n = GenerationStatus.count();
        String text = "Conjuring" + ".".repeat(dots) + (n > 1 ? "  x" + n : "");
        g.drawString(mc.font, text, 18, 7, 0xFFFFE08A, true);
    }

    // ------------------------------------------------------------------
    // Registration (mod bus) + per-tick focus guard (game bus)
    // ------------------------------------------------------------------

    @EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registrar {
        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(LAYER_ID, ConjureHud::render);
        }
    }

    @EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class FocusGuard {
        private static boolean suppressing = false;
        private static boolean savedPauseOnLostFocus = true;

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            boolean busy = GenerationStatus.isActive();
            if (busy) {
                if (!suppressing) { // remember the user's setting once, on the way into "busy"
                    savedPauseOnLostFocus = mc.options.pauseOnLostFocus;
                    suppressing = true;
                }
                // ponytail: toggling the field (not options.save()) keeps it in-memory only, so the
                // user's options.txt is untouched; restored below the instant we go idle.
                mc.options.pauseOnLostFocus = false;
            } else if (suppressing) {
                mc.options.pauseOnLostFocus = savedPauseOnLostFocus;
                suppressing = false;
            }
        }
    }
}
