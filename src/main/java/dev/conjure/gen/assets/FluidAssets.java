package dev.conjure.gen.assets;

import dev.conjure.gen.DynamicPackManager;

import java.io.IOException;

/**
 * Writes the runtime-generated asset files for a fluid slot.
 *
 * <p>Fluid sprites live in the <em>block</em> atlas (not the item atlas), so both the
 * still and flowing textures are placed under {@code assets/conjure/textures/block/}.
 * A single static frame is used for v1 — no animation mcmeta required for the sprite
 * to be stitched into the atlas.
 *
 * <p>The texture paths used here mirror the fixed per-slot {@link net.minecraft.resources.ResourceLocation}s
 * baked into each {@link dev.conjure.content.fluid.ConjureFluidType}:
 * <ul>
 *   <li>still:   {@code conjure:block/fluid_still_slot_N}</li>
 *   <li>flowing: {@code conjure:block/fluid_flow_slot_N}</li>
 * </ul>
 */
public final class FluidAssets {

    private FluidAssets() {}

    /**
     * Writes the still-frame PNG for fluid slot {@code slot}.
     *
     * @param slot  zero-based fluid slot index
     * @param argb  16×16 ARGB pixel grid from {@link dev.conjure.ai.agents.TextureAgent}
     */
    public static void writeStillTexture(int slot, int[][] argb) throws IOException {
        DynamicPackManager.writePngAt(
                "assets/conjure/textures/block/fluid_still_slot_" + slot + ".png", argb);
    }

    /**
     * Writes the flowing-frame PNG for fluid slot {@code slot}. Uses the same pixel data
     * as the still texture (minor visual variant) for v1 simplicity.
     *
     * @param slot  zero-based fluid slot index
     * @param argb  16×16 ARGB pixel grid (typically a slightly modified still variant)
     */
    public static void writeFlowTexture(int slot, int[][] argb) throws IOException {
        DynamicPackManager.writePngAt(
                "assets/conjure/textures/block/fluid_flow_slot_" + slot + ".png", argb);
    }
}
