# dev.conjure.command — /conjure command surface

This package contains the single class that registers the entire `/conjure` command tree with
Brigadier via `RegisterCommandsEvent`. All model calls are dispatched to background threads so
the game thread is never blocked; feedback messages are posted back to the server thread via
`MinecraftServer.execute`.

## Files

| File | Purpose |
|------|---------|
| `ConjureCommands.java` | Registers the `/conjure` sub-commands: `new`, `mod`, `list`, `edit`, `place`, `regenerate`, `delete`, and `nuke`. Includes a `parseKind` helper that maps `item`/`block`/`fluid`/`entity`/`structure` keywords to `SlotKind`. |

## Sub-command summary

| Command | Backend call | Notes |
|---------|-------------|-------|
| `/conjure new <prompt>` | `ModService.build(…, expansive=false)` | Single concrete prompt → one piece; themed/plural → many |
| `/conjure mod <desc>` | `ModService.buildMod(…)` | Always decomposes into many pieces |
| `/conjure list` | reads `SlotRegistry` | Shows configured item slots only |
| `/conjure edit <i> <prompt>` | `GenerationService.regenerateItem` | Preserves slot index so existing item stacks stay valid |
| `/conjure place <i>` | `StructurePlacer.place` | Places structure at player position; slot must be configured |
| `/conjure regenerate <kind> <i> <prompt>` | `GenerationService.regenerate` | Re-runs any kind's pipeline on an existing slot in place (`runForSlot`), preserving its id |
| `/conjure delete <kind> <i>` | `SlotRegistry.reset` + `SlotStore.delete` | Clears one slot back to empty (placeholder); reloads client assets + datapacks |
| `/conjure nuke` | `SlotRegistry.resetAll` + `SlotStore.deleteAll` | Wipes ALL generated content across every kind |
