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
 *   // messaging / player
 *   ctx.message(text) · ctx.getPlayerName() · ctx.giveItem(id,n) · ctx.consumeHeld()
 *   ctx.heal(amt) · ctx.damage(amt) · ctx.giveEffect(id,sec,amp) · ctx.ignite(sec)
 *   // movement
 *   ctx.launch(power) · ctx.dashForward(power)
 *   // world effects
 *   ctx.lightning() · ctx.explode(power) · ctx.playSound(id) · ctx.spawnParticleHere()
 *   // combat (only when a mob was hit)
 *   ctx.hasTargetEntity() · ctx.damageTarget(amt) · ctx.igniteTarget(sec)
 *   ctx.knockbackTarget(power) · ctx.effectTarget(id,sec,amp)
 *   // target block (only when used on / by a block) — drives doors/lamps/safes
 *   ctx.hasTargetBlock() · ctx.getBlockActive() · ctx.setBlockActive(bool)
 *   ctx.breakTargetBlock() · ctx.setTargetBlock(id) · ctx.placeOnFace(id)
 *   // cross-object tunables (key↔safe matching)
 *   ctx.heldStr(key) · ctx.heldNum(key) · ctx.targetBlockStr(key) · ctx.targetBlockNum(key)
 * </pre>
 */
public final class LogicAgent {

    private static final String SYSTEM = """
            You write Minecraft behavior scripts for the Conjure mod. A script runs when the item is
            right-clicked in the air, right-clicked ON A BLOCK, or used to HIT A MOB — so target-aware
            verbs no-op safely when their target is absent; guard them with the has* checks.
            Scripts run in a sandboxed Rhino JS engine. The ONLY API is a global object `ctx`:
              ctx.message(text)                 — chat message to the player
              ctx.giveItem(itemId, count)       — give item (e.g. "minecraft:diamond")
              ctx.heal(amount) / ctx.damage(amount)        — heal / hurt player (2.0 = 1 heart)
              ctx.giveEffect(id, seconds, amp)  — potion effect (amp 0 = level I)
              ctx.ignite(seconds)               — set the player on fire
              ctx.launch(power)                 — fling the player upward (power 0–4)
              ctx.dashForward(power)            — dash in the look direction (power 0–4)
              ctx.lightning()                   — strike lightning at the player
              ctx.explode(power)                — explosion that hurts mobs but breaks NO blocks (0–8)
              ctx.playSound(soundId)            — e.g. "minecraft:entity.player.levelup"
              ctx.spawnParticleHere()           — particle burst at the player
              ctx.getPlayerName()               — player name as String
              ctx.consumeHeld()                 — consume one of the held item (single-use)
              // combat — only meaningful when this hit a mob:
              ctx.hasTargetEntity()             — true if a mob was hit
              ctx.damageTarget(amount) / ctx.igniteTarget(sec) / ctx.knockbackTarget(power)
              ctx.effectTarget(id, seconds, amp)
              // blocks — only meaningful when used on / by a block:
              ctx.hasTargetBlock()              — true if there is a target block
              ctx.getBlockActive() / ctx.setBlockActive(true|false)  — read/toggle a door/lamp/safe
              ctx.breakTargetBlock() / ctx.setTargetBlock(blockId) / ctx.placeOnFace(blockId)
              // cross-object data (for keys, wands that react to blocks):
              ctx.heldStr(key) / ctx.heldNum(key)               — tunables on the held item
              ctx.targetBlockStr(key) / ctx.targetBlockNum(key) — tunables on the target block
            KEY / UNLOCKING items: a lockable Conjure block carries a non-empty "keyId". To unlock,
            check the block then open it, e.g.:
              if (ctx.hasTargetBlock() && ctx.targetBlockStr("keyId") !== "") {
                ctx.setBlockActive(true); ctx.message("The lock clicks open.");
                ctx.playSound("minecraft:block.chest.open");
              }
            For a key that fits only ITS matching safe, compare ids instead:
              ctx.targetBlockStr("keyId") === ctx.heldStr("keyId").
            Rules:
            - NO Java imports, NO require(), NO Packages, NO Java interop of any kind.
            - Use ONLY the ctx API above plus standard JS (Math, String, if, for, var/let/const).
            - Keep the script under 25 lines.
            - Give a REAL effect that fits the item's theme (an effect, reward, motion, combat hit,
              or block interaction) — a chat message may flavor it but is never the whole behavior.
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

    /**
     * Behavior script for a STATEFUL (ACTIVATABLE) block's bare right-click. For a lock-like block
     * (safe/vault) this is a fixed, reliable template — no model round-trip — that keeps the block
     * locked on an empty-handed click (a matching key item opens it via {@code ctx.setBlockActive})
     * but lets a player close it back up once open. ponytail: deterministic over a flaky LLM call;
     * give it a model-authored variant only if richer, themed lock messages prove worth the call.
     */
    public String generateStateful(String prompt, boolean lock) {
        if (!lock) return ""; // free-toggling blocks (doors/lamps) need no script — the block flips itself
        return """
                if (ctx.getBlockActive()) {
                    ctx.setBlockActive(false);
                    ctx.message("You close it.");
                } else {
                    ctx.message("It's locked. You need the right key.");
                    ctx.playSound("minecraft:block.iron_door.close");
                }
                """;
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
