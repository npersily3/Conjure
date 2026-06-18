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
| `GenerationService.java` | The single-piece dispatcher. Routes a prompt through `RouterAgent` to the matching `GenerationPipeline`, runs it on the `conjure-gen` worker thread. Also exposes `regenerateItem` for `/conjure edit`. Wraps every task in `GenerationStatus.begin()/end()`. |
| `GenerationStatus.java` | Common-side atomic counter of in-flight generation tasks. The client HUD (`ConjureHud`) reads it to draw the thinking indicator and to keep the game running while unfocused. |
| `ModService.java` | Multi-piece orchestrator. Runs `ModPlannerAgent` on a `conjure-mod-planner` daemon thread to decompose a description into pieces, then enqueues each piece onto `GenerationService`. Two modes: `buildMod` (always-expansive) and `build` (auto-mode for `/conjure new`). |
| `DynamicPackManager.java` | Owns the on-disk pack at `<gamedir>/conjure/generated/` (registered as both resource pack and datapack). Per-kind write methods for cube textures/models/blockstates/item-models and for slab/stairs/wall assets (which reuse a base texture). Generic `write`/`writePngAt` helpers for custom paths (recipes use `write`). |
| `RecipeTemplates.java` | Deterministic vanilla-pattern recipe JSON for a material family (3-across → 6 slabs, stairs pattern → 4, smelt → smooth, stonecutter). Pure `*Json` builders + `writeFamily`; runnable self-check in `main`. |
| `PixelTexture.java` | Static utilities for PNG I/O: `fromPng(byte[], targetSize)` nearest-neighbour downscales a raw PNG to a target size; `writePng(argb, path)` encodes an ARGB grid to a PNG file; `parseColor` parses `#RRGGBB`/`#AARRGGBB` hex strings. |

## Sub-packages

| Package | Contents |
|---------|----------|
| [`assets/`](assets/README.md) | Per-kind asset writers: `EntityAssets` (skin PNG), `FluidAssets` (still + flow PNGs). |
| [`pipeline/`](pipeline/README.md) | `GenerationPipeline` interface + `ItemPipeline`, `BlockPipeline`, `FluidPipeline`, `EntityPipeline`, `StructurePipeline`, and `PipelineSupport`. |
