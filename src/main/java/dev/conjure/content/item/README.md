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
| `ConjureItem.java` | Pre-registered item shell (500 of them). `getName(stack)` returns `SlotDefinition.displayName`. On right-click (server side), loads and executes the slot's behavior script via `ScriptRuntime`. |
| `ConjureBlockItem.java` | `BlockItem` subclass for block slots. Overrides `getName(stack)` to return the AI-generated display name from the block's `SlotDefinition`, replacing the internal `block_slot_N` translation key. |
