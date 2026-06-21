# dev.conjure.ai.agents — generation sub-agents

Each class here is a single-responsibility AI sub-agent: it calls a `TextModelProvider` (or
falls through to an `ImageModelProvider`) with a focused system prompt, parses the result, and
returns a typed value. The `GenerationService` / pipeline layer composes these agents to produce
a complete slot.

`JsonHelper` is a shared utility rather than an agent, but it lives here because every JSON-
emitting agent depends on it.

## Files

| File | Purpose |
|------|---------|
| `RouterAgent.java` | Classifies a free-form prompt into a `SlotKind` (`ITEM`/`BLOCK`/`FLUID`/`ENTITY`/`STRUCTURE`) so the user only needs `/conjure new`. Falls back to `ITEM` on any failure. |
| `TextureAgent.java` | Generates a pixel-art texture for a given `TextureKind` (ITEM/BLOCK/FLUID each get a dedicated prompt — FLUID is a tileable liquid surface, not a centered icon). Primary path: ComfyUI image backend → `PixelTexture` post-processing; enriches with `visualIntent`. Fallback: text model emits a 16×16 JSON palette/grid. |
| `DataAgent.java` | Generates a display name + one-sentence description, a `visualIntent` (what it should look like) and a **technical per-trigger `usageIntent`** spec (`trigger: action; …` with concrete numbers — drives script generation and the debug overlay), plus `activeVisual` and the building-material `isMaterial`/`variants` classification. Returns a `Result` record. |
| `RecipeAgent.java` | Obtainability recipes. `chooseRecipe` picks a *varied* recipe type (shapeless/shaped/smelting/blasting/smithing/stonecutting) + ingredients (lightly biased toward crafting via a conjured machine); legacy `generate`/`craftIngredients` remain for material families. Always returns usable `minecraft:` ids. |
| `LogicAgent.java` | Generates a behavior script in JavaScript restricted to the `ctx` API, driven by the technical `usageIntent` spec. Hard "JS-not-Java" prompt, then a Rhino **compile-check + one repair retry** (via `ScriptRuntime.validate`) before the script is saved. Strips markdown fences. |
| `WorldgenAgent.java` | From a prompt + target block, produces ore worldgen parameters (vein size, veins/chunk, min/max Y, biome selector) **and** a human-readable `worldgenIntent` string for the tooltip overlay. Robust ore-default fallbacks. |
| `MachineAgent.java` | Decides whether a generated block is PLAIN, SCRIPT-driven, or a WORKBENCH (1–9 `inputs` → `output`, optional `fuel`, `ticks`). A deterministic keyword override forces WORKBENCH for obvious workstations (station/kiln/wheel/…) that weak local models misclassify, and a focused recipe-only follow-up call fills the recipe when the first reply lacks one. |
| `ModPlannerAgent.java` | Decomposes a high-level mod concept or themed request into a list of concrete single-piece prompts. Two modes: EXPANSIVE (always many pieces) and AUTO (single concrete prompts stay one piece). |
| `JsonHelper.java` | Extracts the outermost `{...}` block from raw model output, parses it as JSON, and performs exactly one repair-retry on parse failure by sending the error back to the model. |
