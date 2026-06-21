# dev.conjure.network — client→server packets

Minimal NeoForge networking for interactions that have no server-side hook. Currently just the
**swing trigger**: a left-click in empty air is a client-only signal
(`PlayerInteractEvent.LeftClickEmpty`), so we forward it to the server to run a behavior script with
`ctx.trigger() == "swing"` — enabling "fires even on a miss" item effects (e.g. a wind sword that
knocks back on every swing).

Both classes **self-register** via `@EventBusSubscriber`, so no wiring in `Conjure.java` is needed.

## Files

| File | Purpose |
|------|---------|
| `SwingPayload.java` | Zero-byte `CustomPacketPayload` (`conjure:swing`) with its `StreamCodec`. |
| `SwingClientHandler.java` | `@EventBusSubscriber(Bus.GAME, Dist.CLIENT)`. On `LeftClickEmpty`, if the main hand holds a configured `ConjureItem` with a behavior script, sends a `SwingPayload` to the server. |
| `SwingPacketHandler.java` | `@EventBusSubscriber(Bus.MOD)`. Registers the payload (`RegisterPayloadHandlersEvent` → `playToServer`) and, on receipt, runs the held item's behavior script with a no-target `ScriptContext` tagged `"swing"`. |
