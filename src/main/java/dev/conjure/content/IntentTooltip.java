package dev.conjure.content;

import dev.conjure.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Dev/debug overlay: appends the generated <b>visual intent</b> and <b>usage intent</b> (in red) to
 * a conjured slot's tooltip when {@link Config#SHOW_INTENT} is on.
 *
 * <p>Purpose: when a generated thing looks wrong (e.g. a texture is just a coloured square), the two
 * intents say what the generator was <em>trying</em> to make. A texture that doesn't match the visual
 * intent points at the texture/model pipeline; a behavior that doesn't match the usage intent points
 * at the code (or a request the runtime can't yet fulfil). Stored under {@code strings} keys
 * {@code intentVisual} / {@code intentUsage} by the generation pipelines.
 */
public final class IntentTooltip {

    public static final String VISUAL = "intentVisual";
    public static final String USAGE  = "intentUsage";

    private IntentTooltip() {}

    /** Appends the red intent lines for {@code def} to {@code tooltip}, if the debug flag is on. */
    public static void append(SlotDefinition def, List<Component> tooltip) {
        if (!Config.SHOW_INTENT.get() || !def.configured) return;
        line(tooltip, "visual intent", def.str(VISUAL, ""));
        line(tooltip, "usage intent",  def.str(USAGE, ""));
    }

    private static void line(List<Component> tooltip, String label, String value) {
        if (value.isBlank()) return;
        tooltip.add(Component.literal("[" + label + "] " + value).withStyle(ChatFormatting.RED));
    }
}
