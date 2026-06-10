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
 * Local text provider speaking the Ollama chat API ({@code POST /api/chat}). Ollama is already
 * installed on this machine, so this is the default "local mode" backend. LM Studio and
 * llama.cpp's server expose an OpenAI-compatible API instead; a sibling provider can target
 * {@code /v1/chat/completions} with the same shape.
 */
public final class OllamaProvider implements TextModelProvider {

    private final String endpoint;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public OllamaProvider(String endpoint, String model) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.model = model;
    }

    @Override
    public String complete(String system, String user) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();
        if (system != null && !system.isBlank()) {
            messages.add(message("system", system));
        }
        messages.add(message("user", user));
        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "/api/chat"))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("Ollama " + response.statusCode() + ": " + response.body());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.getAsJsonObject("message").get("content").getAsString();
    }

    @Override
    public String id() {
        return "ollama:" + model;
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
