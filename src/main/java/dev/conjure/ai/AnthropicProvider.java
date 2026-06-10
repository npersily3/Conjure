package dev.conjure.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cloud text provider for the Anthropic Messages API. The API key is read from an environment
 * variable (never stored in config) named by {@code keyEnv}, e.g. {@code ANTHROPIC_API_KEY}.
 */
public final class AnthropicProvider implements TextModelProvider {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    private final String model;
    private final String keyEnv;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public AnthropicProvider(String model, String keyEnv) {
        this.model = model;
        this.keyEnv = keyEnv;
    }

    @Override
    public String complete(String system, String user) throws Exception {
        String key = System.getenv(keyEnv);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Anthropic mode selected but env var " + keyEnv + " is unset.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 8192);
        if (system != null && !system.isBlank()) {
            body.addProperty("system", system);
        }
        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", user);
        messages.add(userMsg);
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .timeout(Duration.ofMinutes(5))
                .header("content-type", "application/json")
                .header("x-api-key", key)
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("Anthropic " + response.statusCode() + ": " + response.body());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray content = json.getAsJsonArray("content");
        return content.get(0).getAsJsonObject().get("text").getAsString();
    }

    @Override
    public String id() {
        return "anthropic:" + model;
    }
}
