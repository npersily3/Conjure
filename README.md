# Conjure

AI-driven, on-the-fly Minecraft content for **NeoForge 1.21.1**. Warning: this was vibe-coded.

In-game you type a command and a team of AI agents generates new content that appears **without
relaunching the game**, up to fixed pool sizes. A lightweight **router** decides what your prompt
should become — an **item**, a **block** (including interactive workbench/kiln-style machines), a
**fluid**, an **entity** (rendered with GeckoLib), or a **structure** — and runs the matching
generation pipeline. You can even ask for a whole mod at once.

```
/conjure new <prompt>            generate live; one concrete prompt → one piece, a themed/plural prompt → many
/conjure mod <description>       decompose a whole-mod idea into many pieces and generate each
/conjure place <index>           build a generated structure near you
/conjure list                    list configured item slots
/conjure edit <index> <prompt>   re-generate an existing item slot
```

---

## Running it

### Prerequisites
> On **Windows**, you can skip the manual setup below: the mod auto-installs Ollama + the text model
> and ComfyUI + a checkpoint on first launch (~10–14 GB). See [`docs/SETUP.md`](docs/SETUP.md) for
> details and the `features.autoInstallBackends` kill-switch. The steps below are what that automates
> (and the manual path for macOS/Linux).

- **JDK 21** (`java -version` → 21.x).
- **Text model** (the brain — required). Default is **local Ollama**:
  - `ollama list` should show a model. Default config expects `gemma4:latest` (any chat model works).
  - Configure in `run/config/conjure-common.toml` → `[text] localModel`/`localEndpoint`
    (the file is created on first launch; default endpoint `http://127.0.0.1:11434`).
  - To use Anthropic instead: `[text] provider = "ANTHROPIC"` and export your key into the env var
    named by `[text] anthropicKeyEnv` (default `ANTHROPIC_API_KEY`).
- **Image model** (the textures — optional). For high-quality textures, run a local **ComfyUI**
  server and point `[image] localEndpoint` at it (e.g. `http://127.0.0.1:8188`). Set
  `[image] fastModel`/`highModel` to a checkpoint filename present in `ComfyUI/models/checkpoints`
  (default `v1-5-pruned-emaonly.safetensors`). If no image server is reachable, Conjure **falls
  back** to having the text model emit pixel-art — so everything still works, the icons are just
  simpler.
- **GeckoLib** (entity models) is a required mod dependency; in the dev workspace it's pulled in
  automatically by Gradle.

### Launch the dev client
From the project root:
```
./gradlew runClient
```
First launch downloads Minecraft + NeoForge (several minutes); later launches are quick. Or run the
generated **runClient** task in IntelliJ.

### Use it
1. **Singleplayer → Create New World** → Game Mode **Creative**, **Allow Cheats: ON**.
2. In chat, describe what you want — the router figures out the kind:
   ```
   /conjure new a glowing ember dagger that heals you when used   (→ one item)
   /conjure new a mossy enchanted stone block                     (→ one block)
   /conjure new a brass kiln that smelts ore                      (→ one interactive machine block)
   /conjure new a glowing blue magical fluid                      (→ one fluid + bucket)
   /conjure new a shadow wraith                                   (→ one GeckoLib entity)
   /conjure new pagoda themed blocks                              (→ many blocks — themed/plural expands)
   ```
   Watch chat for progress, then the result line tells you how to get it
   (`/give @s conjure:item_slot_0`, `/summon conjure:entity_slot_0`, `/conjure place 0`, …).
3. Everything you generate also shows up in the dedicated **Conjure creative tab**, under its
   AI-generated **name** (not `item_slot_N`).
4. **Right-click** items and script/machine blocks to run their generated behavior / open their GUI.
5. Go big: `/conjure mod a beekeeping mod` plans the pieces (beehive block, honeycomb item, bee mob,
   honey fluid, apiary machine, …) and generates them one after another.

Generated assets live under `run/conjure/` — `generated/` (textures, models, scripts) and
`slots/` (persisted slot metadata, per kind; these reload on restart).

### Feature toggles
`run/config/conjure-common.toml`:
- `[image] quality = FAST | HIGH` — FAST (fewer steps, 512px native, seconds) vs HIGH (more steps, 768px native, slow).
- `[features] entityAnimations` — play GeckoLib idle/walk animations on mobs (off = static pose).
- `[features] interactivity` — allow generated blocks to be machines / scripted (off = inert blocks).

