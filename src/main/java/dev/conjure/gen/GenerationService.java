package dev.conjure.gen;

import dev.conjure.Conjure;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.LogicAgent;
import dev.conjure.ai.agents.RouterAgent;
import dev.conjure.ai.agents.TextureAgent;
import dev.conjure.client.ClientHooks;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.persist.SlotStore;
import dev.conjure.content.block.BlockArchetype;
import dev.conjure.registry.ConjureItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Orchestrator that fans out to the three specialized sub-agents:
 * <ol>
 *   <li>{@link TextureAgent} — produces the 16×16 pixel-art PNG</li>
 *   <li>{@link DataAgent}    — produces the display name and flavour description</li>
 *   <li>{@link LogicAgent}   — produces the JS right-click behavior script</li>
 * </ol>
 *
 * <p>Agents run sequentially on a single background thread so the main game thread is never
 * blocked. When all three succeed the orchestrator writes assets to disk, updates the
 * {@link SlotRegistry}, persists the {@link SlotDefinition}, and triggers a client-side
 * resource-pack reload (singleplayer only — the integrated server and client share the JVM).
 *
 * <p>{@link #generate(String, Consumer)} is the single entry point: a {@link RouterAgent} picks
 * ITEM vs BLOCK and the matching pipeline runs. {@link #regenerateItem(int, String, Consumer)}
 * re-runs the item pipeline on an existing slot (preserving its registry id so existing item
 * stacks continue to work).
 */
public final class GenerationService {

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "conjure-gen");
        t.setDaemon(true);
        return t;
    });

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Single entry point behind {@code /conjure new <prompt>}. A {@link RouterAgent} first decides
     * whether the prompt should become an ITEM or a BLOCK, then the matching pipeline allocates a
     * free slot and runs. This is the "model picks the path" routing: extend the switch as new
     * generation pipelines (fluids, entities) come online.
     *
     * @param prompt   user-supplied description
     * @param feedback callback for progress and result messages (called on the generation thread;
     *                 callers must post to the server thread if needed)
     */
    public static void generate(String prompt, Consumer<String> feedback) {
        POOL.submit(() -> {
            try {
                feedback.accept("§7[Conjure] Deciding what to build…");
                SlotKind kind = new RouterAgent().classify(prompt);

                switch (kind) {
                    case BLOCK -> {
                        // SOLID is the first archetype bucket (slots 0 .. SOLID.count-1); restricting
                        // to it guarantees an opaque block that renders with the cube_all model.
                        int slot = SlotRegistry.firstFree(SlotKind.BLOCK, BlockArchetype.SOLID.count);
                        if (slot < 0) {
                            feedback.accept("All " + BlockArchetype.SOLID.count + " solid block slots are full.");
                            return;
                        }
                        feedback.accept("§7[Conjure] Interpreting this as a block.");
                        runBlockPipeline(slot, prompt, feedback);
                    }
                    default -> { // ITEM (also the router's ambiguity fallback)
                        int slot = SlotRegistry.firstFree(SlotKind.ITEM, ConjureItems.ITEM_POOL);
                        if (slot < 0) {
                            feedback.accept("All " + ConjureItems.ITEM_POOL + " item slots are full.");
                            return;
                        }
                        feedback.accept("§7[Conjure] Interpreting this as an item.");
                        runPipeline(slot, prompt, feedback);
                    }
                }
            } catch (Exception e) {
                Conjure.LOGGER.error("Conjure generation failed", e);
                feedback.accept("Generation failed: " + describe(e));
            }
        });
    }

    /**
     * Re-runs the full generation pipeline for an existing item slot, updating its texture,
     * name, behavior, and persisted metadata while preserving the slot index (so existing
     * item stacks remain valid).
     *
     * @param slotIndex the index of the existing slot to regenerate (0-based)
     * @param prompt    new description prompt
     * @param feedback  progress callback
     */
    public static void regenerateItem(int slotIndex, String prompt, Consumer<String> feedback) {
        POOL.submit(() -> {
            try {
                if (slotIndex < 0 || slotIndex >= ConjureItems.ITEM_POOL) {
                    feedback.accept("Invalid slot index " + slotIndex
                            + " (pool size: " + ConjureItems.ITEM_POOL + ").");
                    return;
                }
                runPipeline(slotIndex, prompt, feedback);
            } catch (Exception e) {
                Conjure.LOGGER.error("Conjure regeneration failed for slot {}", slotIndex, e);
                feedback.accept("Regeneration failed: " + describe(e));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Runs all three sub-agents sequentially, assembles the {@link SlotDefinition}, writes
     * assets to disk, persists metadata, and triggers resource reload.
     */
    private static void runPipeline(int slot, String prompt, Consumer<String> feedback) throws Exception {
        String providerId = ProviderFactory.text().id();
        Conjure.LOGGER.info("Conjure: generating item slot {} via {} for prompt: {}",
                slot, providerId, prompt);

        // --- Texture ---------------------------------------------------------
        feedback.accept("§7[Conjure] Generating texture…");
        int[][] argb = new TextureAgent().generate(prompt);
        DynamicPackManager.writeItemTexture(slot, argb);
        DynamicPackManager.writeItemModel(slot);

        // --- Data (name + description) ---------------------------------------
        feedback.accept("§7[Conjure] Generating name…");
        DataAgent.Result data = new DataAgent().generate(prompt);

        // --- Logic (JS behavior script) --------------------------------------
        feedback.accept("§7[Conjure] Generating behavior script…");
        String scriptId = "item_" + slot;
        String scriptSource = new LogicAgent().generate(prompt);
        writeScript(scriptId, scriptSource);

        // --- Assemble SlotDefinition -----------------------------------------
        SlotDefinition def = new SlotDefinition(SlotKind.ITEM, slot);
        def.displayName      = data.displayName();
        def.sourcePrompt     = prompt;
        def.texturePath      = "conjure:item/item_slot_" + slot;
        def.behaviorScriptId = scriptId;
        def.strings.put("description", data.description());

        SlotRegistry.put(def);

        // --- Persist metadata ------------------------------------------------
        SlotStore.save(def);

        // --- Client-side reload ----------------------------------------------
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientHooks.reloadResources();
        }

        feedback.accept("Conjured '" + data.displayName() + "' → item slot #" + slot
                + ". Try: /give @s conjure:item_slot_" + slot);
    }

    /**
     * Block generation pipeline: texture → name, then writes the four block assets (texture,
     * block model, blockstate, block-item model), fills the BLOCK {@link SlotDefinition},
     * persists it, and triggers a client reload. No behavior script (texture + placeable scope).
     */
    private static void runBlockPipeline(int slot, String prompt, Consumer<String> feedback) throws Exception {
        String providerId = ProviderFactory.text().id();
        Conjure.LOGGER.info("Conjure: generating block slot {} via {} for prompt: {}",
                slot, providerId, prompt);

        // --- Texture + block assets -----------------------------------------
        feedback.accept("§7[Conjure] Generating texture…");
        int[][] argb = new TextureAgent().generate(prompt);
        DynamicPackManager.writeBlockTexture(slot, argb);
        DynamicPackManager.writeBlockModel(slot);
        DynamicPackManager.writeBlockState(slot);
        DynamicPackManager.writeBlockItemModel(slot);

        // --- Data (name + description) --------------------------------------
        feedback.accept("§7[Conjure] Generating name…");
        DataAgent.Result data = new DataAgent().generate(prompt);

        // --- Assemble SlotDefinition ----------------------------------------
        SlotDefinition def = new SlotDefinition(SlotKind.BLOCK, slot);
        def.displayName      = data.displayName();
        def.sourcePrompt     = prompt;
        def.texturePath      = "conjure:block/block_slot_" + slot;
        def.behaviorScriptId = ""; // texture + placeable: no right-click behavior
        def.strings.put("description", data.description());

        SlotRegistry.put(def);
        SlotStore.save(def);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientHooks.reloadResources();
        }

        feedback.accept("Conjured '" + data.displayName() + "' → block slot #" + slot
                + ". Try: /give @s conjure:block_slot_" + slot);
    }

    /** Writes the behavior script to {@code <gamedir>/conjure/generated/scripts/<id>.js}. */
    private static void writeScript(String scriptId, String source) throws Exception {
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
    private static String describe(Throwable e) {
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) return msg;
        Throwable cause = e.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return e.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    private GenerationService() {}
}
