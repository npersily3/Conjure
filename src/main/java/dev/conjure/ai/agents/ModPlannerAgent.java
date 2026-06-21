package dev.conjure.ai.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;
import dev.conjure.content.SlotKind;

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

    // -------------------------------------------------------------------------
    // Whole-mod economy planning (used by /conjure mod) — a playable progression
    // -------------------------------------------------------------------------

    /** A piece's place in the gameplay loop. */
    public enum Role { RESOURCE, MATERIAL, PRODUCT }

    /**
     * One planned piece in a mod economy.
     *
     * @param name   unique logical id other pieces reference in {@code from} (may be null in fallback)
     * @param prompt the concrete single-piece generation prompt
     * @param kind   suggested content kind, or null to let the router decide
     * @param role   where it sits in the loop (resource → material → product)
     * @param from   logical names of earlier pieces this one is crafted/smelted from
     */
    public record Piece(String name, String prompt, SlotKind kind, Role role, List<String> from) {}

    /** An ordered mod plan: a natural-resource-first progression of pieces. */
    public record ModPlan(List<Piece> pieces) {
        /** Pieces sorted resource → material → product (stable), so each references only earlier ones. */
        public List<Piece> ordered() {
            List<Piece> out = new ArrayList<>();
            for (Role r : Role.values()) {
                for (Piece p : pieces) if (p.role() == r) out.add(p);
            }
            return out;
        }
    }

    private static final String SYSTEM_MOD = """
            You are the planning agent for Conjure. Design a COMPLETE, PLAYABLE Minecraft progression
            for the mod concept — think about the gameplay loop, not just a bag of items:
              1. NATURAL RESOURCE(S): where the mod's materials come from in the WORLD — an ore/rock
                 block (role "resource", kind "block"), or a mob that drops them (kind "entity").
              2. RAW MATERIALS: the items you get from a resource (role "material"), each listing its
                 resource in "from" (a material from an ore is SMELTED from it).
              3. PRODUCTS: the goal items/blocks the user actually wants (role "product"), each listing
                 the materials/products it is crafted "from".
            Order so every piece only references earlier pieces. Resources have an empty "from".
            Reply with ONLY a JSON object, no prose, no fences:
            {
              "pieces": [
                { "name": "<unique_snake_id>", "prompt": "<3-8 word concrete prompt>",
                  "kind": "item|block|entity", "role": "resource|material|product",
                  "from": ["<earlier piece name>", ...] }
              ]
            }
            Rules: 5-20 pieces. At least one resource. Every material and product lists a non-empty
            "from". Names are short, unique snake_case. Never repeat a concept.
            """;

    /**
     * Plans a whole-mod economy: a resource→material→product progression with crafting dependencies.
     * Falls back to wrapping the flat {@link #plan(String, boolean)} pieces as standalone products if
     * the structured response can't be parsed, so a mod build never hard-fails.
     */
    public ModPlan planMod(String description) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String userMsg = "Design a playable Minecraft mod progression for: " + description;
        List<Piece> pieces = new ArrayList<>();
        try {
            String raw = provider.complete(SYSTEM_MOD, userMsg);
            JsonObject obj = JsonHelper.extractAndParse(raw, SYSTEM_MOD, provider, userMsg);
            if (obj.has("pieces") && obj.get("pieces").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("pieces")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject p = el.getAsJsonObject();
                    String prompt = str(p, "prompt");
                    if (prompt.isBlank()) continue;
                    pieces.add(new Piece(str(p, "name"), prompt, parseKind(str(p, "kind")),
                            parseRole(str(p, "role")), parseFrom(p)));
                    if (pieces.size() >= MAX_PIECES) break;
                }
            }
        } catch (Exception e) {
            // fall through to the flat fallback below
        }
        if (pieces.isEmpty()) {
            for (String prompt : plan(description, true)) {
                pieces.add(new Piece(null, prompt, null, Role.PRODUCT, List.of()));
            }
        }
        return new ModPlan(pieces);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString().trim() : "";
    }

    private static SlotKind parseKind(String s) {
        return switch (s.toLowerCase(java.util.Locale.ROOT)) {
            case "block" -> SlotKind.BLOCK;
            case "entity", "mob" -> SlotKind.ENTITY;
            case "item" -> SlotKind.ITEM;
            default -> null; // let the router decide
        };
    }

    private static Role parseRole(String s) {
        try {
            return Role.valueOf(s.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return Role.PRODUCT;
        }
    }

    private static List<String> parseFrom(JsonObject p) {
        List<String> from = new ArrayList<>();
        if (p.has("from") && p.get("from").isJsonArray()) {
            for (JsonElement e : p.getAsJsonArray("from")) {
                if (!e.isJsonNull()) {
                    String s = e.getAsString().trim();
                    if (!s.isBlank()) from.add(s);
                }
            }
        }
        return from;
    }

    public ModPlannerAgent() {}
}
