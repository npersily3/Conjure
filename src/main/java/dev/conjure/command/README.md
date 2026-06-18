# dev.conjure.command — /conjure command surface

This package contains the single class that registers the entire `/conjure` command tree with
Brigadier via `RegisterCommandsEvent`. All model calls are dispatched to background threads so
the game thread is never blocked; feedback messages are posted back to the server thread via
`MinecraftServer.execute`.

## Files

| File | Purpose |
|------|---------|
| `ConjureCommands.java` | Registers five sub-commands: `new <prompt>` (smart generate — single piece or expand), `mod <description>` (whole-mod decomposition via `ModService`), `list` (show configured item slots), `edit <index> <prompt>` (regenerate an existing item slot), and `place <index>` (place a generated structure via `StructurePlacer`). |

## Sub-command summary

| Command | Backend call | Notes |
|---------|-------------|-------|
| `/conjure new <prompt>` | `ModService.build(…, expansive=false)` | Single concrete prompt → one piece; themed/plural → many |
| `/conjure mod <desc>` | `ModService.buildMod(…)` | Always decomposes into many pieces |
| `/conjure list` | reads `SlotRegistry` | Shows configured item slots only |
| `/conjure edit <i> <prompt>` | `GenerationService.regenerateItem` | Preserves slot index so existing item stacks stay valid |
| `/conjure place <i>` | `StructurePlacer.place` | Places structure at player position; slot must be configured |
