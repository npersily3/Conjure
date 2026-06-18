# dev.conjure.client — client-only subsystems

Everything in this package is client-side only (annotated `@OnlyIn(Dist.CLIENT)` or guarded by
`FMLEnvironment.dist`). It covers two concerns: making the dynamic resource pack live-reloadable,
and rendering generated entities and workbench GUIs.

None of these classes may be referenced from server-only code paths.

## Files

| File | Purpose |
|------|---------|
| `ConjureClientPack.java` | Subscribes to `AddPackFindersEvent` and registers the `<gamedir>/conjure/generated/` folder as an always-active, top-priority client resource pack discovered once at startup. |
| `ClientHooks.java` | Provides `reloadResources()` — calls `Minecraft.reloadResourcePacks()` on the render thread so newly written textures/models are picked up without a relaunch. |
| `ConjureMobModel.java` | Shared GeckoLib `GeoModel` for all `ConjureMob` entity slots. Uses the single static `conjure_mob.geo.json`; resolves per-slot skin textures from `SlotDefinition.texturePath`, falling back to the bundled `default.png`. |
| `ConjureMobRenderer.java` | GeckoLib `GeoEntityRenderer` that wraps `ConjureMobModel.INSTANCE`. One renderer instance per entity-type slot, all sharing the same model singleton. |
| `ConjureScreen.java` | GUI screen for workbench (MACHINE-archetype) `ConjureBlock`s. The slot layout is dynamic, so it draws everything programmatically (flat vanilla-style panel, a pocket behind every menu slot, and a progress bar) instead of using a fixed background image — no per-recipe textures. Contains the inner `ScreenRegistrar` that wires the screen to `ConjureMenus.MACHINE_MENU`. |
