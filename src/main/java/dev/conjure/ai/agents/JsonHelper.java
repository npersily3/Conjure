package dev.conjure.ai.agents;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.conjure.ai.TextModelProvider;

/**
 * Utility for extracting and validating JSON from free-form model output.
 *
 * <p>Models often wrap their JSON answer in markdown fences ({@code ```json ... ```}) or
 * prepend prose explanations. This helper finds the outermost {@code {...}} in the raw
 * response, parses it, and — on failure — performs exactly ONE repair retry by sending
 * the parse error back to the model with instructions to fix the JSON.
 */
public final class JsonHelper {

    private JsonHelper() {}

    /**
     * Extracts the outermost {@code {...}} block from {@code raw} and parses it.
     * If that fails, calls {@code provider.complete(system, repairPrompt)} once and
     * re-attempts. Throws {@link IllegalArgumentException} if both attempts fail.
     *
     * @param raw      the model's raw output
     * @param system   the system prompt used for the original call (reused for the repair)
     * @param provider the provider to use for the repair call
     * @param originalUser the original user prompt (context for the repair)
     * @return a parsed {@link JsonObject}
     */
    public static JsonObject extractAndParse(String raw, String system,
                                             TextModelProvider provider,
                                             String originalUser) throws Exception {
        try {
            return extractJson(raw);
        } catch (Exception firstError) {
            // One repair attempt: tell the model exactly what was wrong.
            String repairPrompt = "Your previous response could not be parsed as JSON.\n"
                    + "Parse error: " + firstError.getMessage() + "\n"
                    + "Your previous response was:\n" + raw + "\n\n"
                    + "Original task: " + originalUser + "\n\n"
                    + "Respond with ONLY the corrected JSON object — no prose, no markdown fences.";
            String repaired = provider.complete(system, repairPrompt);
            return extractJson(repaired);
        }
    }

    /**
     * Strips markdown fences and prose, returning the parsed outermost {@code {...}} block.
     * Handles both {@code ```json ... ```} fences and bare JSON with surrounding text.
     */
    public static JsonObject extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Model returned an empty response");
        }

        // Find outermost { ... } by scanning for first '{' and matching last '}'
        int start = raw.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("No JSON object found in model response");
        }

        // Walk forward to find the matching closing brace
        int depth = 0;
        int end = -1;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }

        if (end < 0) {
            throw new IllegalArgumentException("Unmatched '{' in model response — JSON not terminated");
        }

        String jsonStr = raw.substring(start, end + 1);
        try {
            return JsonParser.parseString(jsonStr).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON parse failed: " + e.getMessage(), e);
        }
    }
}
