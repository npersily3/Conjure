package dev.conjure.bootstrap;

import dev.conjure.Conjure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Downloads and unpacks the ComfyUI Windows portable build (bundled Python, no install needed) and
 * fetches a default Stable Diffusion checkpoint into its {@code models/checkpoints} folder. It does
 * NOT auto-start the server — ComfyUI wants a console + GPU, so the user launches it via the
 * {@code run_nvidia_gpu.bat} that ships inside the portable folder. Until then the mod falls back to
 * the built-in LLM pixel-art texture path.
 *
 * <p>NOTE: this always downloads ComfyUI on first launch by request. That is a deliberately heavy,
 * single-developer default; before any public distribution it should become opt-in (and the .7z
 * extraction should not rely on Windows' bundled {@code tar}). See {@code docs/SETUP.md}.
 */
final class ComfyUiBootstrap {

    private static final String COMFY_7Z_URL =
            "https://github.com/comfyanonymous/ComfyUI/releases/latest/download/"
                    + "ComfyUI_windows_portable_nvidia.7z";

    /**
     * Models auto-downloaded on first launch. FAST = a light SD1.5 pixel-art checkpoint; HIGH = SDXL
     * base + the Pixel Art XL LoRA (best an ~8 GB GPU can run). {@code lora}=true → models/loras,
     * else models/checkpoints. A checkpoint the user renamed in config (not listed here) is left to
     * them to place manually.
     */
    private record Model(String filename, String url, boolean lora) {}

    private static final Model[] MODELS = {
            new Model("PixelartSpritesheet_V.1.ckpt",
                    "https://huggingface.co/Onodofthenorth/SD_PixelArt_SpriteSheet_Generator/resolve/main/PixelartSpritesheet_V.1.ckpt",
                    false),
            new Model("sd_xl_base_1.0.safetensors",
                    "https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/resolve/main/sd_xl_base_1.0.safetensors",
                    false),
            new Model("pixel-art-xl.safetensors",
                    "https://huggingface.co/nerijs/pixel-art-xl/resolve/main/pixel-art-xl.safetensors",
                    true),
    };

    private final Path base;

    ComfyUiBootstrap(Path runtimeDir) {
        this.base = runtimeDir.resolve("ComfyUI");
    }

    void ensure() throws IOException, InterruptedException {
        if (Files.isDirectory(base) && findRunScript(base).isPresent()) {
            Conjure.LOGGER.info("[bootstrap] ComfyUI already present at {}.", base);
            ensureModels();
            return;
        }

        Path archive = base.resolve("ComfyUI_windows_portable_nvidia.7z");
        BootstrapUtil.download(COMFY_7Z_URL, archive, "ComfyUI portable");
        Conjure.LOGGER.info("[bootstrap] extracting ComfyUI (via Windows tar)...");
        BootstrapUtil.extract7z(archive, base);

        ensureModels();

        Optional<Path> run = findRunScript(base);
        Conjure.LOGGER.info("[bootstrap] ComfyUI installed under {}.", base);
        run.ifPresent(p -> Conjure.LOGGER.info(
                "[bootstrap] start it when you want image-generated textures: {}", p));
    }

    /** Downloads the FAST + HIGH models (checkpoints + LoRA) into ComfyUI, idempotently. */
    private void ensureModels() throws IOException, InterruptedException {
        Optional<Path> checkpoints = findModelDir(base, "checkpoints");
        Optional<Path> loras = findModelDir(base, "loras");
        if (checkpoints.isEmpty()) {
            Conjure.LOGGER.warn("[bootstrap] could not locate ComfyUI models/checkpoints folder — "
                    + "place models manually.");
            return;
        }
        for (Model m : MODELS) {
            Optional<Path> dir = m.lora() ? loras : checkpoints;
            if (dir.isEmpty()) {
                Conjure.LOGGER.warn("[bootstrap] no models/{} folder for '{}' — place it manually.",
                        m.lora() ? "loras" : "checkpoints", m.filename());
                continue;
            }
            Path target = dir.get().resolve(m.filename());
            // download() is idempotent (skips if present and non-empty).
            BootstrapUtil.download(m.url(), target, m.filename());
        }
    }

    /** Finds the deepest {@code .../models/<name>} directory inside the portable tree. */
    private static Optional<Path> findModelDir(Path root, String name) throws IOException {
        if (!Files.isDirectory(root)) return Optional.empty();
        try (Stream<Path> s = Files.walk(root, 6)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().equals(name)
                            && p.getParent() != null
                            && "models".equals(p.getParent().getFileName().toString()))
                    .findFirst();
        }
    }

    private static Optional<Path> findRunScript(Path root) throws IOException {
        if (!Files.isDirectory(root)) return Optional.empty();
        try (Stream<Path> s = Files.walk(root, 4)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.equals("run_nvidia_gpu.bat") || n.equals("run_cpu.bat");
                    })
                    .findFirst();
        }
    }
}
