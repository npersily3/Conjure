package dev.conjure.ai.agents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static pre-release review of a generated behavior script — the cheap half of the "review channel".
 *
 * <p>A true runtime dry-run is impossible at generation time (the script needs a real Level/Player
 * and runs off the game thread), so instead we statically catch the two error classes that the
 * Rhino compile-check cannot: (1) calls to {@code ctx.*} methods that don't exist, and (2)
 * string-literal registry ids (effects/particles/sounds/items/blocks/entities) that aren't real.
 * Both checks read only the frozen {@link BuiltInRegistries}, so they are safe on the generation
 * thread. Findings are fed back to the model by {@link LogicAgent} for a targeted fix before save.
 */
public final class ScriptReviewer {

    private ScriptReviewer() {}

    /** The full public {@code ctx} surface a script may call (keep in sync with ScriptContext). */
    private static final Set<String> CTX_METHODS = Set.of(
            "trigger", "message", "getPlayerName", "giveItem", "consumeHeld", "heal", "damage",
            "giveEffect", "playSound", "applyVelocity", "getLevel", "getPlayer", "getTargetEntity",
            "getTargetPos", "getHand", "hasTargetEntity", "hasTargetBlock", "getBlockActive",
            "setBlockActive", "breakTargetBlock", "setTargetBlock", "placeOnFace", "heldStr",
            "heldNum", "targetBlockStr", "targetBlockNum", "applyEffect", "damageNearby",
            "effectNearby", "knockbackNearby", "hurtEntity", "effectEntity", "knockbackEntity",
            "setBlockAt", "particle", "summon");

    private static final Pattern CTX_CALL = Pattern.compile("\\bctx\\.([A-Za-z]+)\\s*\\(");

    // First string-literal id passed to a registry-backed helper, by argument position.
    private static final Pattern P_SOUND  = Pattern.compile("\\bctx\\.playSound\\(\\s*\"([^\"]+)\"");
    private static final Pattern P_PART   = Pattern.compile("\\bctx\\.particle\\(\\s*\"([^\"]+)\"");
    private static final Pattern P_ENTITY = Pattern.compile("\\bctx\\.summon\\(\\s*\"([^\"]+)\"");
    private static final Pattern P_ITEM   = Pattern.compile("\\bctx\\.giveItem\\(\\s*\"([^\"]+)\"");
    private static final Pattern P_BLOCK1 = Pattern.compile("\\bctx\\.(?:setTargetBlock|placeOnFace)\\(\\s*\"([^\"]+)\"");
    private static final Pattern P_EFFECT1 = Pattern.compile("\\bctx\\.giveEffect\\(\\s*\"([^\"]+)\"");
    // effectEntity(e, "id", …) / effectNearby(r, "id", …) — id is the 2nd argument.
    private static final Pattern P_EFFECT2 = Pattern.compile("\\bctx\\.(?:effectEntity|effectNearby)\\(\\s*[^,]+,\\s*\"([^\"]+)\"");
    // setBlockAt(x, y, z, "id") — id is the 4th argument.
    private static final Pattern P_BLOCK4 = Pattern.compile("\\bctx\\.setBlockAt\\(\\s*[^,]+,\\s*[^,]+,\\s*[^,]+,\\s*\"([^\"]+)\"");

    /** Returns a list of human-readable problems; empty when the script looks clean. */
    public static List<String> review(String script) {
        List<String> issues = new ArrayList<>();
        if (script == null || script.isBlank()) return issues;

        // 1. Unknown ctx methods.
        Set<String> unknown = new LinkedHashSet<>();
        Matcher m = CTX_CALL.matcher(script);
        while (m.find()) {
            if (!CTX_METHODS.contains(m.group(1))) unknown.add(m.group(1));
        }
        for (String u : unknown) {
            issues.add("ctx." + u + "(...) is not a real ctx method — use only the documented ctx API");
        }

        // 2. Registry-id literals in the wrong/unknown registry.
        checkIds(script, P_SOUND,  BuiltInRegistries.SOUND_EVENT,  "sound", issues);
        checkIds(script, P_PART,   BuiltInRegistries.PARTICLE_TYPE, "particle", issues);
        checkIds(script, P_ENTITY, BuiltInRegistries.ENTITY_TYPE,  "entity", issues);
        checkIds(script, P_ITEM,   BuiltInRegistries.ITEM,         "item", issues);
        checkIds(script, P_BLOCK1, BuiltInRegistries.BLOCK,        "block", issues);
        checkIds(script, P_BLOCK4, BuiltInRegistries.BLOCK,        "block", issues);
        checkIds(script, P_EFFECT1, BuiltInRegistries.MOB_EFFECT,  "effect", issues);
        checkIds(script, P_EFFECT2, BuiltInRegistries.MOB_EFFECT,  "effect", issues);
        return issues;
    }

    private static void checkIds(String script, Pattern pat, Registry<?> registry,
                                 String label, List<String> issues) {
        Matcher m = pat.matcher(script);
        while (m.find()) {
            String id = m.group(1);
            if (id.startsWith("conjure:")) continue; // dynamic conjure ids aren't in BuiltInRegistries
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc == null || !registry.containsKey(loc)) {
                issues.add("\"" + id + "\" is not a valid " + label + " id");
            }
        }
    }
}
