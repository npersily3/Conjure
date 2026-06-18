# Setup & AI backends

Conjure needs two external AI backends at runtime (everything else — Minecraft, NeoForge, GeckoLib,
Rhino — is fetched/bundled by Gradle):

| Backend | Used for | Default |
|---------|----------|---------|
| **Ollama** + a text model | item/block/fluid/entity/structure logic, names, behavior scripts | `gemma4:latest` |
| **ComfyUI** + a checkpoint | generated textures | `v1-5-pruned-emaonly.safetensors` (SD 1.5) |

Without ComfyUI the mod still works — textures fall back to LLM-generated pixel art. Without a text
backend, generation can't run.

## Automatic install (Windows)

On first launch the mod bootstraps these backends itself — no separate installer needed. This runs
on a background thread (`dev.conjure.bootstrap`), so the game loads normally; generation just isn't
ready until the downloads finish. Progress is in the log (`[bootstrap] …`).

What it does, in order, skipping anything already present:
1. **Ollama** — if nothing answers on the configured endpoint (`127.0.0.1:11434`), it finds Ollama
   on `PATH` or downloads the standalone Windows build into `<gamedir>/conjure/runtime/ollama/`,
   starts `ollama serve`, then pulls the configured model (`gemma4:latest`).
2. **ComfyUI** — downloads the ComfyUI Windows **portable** build (bundled Python) into
   `<gamedir>/conjure/runtime/ComfyUI/`, extracts it, and downloads the default checkpoint into its
   `models/checkpoints/`. It does **not** auto-start ComfyUI — launch `run_nvidia_gpu.bat` from that
   folder when you want image-generated textures.

First run downloads roughly **10–14 GB** (model + ComfyUI + checkpoint). To turn the whole thing
off and manage backends yourself, set in `config/conjure-common.toml`:

```toml
[features]
    autoInstallBackends = false
```

### Caveats (Windows auto-install)
- `.7z` extraction shells out to Windows' bundled `tar` (libarchive), which reads 7z on recent
  Windows 11 builds. If it fails, extract `conjure/runtime/ComfyUI/*.7z` manually with 7-Zip.
- The ComfyUI portable build targets **NVIDIA** GPUs. On other hardware use `run_cpu.bat` (slow) or
  just rely on the pixel-art fallback.
- Only the default SD-1.5 checkpoint filename has a known download URL; if you change
  `image.fastModel`/`highModel` to something else, drop that file into `models/checkpoints/`
  yourself.

> **Note (revisit before distribution):** ComfyUI is downloaded unconditionally on first launch by
> request — a deliberately heavy, single-developer default. Before shipping to other people this
> should become opt-in (a user-initiated "download image backend" action), the `.7z` step should
> not depend on Windows' bundled `tar`, and the flow should be made cross-platform. See
> `docs/DISTRIBUTION.md`.

## Manual install (macOS / Linux)

Automatic install is Windows-only for now; on other platforms the mod logs a notice and skips. Set
things up by hand:

1. **Ollama** — install from <https://ollama.com/download>, then:
   ```sh
   ollama serve            # if not already running as a service
   ollama pull gemma4:latest
   ```
2. **ComfyUI** (optional — textures fall back to pixel art without it):
   ```sh
   git clone https://github.com/comfyanonymous/ComfyUI
   cd ComfyUI
   python -m venv venv && . venv/bin/activate
   pip install -r requirements.txt
   # place a checkpoint in models/checkpoints/, e.g. v1-5-pruned-emaonly.safetensors
   python main.py          # serves on http://127.0.0.1:8188
   ```
3. Confirm `config/conjure-common.toml` points at the right endpoints/model (defaults match the
   above).

## Using Anthropic instead of Ollama

Set `text.provider = ANTHROPIC` in the config and provide an API key via the env var named by
`text.anthropicKeyEnv` (default `ANTHROPIC_API_KEY`). With Anthropic for text you can skip Ollama;
images still use ComfyUI or the pixel-art fallback.
