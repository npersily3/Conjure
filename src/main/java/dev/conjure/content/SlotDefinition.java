package dev.conjure.content;

import java.util.HashMap;
import java.util.Map;

/**
 * The runtime-mutable description of what a pre-registered slot currently "is".
 *
 * <p>An empty/unconfigured slot has {@link #configured} == false and renders as a
 * neutral placeholder. When an AI generation completes, the command layer fills one
 * of these in and drops it into the {@link SlotRegistry}; the slot's item/block/etc.
 * class reads from here every time the game asks for a name, behavior, or property.
 *
 * <p>This object holds only the *hot-reloadable* facets (display name, texture path,
 * behavior-script id, tunable numbers). Facets baked at JVM registration time
 * (registry id, render layer, hitbox size, block archetype) are fixed by the slot
 * the AI was assigned and are NOT represented here.
 */
public final class SlotDefinition {

    public final SlotKind kind;
    public final int slotIndex;

    /** Player-facing name, shown via getName(). */
    public String displayName = "Unconfigured Slot";

    /** Resource-pack-relative texture id, e.g. "conjure:item/slot_017". Reloaded with F3+T. */
    public String texturePath = "";

    /** Id of the behavior script the embedded runtime should run for interactions/ticks. */
    public String behaviorScriptId = "";

    /** The original natural-language prompt that produced this slot (for `edit` mode). */
    public String sourcePrompt = "";

    /** Arbitrary tunables the behavior script reads (durability, damage, light, speed, ...). */
    public final Map<String, Double> numbers = new HashMap<>();
    public final Map<String, String> strings = new HashMap<>();

    public boolean configured = false;

    public SlotDefinition(SlotKind kind, int slotIndex) {
        this.kind = kind;
        this.slotIndex = slotIndex;
    }

    public double num(String key, double fallback) {
        return numbers.getOrDefault(key, fallback);
    }

    public String str(String key, String fallback) {
        return strings.getOrDefault(key, fallback);
    }
}
