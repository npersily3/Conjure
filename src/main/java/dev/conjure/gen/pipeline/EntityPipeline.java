package dev.conjure.gen.pipeline;

import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextureKind;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.TextureAgent;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.gen.assets.EntityAssets;
import dev.conjure.registry.ConjureEntities;

import java.util.function.Consumer;

/**
 * Generation pipeline for Conjure entity (mob) slots.
 *
 * <p>On each run:
 * <ol>
 *   <li>Finds the first unconfigured slot in the ENTITY pool ({@code ConjureEntities.totalPool()} size).
 *       Reports "pool full" and returns early if all slots are taken.</li>
 *   <li>Calls {@link TextureAgent} to generate a 16×16 skin PNG and writes it to the dynamic pack
 *       under {@code assets/conjure/textures/entity/entity_slot_N.png}.</li>
 *   <li>Calls {@link DataAgent} to generate a display name (also used as the mob name).</li>
 *   <li>Assigns attribute numbers (max_health, move_speed, attack_damage, follow_range) derived
 *       from the prompt — using sensible defaults that can be tuned in future via a dedicated
 *       stats agent.</li>
 *   <li>Assembles and commits the {@link SlotDefinition} via {@link PipelineSupport#commit}.</li>
 *   <li>Reports the summon command to the feedback consumer.</li>
 * </ol>
 */
public final class EntityPipeline implements GenerationPipeline {

    @Override
    public void run(String prompt, Consumer<String> feedback) throws Exception {
        // --- Find a free slot -------------------------------------------------
        int slot = SlotRegistry.firstFree(SlotKind.ENTITY, ConjureEntities.totalPool());
        if (slot < 0) {
            feedback.accept("All " + ConjureEntities.totalPool()
                    + " entity slots are full. Use /conjure list to review existing mobs.");
            return;
        }
        runForSlot(slot, prompt, feedback);
    }

    /**
     * Runs the entity pipeline against a specific slot index. Used for new generation and for
     * {@code /conjure regenerate entity <index>} (which preserves the slot id so existing entity
     * references stay valid).
     */
    @Override
    public void runForSlot(int slot, String prompt, Consumer<String> feedback) throws Exception {
        String providerId = ProviderFactory.text().id();
        dev.conjure.Conjure.LOGGER.info(
                "Conjure: generating entity slot {} via {} for prompt: {}", slot, providerId, prompt);

        // --- Skin texture -----------------------------------------------------
        feedback.accept("§7[Conjure] Generating entity skin…");
        int[][] argb = new TextureAgent().generate(prompt, TextureKind.ENTITY);
        EntityAssets.writeSkinTexture(slot, argb);

        // --- Name (from DataAgent) --------------------------------------------
        feedback.accept("§7[Conjure] Generating entity name…");
        DataAgent.Result data = new DataAgent().generate(prompt, SlotKind.ENTITY);

        // --- Assemble SlotDefinition ------------------------------------------
        SlotDefinition def = new SlotDefinition(SlotKind.ENTITY, slot);
        def.displayName      = data.displayName();
        def.sourcePrompt     = prompt;
        // Resource-location convention: no "textures/" prefix, no ".png" suffix
        def.texturePath      = "conjure:entity/entity_slot_" + slot;
        def.behaviorScriptId = "";  // behavior scripts for entities are future work
        def.strings.put("description", data.description());

        // Attribute numbers — sensible defaults; a future StatsAgent can refine these
        def.numbers.put("max_health",    20.0);
        def.numbers.put("move_speed",    0.25);
        def.numbers.put("attack_damage", 2.0);
        def.numbers.put("follow_range",  16.0);

        // --- Commit -----------------------------------------------------------
        PipelineSupport.commit(def);

        // Report the entity id so a mod-economy "mob" resource can get spawn weights (add_spawns).
        dev.conjure.gen.GenerationContext gc = dev.conjure.gen.GenerationContext.current();
        if (gc != null) gc.setCreatedId("conjure:entity_slot_" + slot);

        feedback.accept("Conjured '" + data.displayName() + "' → entity slot #" + slot
                + ". Try: /summon conjure:entity_slot_" + slot);
    }
}
