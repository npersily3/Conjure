# Conjure

AI-driven, on-the-fly Minecraft content for **NeoForge 1.21.1**. Warning this was vibe-coded

In-game you type a command and a team of AI agents generates a new item (logic, texture, and
name) that appears **without relaunching the game**, up to fixed pool sizes. Blocks, fluids,
entities, and structures have pre-registered pools too; item generation is wired end-to-end first.

```
/conjure new <prompt>            generate a new item, live
/conjure list                    list configured item slots
/conjure edit <index> <prompt>   re-generate an existing slot
```

---

## Running it

### Prerequisites
- **JDK 21** (`java -version` → 21.x). Already present on this machine.
- For the default **local** model mode: **Ollama running** with a model pulled.
  - `ollama list` should show a model. Default is `llama3.3:latest`.
  - To use a different one, edit `run/config/conjure-common.toml` → `[text] localModel = "..."`
    (the config file is created on first launch).
  - To use Anthropic instead: set `[text] provider = "ANTHROPIC"` and export your key into the
    env var named by `[text] anthropicKeyEnv` (default `ANTHROPIC_API_KEY`).

### Launch the dev client
From the project root (`C:\Users\nrper\IdeaProjects\Conjure`):
```
./gradlew runClient
```
- First launch downloads Minecraft + NeoForge (several minutes); later launches are quick.
- Or open the project in IntelliJ and run the generated **runClient** task/run-config.

### Use it
1. **Singleplayer → Create New World** → Game Mode **Creative**, **Allow Cheats: ON**.
2. In chat, run a prompt (local models take a few seconds):
   ```
   /conjure new a glowing ember dagger that heals you when used
   ```
   Watch chat for progress (`Generating texture… / name… / behavior…`) then the result line.
3. Give yourself the item it reports (e.g. slot 0):
   ```
   /give @s conjure:item_slot_0
   ```
   The generated icon appears with no relaunch. **Right-click** to run its generated behavior.
4. `/conjure list` shows what you've made; `/conjure edit 0 a frost dagger instead` re-rolls it.

Generated assets live under `run/conjure/` — `generated/` (textures, models, scripts) and
`slots/` (persisted slot metadata; these reload on restart).

### Troubleshooting
| Symptom | Fix |
|---|---|
| `Generation failed: …Connection refused` | Ollama isn't running, or wrong endpoint/model. `ollama list`; check `[text]` config. |
| Item shows a black-and-purple "missing model" | Only **generated** slots have models; unconfigured slots look broken — expected. |
| Texture didn't update after `edit` | Press **F3+T** to force a resource reload (normally automatic). |
| `neo_version` fails to resolve on first build | Bump it to the latest `21.1.x` in `gradle.properties`. |
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
| Blocks | 500 | Split across **archetype buckets** (render layer etc. is baked) |
| Fluids | 32 | Each = source + flowing + FluidType + liquid block + bucket |
| Entities | 128 | Size buckets; appearance via GeckoLib (future) |
| Structures | 100 | Datapack-driven; appears in **newly generated chunks** only |

What's **live-reloadable**: textures/models (resource pack), recipes/loot (datapack), behavior
(interpreted JS). What needs a restart: registry ids beyond the pools. World-creation-only:
terrain shape & dimensions.

Behavior is sandboxed **Rhino** JavaScript with a small `ctx` host API
(`message`, `giveItem`, `heal`, `damage`, `spawnParticleHere`, `getPlayerName`).

## Model routing (local or cloud)

`run/config/conjure-common.toml` selects providers per task; defaults to **local** Ollama.
Anthropic keys are read from an env var, never stored in config. See `dev.conjure.Config` and
`dev.conjure.ai.*`.

## Architecture map

```
dev.conjure
├─ Conjure.java / Config.java       entrypoint + model-routing config
├─ content/                         slot store (SlotKind/SlotDefinition/SlotRegistry)
│                                   + item/block/fluid/entity/structure shells + BlockArchetype
├─ registry/                        the pre-registered pools (Items/Blocks/Fluids/Entities/Structures)
├─ ai/  ├─ providers (Ollama/Anthropic + ProviderFactory)
│       └─ agents/ (TextureAgent, DataAgent, LogicAgent, JsonHelper)
├─ gen/                             GenerationService orchestrator + DynamicPackManager + PixelTexture
├─ script/                         sandboxed Rhino runtime + ctx host API
├─ persist/                         SlotStore (slots survive restart)
├─ client/                         resource-pack registration + live reload
└─ command/                        /conjure commands
```
Full design rationale: **`src/ARCHITECTURE.md`**. Contributing with parallel agents:
**`docs/PARALLEL_AGENTS.md`**.

## Roadmap

1. ✅ Item/block pools + provider layer
2. ✅ Vertical slice: `/conjure new` → Ollama → pixel-art → live item
3. ✅ Fluid (32) + entity (128) pools + 100-slot structure bucket
4. ✅ Sandboxed Rhino scripting; behavior wired into items/blocks
5. ✅ Multi-agent generation (orchestrator → texture / data / logic)
6. ✅ Persistence + `/conjure list` + `/conjure edit`
7. ⬜ Entity models via GeckoLib; structure jigsaw/template-pool writer ("a village")
8. ⬜ Datapack manager for recipes/loot; item tooltips from generated description
9. ⬜ Dedicated-server sync (slots are client-local; singleplayer only)
10. ⬜ `jarJar` Rhino for a shippable (non-dev) jar
