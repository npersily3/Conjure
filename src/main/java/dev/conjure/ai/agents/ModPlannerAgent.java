package dev.conjure.ai.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedCollection;

/**
 * Planning sub-agent that decomposes a high-level mod concept into a list of concrete, individual
 * content generation prompts — one prompt per item/block/fluid/entity/structure the mod needs.
 *
 * <p>Example: given "a beekeeping mod" it might return:
 * <ul>
 *   <li>"a beehive block"</li>
 *   <li>"honeycomb item"</li>
 *   <li>"a bee mob"</li>
 *   <li>"honey fluid"</li>
 *   <li>"an apiary workstation block"</li>
 * </ul>
 *
 * <p>Each prompt in the returned list is a fully self-contained single-piece prompt suitable for
 * direct submission to {@link dev.conjure.gen.GenerationService#generate(String, java.util.function.Consumer)}.
 *
 * <p>Mirrors {@link DataAgent}'s structure: one system prompt, one user message, JSON parse via
 * {@link JsonHelper#extractAndParse}, with a cap of {@value #MAX_PIECES} pieces.
 */
public final class ModPlannerAgent {

    /** Maximum number of pieces to generate — respects pool sizes and keeps plans actionable. */
    static final int MAX_PIECES = 15;

    private static final String SYSTEM = """
            You are a planner for a Minecraft content generator called Conjure.
            Given a high-level mod concept, decompose it into the individual content pieces
            that the mod needs — items, blocks, fluids, entities (mobs), and/or structures.
            Each piece must be a short, concrete, self-contained prompt for exactly ONE
            Minecraft content piece (e.g. "a beehive block", "honeycomb item", "a bee mob",
            "honey fluid", "an apiary workstation block").
            Rules:
            - Reply with ONLY a JSON object — no prose, no markdown fences.
            - Produce between 3 and 15 pieces (aim for the number that makes the mod feel
              complete, not padded).
            - Keep each piece prompt concise (3–8 words) and unambiguous.
            - Cover a mix of content types when the concept warrants it.
            - Never repeat the same concept twice.
            Schema:
            {
              "pieces": ["<prompt>", "<prompt>", ...]
            }
            """;

    /**
     * Asks the text model to decompose {@code modDescription} into concrete content prompts.
     *
     * @param modDescription the user's high-level mod concept (e.g. "a beekeeping mod")
     * @return a non-null, non-empty list of single-piece generation prompts, capped at
     *         {@value #MAX_PIECES} entries and de-duplicated
     * @throws Exception if the model call or JSON parse fails on both the initial attempt and the
     *                   repair retry performed by {@link JsonHelper#extractAndParse}
     */
    public List<String> plan(String modDescription) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String userMsg = "Decompose this Minecraft mod concept into its content pieces: " + modDescription;
        String raw = provider.complete(SYSTEM, userMsg);
        JsonObject obj = JsonHelper.extractAndParse(raw, SYSTEM, provider, userMsg);

        List<String> pieces = new ArrayList<>();
        if (obj.has("pieces") && obj.get("pieces").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("pieces");
            // De-duplicate via insertion-ordered set
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (JsonElement el : arr) {
                if (!el.isJsonNull()) {
                    String piece = el.getAsString().trim();
                    if (!piece.isBlank()) {
                        seen.add(piece);
                    }
                }
                if (seen.size() >= MAX_PIECES) break;
            }
            pieces.addAll(seen);
        }

        if (pieces.isEmpty()) {
            // Graceful fallback: treat the whole description as a single piece rather than failing.
            pieces.add(modDescription);
        }

        return pieces;
    }

    public ModPlannerAgent() {}
}
