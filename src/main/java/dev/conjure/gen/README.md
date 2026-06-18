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
| `GenerationService.java` | The single-piece dispatcher. Routes a prompt through `RouterAgent` to the matching `GenerationPipeline`, runs it on the `conjure-gen` worker thread. Also exposes `regenerateItem` for `/conjure edit`. |
| `ModService.java` | Multi-piece orchestrator. Runs `ModPlannerAgent` on a `conjure-mod-planner` daemon thread to decompose a description into pieces, then enqueues each piece onto `GenerationService`. Two modes: `buildMod` (always-expansive) and `build` (auto-mode for `/conjure new`). |
| `DynamicPackManager.java` | Owns the on-disk pack at `<gamedir>/conjure/generated/`. Provides per-kind write methods for textures, models, block states, and block-item models. Also exposes generic `write` and `writePngAt` helpers for pipelines that need custom paths. |
| `PixelTexture.java` | Static utilities for PNG I/O: `fromPng(byte[], targetSize)` nearest-neighbour downscales a raw PNG to a target size; `writePng(argb, path)` encodes an ARGB grid to a PNG file; `parseColor` parses `#RRGGBB`/`#AARRGGBB` hex strings. |

## Sub-packages

| Package | Contents |
|---------|----------|
| [`assets/`](assets/README.md) | Per-kind asset writers: `EntityAssets` (skin PNG), `FluidAssets` (still + flow PNGs). |
| [`pipeline/`](pipeline/README.md) | `GenerationPipeline` interface + `ItemPipeline`, `BlockPipeline`, `FluidPipeline`, `EntityPipeline`, `StructurePipeline`, and `PipelineSupport`. |
