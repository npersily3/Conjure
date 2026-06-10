# Conjure

AI-driven, on-the-fly Minecraft content for **NeoForge 1.21.1**.

In-game you type a command and a team of AI agents generates a new item / block / fluid /
mob — logic, textures, and data — that appears **without relaunching the game**, up to fixed
pool sizes.

```
\conjure new <model> "<prompt>" <name>     # generate new content
\conjure edit <name> "<prompt>"            # revise existing content
```
(command layer not wired yet — see roadmap)

## The core idea: pre-registered slot pools

Minecraft **freezes its registries** after startup — you can never add a new block/item/
entity/fluid id at runtime, and the JVM can't load new classes either. Conjure works around
this by registering a large pool of generic, configurable **slots** at startup. The AI never
creates a new registry entry; it fills in the runtime `SlotDefinition` behind an existing slot.

| Pool | Size | Status | Notes |
|------|------|--------|-------|
| Items | 500 | ✅ implemented | Uniform shells; name/texture/behavior all runtime |
| Blocks | 500 | ✅ implemented | Split across **archetype buckets** (render layer etc. is baked) |
| Fluids | 32 | 🚧 scaffold | Each = source+flowing+FluidType+liquid block+bucket |
| Entities | 128 | 🚧 scaffold | Size buckets; appearance needs GeckoLib or texture-swap |

What's **live-reloadable** (no restart): textures/models (resource pack, F3+T), recipes/loot
(datapack, `/reload`), and behavior (interpreted scripts). What still needs a restart: truly
novel registry ids beyond the pool. What's world-creation-only: terrain shape & dimensions.

## Model routing (local or cloud)

`config/conjure-common.toml` selects providers per task. Defaults to **local** (Ollama is
already installed here). Anthropic keys are read from an env var, never stored in config.

- Text/logic: `ProviderMode.LOCAL` → Ollama (`/api/chat`) · `ANTHROPIC` → Messages API
- Textures: local image backend (ComfyUI/A1111/sd.cpp), or have the text model emit pixel art

See `dev.conjure.ai.*` and `dev.conjure.Config`.

## Architecture map

```
dev.conjure
├─ Conjure.java            entrypoint; registers pools + config
├─ Config.java             model routing config
├─ content/
│  ├─ SlotKind / SlotDefinition / SlotRegistry   runtime store (the freeze workaround)
│  ├─ item/ConjureItem        slot-backed item shell
│  └─ block/BlockArchetype, ConjureBlock         buckets + slot-backed block shell
├─ registry/
│  ├─ ConjureItems / ConjureBlocks               implemented pools
│  └─ ConjureFluids / ConjureEntities            scaffolds
└─ ai/
   ├─ TextModelProvider + OllamaProvider + AnthropicProvider
   ├─ ImageModelProvider (interface)
   └─ ProviderFactory / ProviderMode
```

## Roadmap

1. ✅ Project + item/block pools + provider layer
2. ✅ **Vertical slice**: `/conjure new "<prompt>"` → Ollama → 16×16 pixel-art → live item
3. ✅ Fluid pool (32 sets) + entity pool (128, size buckets) + 100-slot structure bucket
4. ✅ Embedded **Rhino** scripting runtime + sandbox; behavior wired into items/blocks
5. ✅ Multi-agent generation (orchestrator → texture / data / logic) via `ProviderFactory`
6. ✅ Persistence (slots survive restart) + `/conjure list` + `/conjure edit`
7. ⬜ Entity models via GeckoLib; structure template-pool/jigsaw writer ("a village")
8. ⬜ Datapack manager for recipes/loot; item tooltips from generated description
9. ⬜ Dedicated-server sync (slots are client-local right now; singleplayer only)
10. ⬜ `jarJar` Rhino for a shippable (non-dev) jar

See `src/ARCHITECTURE.md` for an in-depth design write-up.

## Commands

| Command | What it does |
|---|---|
| `/conjure new <prompt>` | Allocate the next free item slot; generate texture + name + behavior; live. |
| `/conjure list` | List configured item slots (index, name, prompt). |
| `/conjure edit <index> <prompt>` | Re-generate an existing slot, preserving its id. |

## Try the slice

1. Ensure Ollama is running with a model (`ollama list`); the default model is
   `llama3.3:latest` (change in `config/conjure-common.toml` → `[text] localModel`).
2. `./gradlew runClient`, create/open a single-player world (Creative, cheats on).
3. `/conjure new "a glowing ruby amulet"` — wait a few seconds for the local model.
4. `/give @s conjure:item_slot_0` — the generated icon appears with no relaunch.

`dev.conjure.gen` (generation + dynamic pack), `dev.conjure.client` (pack registration +
reload), `dev.conjure.command` (the command).

## Build

Requires JDK 21. `./gradlew runClient` to launch a dev client. If `neo_version` fails to
resolve, bump it to the latest `21.1.x` in `gradle.properties`.
