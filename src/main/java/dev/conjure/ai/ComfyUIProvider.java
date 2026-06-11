package dev.conjure.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Texture provider that speaks the <a href="https://github.com/comfyanonymous/ComfyUI">ComfyUI</a>
 * HTTP API. ComfyUI is preferred over A1111 for distribution: it ships portable/headless builds
 * and is driven entirely through a scriptable workflow-graph API (no web UI launch flags).
 *
 * <p>Unlike A1111's single one-shot {@code /sdapi/v1/txt2img} call, ComfyUI is graph-based and
 * asynchronous, so a generation is three steps:
 * <ol>
 *   <li>{@code POST /prompt} with a full txt2img workflow graph → returns a {@code prompt_id};</li>
 *   <li>poll {@code GET /history/{prompt_id}} until the node outputs appear;</li>
 *   <li>{@code GET /view?filename=…} to fetch the rendered PNG bytes.</li>
 * </ol>
 *
 * <p>The returned PNG is at the diffusion <em>native</em> resolution ({@code nativeSize}); the
 * caller ({@link dev.conjure.ai.agents.TextureAgent}) downscales it to the small texture target
 * via {@link dev.conjure.gen.PixelTexture#fromPng}. We deliberately diffuse at a real SD resolution
 * (512/768) rather than the tiny final texture size, because diffusion models produce garbage at
 * 16–64 px.
 */
public final class ComfyUIProvider implements ImageModelProvider {

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

    /** How long to keep polling {@code /history} before giving up. */
    private static final Duration POLL_TIMEOUT = Duration.ofMinutes(10);
    private static final long POLL_INTERVAL_MS = 750;

    private final String endpoint;
    private final String checkpoint;
    private final int steps;
    private final int nativeSize;
    private final String clientId = UUID.randomUUID().toString();
    private final HttpClient http;

    /**
     * @param endpoint   base URL of the ComfyUI server, e.g. {@code http://127.0.0.1:8188}
     * @param checkpoint checkpoint filename for {@code CheckpointLoaderSimple}, e.g.
     *                   {@code v1-5-pruned-emaonly.safetensors}; must exist under
     *                   {@code ComfyUI/models/checkpoints} (ComfyUI has no "currently loaded"
     *                   concept like A1111)
     * @param steps      number of diffusion steps (e.g. 8 for fast, 25 for high quality)
     * @param nativeSize diffusion resolution in pixels (e.g. 512 for SD1.5 fast, 768 for high);
     *                   the final texture is downscaled from this by the caller
     */
    public ComfyUIProvider(String endpoint, String checkpoint, int steps, int nativeSize) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.checkpoint = checkpoint;
        this.steps = steps;
        this.nativeSize = nativeSize;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public byte[] generateTexture(String prompt, int size) throws Exception {
        // 1. Queue the workflow.
        String promptId = queuePrompt(prompt);
        // 2. Wait for the render to finish and locate the produced image.
        JsonObject image = awaitImage(promptId);
        // 3. Download the raw PNG bytes.
        return fetchImage(image);
    }

    // -------------------------------------------------------------------------
    // Step 1: queue a txt2img workflow
    // -------------------------------------------------------------------------

    private String queuePrompt(String prompt) throws Exception {
        JsonObject payload = new JsonObject();
        payload.add("prompt", buildWorkflow(prompt));
        payload.addProperty("client_id", clientId);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "/prompt"))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new RuntimeException(
                    "Cannot reach ComfyUI at " + endpoint + " — is the ComfyUI server running?", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("ComfyUI HTTP " + response.statusCode() + " on /prompt: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("prompt_id")) {
            throw new RuntimeException("ComfyUI /prompt response had no prompt_id: " + response.body());
        }
        return json.get("prompt_id").getAsString();
    }

    /**
     * Builds a standard txt2img workflow graph in ComfyUI's API JSON format. Node ids are arbitrary
     * strings; each node references another node's output as {@code ["<id>", <slotIndex>]}.
     */
    private JsonObject buildWorkflow(String prompt) {
        JsonObject graph = new JsonObject();

        // 4: load the checkpoint (outputs: 0=MODEL, 1=CLIP, 2=VAE)
        graph.add("4", node("CheckpointLoaderSimple",
                obj("ckpt_name", checkpoint)));

        // 6 / 7: positive + negative conditioning
        graph.add("6", node("CLIPTextEncode",
                inputs(o -> {
                    o.addProperty("text", prompt + PROMPT_SUFFIX);
                    o.add("clip", link("4", 1));
                })));
        graph.add("7", node("CLIPTextEncode",
                inputs(o -> {
                    o.addProperty("text", NEGATIVE_PROMPT);
                    o.add("clip", link("4", 1));
                })));

        // 5: empty latent at the native diffusion resolution
        graph.add("5", node("EmptyLatentImage",
                inputs(o -> {
                    o.addProperty("width", nativeSize);
                    o.addProperty("height", nativeSize);
                    o.addProperty("batch_size", 1);
                })));

        // 3: sampler
        graph.add("3", node("KSampler",
                inputs(o -> {
                    o.addProperty("seed", ThreadLocalRandom.current().nextLong(0, Long.MAX_VALUE));
                    o.addProperty("steps", steps);
                    o.addProperty("cfg", 7);
                    o.addProperty("sampler_name", "euler");
                    o.addProperty("scheduler", "normal");
                    o.addProperty("denoise", 1.0);
                    o.add("model", link("4", 0));
                    o.add("positive", link("6", 0));
                    o.add("negative", link("7", 0));
                    o.add("latent_image", link("5", 0));
                })));

        // 8: decode latent → image
        graph.add("8", node("VAEDecode",
                inputs(o -> {
                    o.add("samples", link("3", 0));
                    o.add("vae", link("4", 2));
                })));

        // 9: save (ComfyUI writes to its output folder and reports the filename in /history)
        graph.add("9", node("SaveImage",
                inputs(o -> {
                    o.addProperty("filename_prefix", "conjure");
                    o.add("images", link("8", 0));
                })));

        return graph;
    }

    // -------------------------------------------------------------------------
    // Step 2: poll /history for the finished render
    // -------------------------------------------------------------------------

    /** Polls {@code /history/{promptId}} until an image output appears, then returns its descriptor. */
    private JsonObject awaitImage(String promptId) throws Exception {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "/history/" + promptId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 == 2) {
                JsonObject history = JsonParser.parseString(response.body()).getAsJsonObject();
                if (history.has(promptId)) {
                    JsonObject entry = history.getAsJsonObject(promptId);
                    JsonObject image = firstImage(entry);
                    if (image != null) {
                        return image;
                    }
                }
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        throw new RuntimeException(
                "ComfyUI did not produce an image for prompt " + promptId + " within "
                + POLL_TIMEOUT.toMinutes() + " minutes");
    }

    /** Finds the first {@code images[]} entry across all node outputs in a /history entry. */
    private static JsonObject firstImage(JsonObject historyEntry) {
        if (!historyEntry.has("outputs") || !historyEntry.get("outputs").isJsonObject()) {
            return null;
        }
        JsonObject outputs = historyEntry.getAsJsonObject("outputs");
        for (String nodeId : outputs.keySet()) {
            JsonObject nodeOut = outputs.getAsJsonObject(nodeId);
            if (nodeOut.has("images") && nodeOut.get("images").isJsonArray()) {
                JsonArray images = nodeOut.getAsJsonArray("images");
                if (!images.isEmpty()) {
                    return images.get(0).getAsJsonObject();
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Step 3: download the PNG via /view
    // -------------------------------------------------------------------------

    private byte[] fetchImage(JsonObject image) throws Exception {
        String filename = image.get("filename").getAsString();
        String subfolder = image.has("subfolder") ? image.get("subfolder").getAsString() : "";
        String type = image.has("type") ? image.get("type").getAsString() : "output";

        String query = "filename=" + enc(filename)
                + "&subfolder=" + enc(subfolder)
                + "&type=" + enc(type);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "/view?" + query))
                .timeout(Duration.ofMinutes(1))
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("ComfyUI HTTP " + response.statusCode() + " fetching " + filename);
        }
        return response.body();
    }

    @Override
    public String id() {
        return "comfyui:" + endpoint;
    }

    // -------------------------------------------------------------------------
    // Small JSON builder helpers
    // -------------------------------------------------------------------------

    /** A {@code class_type} + {@code inputs} node. */
    private static JsonObject node(String classType, JsonObject inputs) {
        JsonObject n = new JsonObject();
        n.addProperty("class_type", classType);
        n.add("inputs", inputs);
        return n;
    }

    private static JsonObject obj(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o;
    }

    private interface InputBuilder {
        void build(JsonObject o);
    }

    private static JsonObject inputs(InputBuilder builder) {
        JsonObject o = new JsonObject();
        builder.build(o);
        return o;
    }

    /** A reference to another node's output: {@code ["<nodeId>", <slot>]}. */
    private static JsonArray link(String nodeId, int slot) {
        JsonArray arr = new JsonArray();
        arr.add(nodeId);
        arr.add(slot);
        return arr;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
