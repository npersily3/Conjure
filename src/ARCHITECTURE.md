# Conjure — Architecture (in depth)

This document explains *why* Conjure is built the way it is. For day-to-day conventions and
build commands see `/CLAUDE.md`; for the roadmap see `/README.md`.

---

## 1. The problem

We want a player to type a command and have an AI **create new Minecraft content that appears
immediately, without restarting the game.** Minecraft fights this on three fronts:

1. **Registry freeze.** During startup Minecraft registers every block/item/entity/fluid/etc.
   into registries, then calls `freeze()` on them. After that, *any* attempt to register a new
   id throws. You cannot grow the set of blocks/items/entities/fluids at runtime. (Saved worlds
   also store numeric registry ids, so smuggling entries in via reflection corrupts saves and
   desyncs multiplayer — a non-starter.)
2. **The JVM.** You cannot load brand-new compiled classes, or add methods/fields to existing
   ones, in a running JVM (without special agents). So AI-authored *behavior* cannot be shipped
   as compiled Java.
3. **World generation** is partly frozen at **world creation** and only ever affects **newly
   generated chunks** — never terrain you've already explored.

Conjure is essentially one set of answers to these three constraints.

---

## 2. The central idea: pre-registered slot pools

Because we can't *grow* the registries at runtime, we pre-register **large pools of generic,
reconfigurable slots at startup**, then change what each slot *is* at runtime. The AI never
registers anything; it fills in a `SlotDefinition` behind an already-registered slot.

```
                         startup (registration open)         runtime (registries frozen)
ConjureItems  ──► 500 ConjureItem shells ───────────────┐
ConjureBlocks ──► 500 ConjureBlock shells (bucketed) ───┤   AI fills SlotDefinition
ConjureFluids ──► 32 fluid sets ────────────────────────┼──►  SlotRegistry.put(def)  ──► live
ConjureEntities ► 128 EntityType shells (size buckets) ─┤        (name, texture,
ConjureStructures ► 1 StructureType + 100 datapack slots┘         behavior, stats)
```

### The split that makes it work
Each slot has two kinds of facets:

| Facet | When fixed | Example |
|------|-----------|---------|
| **Baked** | at JVM registration | registry id, block render layer, entity hitbox size, fluid identity |
| **Live** | reconfigurable at runtime | display name, texture, behavior, collision shape, stats |

The whole design is about pushing as much as possible into the **live** column, and *bucketing*
the **baked** column so a pre-registered slot is "close enough" to what the AI wants.

- **Items** (`registry/ConjureItems`): the easy case. Uniform shells; name/texture/behavior are
  all live. 500 of them.
- **Blocks** (`registry/ConjureBlocks` + `content/block/BlockArchetype`): a block's render layer
  (opaque/cutout/translucent), light, and occlusion are *baked*, so we don't make 500 identical
  blanks — we register **archetype buckets** (SOLID 200, TRANSPARENT 80, CUSTOM_SHAPE 80,
  LIGHT 80, MACHINE 60) and the generator picks the nearest fit.
- **Fluids** (`registry/ConjureFluids`): the fiddliest. Each slot is a *set* — `FluidType` +
  source/flowing `BaseFlowingFluid` + `LiquidBlock` + `BucketItem`. Textures are wired at
  client-setup via `ConjureFluidType#initializeClient`, defaulting to water and overridable
  from the slot's `strings` map. 32 sets.
- **Entities** (`registry/ConjureEntities` + `content/entity/ConjureMob`): `EntityType` hitbox
  size is baked, so slots are **size-bucketed** (SMALL/MEDIUM/LARGE). `ConjureMob extends
  PathfinderMob` reads health/speed/damage and basic AI from its `SlotDefinition`. Appearance is
  the open problem — vanilla entity models are *Java, not data* — so the renderer is a
  placeholder (`NoopRenderer`) pending a GeckoLib (data-driven model) integration.
