package dev.conjure.gen;

/*
 * PARENT WIRING INSTRUCTION
 * =========================
 * Add the following sub-command to ConjureCommands.java inside the /conjure dispatcher:
 *
 *   .then(Commands.literal("mod")
 *       .then(Commands.argument("description", StringArgumentType.greedyString())
 *           .executes(ctx -> {
 *               String description = StringArgumentType.getString(ctx, "description");
 *               CommandSourceStack source = ctx.getSource();
 *               MinecraftServer server = source.getServer();
 *
 *               source.sendSuccess(
 *                   () -> Component.literal("§7Planning mod \"" + description + "\"… (this may take a minute)"),
 *                   false);
 *
 *               ModService.buildMod(description, msg ->
 *                   server.execute(() -> source.sendSystemMessage(
 *                       Component.literal("§a[Conjure] §f" + msg))));
 *               return 1;
 *           })))
 *
 * Required imports to add to ConjureCommands.java:
 *   import dev.conjure.gen.ModService;
 *
 * Call signature: ModService.buildMod(String modDescription, Consumer<String> feedback)
 * The method is non-blocking — it returns immediately and dispatches work to a daemon thread.
 */

import dev.conjure.Conjure;
import dev.conjure.ai.agents.ModPlannerAgent;

import java.util.List;
import java.util.function.Consumer;

/**
 * High-level orchestrator for whole-mod generation.
 *
 * <p>Takes a single natural-language mod concept (e.g. "a beekeeping mod with hives, honey, and
 * bees"), uses {@link ModPlannerAgent} to decompose it into a list of individual content prompts,
 * then fires each prompt through {@link GenerationService#generate(String, Consumer)} — which
 * routes, slots, generates assets, and reloads resources on its own single-threaded background pool.
 *
 * <p>The planning + dispatch loop runs on a separate daemon thread so neither the server thread
 * nor the generation thread is ever blocked.
 *
 * <p>See the {@code PARENT WIRING INSTRUCTION} comment at the top of this file for how to hook
 * {@code /conjure mod <description>} into {@link dev.conjure.command.ConjureCommands}.
 */
public final class ModService {

    /**
     * Decomposes {@code modDescription} into individual content prompts via {@link ModPlannerAgent},
     * feeds back a plan summary, then enqueues each prompt onto the generation pool.
     *
     * <p>This method returns immediately; all work happens on a daemon background thread.
     *
     * @param modDescription the user's high-level mod concept
     * @param feedback        progress and result callback — invoked on the planner thread for plan
     *                        messages, then on the generation thread for per-piece messages; callers
     *                        must re-schedule to the server thread before touching game state
     */
    public static void buildMod(String modDescription, Consumer<String> feedback) {
        build(modDescription, feedback, true);
    }

    /**
     * Plans {@code description} into pieces and enqueues each on the generation pool.
     *
     * <p>This method returns immediately; all work happens on a daemon background thread.
     *
     * @param description the content request
     * @param feedback    progress and result callback — invoked on the planner thread for plan
     *                    messages, then on the generation thread for per-piece messages; callers
     *                    must re-schedule to the server thread before touching game state
     * @param expansive   {@code true} for whole-mod planning (always many pieces, {@code /conjure
     *                    mod}); {@code false} for smart auto mode where a single concrete prompt
     *                    yields one piece and themed/plural prompts expand ({@code /conjure new})
     */
    public static void build(String description, Consumer<String> feedback, boolean expansive) {
        Thread planner = new Thread(() -> {
            try {
                List<String> pieces = new ModPlannerAgent().plan(description, expansive);

                // For a single piece, skip the plan summary so a plain /conjure new isn't noisy.
                if (pieces.size() > 1) {
                    feedback.accept("§7[Conjure] Plan (" + pieces.size() + " pieces):");
                    for (int i = 0; i < pieces.size(); i++) {
                        feedback.accept("§7[Conjure]   " + (i + 1) + ". " + pieces.get(i));
                    }
                    feedback.accept("§7[Conjure] Queuing generation — pieces will arrive as they complete.");
                }

                // Enqueue each piece on the existing generation pool. They run sequentially on that
                // pool's single thread, so they won't interfere with each other or any concurrent
                // calls already queued.
                for (String piece : pieces) {
                    GenerationService.generate(piece, feedback);
                }
            } catch (Exception e) {
                Conjure.LOGGER.error("Conjure planning failed for: {}", description, e);
                feedback.accept("Planning failed: " + describe(e));
            }
        }, "conjure-mod-planner");
        planner.setDaemon(true);
        planner.start();
    }

    /**
     * Renders an exception as a human-readable, never-null message for the in-game feedback line.
     * Falls back through cause chain then to the exception class name so bare throwables like
     * {@link java.net.ConnectException} never surface as "null".
     */
    private static String describe(Throwable e) {
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) return msg;
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return e.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    private ModService() {}
}
