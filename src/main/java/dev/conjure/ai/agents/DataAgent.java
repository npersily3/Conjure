package dev.conjure.ai.agents;

import com.google.gson.JsonObject;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;

/**
 * Sub-agent responsible for generating the display name and a short description for a
 * Conjure item slot given a natural-language prompt.
 *
 * <p>Returned fields are packed into a simple two-key JSON object so they can be
 * validated and optionally repaired like every other agent response.
 */
public final class DataAgent {

    private static final String SYSTEM = """
            You name and briefly describe a Minecraft item given a short prompt. Reply with ONLY
            a JSON object, no prose, no markdown fences. Schema:
            {
              "displayName": "<short title-cased item name, max 4 words>",
              "description": "<one sentence flavour text, max 20 words>"
            }
            Keep it immersive and appropriate for a fantasy game item.
            """;

    /**
     * Result record returned to the orchestrator.
     *
     * @param displayName player-facing item name
     * @param description short flavour description stored in {@code strings.get("description")}
     */
    public record Result(String displayName, String description) {}

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
        return new Result(name, desc);
    }

    public DataAgent() {}
}
