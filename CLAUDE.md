# Conjure — agent context

AI-driven, on-the-fly Minecraft content for **NeoForge 1.21.1** (JDK 21). In-game commands
spin up a team of AI agents that generate items/blocks/fluids/mobs/structures that appear
**without relaunching the game**, up to fixed pool sizes.

- modid: `conjure` · base package: `dev.conjure` · mappings: Mojang official + Parchment
- Working name "Conjure" — may be renamed.

## The core trick (read this first)
Minecraft **freezes its registries** after startup — you can never add a new registry id at
runtime, and the JVM can't load new classes. So Conjure pre-registers large **pools of generic
slots** at startup; AI never registers anything, it fills the runtime `SlotDefinition` behind
an existing slot. See `content/{SlotKind,SlotDefinition,SlotRegistry}.java`. Background:
the design constraints are summarized in `src/ARCHITECTURE.md`.

## Build / run
```
./gradlew compileJava --no-daemon     # compile (must end BUILD SUCCESSFUL)
./gradlew runClient                    # launch a dev client
```
First build downloads NeoForge+MC (minutes); later builds are ~10s.

## Verifying NeoForge/MC APIs (important)
Do NOT guess MC/NeoForge signatures — they change across versions. The decompiled sources are
at `build/moddev/artifacts/neoforge-21.1.93-sources.jar` (present after first compile). Extract
and read the class you need, e.g.:
```
unzip -o -q build/moddev/artifacts/neoforge-21.1.93-sources.jar \
  "net/minecraft/world/level/block/Block.java" -d /tmp/nf && less /tmp/nf/net/minecraft/world/level/block/Block.java
```

## Conventions
- **Slot store**: `SlotRegistry.get(kind, index)` → mutable `SlotDefinition` (displayName,
  texturePath, behaviorScriptId, sourcePrompt, plus `numbers`/`strings` maps for extra data).
  Don't add fields to `SlotDefinition`; use the maps.
- **Behavior-script seam**: a slot's behavior is JS at
  `<gamedir>/conjure/generated/scripts/<id>.js` where `<id>` == `SlotDefinition.behaviorScriptId`.
  The generation layer writes the file; the Rhino runtime (`script/`) loads + runs it sandboxed.
- **Dynamic assets**: textures/models written under `<gamedir>/conjure/generated/` (a runtime
  resource pack registered in `client/ConjureClientPack`); `ClientHooks.reloadResources()`
  applies them live.
- **Model routing**: `dev.conjure.Config` + `dev.conjure.ai` — LOCAL (Ollama, default,
  `llama3.3:latest`) or ANTHROPIC (key from env var). `ProviderFactory.text()`.

## Layout
```
dev.conjure
├─ Conjure.java / Config.java          entrypoint + config
├─ content/                            slot store + item/block/fluid/entity/structure shells
├─ registry/                           the pre-registered pools
├─ ai/                                 providers + generation sub-agents
├─ gen/                                orchestration, dynamic pack, pixel-art
├─ script/                             sandboxed Rhino runtime (behavior)
├─ client/                            client-only pack registration + reload
└─ command/                           /conjure commands
```

## Status & roadmap
Roadmap lives in `README.md`. Pools: items+blocks done; fluids/entities/structures + Rhino
scripting + multi-agent generation + persistence + `/conjure list|edit` are the current build.
Singleplayer only for now (slots are client-local; dedicated-server sync is future).

## Conventions for working here
- Commit/branch only when asked. Don't skip hooks.
- Keep changes compiling; run `./gradlew compileJava` before declaring done.
