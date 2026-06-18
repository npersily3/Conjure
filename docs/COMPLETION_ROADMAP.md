# Conjure — Completion Roadmap

Priority order within each group is top-to-bottom. "Effort" is relative to the existing
codebase: **S** = a single focused day, **M** = 2–4 days, **L** = a week or more.

---

## 1. Roadmap Leftovers (README items 13–15)

### 1-A. Structure worldgen — jigsaw / template-pool natural spawning

**What:** Wire the 100 pre-allocated structure slots into NeoForge/Vanilla worldgen so they
appear in newly generated chunks, not just via `/conjure place`. This means
writing each slot's layout as a proper `StructureTemplate` NBT file, creating a
`template_pool` entry that references it, and registering a jigsaw `Structure` JSON so the
game places it during chunk generation.

**Why it matters:** Structures placed only via command feel like creative-mode cheats; natural
worldgen gives them gameplay weight and discoverability.

**Effort / Impact:** L / high

**Architectural fit:**
- `gen/pipeline/StructurePipeline.java` — extend `run()` to write an NBT `StructureTemplate`
  file under `<gamedir>/conjure/generated/data/conjure/structures/structure_slot_N.nbt` in
  addition to the existing JSON palette/layers. See `content/structure/StructurePlacer.java`
  for the existing placement logic that can feed the template writer.
- `registry/ConjureStructures.java` — today calls `SlotRegistry.init` only; needs a datapack
  `structure_set` / `template_pool` JSON written to the same generated pack folder.
- `gen/DynamicPackManager.java` — already owns the on-disk pack; a new helper method to write
  arbitrary datapack JSON files under `data/conjure/worldgen/` is the key seam.
- Constraint from `src/ARCHITECTURE.md §8`: structures affect only **newly generated chunks**;
  already-explored terrain will never receive a slot retroactively — this is a vanilla limit,
  not something Conjure can fix.

---

### 1-B. Animated fluid sprites + per-slot rigged GeckoLib entity models

**What (fluid):** Replace the static still-texture fluid with a short (2–4 frame) animated
sprite sheet. The `FluidPipeline` already generates still and flow textures
(`gen/assets/FluidAssets.java`); this extends that to request a JSON frame sequence from the
text model and write a standard `assets/conjure/textures/block/fluid_still_slot_N.png.mcmeta`
animation descriptor.

**What (entity):** Instead of the shared `conjure_mob.animation.json` driving all entities with
the same idle/walk curves, generate a per-slot GeckoLib `*.geo.json` model (a small parametric
body shape) and `*.animation.json` file. `EntityAssets.java` writes the skin today; extend it
to write model + animation files.

**Why it matters:** Fluids look broken/static without frame animation; the shared entity model
means every generated mob looks identical in shape, which collapses the "uniqueness" value
prop of the generation system.

**Effort / Impact:** M (fluid animation), L (per-slot entity model) / med–high

**Architectural fit:**
- `gen/assets/FluidAssets.java` — add `writeAnimationMeta(int slot)` writing `.mcmeta`.
- `gen/pipeline/FluidPipeline.java` — call it after existing texture writes.
- `gen/assets/EntityAssets.java` — add `writeGeoModel(int slot, JsonObject geoJson)` and
  `writeAnimation(int slot, JsonObject animJson)`.
- `gen/pipeline/EntityPipeline.java` — add a call to a new `ModelAgent` (similar to
  `ai/agents/MachineAgent.java`) that generates GeckoLib JSON for the prompt.
- `client/ConjureMobModel.java` / `client/ConjureMobRenderer.java` — switch the renderer from
  the shared model file to a per-slot resource location.

---

### 1-C. Datapack-generated recipes and loot tables

**What:** After generating an item or block slot, ask the model to produce a Vanilla crafting
recipe JSON and/or a loot table JSON, then write them into the generated datapack so they work
without any restart (they take effect on `/reload` which `ClientHooks.reloadResources()` already
triggers).

**Why it matters:** Generated items that can't be crafted are effectively creative-only;
recipes close the loop and make the mod useful in survival.

**Effort / Impact:** S–M / high

**Architectural fit:**
- `gen/DynamicPackManager.java` — add `writeDataJson(String dataPath, String json)` analogous
  to the existing `writeAssetJson` helper. No structural changes needed.
- `gen/pipeline/ItemPipeline.java` and `gen/pipeline/BlockPipeline.java` — after
  `PipelineSupport.commit(def)`, call a new `RecipeAgent` that returns a JSON shaped recipe
  object and write it under `data/conjure/recipes/item_slot_N.json`.
- `ai/agents/` — add `RecipeAgent.java` (follows the same `TextModelProvider` pattern as
  `DataAgent.java`; system prompt describes Vanilla recipe JSON schema).
