# dev.conjure.script — sandboxed Rhino behavior runtime

This package implements the behavior-script execution layer. Because the JVM cannot load
AI-authored compiled Java, behavior is interpreted JavaScript (Mozilla Rhino). The seam is
filesystem-based: generation writes `<gamedir>/conjure/generated/scripts/<id>.js`; shell classes
invoke `ScriptRuntime.run(id, ctx)` on an interaction.

**Sandbox philosophy (changed):** rather than a fixed menu of pre-baked Java "verbs", scripts now
get **direct access to the real Minecraft API**. The `ClassShutter` allows `net.minecraft.*` (plus a
small set of `java.lang` value types and the `ScriptContext` bridge) and denies everything else, so
`Runtime`, `System`, `Thread`, reflection and IO remain blocked. The instruction budget and
interpreted mode still bound runaway scripts. `ctx` is now a thin layer of **generic** helpers plus
raw accessors (`getLevel()`, `getPlayer()`, …) — the model expresses any effect (lightning, AoE,
particles, summons) with real MC calls instead of a bespoke verb.

`// ponytail:` `net.minecraft` is broad — a script can reach `level.getServer()`. Accepted for
single-player untrusted AI code; tighten to a per-class denylist if dedicated-server sync lands.

## Files

| File | Purpose |
|------|---------|
| `ScriptRuntime.java` | Singleton that loads, compiles (cached by scriptId + file mtime), and executes behavior scripts in a hardened Rhino sandbox: allowlist `ClassShutter`, interpreted mode (so the instruction observer fires), and a 100 k-instruction budget. Also resolves + runs **named reusable effects** from `<gamedir>/conjure/generated/effects/<name>.js` (`runEffect`). |
| `ScriptContext.java` | The `ctx` host object injected into every script: a few generic gameplay helpers plus raw accessors that return real `net.minecraft` objects. Carries a **trigger** tag (`use`/`useOnBlock`/`hitEntity`/`swing`) and optional **target block** / **target entity**; target-aware helpers no-op when their target is absent. No imports, no IO, no reflection. |
| `ScriptException.java` | Checked exception thrown by `ScriptRuntime` when a script file is missing or throws at runtime. Callers catch it, log it, and surface a short message to the player. |

Hooks that run a script, each tagged via `ctx.trigger()`: right-click **in the air** (`use`),
right-click **on a block** (`useOnBlock`, target block + clicked face), **hit a mob**
(`hitEntity`, target entity), and a **swing in empty air** (`swing`, fires even on a miss — driven
by the `dev.conjure.network` swing packet). A stateful block's own right-click runs with the block
as target. One script per slot serves all of them.

## ctx API summary

| Method | Effect |
|--------|--------|
| `ctx.trigger()` | Why the script ran: `"use"`, `"useOnBlock"`, `"hitEntity"`, or `"swing"` |
| `ctx.getLevel()` / `ctx.getPlayer()` | The real `net.minecraft` `Level` / `Player` — call any MC API on them |
| `ctx.getTargetEntity()` / `ctx.getTargetPos()` / `ctx.getHand()` | The hit mob (nullable), target block pos (nullable), and interaction hand |
| `ctx.applyEffect(name)` | Run a reusable effect script `effects/<name>.js` against this same `ctx` |
| `ctx.message(text)` / `ctx.getPlayerName()` | Chat message / player name |
| `ctx.giveItem(id, count)` / `ctx.consumeHeld()` | Give items by registry id; consume one held item (no-op in creative) |
| `ctx.heal(amount)` / `ctx.damage(amount)` | Restore / deal HP to the player (2.0 = 1 heart) |
| `ctx.giveEffect(id, seconds, amp)` | Apply a potion effect to the player (`amp` 0 = level I) |
| `ctx.applyVelocity(x, y, z)` | Add velocity to the player (client-resynced) |
| `ctx.playSound(soundId)` | Play a sound at the player |
| `ctx.hasTargetEntity()` / `ctx.hasTargetBlock()` | Whether a mob / block target is present |
| `ctx.getBlockActive()` / `ctx.setBlockActive(bool)` | Read / toggle a stateful (ACTIVATABLE) block |
| `ctx.breakTargetBlock()` / `ctx.setTargetBlock(id)` / `ctx.placeOnFace(id)` | Break, replace, or place against the target block |
| `ctx.heldStr(key)` / `ctx.heldNum(key)` | Read a tunable on the held item's slot definition |
| `ctx.targetBlockStr(key)` / `ctx.targetBlockNum(key)` | Read a tunable on the target block's slot definition (e.g. a safe's `keyId`) |

Anything richer (summon an entity, area effects, particles, world edits) is written directly against
`ctx.getLevel()` / `ctx.getPlayer()` with the real Minecraft API.