### Troubleshooting
| Symptom | Fix |
|---|---|
| `Generation failed: …Cannot reach Ollama` | Start the text server; check `[text]` endpoint/model. On Windows use `127.0.0.1`, not `localhost`. |
| Textures look like simple pixel-art | No image server reachable — it fell back. Start ComfyUI and set `[image] localEndpoint`/`fastModel`. |
| Block/entity shows black-and-purple "missing" | Only **generated** slots have assets; unconfigured pool slots look broken — expected. |
| Texture didn't update after `edit` | Press **F3+T** to force a resource reload (normally automatic). |
| Build error after editing | `./gradlew compileJava --no-daemon` and read the first error. |

### Just compile (no game)
```
./gradlew compileJava --no-daemon      # must end BUILD SUCCESSFUL
```

---

## The core idea: pre-registered slot pools

Minecraft **freezes its registries** after startup — you can never add a new block/item/entity/
fluid id at runtime, and the JVM can't load new classes. Conjure pre-registers a large pool of
generic **slots** at startup; the AI never registers anything, it fills in the runtime
`SlotDefinition` behind an existing slot.

| Pool | Size | Notes |
|------|------|-------|
| Items | 500 | Uniform shells; name/texture/behavior all runtime |
| Blocks | 500 | Split across **archetype buckets**; MACHINE bucket backs interactive block-entities |
| Fluids | 32 | Each = source + flowing + FluidType + liquid block + bucket |
| Entities | 128 | Size buckets; rendered live via **GeckoLib** + per-slot generated skin |
| Structures | 100 | **Command-placed** from a generated block layout (`/conjure place`) |

What's **live-reloadable**: textures/models (resource pack) and behavior (interpreted JS). What
needs a restart: registry ids beyond the pools.

Behavior is sandboxed **Rhino** JavaScript with a small `ctx` host API
(`message`, `giveItem`, `heal`, `damage`, `spawnParticleHere`, `getPlayerName`).

## Model routing (local or cloud)

`run/config/conjure-common.toml` selects providers per task. **Text** defaults to local Ollama
(or Anthropic). **Image** defaults to a local **ComfyUI** backend, with an automatic
LLM-pixel-art fallback. Anthropic keys are read from an env var, never stored in config. See
`dev.conjure.Config` and `dev.conjure.ai.*`.

## Architecture map

```
dev.conjure
├─ Conjure.java / Config.java       entrypoint + text/image routing + feature toggles
├─ content/                         slot store (SlotKind/SlotDefinition/SlotRegistry)
│                                   + item/block/fluid/entity shells + BlockArchetype
│                                   + block BlockEntity/Menu + structure StructurePlacer
├─ registry/                        the pre-registered pools (Items/Blocks/Fluids/Entities/
│                                   Structures) + BlockEntities + Menus + creative Tab
├─ ai/  ├─ providers (Ollama/Anthropic text, ComfyUI image, ProviderFactory)
│       └─ agents/ (Router, Texture, Data, Logic, Machine, ModPlanner, JsonHelper)
├─ gen/                             GenerationService dispatcher + ModService
│   └─ pipeline/                    GenerationPipeline per kind (Item/Block/Fluid/Entity/Structure)
│   └─ assets/                      per-kind dynamic-pack writers (Fluid/Entity)
│   + DynamicPackManager + PixelTexture
├─ script/                          sandboxed Rhino runtime + ctx host API
├─ persist/                         SlotStore (kind-aware; slots survive restart)
├─ client/                          resource-pack registration + live reload + GeckoLib renderer + GUI screen
└─ command/                         /conjure commands
```
Full design rationale: **`src/ARCHITECTURE.md`**. Contributing with parallel agents:
**`docs/PARALLEL_AGENTS.md`**.

## Roadmap

1. ✅ Item/block pools + provider layer
2. ✅ Vertical slice: `/conjure new` → model → texture → live item
3. ✅ Fluid (32) + entity (128) + structure (100) pools
4. ✅ Sandboxed Rhino scripting; behavior wired into items + blocks
5. ✅ Multi-agent generation (router → per-kind pipeline → texture / data / logic)
6. ✅ Persistence (kind-aware) + `/conjure list` + `/conjure edit`
7. ✅ All kinds generate end-to-end (router dispatches item/block/fluid/entity/structure)
8. ✅ Local image-gen textures (ComfyUI, FAST/HIGH) with LLM-pixel-art fallback
9. ✅ Interactive blocks (BlockEntity + container GUI machines, and script-driven blocks)
10. ✅ Entity models via GeckoLib (per-slot skin, toggleable animations)
11. ✅ Command-placed structures (`/conjure place`); Mod Architect (`/conjure mod`); creative tab; names everywhere
12. ✅ `jarJar` Rhino on the runtime classpath
13. ⬜ Structure **worldgen** (jigsaw/template-pool so structures spawn in new chunks naturally)
14. ⬜ Animated fluid sprites; per-slot rigged GeckoLib models; datapack recipes/loot
15. ⬜ Dedicated-server sync (slots are client-local; singleplayer only)
