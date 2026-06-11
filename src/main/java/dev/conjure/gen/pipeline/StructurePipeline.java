package dev.conjure.gen.pipeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.conjure.Conjure;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;
import dev.conjure.ai.agents.DataAgent;
import dev.conjure.ai.agents.JsonHelper;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.registry.ConjureStructures;

import java.util.function.Consumer;

/**
 * Structure generation pipeline.
 *
 * <p>Asks the text model for a small bounded build (palette + 3D grid, ≤ 9×9×9) and
 * stores it in the slot definition's {@code strings} and {@code numbers} maps.
 * NO world access happens here — all work runs on the background generation thread.
 * Placement is intentionally deferred to {@code /conjure place <index>} so the player
 * can trigger it at the right time.
 *
 * <h2>Slot storage keys</h2>
 * <ul>
 *   <li>{@code strings.put("palette")} — JSON array of block-id strings</li>
 *   <li>{@code strings.put("layers")}  — JSON array-of-arrays-of-arrays [y][z][x]
 *       with palette indices (ints)</li>
 *   <li>{@code numbers.put("sizeX")}, {@code "sizeY"}, {@code "sizeZ"}</li>
 * </ul>
 *
 * <p>The layout can be placed with
 * {@link dev.conjure.content.structure.StructurePlacer#place}.
 */
public final class StructurePipeline implements GenerationPipeline {

    // -------------------------------------------------------------------------
    // Prompt
    // -------------------------------------------------------------------------

    private static final String SYSTEM = """
            You are a Minecraft structure designer. Given a prompt, produce a SMALL bounded build
            as a JSON object.  Rules:
            - Maximum 9×9×9 blocks.
            - Use only vanilla Minecraft block ids (e.g. "minecraft:stone", "minecraft:oak_log").
            - Include "minecraft:air" as palette index 0 where applicable; air blocks will be skipped.
            - The "layers" array goes [y=0 bottom … y=sizeY-1 top], each layer is [z][x] rows.
            - Every row must have exactly sizeX elements; every layer must have exactly sizeZ rows.
            Reply with ONLY the following JSON object — no prose, no markdown fences:
            {
              "palette": ["minecraft:air", "minecraft:stone", ...],
              "sizeX": <int 1-9>,
              "sizeY": <int 1-9>,
              "sizeZ": <int 1-9>,
              "layers": [
                [
                  [<paletteIdx>, <paletteIdx>, ...],
                  ...
                ],
                ...
              ]
            }
            """;

    // -------------------------------------------------------------------------
    // GenerationPipeline impl
    // -------------------------------------------------------------------------

    @Override
    public void run(String prompt, Consumer<String> feedback) throws Exception {
        int slot = SlotRegistry.firstFree(SlotKind.STRUCTURE, ConjureStructures.STRUCTURE_POOL);
        if (slot < 0) {
            feedback.accept("All " + ConjureStructures.STRUCTURE_POOL + " structure slots are full.");
            return;
        }

        Conjure.LOGGER.info("Conjure: generating structure slot {} via {} for prompt: {}",
                slot, ProviderFactory.text().id(), prompt);

        feedback.accept("§7[Conjure] Designing structure layout…");
        TextModelProvider provider = ProviderFactory.text();
        String userMsg = "Design a Minecraft structure for: " + prompt;
        String raw = provider.complete(SYSTEM, userMsg);
        JsonObject layout = JsonHelper.extractAndParse(raw, SYSTEM, provider, userMsg);

        // Validate and extract dimensions
        int sizeX = layout.has("sizeX") ? clamp(layout.get("sizeX").getAsInt(), 1, 9) : 5;
        int sizeY = layout.has("sizeY") ? clamp(layout.get("sizeY").getAsInt(), 1, 9) : 5;
        int sizeZ = layout.has("sizeZ") ? clamp(layout.get("sizeZ").getAsInt(), 1, 9) : 5;

        // Extract and store palette JSON string
        JsonArray palette = layout.has("palette") ? layout.get("palette").getAsJsonArray() : new JsonArray();
        if (palette.isEmpty()) {
            // Fallback minimal palette
            palette.add("minecraft:air");
            palette.add("minecraft:stone");
        }

        // Extract and store layers JSON string
        JsonArray layers = layout.has("layers") ? layout.get("layers").getAsJsonArray() : new JsonArray();

        feedback.accept("§7[Conjure] Generating name…");
        DataAgent.Result data = new DataAgent().generate(prompt);

        SlotDefinition def = new SlotDefinition(SlotKind.STRUCTURE, slot);
        def.displayName  = data.displayName();
        def.sourcePrompt = prompt;
        def.strings.put("description", data.description());
        def.strings.put("palette", palette.toString());
        def.strings.put("layers",  layers.toString());
        def.numbers.put("sizeX", (double) sizeX);
        def.numbers.put("sizeY", (double) sizeY);
        def.numbers.put("sizeZ", (double) sizeZ);

        PipelineSupport.commit(def);

        feedback.accept("Conjured '" + data.displayName() + "' → structure slot #" + slot
                + ". Place it with: /conjure place " + slot);
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
