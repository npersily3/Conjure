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
| `TextureAgent.java` | Generates a pixel-art texture. Primary path: ComfyUI image backend → nearest-neighbour downscale via `PixelTexture.fromPng`. Fallback: asks the text model to emit a 16×16 JSON palette/grid. |
| `DataAgent.java` | Generates a display name and one-sentence flavour description for a content piece. Returns a `Result` record. |
| `LogicAgent.java` | Generates a small right-click behavior script in JavaScript restricted to the `ctx` API. Strips markdown fences from the raw model output. |
| `MachineAgent.java` | Decides whether a generated block should be PLAIN, SCRIPT-driven, or a MACHINE with a furnace-style recipe (including `recipe_input`, `recipe_output`, `recipe_ticks`). |
| `ModPlannerAgent.java` | Decomposes a high-level mod concept or themed request into a list of concrete single-piece prompts. Two modes: EXPANSIVE (always many pieces) and AUTO (single concrete prompts stay one piece). |
| `JsonHelper.java` | Extracts the outermost `{...}` block from raw model output, parses it as JSON, and performs exactly one repair-retry on parse failure by sending the error back to the model. |