- `ai/agents/JsonHelper.java` — reuse as-is for JSON extraction + repair.

---

### 1-D. Dedicated-server sync

**What:** Slots are currently JVM-local (`persist/SlotStore.java` writes to the server's
filesystem, but `client/ClientHooks.java` triggers `Minecraft#reloadResourcePacks()` which only
works in singleplayer where server and client share a JVM). On a dedicated server, connected
clients never see new slots.

**Why it matters:** Multiplayer is a first-class Minecraft use case; without this the mod is
demo-ware for singleplayer only.

**Effort / Impact:** L / high (when multiplayer matters to the maintainer)

**Architectural fit:**
- New packet class `network/SyncSlotPacket.java` — carries a serialized `SlotDefinition` (all
  fields) plus the texture bytes for the slot. NeoForge `PacketDistributor.ALL` from the server
  sends it.
- `gen/pipeline/PipelineSupport.java` — `commit()` currently calls `SlotStore.save` and
  `ClientHooks.reloadResources()`; add a third step: broadcast `SyncSlotPacket` to all
  connected players.
- `client/ClientHooks.java` — add a packet handler that receives `SyncSlotPacket`, writes the
  texture file, puts the def into `SlotRegistry`, then calls `reloadResources()`.
- `persist/SlotStoreLoader.java` — on server startup, replay stored slots to any joining player
  via `PlayerLoggedInEvent`.
- Constraint: texture bytes can be large (ComfyUI 768px PNG); consider a hash-based skip if
  the client already has the file.

---

## 2. Gameplay Depth

### 2-A. Crafting recipes (overlaps with 1-C, but covers the full set)

See item 1-C above — applying to all five kinds, not just items/blocks.

---

### 2-B. Richer mob AI beyond idle/walk

**What:** `ConjureMob.registerGoals()` today adds only `FloatGoal`, `WaterAvoidingRandomStrollGoal`,
`LookAtPlayerGoal`, `RandomLookAroundGoal`. Extend `EntityPipeline` to ask the model for a
`behavior` string (e.g. `"hostile"`, `"passive"`, `"tameable"`) and map it to a Goal preset.

**Why it matters:** Every generated mob behaves identically today; a "Shadow Wraith" that acts
like a passive farm animal is jarring.

**Effort / Impact:** S / med

**Architectural fit:**
- `content/entity/ConjureMob.java` — `registerGoals()` reads `def.str("behavior", "passive")`
  and switches on the value to add `MeleeAttackGoal`, `NearestAttackableTargetGoal`, etc.
- `gen/pipeline/EntityPipeline.java` — add a `def.strings.put("behavior", ...)` populated from
  the `DataAgent` response or a new one-field prompt.
- `content/SlotDefinition.java` — no schema change needed; use the existing `strings` map.

---

### 2-C. Tool and armor archetypes for items

**What:** Items currently have a uniform `ConjureItem` shell with only script-driven behavior.
Add optional archetype support: if the model classifies a prompt as "sword/pickaxe/helmet/etc.",
route it to the correct `BlockArchetype`-style bucket so it inherits the right use logic
(attack, mining, armor slots) rather than being a blank right-click item.

**Why it matters:** "A diamond-hard obsidian pickaxe" generated as a plain item that does
nothing on left-click is a bad experience.

**Effort / Impact:** M / med

**Architectural fit:**
- `content/block/BlockArchetype.java` exists as a model for this pattern; add
  `content/item/ItemArchetype.java` (GENERIC, SWORD, PICKAXE, AXE, SHOVEL, HELMET, etc.).
- `registry/ConjureItems.java` — split the 500-pool into archetype buckets at startup.
- `ai/agents/RouterAgent.java` — or a new `ItemArchetypeAgent` — classify the prompt into
  the right bucket.
- `gen/pipeline/ItemPipeline.java` — pick a slot from the appropriate bucket.

---

### 2-D. Generated advancements

**What:** After generation, write a simple `data/conjure/advancements/obtained_<name>.json`
awarding the player when they pick up or craft the new item for the first time.

**Why it matters:** Advancements are the primary in-game signposting mechanism; they make
content feel "official" rather than hacked in.

**Effort / Impact:** S / low–med

**Architectural fit:**
- `gen/DynamicPackManager.java` — reuse the datapack write path (item 1-C adds this).
- `gen/pipeline/ItemPipeline.java` — one extra write call after commit.

---

### 2-E. Sound generation / assignment

**What:** Assign an existing Vanilla sound event (or a short generated description mapped to
the closest Vanilla sound) to generated items/blocks/entities. Store in
`def.strings.put("sound_event", "minecraft:entity.generic....")`  and read it in
`ConjureItem.use()` / `ConjureBlock.useWithoutItem()` / `ConjureMob`.

