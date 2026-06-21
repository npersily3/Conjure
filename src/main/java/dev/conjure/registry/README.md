# dev.conjure.registry — pre-registered slot pools

This package is responsible for one thing: pre-registering all content pools with the Minecraft
registry system before the registries freeze. Nothing here generates content or modifies a slot at
runtime — it only creates the empty shells that the AI generation layer fills in later.

Each pool class follows the same pattern: a `DeferredRegister`, a `List` of holders, and a static
initializer that loops over the pool size, calls `SlotRegistry.init`, and registers a shell object.
The `Conjure.java` constructor calls into every class here exactly once.

## Files

| File | Purpose |
|------|---------|
| `ConjureItems.java` | Registers 500 `ConjureItem` shells (`item_slot_0` … `item_slot_499`). Also holds the `BLOCK_ITEMS` list that `ConjureBlocks` populates with block `BlockItem` entries. |
| `ConjureBlocks.java` | Registers 500 `ConjureBlock` shells spread across `BlockArchetype` buckets, plus one `ConjureBlockItem` per block slot in the item registry. |
| `ConjureSlabs.java` | Registers 64 `ConjureSlabBlock` shells (`slab_slot_N`) + matching block items. Filled by material-family generation. |
| `ConjureStairs.java` | Registers 64 `ConjureStairBlock` shells (`stairs_slot_N`) + matching block items. |
| `ConjureWalls.java` | Registers 64 `ConjureWallBlock` shells (`wall_slot_N`) + matching block items. |
| `ConjureDataPack.java` | `AddPackFindersEvent` subscriber (common side) registering the runtime `conjure/generated` folder as a `SERVER_DATA` pack so generated recipes load. Always-on; a server `reloadResources` re-reads new recipes live. |
| `ConjureFluids.java` | Registers 32 fluid sets; each set = `ConjureFluidType` + source `BaseFlowingFluid` + flowing `BaseFlowingFluid` + `ConjureLiquidBlock` + `ConjureBucketItem`, spread across four separate `DeferredRegister`s. |
| `ConjureEntities.java` | Registers 128 `ConjureMob` entity types in three size buckets (SMALL 48, MEDIUM 48, LARGE 32). Contains inner `CommonEvents` (attribute registration) and `ClientEvents` (renderer registration) subscribers. |
| `ConjureStructures.java` | Initialises 100 `SlotKind.STRUCTURE` entries in the `SlotRegistry`. Structures are command-placed, not world-gen registered; no Java `StructureType` is needed. |
| `ConjureBlockEntities.java` | Registers ONE shared `BlockEntityType<ConjureBlockEntity>` bound to all MACHINE-archetype `ConjureBlock` instances. One type covers all machine slots to stay within the registry freeze constraint. |
| `ConjureMenus.java` | Registers ONE shared `MenuType<ConjureMenu>` for all machine-archetype block containers. |
| `ConjureTabs.java` | Registers the "Conjure" creative-inventory tab; its `displayItems` generator emits only configured slots — items, blocks, fluids, and the slab/stairs/wall variant items. |
