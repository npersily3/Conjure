# dev.conjure.content.block — block shell and workbench infrastructure

This package contains the block shell and everything that backs interactive (MACHINE-archetype)
blocks, called **workbenches**. The JVM-baked properties of a block (render layer, hardness, sounds,
push reaction) are fixed by the `BlockArchetype` assigned at registration; the display name, texture,
and behavior are resolved at runtime from the `SlotDefinition`.

Only MACHINE-archetype blocks create a `ConjureBlockEntity`; all other archetypes return `null`
from `EntityBlock.newBlockEntity` and behave as plain blocks.

**Stateful blocks** (doors / lamps / switches / safes) use the `ACTIVATABLE` archetype, backed by
`ConjureActivatableBlock`. Because state *properties* are baked at registration and frozen, the
on/off `active` property must be pre-declared on a dedicated subclass rather than added per slot;
the generation layer fills in the two textures (off/on) and, for locks, a gating script. Right-click
toggles `active` directly, unless the slot has a behavior script (a lock), in which case the script
decides — a matching key item opens it via `ctx.setBlockActive`.

The workbench recipe schema is single-sourced in `WorkbenchRecipes`/`WorkbenchRecipe`: the
generation pipeline writes it, the block entity runs it, and the JEI plugin reads it, so the three
can never drift. A recipe is a flexible list of 1–9 ingredients → an output (optionally gated on a
fuel item), processed over a number of ticks; the GUI lays out one slot per ingredient dynamically,
so it need not be a square grid.

## Files

| File | Purpose |
|------|---------|
| `BlockArchetype.java` | Enum of the property buckets pre-registered for the block pool: `SOLID` (200), `TRANSPARENT` (80), `CUSTOM_SHAPE` (80), `LIGHT` (80), `MACHINE` (60), `ACTIVATABLE` (80, appended last so it never shifts earlier slot indices). Each carries a `PropFactory` that produces `BlockBehaviour.Properties`; `ACTIVATABLE` keys its light level off the `active` state. |
| `ConjureBlock.java` | The block shell. Delegates `getName()` to `SlotDefinition.displayName`. On right-click, dispatches to the workbench GUI (for workbench/legacy `machine` slots, via `WorkbenchRecipes.isWorkbench`) or the Rhino script runtime (for `interaction=script`) when interactivity is enabled. Only MACHINE-archetype instances implement `EntityBlock`. |
| `ConjureActivatableBlock.java` | `ConjureBlock` subclass for the `ACTIVATABLE` archetype. Adds a runtime-toggleable `active` boolean property (open/closed, lit/unlit, locked/unlocked) — the pre-registered way to ship stateful blocks despite the registry freeze. Right-click toggles `active`, or runs the slot's gating script (a lock) which leaves toggling to a matching key. `active` drives the blockstate model swap and the emitted light level (`activeLight` tunable). |
| `WorkbenchRecipe.java` | Immutable recipe record (gridSize/inputs/output/outputCount/fuel/ticks + slot index & display name). The shared contract between the pipeline (writer), block entity (runner), and JEI (reader). |
| `WorkbenchRecipes.java` | Single source of truth for the workbench recipe key schema in a `SlotDefinition`. `write` (pipeline), `of` (block entity), `all` (JEI), `isWorkbench`; includes a legacy `interaction=machine` read shim. |
| `ConjureBlockEntity.java` | Block entity for MACHINE-archetype slots. Fixed-capacity container used dynamically: up to 9 ingredient cells + 1 fuel + 1 output. Each server tick reads the live `WorkbenchRecipe` via `WorkbenchRecipes.of`, requires every ingredient (and fuel, if any) present, and produces `outputCount` of the output every `ticks`. Syncs progress via `ContainerData`. |
| `ConjureMenu.java` | Container menu for `ConjureBlockEntity`. Lays out slots **dynamically** from the recipe (one input per ingredient, wrapped 3 per row; optional fuel; output) plus the player inventory and hotbar. Input slots only accept their required item. Exposes `getProgress()` / `getMaxProgress()` for `ConjureScreen`. |
| `ConjureSlabBlock.java` | Slab shell (extends vanilla `SlabBlock`) backed by a `SlotKind.SLAB` slot; `getName()` resolves from the slot. Built as part of a material family, never prompted directly. |
| `ConjureStairBlock.java` | Stairs shell (extends `StairBlock`, neutral stone base state) backed by a `SlotKind.STAIRS` slot. |
| `ConjureWallBlock.java` | Wall shell (extends `WallBlock`) backed by a `SlotKind.WALL` slot. |
