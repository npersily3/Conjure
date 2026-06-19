# dev.conjure.script — sandboxed Rhino behavior runtime

This package implements the behavior-script execution layer. Because the JVM cannot load
AI-authored compiled Java, behavior is interpreted JavaScript (Mozilla Rhino). The seam is
filesystem-based: generation writes `<gamedir>/conjure/generated/scripts/<id>.js`; shell classes
invoke `ScriptRuntime.run(id, ctx)` on right-click.

The sandbox is strict because it runs AI-written code in-process. See `ScriptRuntime` for the
full sandbox design (ClassShutter, instruction budget, interpreted mode).

## Files

| File | Purpose |
|------|---------|
| `ScriptRuntime.java` | Singleton that loads, compiles (cached by scriptId + file mtime), and executes behavior scripts in a hardened Rhino sandbox: `ClassShutter` denies all Java class access, interpreted mode ensures the instruction observer fires, and a 100 k-instruction budget aborts infinite loops. |
| `ScriptContext.java` | The `ctx` host object injected into every script. Provides the whitelisted gameplay API (see table below). A context optionally carries a **target block** (the block a `useOn` item clicked, or a block running its own script) and a **target entity** (a mob a weapon hit); target-aware verbs no-op when their target is absent, so the same script is safe across hooks. No Java imports, no IO, no reflection. |
| `ScriptException.java` | Checked exception thrown by `ScriptRuntime` when a script file is missing or throws at runtime. Callers catch it, log it, and surface a short message to the player. |

Hooks that run a script: an item right-clicked **in the air** (`use`, no target), right-clicked
**on a block** (`useOn`, target block + clicked face), or used to **hit a mob** (`hurtEnemy`, target
entity); and a stateful block's own right-click (target block = itself). One script per slot serves
all of them.

## ctx API summary

| Method | Effect |
|--------|--------|
| `ctx.message(text)` | Send a chat message to the interacting player |
| `ctx.getPlayerName()` | Returns player name as a JS string |
| `ctx.giveItem(itemId, count)` | Give items by registry id (e.g. `"minecraft:diamond"`) |
| `ctx.consumeHeld()` | Consume one of the held item (single-use items); no-op in creative |
| `ctx.heal(amount)` / `ctx.damage(amount)` | Restore / deal HP to the player (2.0 = 1 heart) |
| `ctx.giveEffect(id, seconds, amp)` | Apply a potion effect to the player (`amp` 0 = level I) |
| `ctx.ignite(seconds)` | Set the player on fire (clamped 0–60 s) |
| `ctx.launch(power)` / `ctx.dashForward(power)` | Fling the player up / dash in look direction (0–4) |
| `ctx.lightning()` | Strike lightning at the player |
| `ctx.explode(power)` | Explosion that damages mobs but breaks **no** blocks (0–8) |
| `ctx.playSound(soundId)` | Play a sound at the player |
| `ctx.spawnParticleHere()` | Burst of HEART particles at player position |
| `ctx.hasTargetEntity()` | True when a mob was hit (weapon hook) |
| `ctx.damageTarget(amt)` / `ctx.igniteTarget(s)` / `ctx.knockbackTarget(p)` / `ctx.effectTarget(id,s,amp)` | Act on the hit mob |
| `ctx.hasTargetBlock()` | True when there is a target block |
| `ctx.getBlockActive()` / `ctx.setBlockActive(bool)` | Read / toggle a stateful (ACTIVATABLE) block — open a door, unlock a safe |
| `ctx.breakTargetBlock()` / `ctx.setTargetBlock(id)` / `ctx.placeOnFace(id)` | Break, replace, or place against the target block |
| `ctx.heldStr(key)` / `ctx.heldNum(key)` | Read a tunable on the held item's slot definition |
| `ctx.targetBlockStr(key)` / `ctx.targetBlockNum(key)` | Read a tunable on the target block's slot definition (e.g. a safe's `keyId`) |
