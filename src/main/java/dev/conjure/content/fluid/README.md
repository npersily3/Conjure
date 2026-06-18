# dev.conjure.content.fluid — fluid slot components

Each fluid slot is a set of four objects that Minecraft requires for a working fluid: a
`FluidType`, a source + flowing `BaseFlowingFluid` pair, a `LiquidBlock`, and a `BucketItem`.
This package contains the four shell classes — one per object type — whose display names are
resolved at runtime from the `SlotDefinition`.

Still and flowing sprite `ResourceLocation`s are baked at registration time (fixed per-slot
paths like `conjure:block/fluid_still_slot_N`). The `FluidPipeline` writes PNG files to those
paths so a client reload stitches them into the block atlas.

The `ConjureFluidType.initializeClient` hook is called by the NeoForge framework during client
setup; no separate event subscription is needed.

## Files

| File | Purpose |
|------|---------|
| `ConjureFluidType.java` | NeoForge `FluidType` that holds the still/flowing `ResourceLocation`s baked at registration and delegates to `ConjureFluidClientExtensions` for client-side sprite and tint info. |
| `ConjureFluidClientExtensions.java` | Client-only `IClientFluidTypeExtensions` implementation that returns the still and flowing texture paths and a white (no-tint) color. |
| `ConjureLiquidBlock.java` | `LiquidBlock` shell whose `getName()` returns the AI-generated fluid name from `SlotRegistry`, or a placeholder for unconfigured slots. |
| `ConjureBucketItem.java` | `BucketItem` shell whose `getName(stack)` appends " Bucket" to the AI-generated fluid name from `SlotRegistry`. |
