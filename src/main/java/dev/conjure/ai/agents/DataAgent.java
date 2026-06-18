package dev.conjure.ai.agents;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sub-agent responsible for generating the display name and a short description for a
 * Conjure slot given a natural-language prompt. For blocks it also classifies whether the thing
 * is a building <em>material</em> (and which shaped variants make sense), which the block pipeline
 * uses to expand a material into a family — non-block pipelines simply ignore those fields.
 *
 * <p>Returned fields are packed into a simple JSON object so they can be
 * validated and optionally repaired like every other agent response.
 */
public final class DataAgent {

    /** Variant names the material classifier may return (and the block pipeline knows how to build). */
    public static final Set<String> KNOWN_VARIANTS = Set.of("smooth", "bricks", "slab", "stairs", "wall");

    private static final String SYSTEM = """
            You name and briefly describe a Minecraft thing given a short prompt, and classify whether
            it is a building material block. Reply with ONLY a JSON object, no prose, no markdown
            fences. Schema:
            {
              "displayName": "<short title-cased name, max 4 words>",
              "description": "<one sentence flavour text, max 20 words>",
              "isMaterial": <true only if this is a solid building/decorative BLOCK material like
                             stone, marble, brick, wood, metal block; false for items, tools, ores,
                             machines, plants, glass, magical orbs, etc.>,
              "variants": [<for a material, the subset of "smooth","bricks","slab","stairs","wall"
                           that make sense; otherwise []>]
            }
            Keep names immersive and appropriate for a fantasy game.
            """;

    /**
     * Result record returned to the orchestrator.
     *
     * @param displayName player-facing name
     * @param description short flavour description stored in {@code strings.get("description")}
     * @param isMaterial  true if this is a building-material block eligible for family expansion
     * @param variants    requested shaped variants (subset of {@link #KNOWN_VARIANTS}); empty if none
     */
    public record Result(String displayName, String description, boolean isMaterial, Set<String> variants) {}

    /**
     * Calls the text model to produce a name and description for {@code prompt}.
     *
     * @param prompt the original user-supplied item description
     * @return a {@link Result} with display name and description
     * @throws Exception if the model call or JSON parse fails
     */
    public Result generate(String prompt) throws Exception {
        TextModelProvider provider = ProviderFactory.text();
        String userMsg = "Name and describe this Minecraft item: " + prompt;
        String raw = provider.complete(SYSTEM, userMsg);
        JsonObject obj = JsonHelper.extractAndParse(raw, SYSTEM, provider, userMsg);

        String name = obj.has("displayName") ? obj.get("displayName").getAsString() : "Conjured Item";
        String desc = obj.has("description") ? obj.get("description").getAsString() : "";

        boolean isMaterial = obj.has("isMaterial") && obj.get("isMaterial").getAsBoolean();
        Set<String> variants = new LinkedHashSet<>();
        if (isMaterial && obj.has("variants") && obj.get("variants").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("variants");
            for (int i = 0; i < arr.size(); i++) {
                String v = arr.get(i).getAsString().trim().toLowerCase(java.util.Locale.ROOT);
                if (KNOWN_VARIANTS.contains(v)) variants.add(v);
            }
        }
        return new Result(name, desc, isMaterial, variants);
    }

    public DataAgent() {}
}
