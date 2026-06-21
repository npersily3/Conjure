package dev.conjure.content;

import java.util.List;

/**
 * Helpers for reading AI-generated intent strings out of a {@link SlotDefinition}'s
 * {@code strings} map and appending them to an item/block tooltip.
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>{@link #VISUAL}  — describes what the texture/model should look like
 *       (e.g. "glowing cyan crystal with sharp facets, pixel-art style").</li>
 *   <li>{@link #USAGE}   — describes how the item/block behaves or is used
 *       (e.g. "right-click to heal; apply to a beacon to boost mining speed").</li>
 *   <li>{@link #WORLDGEN} — describes the world-generation parameters chosen for an ore/block
 *       (e.g. "ore veins of 4-8 in stone, Y -32..48, common in plains/forest biomes").
 *       Only present on blocks that have had {@link dev.conjure.gen.pipeline.WorldgenPipeline}
 *       run for them.  Since worldgen registries freeze on world load, this setting only takes
 *       effect after a world rejoin.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In appendHoverText / getTooltipLines:
 * IntentTooltip.append(def, tooltipList);
 * }</pre>
 */
public final class IntentTooltip {

    // -------------------------------------------------------------------------
    // String-map keys
    // -------------------------------------------------------------------------

    /** Key for the AI-generated visual description (how the texture/model was intended to look). */
    public static final String VISUAL  = "visualIntent";

    /** Key for the AI-generated usage description (how the item/block behaves). */
    public static final String USAGE   = "usageIntent";

    /**
     * Key for the AI-generated worldgen description (where and how the block spawns in the world).
     * Only present after {@link dev.conjure.gen.pipeline.WorldgenPipeline} has run for this slot.
     * Applies only after a world rejoin because worldgen feature registries freeze at world load.
     */
    public static final String WORLDGEN = "worldgenIntent";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Appends all non-blank intent lines from {@code def.strings} to {@code lines}.
     * Lines are formatted in gray with a short prefix label.  Missing keys are silently
     * skipped, so callers do not need to guard against absent data.
     *
     * @param def   the slot whose intents to render
     * @param lines the tooltip list to append to
     */
    public static void append(SlotDefinition def, List<net.minecraft.network.chat.Component> lines) {
        appendKey(def, VISUAL,   "Visual",   lines);
        appendKey(def, USAGE,    "Usage",    lines);
        appendKey(def, WORLDGEN, "Worldgen", lines);
    }

    /**
     * Appends a single intent key if present and non-blank.
     */
    public static void appendKey(SlotDefinition def, String key, String label,
                                  List<net.minecraft.network.chat.Component> lines) {
        String value = def.str(key, "");
        if (!value.isBlank()) {
            lines.add(net.minecraft.network.chat.Component.literal(
                    net.minecraft.ChatFormatting.GRAY + label + ": " + value));
        }
    }

    /**
     * Appends only the worldgen intent line (if present) to {@code lines}.
     * Convenience variant for callers that only want the worldgen tooltip.
     */
    public static void appendWorldgen(SlotDefinition def,
                                      List<net.minecraft.network.chat.Component> lines) {
        appendKey(def, WORLDGEN, "Worldgen", lines);
    }

    private IntentTooltip() {}
}
