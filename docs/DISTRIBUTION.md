# Conjure — Distribution Analysis

This document covers what it takes to ship Conjure to end users, what the friction points are,
and the realistic options for doing so. It is written for the maintainer to inform a future
decision — not a plan to execute today.

> **First-launch auto-install — must change before distribution.** The mod currently downloads the
> AI backends automatically on first launch (`dev.conjure.bootstrap`), and **always downloads
> ComfyUI + a checkpoint** (~10–14 GB total) with no prompt. That's a deliberate single-developer
> default. Before shipping to others it should become **user-initiated and opt-in** (especially the
> heavy ComfyUI/checkpoint download), be made cross-platform (it's Windows-only and leans on
> Windows' bundled `tar` for `.7z`), and respect mod-platform rules about downloading external
> executables. See `docs/SETUP.md` for current behavior and the `features.autoInstallBackends`
> kill-switch.

---

## 1. What the build artifact actually is

Running `./gradlew build` produces `build/libs/conjure-0.1.0.jar`.

Key facts about this jar:

- **Rhino is bundled jar-in-jar.** The build uses NeoForge's `jarJar` mechanism to embed
  `org.mozilla:rhino:1.7.15` inside the jar. End users do not need to install Rhino separately;
  it ships inside the mod file. This is already correct for distribution.
- **GeckoLib is NOT bundled.** `software.bernie.geckolib:geckolib-neoforge-1.21.1:4.7.6` is
  declared as a plain `implementation` dependency (compile + dev classpath), not jarJar'd. It is
  declared as a required dependency in `neoforge.mods.toml`. End users must install GeckoLib as
  a separate mod file, identical to how most entity-rendering mods on Modrinth work.
- The jar is about the size of the mod code plus the Rhino runtime (Rhino alone is ~1.5 MB).
  All generated content (textures, models, scripts, persisted slot metadata) lives under
  `<gamedir>/conjure/` at runtime and is never part of the jar.

---

## 2. The end-user install reality

A player wanting to run Conjure needs all of the following:

| Component | Where | Size / effort |
|---|---|---|
| Minecraft Java Edition | Mojang launcher | Already present for most players |
| NeoForge 21.1.93 | NeoForge installer | ~10 MB download, one-click installer |
| GeckoLib 4.7.6 (NeoForge) | Modrinth / CurseForge | ~1.5 MB, drop in mods/ |
| conjure-0.1.0.jar | This repo's releases | Drop in mods/ |
| **AI text backend** | User's machine or cloud | The real friction (see below) |
| ComfyUI + SD checkpoint | Optional, user's machine | ~5-10 GB, significant setup |

### The AI backend friction

This is the central distribution challenge. Every piece of content generated requires a live AI
text model call. There are two paths:

**Option A — Local Ollama (the default)**

The user must install Ollama, start the service (`ollama serve`), and pull the `llama3.3:latest`
model (~9 GB). This is not a Minecraft player's typical setup. `llama3.3` also requires a GPU
with at least 8–12 GB of VRAM for acceptable generation speed; on CPU-only machines generation
will take 30–120 seconds per piece.

**Option B — Anthropic cloud**

The user needs an Anthropic API account and a funded API key. Simpler to install (no model
download), but introduces ongoing cost and a hard dependency on an external service and internet
connectivity. Typical generation cost per `/conjure new` is under $0.05 with Sonnet; a full
`/conjure mod` run (20 pieces) is roughly $0.50–$1.00.

**ComfyUI is optional.** The LLM pixel-art fallback in `TextureAgent` means the mod works
without any image server. Textures will be simpler (16x16 grid-style), but all gameplay
functions.

---

## 3. The hard singleplayer-only constraint

From `src/ARCHITECTURE.md` section 7:

> **Singleplayer assumption (current):** the integrated server and client share one JVM, so a
> slot written by the (server-side) command is instantly visible to the (client-side) reload.
> On a dedicated server this breaks.

`SlotRegistry` is a JVM-local concurrent map. There is no server-to-client packet to push new
`SlotDefinition`s, no mechanism to trigger a client-side resource reload on a remote client,
and no shared persistence across multiple clients connecting to a dedicated server.

**Consequence for distribution:**

- The mod must be advertised and installed as a **singleplayer mod only**. Users putting it on a
  shared server will find that generated content is only visible to the player who generated it,
  and other players see broken textures (black-and-purple) or missing names.
- CurseForge/Modrinth listings should carry a prominent singleplayer-only warning.
- The mod's creative tab, `/conjure list`, and all generated slot state are inherently
  per-session until dedicated-server sync is implemented (roadmap item 15).

---

## 4. Distribution options with trade-offs

### Option A — Modrinth/CurseForge jar + slim end-user installer (setup.ps1)

**What it is:** Publish the jar normally to the mod platforms. The `setup.ps1` script (already
present in the repo) handles the AI backend setup (Ollama or Anthropic key) and optional
ComfyUI. A user installs the jar like any other mod, then runs setup.ps1 once.

**Pros:**
- Standard distribution pathway; players know how to drop a jar in their mods folder.
- GeckoLib is already on Modrinth so the dependency link works automatically.
- setup.ps1 is idempotent and safe to re-run; DryRun mode lets users preview changes.
- No bundled bloat; the jar stays small.

**Cons:**
- setup.ps1 is Windows-only. macOS/Linux players need a separate install guide or script.
- Players must read documentation to understand the AI backend requirement before they install.
- Modrinth/CurseForge listings cannot gate installation on the AI backend being present, so
  confused "it doesn't work" reports are inevitable.
- The ~9 GB Ollama model download is a shock for players who just wanted to try a mod.

**Best fit:** The correct path for this project when it reaches a releasable state. The mod
fits the standard Minecraft modding distribution model; the AI backend is just an unusual extra
prerequisite that needs clear documentation.

### Option B — All-in-one launcher/bundle including the backend

**What it is:** Ship a custom launcher (or a MultiMC/Prism instance zip) that bundles or
auto-downloads NeoForge, the mod, GeckoLib, and installs Ollama/model in one step.

**Pros:**
- Zero friction for end users; one download installs everything.
- Can enforce the exact Ollama version and model known to work.
- Eliminates the "I installed the jar but nothing works" class of support request.

**Cons:**
- Enormous engineering investment for a hobby mod. Maintaining a custom launcher is a project
  unto itself.
- Bundling Ollama + model weights (~9 GB) makes the download impractical to host.
- Prism/MultiMC instance zips can bundle mod jars but cannot bundle system-level software like
  Ollama; users still have to install that separately.
- Legal/ToS questions around redistributing Mojang/NeoForge assets in a bundle.

**Best fit:** Not recommended for this stage. The payoff does not justify the effort unless
Conjure becomes a flagship commercial product.

### Option C — Keep dev-repo-only for now

**What it is:** Do not publish to Modrinth/CurseForge. Distribute only via GitHub releases or
the README, targeting technically capable users who can follow the setup guide.

**Pros:**
- Zero maintenance overhead for distribution infrastructure.
- No obligation to support users who lack the AI backend setup.
- Appropriate for the current development stage (roadmap items 13-15 are not done).
- Singleplayer-only limitation is less awkward when the audience is developers.

**Cons:**
- Minimal reach; effectively zero non-developer adoption.
- Does not validate the end-user experience; feedback loop is slower.

**Best fit:** The correct choice right now, until at minimum roadmap items 13 (structure
worldgen) and 15 (dedicated-server sync) are done, and the Windows-only setup story is
resolved.

---

## 5. Recommendation

**Ship nothing publicly until roadmap item 15 (dedicated-server sync) is addressed or the
singleplayer-only constraint is prominently baked into the mod's UX.**

When the mod is ready to publish, use **Option A** (Modrinth/CurseForge jar + setup.ps1):

1. Publish the jar to Modrinth with GeckoLib listed as a required dependency.
2. The project page should lead with "singleplayer only" and "requires Ollama OR an Anthropic
   API key" in the first paragraph, not buried in a wiki.
3. Extend setup.ps1 to cover macOS/Linux (or write a `setup.sh` equivalent) before publishing
   broadly — Windows-only setup is a significant barrier.
4. Consider making the Anthropic path the recommended quickstart (no 9 GB download, no GPU
   requirement) while keeping Ollama as the "free, private, local" alternative.
5. Keep ComfyUI optional and clearly labeled as such; the LLM pixel-art fallback is good enough
   for most players to evaluate the mod.

The jarJar Rhino bundling is already done correctly. The GeckoLib dependency declaration in
`neoforge.mods.toml` will be handled automatically by Modrinth's dependency resolution. The
main remaining work before a public release is the multiplayer-safety story and cross-platform
setup tooling.
