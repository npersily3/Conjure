# dev.conjure.content.structure — structure placement

This package contains the code that turns a `SlotDefinition`'s stored block layout into actual
blocks placed in the world. Generation (asking the AI for a layout) happens in
`gen/pipeline/StructurePipeline`; this package is purely placement-time logic, called by
`/conjure place <index>` from `ConjureCommands`.

The layout is stored in the `SlotDefinition`'s `strings` map (palette JSON, layers JSON) and
`numbers` map (sizeX/Y/Z) — no new fields on `SlotDefinition`.

## Files

| File | Purpose |
|------|---------|
| `StructurePlacer.java` | Reads the `palette` and `layers` JSON arrays from a configured `SlotDefinition`, resolves each palette entry to a `BlockState`, and places the 3D grid into the world starting from a given origin. Air entries are skipped; unknown block ids fall back to stone with a warning. Must be called from the server thread. |
