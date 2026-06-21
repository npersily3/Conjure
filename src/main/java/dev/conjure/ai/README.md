# dev.conjure.ai — provider layer

This package defines the two provider interfaces (`TextModelProvider`, `ImageModelProvider`) and
their concrete implementations. It is deliberately provider-agnostic: swapping from local Ollama
to Anthropic, or switching the image backend, only changes which implementation `ProviderFactory`
returns — no other code changes.

The `agents/` sub-package contains the focused sub-agents that actually call these providers; this
package is purely about the transport and configuration layer.

## Files

| File | Purpose |
|------|---------|
| `TextModelProvider.java` | Interface: `complete(system, user) → String` plus a human-readable `id()`. All text/logic agents use this. |
| `Prompts.java` | Two-level system-prompt scaffolding: a shared `GLOBAL` preamble + a per-`SlotKind` purpose layer, composed via `system(kind, agentSystem)` so each agent's own `SYSTEM` becomes the focused leaf and conveys whether it's making an item vs a block vs … |
| `ImageModelProvider.java` | Interface: `generateTexture(prompt, size, kind) → byte[]` (raw PNG). Used by `TextureAgent`. |
| `TextureKind.java` | Enum: `ITEM`/`BLOCK`/`ENTITY`/`FLUID`. Steers prompts — a BLOCK gets an opaque tileable-surface prompt, an ITEM a centered-icon-on-transparent prompt. |
| `OllamaProvider.java` | `TextModelProvider` that talks to a local Ollama server via `POST /api/chat`. Default local-mode backend. |
| `AnthropicProvider.java` | `TextModelProvider` that calls the Anthropic Messages API. API key read from an env var named in config. |
| `ComfyUIProvider.java` | `ImageModelProvider` for a local ComfyUI server: queues a txt2img workflow graph, polls `/history`, downloads the PNG. Three-step async protocol. Positive/negative prompts vary by `TextureKind` (block vs icon). |
| `ProviderFactory.java` | Reads `Config` and constructs the active `TextModelProvider` or `ImageModelProvider`. Called per-request so config edits apply live. |
| `ProviderMode.java` | Enum: `LOCAL` (Ollama / ComfyUI) or `ANTHROPIC` (cloud). Controls routing in `ProviderFactory`. |
| `ImageQuality.java` | Enum: `FAST` (fewer diffusion steps, 512 px native) or `HIGH` (more steps, 768 px native). Drives ComfyUI step count and native resolution. |
