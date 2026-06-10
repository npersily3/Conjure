package dev.conjure.persist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.conjure.Conjure;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Persists and loads {@link SlotDefinition}s (for item slots) to/from
 * {@code <gamedir>/conjure/slots/item_<index>.json}.
 *
 * <p>Only ITEM kind slots are handled here; the per-file layout keeps each item's data
 * independently readable and avoids write-contention between parallel generation tasks.
 *
 * <p>Fields serialised: {@code displayName}, {@code texturePath}, {@code behaviorScriptId},
 * {@code sourcePrompt}, {@code numbers}, {@code strings}. The {@code kind} and
 * {@code slotIndex} are encoded in the filename and re-derived on load; {@code configured}
 * is implicitly {@code true} for any file that exists.
 *
 * <p>Texture/model PNGs and behavior {@code .js} files are written separately by the
 * {@link dev.conjure.gen.DynamicPackManager} / orchestrator; this class only handles the
 * metadata JSON.
 */
public final class SlotStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SlotStore() {}

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    /**
     * Serialises {@code def} to {@code <gamedir>/conjure/slots/item_<index>.json}.
     * Creates parent directories on first call.
     *
     * @param def the fully-configured {@link SlotDefinition} to persist
     * @throws IOException on file-system errors
     */
    public static void save(SlotDefinition def) throws IOException {
        Path file = slotFile(def.kind, def.slotIndex);
        Files.createDirectories(file.getParent());

        JsonObject obj = new JsonObject();
        obj.addProperty("displayName", def.displayName);
        obj.addProperty("texturePath", def.texturePath);
        obj.addProperty("behaviorScriptId", def.behaviorScriptId);
        obj.addProperty("sourcePrompt", def.sourcePrompt);

        JsonObject numbers = new JsonObject();
        for (Map.Entry<String, Double> e : def.numbers.entrySet()) {
            numbers.addProperty(e.getKey(), e.getValue());
        }
        obj.add("numbers", numbers);

        JsonObject strings = new JsonObject();
        for (Map.Entry<String, String> e : def.strings.entrySet()) {
            strings.addProperty(e.getKey(), e.getValue());
        }
        obj.add("strings", strings);

        Files.writeString(file, GSON.toJson(obj));
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    /**
     * Scans {@code <gamedir>/conjure/slots/} for {@code item_*.json} files and restores each
     * into the {@link SlotRegistry}. Called once at startup (see {@link SlotStoreLoader}).
     * Unknown slot indices (outside the pre-registered pool) are skipped with a warning.
     */
    public static void loadAll() {
        Path slotsDir = slotsRoot();
        if (!Files.exists(slotsDir)) {
            return; // nothing persisted yet — fresh install
        }

        try (var stream = Files.list(slotsDir)) {
            stream.filter(p -> p.getFileName().toString().matches("[a-z]+_\\d+\\.json"))
                  .forEach(SlotStore::loadOne);
        } catch (IOException e) {
            Conjure.LOGGER.error("Conjure: failed to list slot store directory", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static void loadOne(Path file) {
        String name = file.getFileName().toString(); // "<kind>_<n>.json"
        String stem = name.substring(0, name.length() - ".json".length());
        int sep = stem.lastIndexOf('_');
        SlotKind kind;
        int index;
        try {
            kind = SlotKind.valueOf(stem.substring(0, sep).toUpperCase(java.util.Locale.ROOT));
            index = Integer.parseInt(stem.substring(sep + 1));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            Conjure.LOGGER.warn("Conjure SlotStore: skipping unrecognised file {}", name);
            return;
        }

        String json;
        try {
            json = Files.readString(file);
        } catch (IOException e) {
            Conjure.LOGGER.error("Conjure SlotStore: cannot read {}", file, e);
            return;
        }

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            SlotDefinition def = new SlotDefinition(kind, index);

            if (obj.has("displayName"))      def.displayName      = obj.get("displayName").getAsString();
            if (obj.has("texturePath"))      def.texturePath      = obj.get("texturePath").getAsString();
            if (obj.has("behaviorScriptId")) def.behaviorScriptId = obj.get("behaviorScriptId").getAsString();
            if (obj.has("sourcePrompt"))     def.sourcePrompt     = obj.get("sourcePrompt").getAsString();

            if (obj.has("numbers")) {
                obj.getAsJsonObject("numbers").entrySet()
                   .forEach(e -> def.numbers.put(e.getKey(), e.getValue().getAsDouble()));
            }
            if (obj.has("strings")) {
                obj.getAsJsonObject("strings").entrySet()
                   .forEach(e -> def.strings.put(e.getKey(), e.getValue().getAsString()));
            }

            SlotRegistry.put(def);
            Conjure.LOGGER.debug("Conjure SlotStore: restored {} slot {} (\"{}\")", kind, index, def.displayName);
        } catch (Exception e) {
            Conjure.LOGGER.error("Conjure SlotStore: failed to parse {}", file, e);
        }
    }

    private static Path slotsRoot() {
        return FMLPaths.GAMEDIR.get().resolve("conjure").resolve("slots");
    }

    private static Path slotFile(SlotKind kind, int index) {
        return slotsRoot().resolve(kind.name().toLowerCase(java.util.Locale.ROOT) + "_" + index + ".json");
    }
}
