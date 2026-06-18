# dev.conjure.content — slot store (core data contract)

This package defines the three types that make Conjure's "no-relaunch" trick work. They are the
single shared contract between the registration layer (frozen JVM registries), the AI generation
layer, and every shell class (item, block, fluid, entity). Nothing outside this package should
add fields to `SlotDefinition`; use its `numbers` and `strings` maps for new per-slot data.

The sub-packages contain the concrete shell classes for each content kind.

## Files

| File | Purpose |
|------|---------|
| `SlotKind.java` | Enum of the five content families: `ITEM`, `BLOCK`, `FLUID`, `ENTITY`, `STRUCTURE`. Used as the first dimension of the `SlotRegistry` key. |
| `SlotDefinition.java` | Mutable runtime description of a pre-registered slot: `displayName`, `texturePath`, `behaviorScriptId`, `sourcePrompt`, plus open-ended `numbers` and `strings` maps for extra data. When `configured == false` the slot is still an empty placeholder. |
| `SlotRegistry.java` | Concurrent `(SlotKind, index) → SlotDefinition` map. `put(def)` swaps a fully-built definition atomically; `firstFree(kind, poolSize)` finds the lowest unconfigured slot for new generation. |

## Sub-packages

| Package | Contents |
|---------|----------|
| [`block/`](block/README.md) | `ConjureBlock`, `ConjureBlockEntity`, `ConjureMenu`, `BlockArchetype` |
| [`entity/`](entity/README.md) | `ConjureMob` |
| [`fluid/`](fluid/README.md) | `ConjureFluidType`, `ConjureBucketItem`, `ConjureLiquidBlock`, `ConjureFluidClientExtensions` |
| [`item/`](item/README.md) | `ConjureItem`, `ConjureBlockItem` |
| [`structure/`](structure/README.md) | `StructurePlacer` |
