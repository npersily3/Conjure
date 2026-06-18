package dev.conjure.bootstrap;

import dev.conjure.Config;
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

    /** Only this checkpoint filename has a known download URL; others must be placed manually. */
    private static final String KNOWN_CHECKPOINT = "v1-5-pruned-emaonly.safetensors";
    private static final String KNOWN_CHECKPOINT_URL =
            "https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/"
                    + KNOWN_CHECKPOINT;

    private final Path base;

    ComfyUiBootstrap(Path runtimeDir) {
        this.base = runtimeDir.resolve("ComfyUI");
    }

    void ensure() throws IOException, InterruptedException {
        if (Files.isDirectory(base) && findRunScript(base).isPresent()) {
            Conjure.LOGGER.info("[bootstrap] ComfyUI already present at {}.", base);
            ensureCheckpoint();
            return;
        }

        Path archive = base.resolve("ComfyUI_windows_portable_nvidia.7z");
        BootstrapUtil.download(COMFY_7Z_URL, archive, "ComfyUI portable");
        Conjure.LOGGER.info("[bootstrap] extracting ComfyUI (via Windows tar)...");
        BootstrapUtil.extract7z(archive, base);

        ensureCheckpoint();

        Optional<Path> run = findRunScript(base);
        Conjure.LOGGER.info("[bootstrap] ComfyUI installed under {}.", base);
        run.ifPresent(p -> Conjure.LOGGER.info(
                "[bootstrap] start it when you want image-generated textures: {}", p));
    }

    private void ensureCheckpoint() throws IOException, InterruptedException {
        Optional<Path> checkpoints = findCheckpointsDir(base);
        if (checkpoints.isEmpty()) {
            Conjure.LOGGER.warn("[bootstrap] could not locate ComfyUI models/checkpoints folder — "
                    + "place a checkpoint there manually.");
            return;
        }
        String wanted = Config.IMAGE_FAST_MODEL.get();
        Path target = checkpoints.get().resolve(wanted);
        if (Files.exists(target)) {
            Conjure.LOGGER.info("[bootstrap] checkpoint '{}' already present.", wanted);
            return;
        }
        if (!KNOWN_CHECKPOINT.equals(wanted)) {
            Conjure.LOGGER.warn("[bootstrap] no known download URL for checkpoint '{}'; "
                    + "drop it into {} manually.", wanted, checkpoints.get());
            return;
        }
        BootstrapUtil.download(KNOWN_CHECKPOINT_URL, target, "SD 1.5 checkpoint");
    }

    /** Finds the deepest {@code .../models/checkpoints} directory inside the portable tree. */
    private static Optional<Path> findCheckpointsDir(Path root) throws IOException {
        if (!Files.isDirectory(root)) return Optional.empty();
        try (Stream<Path> s = Files.walk(root, 6)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null
                            && p.getFileName().toString().equals("checkpoints")
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
