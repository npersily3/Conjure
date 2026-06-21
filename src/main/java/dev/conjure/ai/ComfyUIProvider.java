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
     * Pixel-art / game-icon suffix appended to an ITEM/ENTITY/FLUID prompt to nudge diffusion
     * toward a crisp, low-resolution Minecraft sprite (centered subject on a transparent BG).
     */
    private static final String ITEM_SUFFIX =
            ", pixel art, game icon, Minecraft item icon, 16-bit sprite, transparent background, "
            + "clean edges, vibrant colors, simple design";

    /**
     * Block suffix: a tileable, opaque <em>surface</em> that fills the frame — the opposite of a
     * centered icon. Diffusion at low native resolution (64 px) cannot render detailed 3-D scenes,
     * so the model is forced toward flat, low-detail color fields which post-processing then
     * downscales to 16×16. The suffix reinforces "surface, not object".
     */
    private static final String BLOCK_SUFFIX =
            ", seamless tileable flat surface texture, top-down orthographic view, fills entire "
            + "frame edge to edge, no object, no background, no perspective, no horizon, no sky, "
            + "no shadow, flat color fields, pixel art, 16-bit, Minecraft block texture, "
            + "abstract pattern, repeating pattern";

    private static final String NEGATIVE_BASE =
            "blurry, anti-aliasing, smooth gradients, photorealistic, photo, 3d render, "
            + "watermark, signature, text, low quality, deformed";

    /** Extra negatives for blocks: kill the "object on a background" / "diorama" failure modes. */
    private static final String BLOCK_NEGATIVE_EXTRA =
            ", centered object, single object, isolated object, background, sky, horizon, "
            + "drop shadow, border, frame, vignette, perspective, depth, diorama, scene, "
            + "landscape, isometric, 3d, realistic, detailed, complex";

    /** Fluid suffix: a seamless top-down liquid surface — opaque, tileable, never a centered icon. */
    private static final String FLUID_SUFFIX =
            ", seamless tileable liquid surface texture, top-down view, water-like ripples, "
            + "fills entire frame edge to edge, no transparency, no object, no background, "
            + "no perspective, flat, pixel art, 16-bit, Minecraft fluid texture";

    /** Extra negatives for fluids: no transparent/centered-icon failure modes. */
    private static final String FLUID_NEGATIVE_EXTRA =
            ", transparent background, centered object, single object, border, frame, perspective, "
            + "3d, diorama, scene";

    /**
     * Native diffusion resolution for BLOCK textures. Much lower than the default 512/768 so the
     * model literally cannot render a detailed 3-D scene — it resolves to flat color regions which
     * the post-processor downscales cleanly to 16×16. 64 px is the minimum ComfyUI's VAE handles
     * without artefacts (SD1.5 VAE minimum is 64, must be a multiple of 8).
     */
    private static final int BLOCK_NATIVE_SIZE = 64;

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
    public byte[] generateTexture(String prompt, int size, TextureKind kind) throws Exception {
        // 1. Queue the workflow at an appropriate native resolution.
        //    For BLOCK we use a deliberately low native size (64 px) so the diffusion model cannot
        //    render a detailed 3-D scene; the result is flat color regions that downscale cleanly.
        String promptId = queuePrompt(prompt, kind);
        // 2. Wait for the render to finish and locate the produced image.
        JsonObject image = awaitImage(promptId);
        // 3. Download the raw PNG bytes.
        return fetchImage(image);
    }

    /**
     * Returns the native diffusion resolution to use for the given kind.
     * BLOCK uses {@value #BLOCK_NATIVE_SIZE} px to force flat, non-detailed output.
     * All other kinds use the configured {@code nativeSize} (512/768).
     */
    private int nativeSizeFor(TextureKind kind) {
        return (kind == TextureKind.BLOCK) ? BLOCK_NATIVE_SIZE : nativeSize;
    }

    /** Positive-prompt suffix for the texture kind. */
    private static String suffixFor(TextureKind kind) {
        return switch (kind) {
            case BLOCK -> BLOCK_SUFFIX;
            case FLUID -> FLUID_SUFFIX;
            default    -> ITEM_SUFFIX;
        };
    }

    /** Negative prompt for the texture kind. */
    private static String negativeFor(TextureKind kind) {
        return switch (kind) {
            case BLOCK -> NEGATIVE_BASE + BLOCK_NEGATIVE_EXTRA;
            case FLUID -> NEGATIVE_BASE + FLUID_NEGATIVE_EXTRA;
            default    -> NEGATIVE_BASE;
        };
    }

    // -------------------------------------------------------------------------
    // Step 1: queue a txt2img workflow
    // -------------------------------------------------------------------------

    private String queuePrompt(String prompt, TextureKind kind) throws Exception {
        JsonObject payload = new JsonObject();
        payload.add("prompt", buildWorkflow(prompt, kind));
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
     *
     * <p>The checkpoint is always loaded via a {@code CheckpointLoaderSimple} node (node "4") wired
     * with the {@code checkpoint} field sourced from {@link dev.conjure.Config#IMAGE_FAST_MODEL} /
     * {@link dev.conjure.Config#IMAGE_HIGH_MODEL} (set by {@link dev.conjure.ai.ProviderFactory}).
     * ComfyUI has no concept of a "currently loaded" model — every workflow must explicitly name its
     * checkpoint.
     *
     * <p>The {@code EmptyLatentImage} node uses {@link #nativeSizeFor(TextureKind)}, which returns
     * {@value #BLOCK_NATIVE_SIZE} px for BLOCK (to force flat output) and the configured
     * {@link #nativeSize} for everything else.
     */
    private JsonObject buildWorkflow(String prompt, TextureKind kind) {
        JsonObject graph = new JsonObject();
        int kindNativeSize = nativeSizeFor(kind);

        // 4: load the configured checkpoint (outputs: 0=MODEL, 1=CLIP, 2=VAE)
        graph.add("4", node("CheckpointLoaderSimple",
                obj("ckpt_name", checkpoint)));

        // 6 / 7: positive + negative conditioning
        graph.add("6", node("CLIPTextEncode",
                inputs(o -> {
                    o.addProperty("text", prompt + suffixFor(kind));
                    o.add("clip", link("4", 1));
                })));
        graph.add("7", node("CLIPTextEncode",
                inputs(o -> {
                    o.addProperty("text", negativeFor(kind));
                    o.add("clip", link("4", 1));
                })));

        // 5: empty latent — use kind-specific native size (BLOCK: 64 px; others: 512/768)
        graph.add("5", node("EmptyLatentImage",
                inputs(o -> {
                    o.addProperty("width", kindNativeSize);
                    o.addProperty("height", kindNativeSize);
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
