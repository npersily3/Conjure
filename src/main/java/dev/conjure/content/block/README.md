# dev.conjure.content.block — block shell and machine infrastructure

This package contains the block shell and everything that backs interactive (machine-archetype)
blocks. The JVM-baked properties of a block (render layer, hardness, sounds, push reaction) are
fixed by the `BlockArchetype` assigned at registration; the display name, texture, and behavior
are resolved at runtime from the `SlotDefinition`.

Only MACHINE-archetype blocks create a `ConjureBlockEntity`; all other archetypes return `null`
from `EntityBlock.newBlockEntity` and behave as plain blocks.

## Files

| File | Purpose |
|------|---------|
| `BlockArchetype.java` | Enum of the five property buckets pre-registered for the block pool: `SOLID` (200), `TRANSPARENT` (80), `CUSTOM_SHAPE` (80), `LIGHT` (80), `MACHINE` (60). Each carries a `PropFactory` that produces `BlockBehaviour.Properties`. |
| `ConjureBlock.java` | The block shell. Delegates `getName()` to `SlotDefinition.displayName`. On right-click, dispatches to the machine GUI (for `interaction=machine`) or the Rhino script runtime (for `interaction=script`) when interactivity is enabled. Only MACHINE-archetype instances implement `EntityBlock`. |
| `ConjureBlockEntity.java` | Block entity for MACHINE-archetype slots. Holds a 3-slot inventory (input / fuel / output). On each server tick, reads the live `SlotDefinition` for `recipe_input`, `recipe_output`, and `recipe_ticks` and processes items furnace-style. Syncs progress to the client via `ContainerData`. |
| `ConjureMenu.java` | Container menu for `ConjureBlockEntity`. Lays out three machine slots plus the full player inventory and hotbar. Exposes `getProgress()` / `getMaxProgress()` for `ConjureScreen`'s progress arrow. |
| `ConjureSlabBlock.java` | Slab shell (extends vanilla `SlabBlock`) backed by a `SlotKind.SLAB` slot; `getName()` resolves from the slot. Built as part of a material family, never prompted directly. |
| `ConjureStairBlock.java` | Stairs shell (extends `StairBlock`, neutral stone base state) backed by a `SlotKind.STAIRS` slot. |
| `ConjureWallBlock.java` | Wall shell (extends `WallBlock`) backed by a `SlotKind.WALL` slot. |
