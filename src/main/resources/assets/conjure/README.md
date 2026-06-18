# assets/conjure — static shipped assets

This folder contains the assets that ship inside the mod jar itself. They are small in number and
serve as permanent fallbacks or shared definitions that all generated content reuses.

Contrast this with the **runtime-generated** assets that live at
`<gamedir>/conjure/generated/assets/conjure/` — textures, models, blockstates, and fluid sprites
written by `DynamicPackManager` at generation time. Those are NOT in the jar; they are read from
disk by `ConjureClientPack`'s always-active resource pack after each `reloadResourcePacks()`.

## Static files shipped in the jar

```
assets/conjure/
├── animations/
│   └── conjure_mob.animation.json   — Shared GeckoLib animation for all ConjureMob slots:
│                                       idle (2-second body/head bob) and walk (4-leg swing).
│                                       Referenced by ConjureMobModel via ResourceLocation.
├── geo/
│   └── conjure_mob.geo.json         — Shared GeckoLib geometry (bone structure) for all
│                                       ConjureMob slots. Defines body, head, and four leg bones.
│                                       One geo file is reused across all 128 entity slots.
└── textures/entity/
    └── default.png                  — Fallback entity skin shown for entity slots that have not
                                       been configured yet (no AI-generated texture). Prevents
                                       unconfigured slots from appearing as black-and-purple
                                       "missing texture" in the world.
```

## What is generated at runtime (NOT in the jar)

| Path under `<gamedir>/conjure/generated/assets/conjure/` | Written by |
|----------------------------------------------------------|------------|
| `textures/item/item_slot_N.png` | `DynamicPackManager.writeItemTexture` |
| `models/item/item_slot_N.json` | `DynamicPackManager.writeItemModel` |
| `textures/block/block_slot_N.png` | `DynamicPackManager.writeBlockTexture` |
| `models/block/block_slot_N.json` | `DynamicPackManager.writeBlockModel` |
| `blockstates/block_slot_N.json` | `DynamicPackManager.writeBlockState` |
| `models/item/block_slot_N.json` | `DynamicPackManager.writeBlockItemModel` |
| `textures/block/fluid_still_slot_N.png` | `FluidAssets.writeStillTexture` |
| `textures/block/fluid_flow_slot_N.png` | `FluidAssets.writeFlowTexture` |
| `textures/item/bucket_slot_N.png` + `models/item/bucket_slot_N.json` | `FluidAssets.writeBucketAssets` (hardcoded, tinted) |
| `textures/entity/entity_slot_N.png` | `EntityAssets.writeSkinTexture` |
| `scripts/<id>.js` | `PipelineSupport.writeScript` |
