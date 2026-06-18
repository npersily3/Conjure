# dev.conjure.content.entity — mob shell

This package contains the single generic mob class that backs every entity slot. It is kept
separate from the other content shells because it carries a GeckoLib dependency
(`GeoEntity`) and size-bucket wiring that no other shell kind needs.

The JVM-baked aspects of an entity (hitbox dimensions, tracking range, mob category) are fixed by
the size bucket (SMALL/MEDIUM/LARGE) assigned during registration in `ConjureEntities`. All
"personality" — name, health, speed, attack damage, skin texture — is read at runtime from the
`SlotDefinition`.

## Files

| File | Purpose |
|------|---------|
| `ConjureMob.java` | `PathfinderMob` + `GeoEntity` shell. Reads health/speed/attack/follow-range from `SlotDefinition.numbers` and applies them on spawn. Implements GeckoLib idle/walk animation controllers (toggleable via `Config.ENTITY_ANIMATIONS`). Returns an AI-generated display name from `SlotDefinition.displayName`. |
