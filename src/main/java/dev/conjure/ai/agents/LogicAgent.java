package dev.conjure.ai.agents;

import com.mojang.logging.LogUtils;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;
import dev.conjure.script.ScriptRuntime;
import org.slf4j.Logger;

/**
 * Sub-agent responsible for generating a small JavaScript right-click behavior script.
 *
 * <p>Scripts are executed by the Rhino sandbox and may ONLY interact with the game through the
 * injected {@code ctx} object. No Java imports, no {@code require}, no globals other than standard
 * JS builtins and {@code ctx}.
 *
 * <h2>Available ctx API</h2>
 * <pre>
 *   // trigger (what caused this script to run)
 *   ctx.trigger()                             — "use" | "useOnBlock" | "hitEntity" | "swing"
 *
 *   // raw Minecraft object accessors — returns real MC objects; call any MC API on them
 *   ctx.getLevel()                            — net.minecraft.world.level.Level
 *   ctx.getPlayer()                           — net.minecraft.world.entity.player.Player
 *   ctx.getTargetEntity()                     — net.minecraft.world.entity.LivingEntity or null
 *   ctx.getTargetPos()                        — net.minecraft.core.BlockPos or null
 *   ctx.getHand()                             — net.minecraft.world.InteractionHand
 *
 *   // generic velocity (replaces launch / dashForward)
 *   ctx.applyVelocity(x, y, z)               — impulse added to player delta movement
 *
 *   // messaging / player
 *   ctx.message(text)                         — chat message to the player
 *   ctx.getPlayerName()                       — player name as String
 *   ctx.giveItem(itemId, count)               — e.g. "minecraft:diamond"
 *   ctx.consumeHeld()                         — consume one of the held item (single-use)
 *   ctx.heal(amount) / ctx.damage(amount)     — 2.0 = 1 heart
 *   ctx.giveEffect(id, seconds, amp)          — potion effect (amp 0 = level I)
 *   ctx.playSound(soundId)                    — e.g. "minecraft:entity.player.levelup"
 *
 *   // target entity (only when trigger is "hitEntity")
 *   ctx.hasTargetEntity()                     — true if a mob was hit
 *
 *   // target block (only when trigger is "useOnBlock" or block's own script)
 *   ctx.hasTargetBlock()                      — true if there is a target block
 *   ctx.getBlockActive() / ctx.setBlockActive(bool)   — read/toggle a door/lamp/safe
 *   ctx.breakTargetBlock() / ctx.setTargetBlock(id) / ctx.placeOnFace(id)
 *
 *   // cross-object tunables (key↔safe matching)
 *   ctx.heldStr(key) / ctx.heldNum(key)
 *   ctx.targetBlockStr(key) / ctx.targetBlockNum(key)
 *
 *   // reusable named effects (runs effects/<name>.js against this ctx)
 *   ctx.applyEffect(name)
 * </pre>
 */
