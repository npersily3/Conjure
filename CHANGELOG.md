# Changelog

All notable changes to Conjure are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/); the project predates semantic versioning so
dates are omitted until a release cadence exists.

## [Unreleased]

### Added
- **Stateful blocks (`ACTIVATABLE` archetype).** A new `ConjureActivatableBlock` carries a
  runtime-toggleable `active` on/off state — the pre-registered way to ship doors, lamps, switches,
  and locked containers despite the registry freeze. `active` drives a two-texture model swap and the
  emitted light level (`activeLight` tunable). Appended last in `BlockArchetype` (80 slots) so no
  existing slot index shifts.
- **`STATEFUL` classification.** `MachineAgent` can now route a block to the stateful archetype, via
  the model or a keyword backstop (door/gate/hatch/lamp/lantern/switch/lever/safe/vault/…). Doors and
  lamps toggle freely on right-click; lock-like blocks (safe/vault) get a gating script and a `keyId`.
- **Item interaction hooks.** `ConjureItem` now runs its behavior script on three hooks instead of
  one: `use` (right-click air), `useOn` (right-click a block — passes the clicked block + face), and
  `hurtEnemy` (hitting a mob — passes the mob).
- **Expanded `ctx` script API.** New verbs in `ScriptContext`:
  - movement: `launch`, `dashForward`
  - world: `ignite`, `lightning`, `explode` (damages mobs, breaks no blocks)
  - combat (the hit mob): `hasTargetEntity`, `damageTarget`, `igniteTarget`, `knockbackTarget`,
    `effectTarget`
  - target block: `hasTargetBlock`, `getBlockActive`, `setBlockActive`, `breakTargetBlock`,
    `setTargetBlock`, `placeOnFace`
  - cross-object tunables: `heldStr`/`heldNum`, `targetBlockStr`/`targetBlockNum`
- **Key ↔ lock pattern.** A lockable block exposes a `keyId`; a key item's `useOn` script opens it via
  `ctx.setBlockActive`. By default any conjured key opens any conjured lock; set matching `keyId`s for
  a key that fits only its safe (`ctx.targetBlockStr("keyId") === ctx.heldStr("keyId")`).
- `DynamicPackManager.writeActivatableAssets` — two textures (off/on) + a two-variant blockstate.
- `slotDef()` accessors on `ConjureItem` / `ConjureBlockItem` for cross-object script reads.
- **Intent debug overlay.** `DataAgent` now emits a split `visualIntent` (what it should depict) and
  `usageIntent` (what it should do); both are stored per slot and, when `features.showIntent` is on
  (default on), appended in red to every conjured block/item tooltip via `IntentTooltip`. Lets you
  tell a texture/model failure (texture ≠ visual intent) from a behavior gap (code can't do the usage
  intent). Falls back to the raw prompt when a weak model omits the fields.

### Changed
- `LogicAgent` prompt documents the full expanded `ctx` API and the key/unlock pattern.
- Player-motion verbs resync velocity to the client (`ClientboundSetEntityMotionPacket`) so launches
  and dashes actually move the player.

### Notes / known limitations
- `useOn` returns `CONSUME` for any block while holding a scripted item, so such an item can't open
  vanilla containers in the same hand.
- Stateful "doors" swap model/state but are not yet walk-through (vanilla `DoorBlock` archetype is
  future work); furnace `lit` glow and distinct auto-paired key↔safe ids are also future work.