**Why it matters:** Silent items and mobs feel unfinished; sound has outsized perceptual impact.

**Effort / Impact:** S / med

**Architectural fit:**
- `content/item/ConjureItem.java` — play `BuiltInRegistries.SOUND_EVENT.get(...)` on
  interaction.
- `gen/pipeline/ItemPipeline.java` / `EntityPipeline.java` — ask `DataAgent` for a sound_event
  key (add it to the existing `DataAgent` JSON schema rather than a new agent).
- `ai/agents/DataAgent.java` — extend the response schema.

---

## 3. UX and Safety

### 3-A. `/conjure regenerate` and `/conjure delete`

**What:**
- `regenerate <kind> <index>` — re-runs the pipeline for any kind (today `edit` only works for
  items, as seen in `command/ConjureCommands.java` lines 105–128).
- `delete <kind> <index>` — marks the slot as unconfigured, removes the persisted JSON from
  `<gamedir>/conjure/slots/`, and reloads resources so the slot reverts to its placeholder.

**Why it matters:** Without delete, the pool fills permanently; without multi-kind regenerate,
editing blocks/fluids/entities requires direct filesystem hacking.

**Effort / Impact:** S / high

**Architectural fit:**
- `command/ConjureCommands.java` — add two new sub-commands. `regenerate` dispatches to
  `GenerationService.generate()` with a forced slot index (need a `runForSlot` variant on each
  pipeline, following the existing `ItemPipeline.runForSlot()`). `delete` calls a new
  `SlotStore.delete(kind, index)` and `SlotRegistry.reset(kind, index)`.
- `persist/SlotStore.java` — add `delete(SlotKind, int)` (delete the file, straightforward).
- `content/SlotRegistry.java` — add `reset(SlotKind, int)` that puts back an empty
  `SlotDefinition` with `configured = false`.

---

### 3-B. Balance guardrails on generated entity / item stats

**What:** The model can trivially generate an entity with `max_health = 10000` or an item that
heals for `9999`. Clamp stats server-side in `EntityPipeline` (and in `ScriptContext.heal/damage`)
to sane ranges, and optionally run a post-generation check agent that flags outliers.

**Why it matters:** Unclamped AI-generated stats can trivially grief a world or crash balance.

**Effort / Impact:** S / high

**Architectural fit:**
- `gen/pipeline/EntityPipeline.java` — clamp `max_health`, `move_speed`, `attack_damage`,
  `follow_range` after parsing; the attribute ranges are documented in `ConjureMob.java`.
- `script/ScriptContext.java` — `heal()` and `damage()` already cap at 0 (ignoring negatives),
  but have no upper bound; add a reasonable ceiling (e.g. 40 HP).
- `ai/agents/DataAgent.java` — extend the response schema to include optional stat fields;
  clamped downstream rather than asking the model to self-limit.

---

### 3-C. Pool-exhaustion UX

**What:** When `SlotRegistry.firstFree()` returns -1 the pipeline today prints a bare
"All N slots are full" message and stops. Surface a richer message listing how many slots are
configured, suggest `/conjure delete`, and optionally show the least-recently-used slot index.

**Why it matters:** New users hitting pool exhaustion with no guidance will assume the mod is
broken.

**Effort / Impact:** S / med

**Architectural fit:**
- Each `*Pipeline.java` handles the `-1` case; extract this into a shared
  `PipelineSupport.poolFullMessage(SlotKind kind, int poolSize)` helper in
  `gen/pipeline/PipelineSupport.java`.
- `content/SlotRegistry.java` — add a `countConfigured(SlotKind)` helper.

---

### 3-D. In-game status / config screen

**What:** A simple screen (extending `net.minecraft.client.gui.screens.Screen`) that shows
pool occupancy per kind, the current text/image provider, and allows toggling
`entityAnimations` and `interactivity` feature flags without editing TOML.

**Why it matters:** Non-technical players have no way to inspect state or adjust features
without a text editor.

**Effort / Impact:** M / med

**Architectural fit:**
- `client/ConjureScreen.java` already exists (currently the machine block GUI); a second screen
  class `client/ConjureStatusScreen.java` should be added rather than repurposing it.
- `Config.java` — config values are `ForgeConfigSpec.BooleanValue`; they can be read and set
  programmatically on the client.
- Add a keybind in `client/ClientHooks.java` to open the screen.

---

### 3-E. Export / share a generated content pack

**What:** `/conjure export` zips up `<gamedir>/conjure/generated/` (assets) +
`<gamedir>/conjure/slots/` (metadata) into a distributable `.zip` that another player can drop
into their `<gamedir>/conjure/` folder and load with `/conjure import <path>`.

