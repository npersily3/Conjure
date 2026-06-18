package dev.conjure.content;

/** The registry-bound content families that Conjure pre-allocates as configurable pools. */
public enum SlotKind {
    ITEM,
    BLOCK,
    FLUID,
    ENTITY,
    STRUCTURE,
    // Shaped building-block variants, spawned as a family alongside a BLOCK (see material-family
    // generation). Not router targets — they are derived from a base block, never prompted directly.
    SLAB,
    STAIRS,
    WALL
}
