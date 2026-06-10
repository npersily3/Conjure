# Parallel agentic work — framework

How to split Conjure work across several Claude sub-agents that run **at the same time**
without clobbering each other. Read this before launching a multi-part task.

## Why the first big session was slow (and the fix)

That session ran three agents **sequentially** because worktree isolation was unavailable: the
parent Claude had been launched from `C:\WINDOWS\system32` (not a git repo), so the Agent tool
couldn't create per-agent worktrees, and running concurrent agents in one shared working tree
would have collided on Gradle builds and shared files.

**The fix is structural — do all three of these:**
1. **Launch Claude from the repo root** (`C:\Users\nrper\IdeaProjects\Conjure`). Now
   `Agent(isolation: "worktree")` gives each agent its own git worktree + branch + build dir →
   true parallelism, zero collisions.
2. **Partition by disjoint file ownership** (lanes below) so merges are trivial.
3. **Define seams up front** so agents never need to see each other's in-progress code.

With those, this milestone's three agents could have run in parallel in ~10 min instead of ~30.

## Golden rules

- One agent = one **lane** (disjoint file set). Never let two agents own the same file.
- **Contracts are frozen** during a parallel run; agree on them before spawning.
- Every agent must **leave its worktree compiling** (`./gradlew compileJava --no-daemon` →
  BUILD SUCCESSFUL) before reporting done.
- The **parent integrates**: merge branches, run one clean compile, commit. Agents never commit.
- Prefer **fan-out over depth**: 3–4 parallel agents on disjoint lanes beats a chain.

## Ownership lanes (Conjure-specific)

| Lane | Owns (create/edit only here) | Typical work |
|------|------------------------------|--------------|
| **GEN** | `ai/**`, `gen/**`, `persist/**`, `command/**` | generation agents, orchestration, commands, persistence |
| **RUNTIME** | `script/**`, `content/item/**`, `content/block/**`, + Rhino dep line in `build.gradle` | scripting, behavior execution, item/block interaction |
| **CONTENT** | `registry/**`, `content/{fluid,entity,structure,...}/**` | new/expanded content pools |
| **CLIENT** | `client/**` | rendering, resource/data-pack registration, tooltips |
| **DOCS** | `*.md`, `docs/**`, `src/ARCHITECTURE.md` | documentation |

**Frozen / shared — do NOT edit during a parallel run** (they are contracts every lane reads):
`content/SlotKind.java`, `content/SlotDefinition.java`, `content/SlotRegistry.java`,
`content/block/BlockArchetype.java`, `Config.java`. Need extra per-slot data? Use
`SlotDefinition`'s `numbers`/`strings` maps — never add fields mid-run.

**Single-owner hub files** (assign to exactly ONE lane per run, or let the parent edit during
integration): `Conjure.java` (registration wiring), `build.gradle` (beyond the one dep line),
`gradle.properties`. If two lanes would both need `Conjure.java`, give it to the parent instead.

## Seams (define before spawning)

A seam lets two lanes integrate via a stable contract neither has to see the other implement:
- **Behavior scripts**: file at `<gamedir>/conjure/generated/scripts/<id>.js`, where `<id>` ==
  `SlotDefinition.behaviorScriptId`. GEN writes it; RUNTIME runs it.
- **`ctx` host API**: `message / giveItem / heal / damage / spawnParticleHere / getPlayerName`.
  Agreed signature list; GEN's LogicAgent generates against it, RUNTIME implements it.
- **Dynamic assets**: written under `<gamedir>/conjure/generated/`; CLIENT registers the pack,
  anyone may write files into it.

If a task needs a *new* seam, the parent defines the interface (or a stub) and commits it to the
base branch **before** spawning, so all worktrees branch from a base that already has it.

## Standard agent prompt template

Fill the placeholders. Keep the preamble verbatim — it's what prevented API-guessing churn.

```
You are implementing part of the "Conjure" Minecraft mod (NeoForge 1.21.1, JDK 21,
modid `conjure`, base package `dev.conjure`). Task: <ONE-LINE GOAL>.

ENVIRONMENT
- You are in an isolated git worktree. Build/verify: `./gradlew compileJava --no-daemon`
  from the worktree root; it MUST end BUILD SUCCESSFUL before you finish. Run it once at the
  start (populates build/moddev/artifacts/ incl. the decompiled sources jar).
- VERIFY every unfamiliar NeoForge/MC API against the real sources — do NOT guess:
  `unzip -o -q build/moddev/artifacts/neoforge-21.1.93-sources.jar "<path/To/Class.java>" -d /tmp/nf`
  then read it. Mappings are Mojang official + Parchment.
- Do NOT run git / commit.

CONTEXT — read first: src/ARCHITECTURE.md, CLAUDE.md, and
  content/{SlotKind,SlotDefinition,SlotRegistry}.java, plus <FILES RELEVANT TO LANE>.

OWNERSHIP — you may ONLY create/edit: <LANE FILE GLOBS>. Touch nothing else.
  Do NOT modify SlotDefinition's fields (use its numbers/strings maps).

SEAM — <the contract(s) this lane integrates through, e.g. the behavior-script seam + ctx API>.

TASKS
  1. ...
  2. ...

REPORT when done: (a) files created/edited, (b) compileJava is BUILD SUCCESSFUL,
  (c) <lane-specific contract surface>, (d) TODOs/limitations.
```

## Launch & integration protocol (parent)

1. **Prep**: ensure the base branch compiles; if a new seam is needed, add the interface/stub and
   commit it.
2. **Spawn in parallel**: one `Agent(isolation: "worktree", subagent_type: "general-purpose",
   model: "sonnet")` call per lane, **all in a single message** so they run concurrently.
3. **Integrate**: merge each agent's branch (disjoint files → clean merges). If the parent owns a
   hub file like `Conjure.java`, apply the wiring now.
4. **Verify**: one `./gradlew clean compileJava --no-daemon` on the merged tree. Spot-check the
   riskiest runtime wiring (registration attached to the mod bus, seam paths match).
5. **Commit** the integrated milestone.

## Worked example — this milestone

Lanes **GEN** (orchestrator + agents + persistence + commands), **RUNTIME** (Rhino sandbox +
item/block wiring + Rhino dep), **CONTENT** (fluids + entities + structure bucket + `Conjure.java`
wiring). Seams: behavior-script file + `ctx` API. CONTENT solely owned `Conjure.java`. Result:
39 files, clean compile — and with worktree isolation these three are fully parallelizable.