- **Structures** (`registry/ConjureStructures`): special — `Registries.STRUCTURE` is a
  **datapack** registry, not a built-in one. So we register exactly one `StructureType` in Java,
  and the 100 "slots" are a **naming convention** (`conjure:structure_slot_0..99`) that become
  real only when the generator writes `data/conjure/worldgen/structure/structure_slot_N.json`.
  Consequence (inherent to Minecraft): structures appear only in **newly generated chunks**.

### The runtime store
`content/SlotRegistry` is a concurrent map `(SlotKind, index) → SlotDefinition`. `SlotDefinition`
(`content/SlotDefinition`) holds the live facets: `displayName`, `texturePath`,
`behaviorScriptId`, `sourcePrompt`, plus open-ended `numbers` and `strings` maps so new kinds of
per-slot data never require a schema change. Every shell (`ConjureItem`, `ConjureBlock`, …) is
thin: it looks up its `SlotDefinition` on demand and delegates.

---

## 3. Making visuals live: the dynamic resource pack

`gen/DynamicPackManager` owns an on-disk pack at `<gamedir>/conjure/generated/`. The generator
writes `assets/conjure/textures/item/item_slot_N.png` and a matching model json there.
`client/ConjureClientPack` registers that folder as an **always-active, top-priority** client
resource pack (via `AddPackFindersEvent`), discovered once at startup. Thereafter
`client/ClientHooks.reloadResources()` calls `Minecraft#reloadResourcePacks()`, which re-reads
the folder — so files written between reloads are picked up **without a relaunch**. (Datapack
content for recipes/structures works the same way through `/reload`.)

Why this works against the freeze: we're not adding a *new* pack each time (that needs a repo
rebuild) — we keep one pack and change its *contents*, which reloads are designed to handle.

---

## 4. Making behavior live: the sandboxed script runtime

Since the JVM can't load AI-authored Java, behavior is **interpreted JavaScript** (`script/`,
Mozilla Rhino). The seam is deliberately filesystem-based so the generation and execution halves
stay decoupled:

```
LogicAgent ──writes──► <gamedir>/conjure/generated/scripts/<id>.js
                                   ▲
SlotDefinition.behaviorScriptId = <id>
                                   │
ConjureItem.use() / ConjureBlock.useWithoutItem()  ──► ScriptRuntime.run(id, ctx)
```

`script/ScriptRuntime` loads and caches scripts (by id + mtime) and runs them in a **sandbox**
that matters because it executes AI-written code:
- a `ClassShutter` that denies *all* Java class visibility (no reflection, IO, threads),
- interpreted mode (`optimizationLevel = -1`) so the instruction observer fires,
- an instruction-count budget (~100k) that aborts infinite loops,
- sealed standard objects, and **only** a single host object exposed.

That host object is `script/ScriptContext` (`ctx`), a deliberately narrow API:
`message`, `giveItem`, `heal`, `damage`, `spawnParticleHere`, `getPlayerName`. Scripts run
server-side only (`!level.isClientSide`); errors are caught, logged, and surfaced to the player
rather than crashing the game.

---

## 5. Generation: routing + the agent team

`gen/GenerationService` is the per-piece **orchestrator**. Given one piece prompt it first asks
`ai/agents/RouterAgent` to classify it into a `SlotKind`, then runs the matching
`gen/pipeline/*Pipeline` (ITEM/BLOCK/FLUID/ENTITY/STRUCTURE) on a single background thread (model
calls block for seconds and must never touch the main thread). A pipeline allocates a slot and
runs the specialized sub-agents:

```
one piece prompt
        │
        ▼
GenerationService (conjure-gen worker thread)
   ├─ RouterAgent  ─► SlotKind ─► pick the matching pipeline
   ├─ TextureAgent ─► ComfyUI PNG (primary) or LLM pixel-art JSON (fallback) ─► DynamicPackManager
   ├─ DataAgent    ─► displayName + description
   ├─ LogicAgent   ─► behavior JS (ctx API) ─► scripts/<id>.js
   ├─ assemble SlotDefinition ─► SlotRegistry.put
   ├─ SlotStore.save  (persist)
   └─ ClientHooks.reloadResources()  (client dist only)
        │
        ▼  back on the server thread
   player sees: "Conjured '<name>' → <kind> slot #N"
```

