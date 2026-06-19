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
| `ScriptContext.java` | The `ctx` host object injected into every script. Provides the whitelisted gameplay API: `message(text)`, `giveItem(itemId, count)`, `heal(amount)`, `damage(amount)`, `giveEffect(id, seconds, amp)`, `playSound(soundId)`, `consumeHeld()`, `spawnParticleHere()`, `getPlayerName()`. No Java imports, no IO, no reflection. |
| `ScriptException.java` | Checked exception thrown by `ScriptRuntime` when a script file is missing or throws at runtime. Callers catch it, log it, and surface a short message to the player. |

## ctx API summary

| Method | Effect |
|--------|--------|
| `ctx.message(text)` | Send a chat message to the interacting player |
| `ctx.giveItem(itemId, count)` | Give items by registry id (e.g. `"minecraft:diamond"`) |
| `ctx.heal(amount)` | Restore HP (2.0 = 1 heart) |
| `ctx.damage(amount)` | Deal magic damage |
| `ctx.giveEffect(id, seconds, amp)` | Apply a potion effect (`amp` 0 = level I), e.g. `"minecraft:speed"` |
| `ctx.playSound(soundId)` | Play a sound at the player, e.g. `"minecraft:entity.player.levelup"` |
| `ctx.consumeHeld()` | Consume one of the held item (single-use items); no-op in creative |
| `ctx.spawnParticleHere()` | Burst of HEART particles at player position |
| `ctx.getPlayerName()` | Returns player name as a JS string |
