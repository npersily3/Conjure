# `dev.conjure.bootstrap`

First-launch setup of the **external AI backends** the mod relies on. Everything Gradle can fetch
(Minecraft, NeoForge, GeckoLib, Rhino) is already bundled in the jar; this package handles the
things that live *outside* the JVM: the Ollama server + text model, and ComfyUI + a checkpoint.

It runs once during `FMLCommonSetupEvent` (wired in `Conjure.java`), entirely on the
`conjure-bootstrap` daemon thread, so it never blocks world load. Every step is idempotent — if a
backend is already present/reachable, the run is a no-op. Windows-only for now; disable with
`features.autoInstallBackends = false` in `conjure-common.toml`. See `docs/SETUP.md` for behavior,
manual macOS/Linux steps, and the distribution caveats.

| File | Purpose |
|------|---------|
| `BackendBootstrap.java` | Public entry point (`start()`). Checks the config kill-switch, guards to Windows, spawns the daemon thread, and runs the Ollama then ComfyUI bootstrappers, isolating failures. |
| `OllamaBootstrap.java` | Ensures Ollama is running and the configured text model (`gemma4:latest` by default) is pulled: detect endpoint → find/install standalone Ollama → `ollama serve` → `POST /api/pull`. |
| `ComfyUiBootstrap.java` | Downloads the ComfyUI Windows portable build, extracts it, and fetches a default SD checkpoint into `models/checkpoints`. Does not auto-start the server. |
| `BootstrapUtil.java` | Shared helpers: HTTP download with progress logging, `.zip` extraction (JDK) and `.7z` extraction (Windows `tar`), process launch/detach, PATH lookup, reachability polling. |

Downloads land under `<gamedir>/conjure/runtime/` (`ollama/` and `ComfyUI/`).
