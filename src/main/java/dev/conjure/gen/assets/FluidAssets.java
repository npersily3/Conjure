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

    // -------------------------------------------------------------------------
    // Bucket — hardcoded formula (no AI). A fixed pail mask is rendered with the
    // bucket walls in fixed metal grey and the interior "fluid" pixels tinted to the
    // fluid's own dominant colour, so every generated fluid gets a recognisable bucket
    // icon for free. Writes textures/item/bucket_slot_N.png + models/item/bucket_slot_N.json.
    // -------------------------------------------------------------------------

    /** Bucket pail mask, 16 rows × 16 cols. '.'=transparent 'D'=dark metal 'M'=metal 'F'=fluid. */
    private static final String[] BUCKET_MASK = {
            "................",
            "................",
            "................",
            "...DDDDDDDDDD...",
            "...DFFFFFFFFD...",
            "...DFFFFFFFFD...",
            "...DFFFFFFFFD...",
            "...DMMMMMMMMD...",
            "...DMMMMMMMMD...",
            "...DMMMMMMMMD...",
            "...DMMMMMMMMD...",
            "...DMMMMMMMMD...",
            "...DMMMMMMMMD...",
            "...DMMMMMMMMD...",
            "...DDDDDDDDDD...",
            "................",
    };

    private static final int TRANSPARENT = 0x00000000;
    private static final int METAL_DARK  = 0xFF3A3A3A;
    private static final int METAL_BODY  = 0xFF9A9A9A;
    private static final int DEFAULT_FLUID = 0xFF3F76E4; // water-ish, used when a fluid sprite is empty

    /**
     * Writes the bucket icon PNG + item model for fluid slot {@code slot}, tinted to the fluid's
     * dominant colour. Purely deterministic — no model call.
     *
     * @param slot      zero-based fluid slot index
     * @param stillArgb the fluid's still 16×16 sprite, used only to pick the tint colour
     */
    public static void writeBucketAssets(int slot, int[][] stillArgb) throws IOException {
        int tint = dominantColor(stillArgb);
        int[][] icon = renderBucket(tint);
        DynamicPackManager.writePngAt(
                "assets/conjure/textures/item/bucket_slot_" + slot + ".png", icon);
        DynamicPackManager.write(
                "assets/conjure/models/item/bucket_slot_" + slot + ".json",
                "{\n  \"parent\": \"minecraft:item/generated\",\n"
                + "  \"textures\": { \"layer0\": \"conjure:item/bucket_slot_" + slot + "\" }\n}");
    }

    /** Renders the bucket mask to a 16×16 ARGB grid with the fluid interior tinted to {@code fluidArgb}. */
    static int[][] renderBucket(int fluidArgb) {
        int fluidShade = 0xFF000000 | scale(fluidArgb, 0.78); // slightly darker fluid for depth
        int[][] out = new int[16][16];
        for (int y = 0; y < 16; y++) {
            String row = BUCKET_MASK[y];
            for (int x = 0; x < 16; x++) {
                out[y][x] = switch (row.charAt(x)) {
                    case 'D' -> METAL_DARK;
                    case 'M' -> METAL_BODY;
                    case 'F' -> (x % 2 == 0) ? (0xFF000000 | (fluidArgb & 0xFFFFFF)) : fluidShade;
                    default  -> TRANSPARENT;
                };
            }
        }
        return out;
    }

    /** Average colour of all opaque pixels (alpha &gt; 127), full-alpha; {@link #DEFAULT_FLUID} if none. */
    static int dominantColor(int[][] argb) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        if (argb != null) {
            for (int[] row : argb) {
                if (row == null) continue;
                for (int px : row) {
                    if (((px >>> 24) & 0xFF) <= 127) continue;
                    r += (px >> 16) & 0xFF;
                    g += (px >> 8) & 0xFF;
                    b += px & 0xFF;
                    n++;
                }
            }
        }
        if (n == 0) return DEFAULT_FLUID;
        return 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    /** Multiplies the RGB channels of {@code argb} by {@code f} (clamped 0..255), dropping alpha. */
    private static int scale(int argb, double f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** ponytail: self-check for the pure bucket formula — run with {@code java -ea}. */
    public static void main(String[] args) {
        // Dominant colour of a solid-blue sprite is blue; an empty sprite falls back to the default.
        int[][] blue = new int[16][16];
        for (int[] row : blue) java.util.Arrays.fill(row, 0xFF0000FF);
        assert dominantColor(blue) == 0xFF0000FF : "blue in → blue out";
        assert dominantColor(new int[16][16]) == DEFAULT_FLUID : "empty → default fluid";

        // Every metal/fluid pixel is fully opaque; background stays transparent.
        int[][] icon = renderBucket(0xFF0000FF);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean solid = BUCKET_MASK[y].charAt(x) != '.';
                int a = (icon[y][x] >>> 24) & 0xFF;
                assert solid == (a == 0xFF) : "opacity mismatch at " + x + "," + y;
            }
        }
        System.out.println("FluidAssets self-check passed.");
    }
}
