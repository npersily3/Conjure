# dev.conjure.persist — slot persistence

This package makes generated content survive a server restart. After a `SlotDefinition` is
committed to the `SlotRegistry`, `SlotStore.save` writes a JSON sidecar to
`<gamedir>/conjure/slots/<kind>_<index>.json`. On the next startup, `SlotStoreLoader` restores
all such files back into the registry before any player or command can interact with slots.

Textures, models, and behavior scripts are already on disk (written by `DynamicPackManager` and
`PipelineSupport`), so a restored slot is fully functional without any additional regeneration.

## Files

| File | Purpose |
|------|---------|
| `SlotStore.java` | Serialises a `SlotDefinition` to a per-slot JSON file and deserialises all slot files on startup. Handles all `SlotKind` values; the filename `<kind>_<n>.json` encodes both dimensions. |
| `SlotStoreLoader.java` | `@EventBusSubscriber` that hooks `ServerStartingEvent` to call `SlotStore.loadAll()` before any world ticks or commands run. |
