package dev.conjure.ai.agents;

import com.google.gson.JsonObject;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;
import dev.conjure.content.SlotKind;

import java.util.Locale;

/**
 * Routing sub-agent: looks at a single natural-language prompt and decides which content family
 * Conjure should build it as, so the user only ever types {@code /conjure new <prompt>} and the
 * model picks the path. This is the provider-agnostic alternative to model tool-calling (local
 * Ollama models don't reliably support function calling), and the place to plug in FLUID / ENTITY
 * routing once those generation pipelines exist.
 *
 * <p>Today only {@link SlotKind#ITEM} and {@link SlotKind#BLOCK} have generation pipelines, so the
 * model is constrained to those two and anything ambiguous falls back to {@link SlotKind#ITEM}.
 */
public final class RouterAgent {

    private static final String SYSTEM = """
            You are a router for a Minecraft content generator. Given a short prompt, decide which
            kind of content to create, and reply with ONLY a JSON object — no prose, no markdown
            fences. Schema:
            {
              "kind": "ITEM" | "BLOCK" | "FLUID" | "ENTITY" | "STRUCTURE"
            }
            Guidance:
            - ITEM: things held, worn, eaten, or used from the inventory — tools, weapons, food,
              potions, materials, trinkets, wearables.
            - BLOCK: things placed and built with — ores, building materials, decorative blocks,
              lamps, furniture, AND interactive machines/workstations like furnaces, kilns, altars.
            - FLUID: liquids you can place/bucket and that flow — lava-like, water-like, magical brews.
            - ENTITY: living creatures / mobs — animals, monsters, NPCs that move and can be fought.
            - STRUCTURE: multi-block builds placed in the world — towers, huts, shrines, ruins.
            If genuinely ambiguous, choose ITEM.
            """;

    /**
     * Classifies {@code prompt} into a {@link SlotKind}. Never throws for routing reasons: on any
     * model/parse failure it logs nothing and defaults to {@link SlotKind#ITEM}, so generation can
     * always proceed.
     *
     * @param prompt the user-supplied description
     * @return {@link SlotKind#ITEM} or {@link SlotKind#BLOCK}
     */
    public SlotKind classify(String prompt) {
        try {
            TextModelProvider provider = ProviderFactory.text();
            String userMsg = "Route this Minecraft content request: " + prompt;
            String raw = provider.complete(SYSTEM, userMsg);
            JsonObject obj = JsonHelper.extractAndParse(raw, SYSTEM, provider, userMsg);

            if (obj.has("kind")) {
                String kind = obj.get("kind").getAsString().trim().toUpperCase(Locale.ROOT);
                try {
                    return SlotKind.valueOf(kind);
                } catch (IllegalArgumentException unknownKind) {
                    // Unrecognised label — fall through to the ITEM default.
                }
            }
        } catch (Exception ignored) {
            // Routing is best-effort; fall through to the ITEM default below.
        }
        return SlotKind.ITEM;
    }

    public RouterAgent() {}
}
