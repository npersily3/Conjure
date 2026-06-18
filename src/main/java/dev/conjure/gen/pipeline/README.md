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
| `GenerationPipeline.java` | Interface: `run(prompt, feedback)` (allocate a free slot + generate) plus a `runForSlot(index, prompt, feedback)` default used by `/conjure regenerate` to re-generate an existing slot in place. The default delegates to `run` (allocates a new slot); pipelines that support targeting override it. |
| `PipelineSupport.java` | Shared helpers: `commit(def)` (registry swap + persist + client reload), `commitQuiet(def)` (no reload — for batching a family), `reloadIfClient()` / `reloadData()` (asset + datapack reload), `writeScript(id, source)`, and `describe(e)`. |
| `ItemPipeline.java` | ITEM generation: texture → name + description → behavior script → commit. Overrides `runForSlot(index, …)` for `/conjure edit` and `/conjure regenerate item`. |
| `BlockPipeline.java` | BLOCK generation: texture → name → workbench/script/plain decision → allocates MACHINE or SOLID slot → commit. Workbench slots write their recipe via `WorkbenchRecipes`. Any block (recipes enabled) also gets a shapeless crafting recipe from vanilla ingredients. A building material instead expands into a **family** (base + smooth/bricks cubes + slab/stairs/wall) wired with vanilla-pattern recipes. Overrides `runForSlot` to regenerate a single block in place (no family; workbench only if the slot is in the MACHINE range). |
| `FluidPipeline.java` | FLUID generation: still texture (reused for flow) → name → writes still + flow PNGs and a hardcoded bucket icon/model (tinted to the fluid colour) via `FluidAssets` → commit. Overrides `runForSlot` for `/conjure regenerate fluid`. |
| `EntityPipeline.java` | ENTITY generation: skin texture → name → writes skin PNG via `EntityAssets` → assigns default attribute numbers → commit. Overrides `runForSlot` for `/conjure regenerate entity`. |
| `StructurePipeline.java` | STRUCTURE generation: asks the text model for a palette + 3D block grid (≤ 9×9×9) → name → stores layout in `SlotDefinition.strings/numbers` → commit. Overrides `runForSlot` for `/conjure regenerate structure`. Placement is deferred to `/conjure place`. |
