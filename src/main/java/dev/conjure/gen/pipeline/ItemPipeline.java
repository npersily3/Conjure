package dev.conjure.gen.pipeline;

import dev.conjure.Conjure;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextureKind;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.LogicAgent;
import dev.conjure.ai.agents.TextureAgent;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.gen.DynamicPackManager;
import dev.conjure.registry.ConjureItems;

import java.util.function.Consumer;

/**
 * Item generation: texture → name + description → right-click behavior script. Writes the item
 * texture + model, fills the {@link SlotKind#ITEM} slot, persists, and reloads.
 */
public final class ItemPipeline implements GenerationPipeline {

    @Override
    public void run(String prompt, Consumer<String> feedback) throws Exception {
        int slot = SlotRegistry.firstFree(SlotKind.ITEM, ConjureItems.ITEM_POOL);
        if (slot < 0) {
            feedback.accept("All " + ConjureItems.ITEM_POOL + " item slots are full.");
            return;
        }
        runForSlot(slot, prompt, feedback);
    }

    /**
     * Runs the item pipeline against a specific slot index. Used both for new generation and for
     * {@code /conjure edit} regeneration (which preserves the slot id so existing stacks stay valid).
     *
     * <p>DataAgent runs FIRST so its description can enrich the texture prompt: the display name
     * and description together give the image backend more visual context than the raw user prompt
     * alone (e.g. "Ember Dagger — a blade forged from volcanic obsidian" is richer than just
     * "fire dagger"). The description is passed as the {@code visualIntent} parameter to
     * {@link TextureAgent#generate(String, String, TextureKind)}.
     */
    public void runForSlot(int slot, String prompt, Consumer<String> feedback) throws Exception {
        Conjure.LOGGER.info("Conjure: generating item slot {} via {} for prompt: {}",
                slot, ProviderFactory.text().id(), prompt);

        // DataAgent first — its description enriches the texture prompt
        feedback.accept("§7[Conjure] Generating name…");
        DataAgent.Result data = new DataAgent().generate(prompt);

        // Feed the description as visual intent so the image backend gets richer context
        feedback.accept("§7[Conjure] Generating texture…");
        int[][] argb = new TextureAgent().generate(prompt, data.description(), TextureKind.ITEM);
        DynamicPackManager.writeItemTexture(slot, argb);
        DynamicPackManager.writeItemModel(slot);

        feedback.accept("§7[Conjure] Generating behavior script…");
        String scriptId = "item_" + slot;
        PipelineSupport.writeScript(scriptId, new LogicAgent().generate(prompt));

        SlotDefinition def = new SlotDefinition(SlotKind.ITEM, slot);
        def.displayName      = data.displayName();
        def.sourcePrompt     = prompt;
        def.texturePath      = "conjure:item/item_slot_" + slot;
        def.behaviorScriptId = scriptId;
        def.strings.put("description", data.description());

        PipelineSupport.commit(def);

        feedback.accept("Conjured '" + data.displayName() + "' → item slot #" + slot
                + ". Try: /give @s conjure:item_slot_" + slot);
    }
}
