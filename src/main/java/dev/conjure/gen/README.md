# dev.conjure.gen — generation orchestration

This package is the top-level orchestration layer for content generation. It owns the background
thread pool, dispatches prompts to per-kind pipelines, manages the on-disk dynamic resource pack,
and handles PNG encoding. The `pipeline/` sub-package holds the per-kind pipeline implementations;
`assets/` holds per-kind asset writers.

All model calls run on the single `conjure-gen` background thread (via `GenerationService`'s
executor) so the game thread is never blocked.

## Files

| File | Purpose |
|------|---------|
| `GenerationService.java` | The single-piece dispatcher. Routes a prompt through `RouterAgent` to the matching `GenerationPipeline` on the `conjure-gen` worker thread. `generateForMod` runs one economy piece synchronously with a `GenerationContext` (resource pieces route to `WorldgenPipeline`) and returns the created id. Also `regenerateItem`/`regenerate`. |
| `GenerationStatus.java` | Common-side atomic counter of in-flight generation tasks. The client HUD (`ConjureHud`) reads it to draw the thinking indicator and to keep the game running while unfocused. |
| `GenerationContext.java` | Thread-local passed during whole-mod generation: the resolved ingredient ids the current piece is crafted from, plus a slot for the pipeline to report the id it created so the next piece can reference it. Absent for plain `/conjure new`. |
| `ModService.java` | Multi-piece orchestrator. `build` (auto, `/conjure new`) plans a flat piece list and enqueues each. `buildMod` (`/conjure mod`) plans a **gameplay-loop economy** (`ModPlannerAgent.planMod`) and generates pieces in resource→material→product order, threading each created id into the recipes of the pieces crafted from it. |
| `DynamicPackManager.java` | Owns the on-disk pack at `<gamedir>/conjure/generated/` (registered as both resource pack and datapack). Per-kind write methods for cube textures/models/blockstates/item-models, for slab/stairs/wall assets (which reuse a base texture), and `writeActivatableAssets` (two textures + a two-variant blockstate for stateful off/on blocks). Generic `write`/`writePngAt` helpers for custom paths (recipes use `write`). |
| `RecipeTemplates.java` | Deterministic recipe JSON builders — shaped/shapeless/smelting/blasting/smithing/stonecutting/campfire — plus material-family wiring (`writeFamily`), `writeChosen` (dispatch a `RecipeAgent` choice; shared by item+block pipelines), and `writeChain` (mod-economy craft/smelt from generated ingredients). Self-check in `main`. |
| `LootTableTemplates.java` | Writes a self-drop block loot table (`writeSelfDrop`) to `data/conjure/loot_table/blocks/<id>.json` so conjured blocks drop in survival. |
| `WorldgenWriter.java` | Emits the runtime datapack JSON that makes a conjured block/mob appear in the world, one method per resource type: `writeOre` (underground vein), `writePlant` (surface `random_patch`, `vegetal_decoration`), `writeTree` (`minecraft:tree`), `writeMobSpawn` (NeoForge `add_spawns`). Decoupled from generation; takes effect after a world rejoin. |
| `PixelTexture.java` | PNG I/O + post-processing: `fromPng(bytes, size, kind)` downscales and, per kind, masks an item's background to transparent (border-sampled flood-fill), and for BLOCK/FLUID hard-downscales to 16×16, makes the texture seamlessly tileable, and palette-quantizes for a flat 16-bit look. `writePng`/`parseColor` helpers; self-check in `main`. |

## Sub-packages

| Package | Contents |
|---------|----------|
| [`assets/`](assets/README.md) | Per-kind asset writers: `EntityAssets` (skin PNG), `FluidAssets` (still + flow PNGs). |
| [`pipeline/`](pipeline/README.md) | `GenerationPipeline` interface + `ItemPipeline`, `BlockPipeline`, `FluidPipeline`, `EntityPipeline`, `StructurePipeline`, and `PipelineSupport`. |
