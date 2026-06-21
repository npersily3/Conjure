package dev.conjure.gen;

import dev.conjure.Conjure;
import dev.conjure.ai.agents.ModPlannerAgent;
import dev.conjure.content.SlotKind;
import dev.conjure.gen.pipeline.PipelineSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Wired into {@code /conjure mod <description>} via {@link dev.conjure.command.ConjureCommands}.
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
        Thread planner = new Thread(() -> {
            try {
                ModPlannerAgent.ModPlan plan = new ModPlannerAgent().planMod(modDescription);
                List<ModPlannerAgent.Piece> ordered = plan.ordered();

                feedback.accept("§7[Conjure] Mod plan (" + ordered.size() + " pieces, resource→product):");
                for (ModPlannerAgent.Piece p : ordered) {
                    feedback.accept("§7[Conjure]   [" + p.role().name().toLowerCase() + "] " + p.prompt());
                }

                // Generate in order, threading each created id to the pieces crafted from it.
                Map<String, String> made = new LinkedHashMap<>();
                for (ModPlannerAgent.Piece p : ordered) {
                    List<String> ingredientIds = new ArrayList<>();
                    for (String ref : p.from()) {
                        String id = made.get(ref);
                        if (id != null) ingredientIds.add(id);
                    }
                    boolean worldgenResource = p.role() == ModPlannerAgent.Role.RESOURCE
                            && (p.kind() == null || p.kind() == SlotKind.BLOCK);
                    boolean smelt = p.role() == ModPlannerAgent.Role.MATERIAL && ingredientIds.size() == 1;

                    String created = GenerationService.generateForMod(
                            p.prompt(), p.kind(), worldgenResource, ingredientIds, smelt, feedback);
                    if (created != null && p.name() != null && !p.name().isBlank()) {
                        made.put(p.name(), created);
                    }
                }
                feedback.accept("§a[Conjure] §fMod complete (" + made.size()
                        + " linked pieces). §7Ore resources spawn in new chunks after a world rejoin.");
            } catch (Exception e) {
                Conjure.LOGGER.error("Conjure mod planning failed for: {}", modDescription, e);
                feedback.accept("Mod planning failed: " + PipelineSupport.describe(e));
            }
        }, "conjure-mod-planner");
        planner.setDaemon(true);
        planner.start();
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
                feedback.accept("Planning failed: " + PipelineSupport.describe(e));
            }
        }, "conjure-mod-planner");
        planner.setDaemon(true);
        planner.start();
    }

    private ModService() {}
}
