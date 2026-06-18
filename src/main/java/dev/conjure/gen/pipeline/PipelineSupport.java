package dev.conjure.gen.pipeline;

import dev.conjure.client.ClientHooks;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotRegistry;
import dev.conjure.persist.SlotStore;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared helpers used by every {@link GenerationPipeline}: committing a finished slot
 * (registry + persistence + client reload), writing behavior scripts, and rendering a
 * never-null error message.
 */
public final class PipelineSupport {

    private PipelineSupport() {}

    /**
     * Publishes a finished slot: swaps it into the {@link SlotRegistry}, persists it via
     * {@link SlotStore}, and triggers a client-side resource reload so new assets appear live.
     */
    public static void commit(SlotDefinition def) throws Exception {
        commitQuiet(def);
        reloadIfClient();
    }

    /**
     * Publishes a slot (registry + persistence) WITHOUT triggering a client reload. Used when
     * committing many slots at once (a material family); the caller reloads once at the end via
     * {@link #reloadIfClient()} to avoid a reload per member.
     */
    public static void commitQuiet(SlotDefinition def) throws Exception {
        SlotRegistry.put(def);
        SlotStore.save(def);
    }

    /** Triggers a client resource-pack reload (no-op on a dedicated server). */
    public static void reloadIfClient() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientHooks.reloadResources();
        }
    }

    /**
     * Reloads the integrated/dedicated server's datapacks so newly written recipes take effect
     * without a relaunch (the {@code commit} path only refreshes client <em>assets</em>; recipes
     * live in the datapack). No-op when no server is running. Scheduled on the server thread.
     */
    public static void reloadData() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> server.reloadResources(server.getPackRepository().getSelectedIds()));
        }
    }

    /** Writes a behavior script to {@code <gamedir>/conjure/generated/scripts/<id>.js}. */
    public static void writeScript(String scriptId, String source) throws Exception {
        Path dir = FMLPaths.GAMEDIR.get()
                .resolve("conjure")
                .resolve("generated")
                .resolve("scripts");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(scriptId + ".js"), source);
    }

    /**
     * Renders an exception as a human-readable, never-null message for the in-game feedback line.
     * Falls back to the (possibly wrapped) cause's message, then the exception type, so a
     * message-less throwable (e.g. a bare {@link java.net.ConnectException}) never surfaces as "null".
     */
    public static String describe(Throwable e) {
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) return msg;
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return e.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return e.getClass().getSimpleName();
    }
}