Each agent is just a `TextModelProvider` call with a focused system prompt. The provider is
chosen by config (`ai/ProviderFactory` → Ollama local **or** Anthropic cloud), so the same
pipeline runs locally or in the cloud unchanged. `ai/agents/JsonHelper` strips fences/prose to
the outermost `{...}` and does one repair-retry on malformed JSON.

`TextureAgent`'s primary path is the **ComfyUI** image backend (`ai/ComfyUIProvider`): it POSTs a
txt2img workflow graph, polls `/history`, and downloads the rendered PNG, which
`gen/PixelTexture#fromPng` nearest-neighbour downscales to the texture size. ComfyUI (rather than
A1111) is the default because it ships portable/headless and is driven entirely through its
workflow API — friendlier to distribute. If no ComfyUI server is reachable, generation falls back
to asking the text model for a 16×16 pixel-art grid, so it never hard-depends on an image server.

### 5a. Whole-mod / multi-piece generation

A user rarely wants exactly one slot. `ai/agents/ModPlannerAgent` decomposes a request into a
list of concrete single-piece prompts; `gen/ModService` runs the planner on its own
`conjure-mod-planner` daemon thread, then fans each piece onto `GenerationService.generate` (the
single-thread gen pool serializes them). Two modes share the planner:

- **AUTO** (`/conjure new`): if the request is one concrete thing ("a glowing ember dagger") it
  returns a single piece; if it's a theme/set/plural ("pagoda blocks") it expands generously,
  erring toward *more* pieces than strictly necessary (up to 20).
- **EXPANSIVE** (`/conjure mod`): always decomposes a whole-mod concept into many mixed-kind
  pieces.

### 5b. Command surface

`command/ConjureCommands` registers: `new <prompt>` (smart generate — one piece or many),
`mod <description>` (always-expansive whole-mod), `list` (configured item slots),
`edit <index> <prompt>` (regenerate one item slot, preserving its registry id), and
`place <index>` (build a generated structure near the player).

---

## 6. Persistence

`persist/SlotStore` serializes each configured `SlotDefinition` (including `sourcePrompt`,
`numbers`, `strings`, `behaviorScriptId`) to `<gamedir>/conjure/slots/item_N.json`.
`persist/SlotStoreLoader` (`ServerStartingEvent`) restores them into `SlotRegistry` on startup.
Textures/models/scripts already live on disk, so a restored slot is fully functional. This is
also what powers `/conjure edit` and lets generated content survive a restart.

---

## 7. Threading & sides (important invariants)

- **Model calls** run on the `conjure-gen` worker thread; never block the main thread.
- **`SlotRegistry.put`** swaps a fully-built `SlotDefinition` atomically — no half-state.
- **Scripts** run server-side only.
- **Resource reload** happens on the client render thread via `Minecraft#execute`.
- **Singleplayer assumption (current):** the integrated server and client share one JVM, so a
  slot written by the (server-side) command is instantly visible to the (client-side) reload.
  On a dedicated server this breaks — see limitations.

---

## 8. Known limitations / future work

- **Dedicated-server sync.** Slots are JVM-local. Multiplayer needs a server→client packet to
  push new `SlotDefinition`s + trigger a client reload. Today it's singleplayer-correct only.
- **Production classpath.** Rhino is a dev `implementation` dep; a shippable jar needs `jarJar`.
- **Entity appearance.** `NoopRenderer` placeholder → GeckoLib for data-driven, AI-designable
  models.
- **Structures are shells.** The `StructureType` exists; the datapack template-pool writer (and
  jigsaw wiring for "a village = many house slots stitched together") is not built yet.
- **Truly novel registry ids** (beyond pool sizes) and **fundamental terrain/dimension worldgen**
  remain restart- / world-creation-bound by Minecraft itself — pools and datapacks mitigate but
  cannot fully remove these.
```
