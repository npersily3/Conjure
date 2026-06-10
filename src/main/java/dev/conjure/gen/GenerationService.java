package dev.conjure.gen;

import dev.conjure.Conjure;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.LogicAgent;
import dev.conjure.ai.agents.TextureAgent;
import dev.conjure.client.ClientHooks;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.persist.SlotStore;
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
 * <p>{@link #generateItem(String, Consumer)} creates a new item slot.
 * {@link #regenerateItem(int, String, Consumer)} re-runs the full pipeline on an existing slot
 * (preserving its registry id so existing item stacks continue to work).
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
     * Allocates the next free item slot and runs the full generation pipeline.
     *
     * @param prompt   user-supplied description of the item
     * @param feedback callback for progress and result messages (called on the generation thread;
     *                 callers must post to the server thread if needed)
     */
    public static void generateItem(String prompt, Consumer<String> feedback) {
        POOL.submit(() -> {
            try {
                int slot = SlotRegistry.firstFree(SlotKind.ITEM, ConjureItems.ITEM_POOL);
                if (slot < 0) {
                    feedback.accept("All " + ConjureItems.ITEM_POOL + " item slots are full.");
                    return;
                }
                runPipeline(slot, prompt, feedback);
            } catch (Exception e) {
                Conjure.LOGGER.error("Conjure generation failed", e);
                feedback.accept("Generation failed: " + e.getMessage());
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
                feedback.accept("Regeneration failed: " + e.getMessage());
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

    /** Writes the behavior script to {@code <gamedir>/conjure/generated/scripts/<id>.js}. */
    private static void writeScript(String scriptId, String source) throws Exception {
        Path dir = FMLPaths.GAMEDIR.get()
                .resolve("conjure")
                .resolve("generated")
                .resolve("scripts");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(scriptId + ".js"), source);
    }

    private GenerationService() {}
}
