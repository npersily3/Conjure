package dev.conjure.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Texture provider that speaks the AUTOMATIC1111 / Forge SD web UI REST API
 * ({@code POST /sdapi/v1/txt2img}). Requires the web UI to be launched with {@code --api}.
 *
 * <p>The response contains a base64-encoded PNG image in {@code images[0]}. This class
 * decodes that into raw bytes and returns them as a PNG. The caller (TextureAgent) is
 * responsible for scaling to the target size if necessary.
 */
public final class A1111Provider implements ImageModelProvider {

    /**
     * Pixel-art / game-icon suffix appended to the user prompt to nudge diffusion toward
     * crisp, low-resolution Minecraft-style output.
     */
    private static final String PROMPT_SUFFIX =
            ", pixel art, game icon, Minecraft item icon, 16-bit sprite, transparent background, "
            + "clean edges, vibrant colors, simple design";

    private static final String NEGATIVE_PROMPT =
            "blurry, anti-aliasing, smooth gradients, photorealistic, photo, 3d render, "
            + "watermark, signature, text, low quality, deformed";

    private final String endpoint;
    private final String modelName;
    private final int steps;
    private final HttpClient http;

    /**
     * @param endpoint  base URL of the A1111 web UI, e.g. {@code http://127.0.0.1:7860}
     * @param modelName checkpoint name to override ({@code sd_model_checkpoint}); blank = leave
     *                  whatever model A1111 currently has loaded
     * @param steps     number of diffusion steps (typically 8 for turbo, 25 for full)
     */
    public A1111Provider(String endpoint, String modelName, int steps) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.modelName = modelName;
        this.steps = steps;
        // Long timeout: diffusion can take tens of seconds even on fast hardware
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public byte[] generateTexture(String prompt, int size) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("prompt", prompt + PROMPT_SUFFIX);
        body.addProperty("negative_prompt", NEGATIVE_PROMPT);
        body.addProperty("steps", steps);
        body.addProperty("width", size);
        body.addProperty("height", size);
        body.addProperty("sampler_name", "DPM++ 2M Karras");
        body.addProperty("cfg_scale", 7);
        body.addProperty("batch_size", 1);

        // Optionally override the loaded checkpoint
        if (modelName != null && !modelName.isBlank()) {
            JsonObject overrideSettings = new JsonObject();
            overrideSettings.addProperty("sd_model_checkpoint", modelName);
            body.add("override_settings", overrideSettings);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "/sdapi/v1/txt2img"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new RuntimeException(
                    "Cannot reach A1111 at " + endpoint + " — is the web UI running with --api?", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("A1111 HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray images = json.getAsJsonArray("images");
        if (images == null || images.isEmpty()) {
            throw new RuntimeException("A1111 response contained no images: " + response.body());
        }

        // images[0] is a base64-encoded PNG, sometimes with a data URI prefix
        String b64 = images.get(0).getAsString();
        if (b64.contains(",")) {
            // strip "data:image/png;base64," prefix if present
            b64 = b64.substring(b64.indexOf(',') + 1);
        }
        return Base64.getDecoder().decode(b64.strip());
    }

    @Override
    public String id() {
        return "a1111:" + endpoint;
    }
}
