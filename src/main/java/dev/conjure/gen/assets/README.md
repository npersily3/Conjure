# dev.conjure.gen.assets — per-kind asset writers

This package contains one utility class per content kind that needs non-standard asset paths.
Items and blocks write assets directly via `DynamicPackManager`'s named methods; fluids and
entities need different folder conventions (block atlas vs entity atlas), so they get their own
writers here.

Each class is a thin wrapper: it delegates all actual disk I/O to `DynamicPackManager.writePngAt`
and simply knows the correct relative path for its kind.

## Files

| File | Purpose |
|------|---------|
| `EntityAssets.java` | Writes the per-slot skin PNG to `assets/conjure/textures/entity/entity_slot_N.png` in the dynamic pack. The geo model and animation JSON are static (bundled in the jar) and are never written here. |
| `FluidAssets.java` | Writes still-frame and flowing-frame PNGs to `assets/conjure/textures/block/fluid_still_slot_N.png` and `fluid_flow_slot_N.png` so the block atlas stitcher picks them up. Both textures use the same ARGB data for v1 simplicity. |
