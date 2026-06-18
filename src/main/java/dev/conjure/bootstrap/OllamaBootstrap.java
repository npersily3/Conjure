package dev.conjure.bootstrap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.conjure.Config;
import dev.conjure.Conjure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Ensures a local Ollama server is running and the configured text model is pulled. Order:
 * <ol>
 *   <li>If the configured endpoint already answers, skip straight to the model check.</li>
 *   <li>Otherwise find {@code ollama} (our managed copy, then PATH); if absent, download the
 *       standalone Windows zip into {@code <gamedir>/conjure/runtime/ollama} and extract it.</li>
 *   <li>Start {@code ollama serve} (detached) and wait for the port.</li>
 *   <li>Pull {@link Config#LOCAL_TEXT_MODEL} via {@code POST /api/pull} if not already present.</li>
 * </ol>
 */
final class OllamaBootstrap {

    private static final String OLLAMA_ZIP_URL =
            "https://ollama.com/download/ollama-windows-amd64.zip";

    private final Path ollamaDir;

    OllamaBootstrap(Path runtimeDir) {
        this.ollamaDir = runtimeDir.resolve("ollama");
    }

    void ensure() throws IOException, InterruptedException {
        String endpoint = Config.LOCAL_TEXT_ENDPOINT.get().replaceAll("/+$", "");
        String model = Config.LOCAL_TEXT_MODEL.get();

        if (BootstrapUtil.httpReachable(endpoint)) {
            Conjure.LOGGER.info("[bootstrap] Ollama already running at {}.", endpoint);
        } else {
            startOrInstall();
            if (!BootstrapUtil.waitReachable(endpoint, 60)) {
                Conjure.LOGGER.warn("[bootstrap] Ollama did not come up at {} within 60s — "
                        + "skipping model pull.", endpoint);
                return;
            }
            Conjure.LOGGER.info("[bootstrap] Ollama is up at {}.", endpoint);
        }
        ensureModel(endpoint, model);
    }

    private void startOrInstall() throws IOException, InterruptedException {
        Path installed = ollamaDir.resolve("ollama.exe");
        Path exe = Files.exists(installed) ? installed : BootstrapUtil.whereWindows("ollama");

        if (exe == null) {
            Conjure.LOGGER.info("[bootstrap] Ollama not found — installing standalone build...");
            Path zip = ollamaDir.resolve("ollama-windows-amd64.zip");
            BootstrapUtil.download(OLLAMA_ZIP_URL, zip, "Ollama");
            BootstrapUtil.unzip(zip, ollamaDir);
            exe = installed;
            if (!Files.exists(exe)) {
                throw new IOException("Ollama executable missing after extract: " + exe);
            }
        }

        Conjure.LOGGER.info("[bootstrap] starting `ollama serve` ({})...", exe);
        BootstrapUtil.startDetached(exe.getParent(), ollamaDir.resolve("serve.log"),
                exe.toString(), "serve");
    }

    private void ensureModel(String endpoint, String model) throws IOException, InterruptedException {
        if (modelPresent(endpoint, model)) {
            Conjure.LOGGER.info("[bootstrap] model '{}' already pulled.", model);
            return;
        }
        Conjure.LOGGER.info("[bootstrap] pulling model '{}' — large download, this can take a "
                + "while (progress in the Ollama window)...", model);

        JsonObject body = new JsonObject();
        body.addProperty("name", model);
        body.addProperty("stream", false);
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint + "/api/pull"))
                .timeout(Duration.ofHours(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> r = BootstrapUtil.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) {
            throw new IOException("Ollama pull failed: HTTP " + r.statusCode() + " — " + r.body());
        }
        Conjure.LOGGER.info("[bootstrap] model '{}' ready.", model);
    }

    private boolean modelPresent(String endpoint, String model) {
        try {
            HttpResponse<String> r = BootstrapUtil.HTTP.send(
                    HttpRequest.newBuilder(URI.create(endpoint + "/api/tags")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) return false;
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            if (!o.has("models")) return false;
            String bare = model.endsWith(":latest") ? model.substring(0, model.length() - 7) : model;
            for (JsonElement el : o.getAsJsonArray("models")) {
                String name = el.getAsJsonObject().get("name").getAsString();
                if (name.equals(model) || name.equals(bare) || name.equals(bare + ":latest")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // treat as "not present"; a redundant pull is harmless
        }
        return false;
    }
}
