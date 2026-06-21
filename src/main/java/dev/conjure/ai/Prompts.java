package dev.conjure.ai;

import dev.conjure.content.SlotKind;

/**
 * Two-level system-prompt scaffolding shared by every generation agent.
 *
 * <p>Historically each agent carried its own fully self-contained {@code SYSTEM} string, repeating
 * the same "you generate Minecraft content / reply with only JSON" boilerplate and never knowing
 * <em>which kind</em> of thing it was generating. {@link #system(SlotKind, String)} composes three
 * layers so an agent's own prompt becomes the focused leaf:
 *
 * <pre>
 *   GLOBAL            — universal Conjure/Minecraft context + output discipline (shared by all)
 *   forKind(kind)     — the gameplay PURPOSE of this kind (item vs block vs fluid vs …)
 *   agentSystem       — the agent's task-specific instructions (its existing SYSTEM constant)
 * </pre>
 *
 * Agents keep their {@code SYSTEM} text; the duplicated preamble lives here once.
 */
public final class Prompts {

    private Prompts() {}

    /** Universal context prepended to every agent prompt. */
    public static final String GLOBAL = """
            You generate content for "Conjure", an AI-driven Minecraft mod (NeoForge 1.21.1). The
            things you describe become real, playable items/blocks/fluids/mobs that a survival player
            obtains, crafts, and uses — design with the Minecraft gameplay loop in mind, not just
            flavour. When asked for JSON, reply with ONLY a single JSON object: no prose, no comments,
            no markdown code fences.
            """;

    /** The gameplay purpose of each {@link SlotKind} — what the player is meant to do with it. */
    public static String forKind(SlotKind kind) {
        return switch (kind) {
            case ITEM -> """
                    THIS IS AN ITEM: a hand-held tool, weapon, consumable, or crafting material. Items
                    are CRAFTED from materials (not just handed out) and usually have an active effect
                    on use or on hitting a mob. Think about how the player obtains it and why they hold it.
                    """;
            case BLOCK, SLAB, STAIRS, WALL -> """
                    THIS IS A BLOCK: a placed block — building/decorative material, a functional/stateful
                    block, or a machine. It must be obtainable in survival (crafted, smelted, or mined),
                    and may be a natural resource that generates in the world.
                    """;
            case FLUID -> """
                    THIS IS A FLUID: a liquid that fills space, flows, and is picked up with a bucket.
                    """;
            case ENTITY -> """
                    THIS IS A MOB: a living entity that spawns, moves, and can be fought or farmed —
                    often the natural SOURCE of a material it drops when killed.
                    """;
            case STRUCTURE -> """
                    THIS IS A STRUCTURE: a placed arrangement of blocks the player discovers or builds.
                    """;
        };
    }

    /** Compose the full system prompt for an agent serving {@code kind}. */
    public static String system(SlotKind kind, String agentSystem) {
        return GLOBAL + "\n" + forKind(kind) + "\n" + agentSystem;
    }
}
