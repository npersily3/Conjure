# dev.conjure.gen.pipeline — per-kind generation pipelines

This package contains one `GenerationPipeline` implementation per content kind, plus the shared
`PipelineSupport` helper. Each pipeline owns the full end-to-end flow for its kind: allocate a
free slot, call the relevant AI agents, write assets, build and commit a `SlotDefinition`.

`GenerationService` maps a `SlotKind` to its pipeline and invokes `run`. Adding support for a new
content kind means implementing `GenerationPipeline` and registering it in `GenerationService` —
no edits to existing pipelines are needed.

## Files

| File | Purpose |
|------|---------|
| `GenerationPipeline.java` | Interface: a single `run(prompt, feedback)` method. Implementations allocate a slot, call agents, write assets, commit, and report progress via `feedback`. |
| `PipelineSupport.java` | Shared helpers: `commit(def)` (registry swap + persist + client reload), `commitQuiet(def)` (no reload — for batching a family), `reloadIfClient()` / `reloadData()` (asset + datapack reload), `writeScript(id, source)`, and `describe(e)`. |
| `ItemPipeline.java` | ITEM generation: texture → name + description → behavior script → commit. Also exposes `runForSlot(index, …)` for `/conjure edit`. |
| `BlockPipeline.java` | BLOCK generation: texture → name → machine/script/plain decision → allocates MACHINE or SOLID slot → commit. Any block (recipes enabled) also gets a shapeless crafting recipe from vanilla ingredients. A building material instead expands into a **family** (base + smooth/bricks cubes + slab/stairs/wall) wired with vanilla-pattern recipes, committed quietly with one asset + datapack reload at the end. |
| `FluidPipeline.java` | FLUID generation: still texture (reused for flow) → name → writes still + flow PNGs via `FluidAssets` → commit. |
| `EntityPipeline.java` | ENTITY generation: skin texture → name → writes skin PNG via `EntityAssets` → assigns default attribute numbers → commit. |
| `StructurePipeline.java` | STRUCTURE generation: asks the text model for a palette + 3D block grid (≤ 9×9×9) → name → stores layout in `SlotDefinition.strings/numbers` → commit. Placement is deferred to `/conjure place`. |
