package dev.conjure.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.conjure.Conjure;
import dev.conjure.ai.ProviderFactory;
import dev.conjure.ai.TextModelProvider;
import dev.conjure.client.ClientHooks;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.registry.ConjureItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The (single) generation agent for the first vertical slice: prompt -> a configured item slot
 * with an LLM-emitted 16x16 texture, applied live. Runs off-thread (the model call blocks for
 * seconds), then the slot store + resource reload take effect; in singleplayer the integrated
 * server and client share this JVM so the live slot is visible immediately.
 *
 * <p>Later this becomes the orchestrator that fans out to logic / texture / data sub-agents.
 */
public final class GenerationService {

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "conjure-gen");
        t.setDaemon(true);
        return t;
    });

    private static final String SYSTEM = """
            You design Minecraft item icons as 16x16 pixel art and respond with ONLY a JSON object,
            no prose, no markdown fences. Schema:
            {
              "displayName": "<short item name>",
              "palette": { "0": "#00000000", "1": "#RRGGBB", "2": "#RRGGBBAA", ... },
              "rows": ["................", ... 16 strings of 16 chars each ...]
            }
            Each char in a row indexes the palette. Use "0" mapped to "#00000000" for transparent
            background. Keep a clear silhouette. Exactly 16 rows of exactly 16 characters.
            """;

    public static void generateItem(String prompt, Consumer<String> feedback) {
        POOL.submit(() -> {
            try {
                int slot = SlotRegistry.firstFree(SlotKind.ITEM, ConjureItems.ITEM_POOL);
                if (slot < 0) {
                    feedback.accept("All " + ConjureItems.ITEM_POOL + " item slots are full.");
                    return;
                }

                TextModelProvider provider = ProviderFactory.text();
                Conjure.LOGGER.info("Conjure: generating item slot {} via {} for prompt: {}",
                        slot, provider.id(), prompt);
                String raw = provider.complete(SYSTEM, prompt);
                Parsed parsed = parse(raw);

                DynamicPackManager.writeItemTexture(slot, parsed.argb);
                DynamicPackManager.writeItemModel(slot);

                SlotDefinition def = new SlotDefinition(SlotKind.ITEM, slot);
                def.displayName = parsed.displayName;
                def.sourcePrompt = prompt;
                def.texturePath = "conjure:item/item_slot_" + slot;
                SlotRegistry.put(def);

                if (FMLEnvironment.dist == Dist.CLIENT) {
                    ClientHooks.reloadResources();
                }

                feedback.accept("Conjured '" + parsed.displayName + "' -> item slot #" + slot
                        + ". Try: /give @s conjure:item_slot_" + slot);
            } catch (Exception e) {
                Conjure.LOGGER.error("Conjure generation failed", e);
                feedback.accept("Generation failed: " + e.getMessage());
            }
        });
    }

    private record Parsed(String displayName, int[][] argb) {}

    private static Parsed parse(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Model returned no JSON object");
        }
        JsonObject obj = JsonParser.parseString(raw.substring(start, end + 1)).getAsJsonObject();

        String name = obj.has("displayName") ? obj.get("displayName").getAsString() : "Conjured Item";
        JsonObject palette = obj.getAsJsonObject("palette");
        JsonArray rows = obj.getAsJsonArray("rows");

        int height = rows.size();
        int[][] argb = new int[height][];
        for (int y = 0; y < height; y++) {
            String row = rows.get(y).getAsString();
            argb[y] = new int[row.length()];
            for (int x = 0; x < row.length(); x++) {
                String key = String.valueOf(row.charAt(x));
                String hex = palette.has(key) ? palette.get(key).getAsString() : "#00000000";
                argb[y][x] = PixelTexture.parseColor(hex);
            }
        }
        return new Parsed(name, argb);
    }

    private GenerationService() {}
}
