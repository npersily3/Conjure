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
    static final int MAX_PIECES = 20;

    /**
     * EXPANSIVE mode (used by {@code /conjure mod}): always decompose a whole-mod concept into many
     * pieces, erring toward more rather than fewer.
     */
    private static final String SYSTEM_EXPANSIVE = """
            You are a planner for a Minecraft content generator called Conjure.
            Given a high-level mod concept, decompose it into the individual content pieces
            that the mod needs — items, blocks, fluids, entities (mobs), and/or structures.
            Each piece must be a short, concrete, self-contained prompt for exactly ONE
            Minecraft content piece (e.g. "a beehive block", "honeycomb item", "a bee mob",
            "honey fluid", "an apiary workstation block").
            Rules:
            - Reply with ONLY a JSON object — no prose, no markdown fences.
            - Produce between 5 and 20 pieces. Err on the side of MORE pieces than strictly
              necessary — a generous, content-rich mod is better than a sparse one.
            - Keep each piece prompt concise (3–8 words) and unambiguous.
            - Cover a mix of content types when the concept warrants it.
            - When the mod includes a workstation/machine block (a station, kiln, press, mill,
              altar, etc.), center the mod on it: include the raw-material pieces it consumes and
              the signature item it produces, so that machine is the intended way to craft them.
            - Never repeat the same concept twice.
            Schema:
            {
              "pieces": ["<prompt>", "<prompt>", ...]
            }
            """;

    /**
     * AUTO mode (used by {@code /conjure new}): decide whether the request is a single concrete
     * piece or a theme/set, and expand generously only when it is plural/thematic.
     */
    private static final String SYSTEM_AUTO = """
            You are a planner for a Minecraft content generator called Conjure.
            Given a content request, decide whether it describes ONE concrete piece or a
            THEME / SET / collection, then output the pieces to generate.
            Each piece must be a short, concrete, self-contained prompt for exactly ONE
            Minecraft content piece — an item, block, fluid, entity (mob), or structure.
            Rules:
            - Reply with ONLY a JSON object — no prose, no markdown fences.
            - If the request is clearly ONE concrete thing (e.g. "a glowing ember dagger"),
              return EXACTLY ONE piece: the request itself.
            - If the request implies multiple pieces — a theme, a set, a "mod", or plural nouns
              like "pagoda blocks" or "dwarven weapons" — decompose it into many pieces and
              err on the side of MORE pieces than strictly necessary (up to 20).
            - Keep each piece prompt concise (3–8 words) and unambiguous.
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
        return plan(modDescription, true);
    }

    /**
     * Asks the text model to decompose {@code description} into concrete content prompts.
     *
     * @param description the content request
     * @param expansive   {@code true} for whole-mod planning (always many pieces, used by
     *                    {@code /conjure mod}); {@code false} for smart auto mode (single concrete
     *                    prompts stay one piece, themed/plural prompts expand — used by
     *                    {@code /conjure new})
     * @return a non-null, non-empty list of single-piece generation prompts, capped at
     *         {@value #MAX_PIECES} entries and de-duplicated
     * @throws Exception if the model call or JSON parse fails on both attempts
     */
    public List<String> plan(String description, boolean expansive) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String system = expansive ? SYSTEM_EXPANSIVE : SYSTEM_AUTO;
        String userMsg = "Decompose this Minecraft content request into its content pieces: " + description;
        String raw = provider.complete(system, userMsg);
        JsonObject obj = JsonHelper.extractAndParse(raw, system, provider, userMsg);

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
            pieces.add(description);
        }

        return pieces;
    }

    public ModPlannerAgent() {}
}
