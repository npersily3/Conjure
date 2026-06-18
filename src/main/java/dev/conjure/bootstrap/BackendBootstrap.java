package dev.conjure.bootstrap;

import dev.conjure.Config;
import dev.conjure.Conjure;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Entry point for first-launch AI-backend setup. Called once during {@code FMLCommonSetupEvent}
 * (config is loaded by then) and does all real work on the {@code conjure-bootstrap} daemon thread
 * so it never blocks the game: the world loads normally and generation simply isn't ready until the
 * downloads finish. Every step is idempotent — once a backend is present, re-runs are no-ops.
 *
 * <p>Windows-only for now (see {@code docs/SETUP.md} for the manual macOS/Linux steps). Disable
 * entirely with {@code features.autoInstallBackends = false} in {@code conjure-common.toml}.
 */
public final class BackendBootstrap {

    private static volatile boolean started = false;

    private BackendBootstrap() {}

    /** Idempotent; safe to call more than once. */
    public static synchronized void start() {
        if (started) return;
        started = true;

        if (!Config.AUTO_INSTALL_BACKENDS.get()) {
            Conjure.LOGGER.info("[bootstrap] auto-install disabled "
                    + "(features.autoInstallBackends=false) — skipping backend setup.");
            return;
        }

        Thread t = new Thread(BackendBootstrap::run, "conjure-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    private static void run() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            Conjure.LOGGER.warn("[bootstrap] Automatic backend install is Windows-only for now. "
                    + "On macOS/Linux install Ollama + the text model and ComfyUI manually — "
                    + "see docs/SETUP.md.");
            return;
        }

        Path runtime = FMLPaths.GAMEDIR.get().resolve("conjure").resolve("runtime");
        Conjure.LOGGER.info("[bootstrap] checking AI backends (runtime dir: {})...", runtime);

        try {
            new OllamaBootstrap(runtime).ensure();
        } catch (Exception e) {
            Conjure.LOGGER.error("[bootstrap] Ollama setup failed: {}", e.toString());
        }
        try {
            new ComfyUiBootstrap(runtime).ensure();
        } catch (Exception e) {
            Conjure.LOGGER.error("[bootstrap] ComfyUI setup failed: {}", e.toString());
        }

        Conjure.LOGGER.info("[bootstrap] backend setup pass complete.");
    }
}
