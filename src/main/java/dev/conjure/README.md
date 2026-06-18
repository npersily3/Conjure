# dev.conjure — root package

This package is the mod entrypoint. It wires together all the subpackages at startup and holds
the two cross-cutting singletons that every other package depends on: the mod instance and the
shared config. All of the interesting work lives in the subpackages listed below.

## Files

| File | Purpose |
|------|---------|
| `Conjure.java` | `@Mod` entrypoint — kicks off all pool registrations at startup and logs pool sizes. |
| `Config.java` | NeoForge `ModConfigSpec` holding text-model routing (Ollama/Anthropic), image-model routing (ComfyUI), quality tiers, and feature toggles (`entityAnimations`, `interactivity`). API keys are never stored here — only the name of the env var to read them from. |

## Subpackages

| Package | What it does | README |
|---------|-------------|--------|
| [`ai/`](ai/README.md) | Provider interfaces and implementations (Ollama, Anthropic, ComfyUI); `ProviderFactory` selects the active backend from config. |
| [`bootstrap/`](bootstrap/README.md) | First-launch auto-install of the external AI backends (Ollama + text model, ComfyUI + checkpoint) on a background thread. Windows-only; toggled by `features.autoInstallBackends`. |
| [`ai/agents/`](ai/agents/README.md) | Focused sub-agents that call providers: Router, Texture, Data, Logic, Machine, ModPlanner, and the JSON repair helper. |
| [`client/`](client/README.md) | Client-only code: dynamic resource-pack registration, live resource reload, GeckoLib mob model/renderer, machine GUI screen. |
| [`command/`](command/README.md) | Registers the `/conjure` command tree (`new`, `list`, `edit`, `place`, `mod`). |
| [`content/`](content/README.md) | The slot store: `SlotKind`, `SlotDefinition`, `SlotRegistry` — the core data contract that bridges frozen JVM registries to AI-produced runtime data. |
| [`content/block/`](content/block/README.md) | Block shell (`ConjureBlock`), block entity (`ConjureBlockEntity`), container menu (`ConjureMenu`), and the archetype bucket enum. |
| [`content/entity/`](content/entity/README.md) | The generic GeckoLib mob shell (`ConjureMob`) that reads all behaviour from its `SlotDefinition`. |
| [`content/fluid/`](content/fluid/README.md) | The four per-slot fluid components: `FluidType`, bucket item, liquid block, and client texture extensions. |
| [`content/item/`](content/item/README.md) | Item shells: `ConjureItem` (standalone items) and `ConjureBlockItem` (block-item wrappers with runtime names). |
| [`content/structure/`](content/structure/README.md) | `StructurePlacer` reads the AI-generated block layout from a `SlotDefinition` and places it in the world. |
| [`gen/`](gen/README.md) | Generation orchestration: `GenerationService` dispatcher, `ModService` multi-piece planner, `DynamicPackManager` for disk I/O, and `PixelTexture` for PNG encoding. |
| [`gen/assets/`](gen/assets/README.md) | Per-kind asset writers (`EntityAssets`, `FluidAssets`) that delegate to `DynamicPackManager`. |
| [`gen/pipeline/`](gen/pipeline/README.md) | One `GenerationPipeline` implementation per content kind (Item, Block, Fluid, Entity, Structure) plus the shared `PipelineSupport` helper. |
| [`persist/`](persist/README.md) | `SlotStore` serialises `SlotDefinition`s to JSON on disk; `SlotStoreLoader` restores them at server startup. |
| [`registry/`](registry/README.md) | The pre-registered pools: `ConjureItems`, `ConjureBlocks`, `ConjureFluids`, `ConjureEntities`, `ConjureStructures`, plus `ConjureBlockEntities`, `ConjureMenus`, and `ConjureTabs`. |
| [`script/`](script/README.md) | Sandboxed Rhino JavaScript runtime: `ScriptRuntime`, `ScriptContext` (the `ctx` host API), and `ScriptException`. |