public final class LogicAgent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String SYSTEM = """
            You write Minecraft behavior scripts for the Conjure mod. Scripts are executed in a
            sandboxed Rhino JS engine on the server. The ONLY API is a global object `ctx`.

            TRIGGER — check ctx.trigger() to know why the script is running:
              "use"        — player right-clicked in air
              "useOnBlock" — player right-clicked on a block
              "hitEntity"  — weapon hit a living entity
              "swing"      — player left-clicked in empty air

            RAW MINECRAFT ACCESSORS — return real net.minecraft objects; call any MC API on them:
              ctx.getLevel()                   — Level (cast to ServerLevel for spawning)
              ctx.getPlayer()                  — Player
              ctx.getTargetEntity()            — LivingEntity or null (non-null on hitEntity)
              ctx.getTargetPos()               — BlockPos or null (non-null on useOnBlock)
              ctx.getHand()                    — InteractionHand

            GENERIC VELOCITY (replaces old launch/dashForward):
              ctx.applyVelocity(x, y, z)       — add impulse to player movement

            HELPER VERBS (high-level convenience):
              ctx.message(text)                — chat message to the player
              ctx.getPlayerName()              — player name as String
              ctx.giveItem(itemId, count)      — give item (e.g. "minecraft:diamond")
              ctx.heal(amount) / ctx.damage(amount)  — 2.0 = 1 heart
              ctx.giveEffect(id, seconds, amp)       — potion effect (amp 0 = level I)
              ctx.playSound(soundId)           — e.g. "minecraft:entity.player.levelup"
              ctx.consumeHeld()                — consume one of the held item (single-use)
              ctx.hasTargetEntity()            — true if a mob was hit
              ctx.hasTargetBlock()             — true if there is a target block
              ctx.getBlockActive() / ctx.setBlockActive(bool)   — read/toggle a door/lamp/safe
              ctx.breakTargetBlock() / ctx.setTargetBlock(id) / ctx.placeOnFace(id)
              ctx.heldStr(key) / ctx.heldNum(key)               — tunables on the held item
              ctx.targetBlockStr(key) / ctx.targetBlockNum(key) — tunables on the target block
              ctx.applyEffect(name)            — run a named reusable effect script

            GENERIC ENTITY / WORLD HELPERS (prefer these over raw MC API):
              ctx.nearbyEntities(radius)       — array of living entities near the player (for-loop it)
              ctx.hurtEntity(entity, amount)   — magic damage to an entity (2 = 1 heart)
              ctx.effectEntity(entity, id, sec, amp) — potion effect on an entity
              ctx.knockbackEntity(entity, power)     — push an entity away from the player (0–4)
              ctx.setBlockAt(x, y, z, blockId) — place a block at world coords
              ctx.particle(particleId, x, y, z, count) — spawn a simple particle (e.g. "minecraft:heart")
              ctx.summon(entityId, x, y, z)    — summon an entity (e.g. "minecraft:zombie")

            KEY / UNLOCKING items: a lockable Conjure block carries a non-empty "keyId". Example:
              if (ctx.hasTargetBlock() && ctx.targetBlockStr("keyId") !== "") {
                ctx.setBlockActive(true); ctx.message("The lock clicks open.");
                ctx.playSound("minecraft:block.chest.open");
              }

            EXAMPLE — summon a lightning bolt at the player using raw MC API:
              var sl = ctx.getLevel();
              var p = ctx.getPlayer();
              var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(sl);
              if (bolt) { bolt.moveTo(p.getX(), p.getY(), p.getZ()); sl.addFreshEntity(bolt); }

            EXAMPLE — area knockback on swing (push nearby mobs away):
              if (ctx.trigger() === "swing") {
                var p = ctx.getPlayer();
                var level = ctx.getLevel();
                var nearby = level.getEntities(p, p.getBoundingBox().inflate(5));
                for (var i = 0; i < nearby.size(); i++) {
                  var e = nearby.get(i);
                  if (e !== p) e.knockback(2, p.getX()-e.getX(), p.getZ()-e.getZ());
                }
                ctx.playSound("minecraft:entity.player.attack.sweep");
              }

            EXAMPLE — spawn a phantom on right-click:
              var sl = ctx.getLevel();
              var p = ctx.getPlayer();
              var phantom = net.minecraft.world.entity.EntityType.PHANTOM.create(sl);
              if (phantom) {
                phantom.moveTo(p.getX(), p.getY()+3, p.getZ());
                sl.addFreshEntity(phantom);
                ctx.message("A phantom answers your call.");
              }

            JAVASCRIPT, NOT JAVA — this is the #1 cause of broken scripts. The sandbox runs JavaScript:
            - Declare variables with `var` ONLY.  WRONG: `BlockState s = x;`   RIGHT: `var s = x;`
            - NO `new net.minecraft...()` constructors. Use ctx helpers (ctx.summon, ctx.setBlockAt) or
              factory methods like `net.minecraft.world.entity.EntityType.ZOMBIE.create(level)`.
            - NO arrow functions.  WRONG: `list.filter(e => e != p)`   RIGHT: a plain `for` loop.
            - NO method references `::`. NO `java.util.*`, `java.io.*`, `java.lang.reflect.*` (denied).
            - Prefer the ctx helpers above (ctx.nearbyEntities, ctx.hurtEntity, …) over raw MC API.

            Rules:
            - NO require(), NO Packages, NO imports.
            - Use ONLY the ctx API above plus standard JS (Math, String, if, for, var).
            - Keep the script under 25 lines.
            - Give a REAL gameplay effect that fits the item's theme — a chat message may flavor it
              but is never the whole behavior.
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
        return generate(prompt, "");
    }

    /**
     * Generates a behavior script driven by a precise per-trigger {@code usageIntent} spec, then
     * compile-checks it in the Rhino sandbox and does ONE repair retry if it fails to compile
     * (mirrors {@link JsonHelper#extractAndParse}). The compile check catches the most common
     * failure mode — the model writing Java instead of JavaScript — before the script hits disk.
     *
     * @param prompt      the original user-supplied item description
     * @param usageIntent the technical trigger→action spec (may be blank/"none")
     * @return raw JavaScript source to be written as {@code <id>.js}
     * @throws Exception if the model call fails
     */
    public String generate(String prompt, String usageIntent) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        StringBuilder user = new StringBuilder("Write a behavior script for this Minecraft item: ")
                .append(prompt);
        if (usageIntent != null && !usageIntent.isBlank() && !usageIntent.equalsIgnoreCase("none")) {
            user.append("\n\nImplement EXACTLY this behavior spec (trigger: action): ").append(usageIntent);
        }

        String script = stripFences(provider.complete(SYSTEM, user.toString()));

        // Compile-check + one repair retry.
        String error = ScriptRuntime.get().validate(script);
        if (error == null) return script;

        String repairMsg = "Your previous script failed to compile in the Rhino JavaScript sandbox.\n"
                + "Compile error: " + error + "\n"
                + "Your previous script was:\n" + script + "\n\n"
                + "The usual cause is writing Java instead of JavaScript. Fix it: use only `var` (no "
                + "type declarations), no `new net.minecraft...()`, no arrow functions `=>`, no method "
                + "references `::`, no java.util/java.io/java.lang.reflect. Respond with ONLY the "
                + "corrected raw JavaScript.";
        String repaired = stripFences(provider.complete(SYSTEM, repairMsg));
        String stillBad = ScriptRuntime.get().validate(repaired);
        if (stillBad != null) {
            LOGGER.warn("[Conjure] behavior script still invalid after one repair: {}", stillBad);
        }
        return repaired;
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