**Why it matters:** Players want to share their conjured worlds; without export/import the
content is locked to one machine.

**Effort / Impact:** M / low–med (the data is already on disk; it's mostly IO plumbing)

**Architectural fit:**
- New `persist/PackExporter.java` / `persist/PackImporter.java`.
- `command/ConjureCommands.java` — two new sub-commands.
- `client/ClientHooks.java` — import triggers a resource reload.
- No changes to `SlotDefinition`, `SlotRegistry`, or any pipeline.

---

## 4. Robustness

### 4-A. Schema validation of model JSON output

**What:** Every pipeline calls `JsonHelper.extractAndParse()` which does structural JSON
parsing and one repair retry, but does not validate that required keys (`palette`, `layers`,
`displayName`, etc.) are present and of the right type. Add per-pipeline schema validators
that fail fast with a descriptive error rather than a `NullPointerException` deeper in the
pipeline.

**Why it matters:** Schema errors from a confused model currently surface as cryptic Java
exceptions rather than useful feedback.

**Effort / Impact:** S / med

**Architectural fit:**
- `ai/agents/JsonHelper.java` — add a `validate(JsonObject obj, String... requiredKeys)`
  static helper.
- `gen/pipeline/StructurePipeline.java` — the most fragile today (requires `palette`, `layers`,
  `sizeX/Y/Z`); add validation before the `clamp()` calls.
- All other pipelines — add the same guard for their key fields.

---

### 4-B. Automated pipeline tests

**What:** JUnit tests for the generation pipelines that run with a mock `TextModelProvider`
returning canned JSON, exercising `SlotRegistry` state mutations, `SlotStore` read/write, and
the `StructurePlacer` placement logic without starting Minecraft.

**Why it matters:** The pipeline code has no tests; regressions in `SlotStore` serialization
or `StructurePipeline` parsing currently surface only in a live game session.

**Effort / Impact:** M / med

**Architectural fit:**
- `ai/TextModelProvider.java` — it is an interface; a `MockTextProvider` can be substituted in
  test code without any production changes.
- `persist/SlotStore.java` / `persist/SlotStoreLoader.java` — use a temp directory for the
  gamedir path (already injectable via `FMLPaths.GAMEDIR`; a test override is needed).
- `gen/pipeline/*.java` — pipelines accept a `Consumer<String>` feedback; no MC-specific deps
  block unit testing if the provider and file paths are injected.
- `content/structure/StructurePlacer.java` — needs a mock `ServerLevel`; this is the hardest
  part and can be deferred.

---

### 4-C. Generation timeout and cancellation

**What:** The single `conjure-gen` thread pool (`GenerationService.POOL`) has no timeout on
model calls. A hung Ollama connection or a very slow ComfyUI job blocks all subsequent
`/conjure new` calls forever. Wrap each pipeline in a `Future` with a configurable timeout
(e.g. 120 s) and surface a user-facing "timed out" message.

**Why it matters:** A first-time user running against a slow local model will see the game hang
silently with no feedback.

**Effort / Impact:** S / med

**Architectural fit:**
- `gen/GenerationService.java` — wrap `POOL.submit(...)` in a `Future` and call
  `future.get(timeout, TimeUnit.SECONDS)` on a separate watcher thread, or switch to a
  `ScheduledExecutorService` with a watchdog.
- `Config.java` — add a `[gen] timeoutSeconds` TOML config value.

---

## Recommended Next 2–3

| Priority | Item | Why |
|----------|------|-----|
| **1** | **1-C: Datapack recipes** | S effort, high impact — closes the survival-mode loop for items and blocks immediately; reuses the existing dynamic pack infrastructure with minimal new code. |
| **2** | **3-A: `/conjure regenerate` + `/conjure delete`** | S effort, high impact — without delete the pool fills permanently after ~500 generations; without multi-kind regenerate, editing non-item content requires direct filesystem hacking. Both are blockers to long-term use. |
| **3** | **3-B: Stat guardrails** | S effort, high impact for safety — `ScriptContext` already clamps negatives but has no upper bound; unclamped AI-generated damage/health is the most likely way a first-time user accidentally breaks their world. A one-line clamp in `EntityPipeline.java` and `ScriptContext.java` pays off immediately. |

**Rationale:** These three items share the property that they touch only 2–3 files each, ship
in under a day individually, and each one removes a concrete "this is broken for real use"
problem (pool fills forever, survival recipes absent, unclamped damage). The larger items (1-A
worldgen, 1-D multiplayer, 1-B entity models) are high-value but each represents a multi-day
effort with nontrivial new dependencies or Minecraft API surface; tackle them after the quick
wins stabilize the experience.
