package dev.conjure.ai.agents;

import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;

/**
 * Sub-agent responsible for generating a small JavaScript right-click behavior script.
 *
 * <p>Scripts are executed by the Rhino sandbox and may ONLY interact with the game through the
 * injected {@code ctx} object. No Java imports, no {@code require}, no globals other than standard
 * JS builtins and {@code ctx}.
 *
 * <h2>Available ctx API</h2>
 * <pre>
 *   ctx.message(String text)            — sends a chat message to the player
 *   ctx.giveItem(String itemId, int n)  — gives items (e.g. "minecraft:diamond")
 *   ctx.heal(double amount)             — heals the player (2.0 = 1 heart)
 *   ctx.damage(double amount)           — damages the player
 *   ctx.spawnParticleHere()             — spawns a decorative particle burst
 *   ctx.getPlayerName() → String        — returns the player's name
 * </pre>
 */
public final class LogicAgent {

    private static final String SYSTEM = """
            You write Minecraft item right-click behavior scripts for the Conjure mod.
            Scripts run inside a sandboxed Rhino JS engine. The ONLY API available is a global
            object called `ctx` with these methods:
              ctx.message(text)              — send chat message to player
              ctx.giveItem(itemId, count)    — give item (e.g. "minecraft:diamond")
              ctx.heal(amount)               — heal player (2.0 = 1 heart)
              ctx.damage(amount)             — damage player
              ctx.spawnParticleHere()        — spawn a particle burst at the player
              ctx.getPlayerName()            — returns player name as String
            Rules:
            - NO Java imports, NO require(), NO Packages, NO Java interop of any kind.
            - Use ONLY the ctx API above plus standard JS (Math, String, if, for, var/let/const).
            - Keep the script under 20 lines.
            - Make the behavior interesting and thematically appropriate for the item.
            - Respond with ONLY the raw JavaScript, NO markdown fences, NO comments about the code.
            """;

    /**
     * Generates a JS behavior script appropriate for the item described by {@code prompt}.
     *
     * @param prompt the original user-supplied item description
     * @return raw JavaScript source to be written as {@code <id>.js}
     * @throws Exception if the model call fails
     */
    public String generate(String prompt) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String userMsg = "Write a right-click behavior script for this Minecraft item: " + prompt;
        String raw = provider.complete(SYSTEM, userMsg);

        // Strip common markdown code fences if present — scripts are plain JS, not JSON,
        // so we cannot use JsonHelper here; just do a lightweight strip.
        return stripFences(raw);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Removes {@code ```javascript}, {@code ```js}, or plain {@code ```} fences from the
     * model response. Keeps the inner lines exactly as-is so indentation is preserved.
     */
    private static String stripFences(String raw) {
        if (raw == null) return "";
        String trimmed = raw.strip();

        // Remove opening fence line (```javascript, ```js, ```)
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            if (newline >= 0) {
                trimmed = trimmed.substring(newline + 1);
            }
        }
        // Remove trailing closing fence
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.lastIndexOf("```")).stripTrailing();
        }
        return trimmed;
    }

    public LogicAgent() {}
}
