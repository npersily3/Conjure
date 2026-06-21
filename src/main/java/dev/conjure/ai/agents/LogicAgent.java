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

            Rules:
            - NO Java imports, NO require(), NO Packages, NO Java interop with disallowed classes.
            - Allowed extra classes: net.minecraft.* (already accessible via raw accessors).
            - Use ONLY the ctx API above plus standard JS (Math, String, if, for, var/let/const).
            - Keep the script under 25 lines.
            - Give a REAL gameplay effect that fits the item's theme — chat message may flavor it
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
        TextModelProvider provider = ProviderFactory.text();
        String userMsg = "Write a behavior script for this Minecraft item: " + prompt;
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
