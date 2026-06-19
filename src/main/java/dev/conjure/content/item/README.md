# dev.conjure.content.item — item shells

This package contains the two item shell classes. Both resolve their display name at runtime from
the `SlotRegistry`; `ConjureItem` additionally runs its `behaviorScriptId` through the Rhino
sandbox on right-click.

`ConjureBlockItem` exists because the generic `BlockItem` uses the block's translation key — which
for Conjure blocks would show the internal `block_slot_N` id — so a subclass is needed to
override `getName(stack)`.

## Files

| File | Purpose |
|------|---------|
| `ConjureItem.java` | Pre-registered item shell (500 of them). `getName(stack)` returns `SlotDefinition.displayName`. Runs the slot's behavior script via `ScriptRuntime` on three hooks: `use` (right-click air, no target), `useOn` (right-click a block — passes the clicked block/face, so a key can unlock a safe), and `hurtEnemy` (hitting a mob — passes the mob for weapon effects). `slotDef()` exposes the slot for cross-object script reads. |
| `ConjureBlockItem.java` | `BlockItem` subclass for block slots, parameterised by `SlotKind` so it backs the cube pool (`BLOCK`) and the shaped variant pools (`SLAB`/`STAIRS`/`WALL`). Overrides `getName(stack)` to return the AI-generated display name, replacing the internal `*_slot_N` key. `slotDef()` exposes the slot for cross-object script reads. |
