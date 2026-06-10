package dev.conjure.gen;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns the on-disk, runtime-generated resource pack at {@code <gamedir>/conjure/generated/}.
 * AI-produced textures and models are written here, then the client re-reads the folder on
 * {@code reloadResourcePacks()} — so new visuals appear without a relaunch. Pure file IO, so
 * this class is safe to touch from either side; the pack registration + reload are client-only.
 */
public final class DynamicPackManager {

    private static Path root;

    public static synchronized Path root() {
        if (root == null) {
            root = FMLPaths.GAMEDIR.get().resolve("conjure").resolve("generated");
            try {
                Files.createDirectories(root.resolve("assets/conjure/textures/item"));
                Files.createDirectories(root.resolve("assets/conjure/models/item"));
                Path meta = root.resolve("pack.mcmeta");
                if (!Files.exists(meta)) {
                    Files.writeString(meta,
                            "{\"pack\":{\"description\":\"Conjure generated content\",\"pack_format\":34}}");
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not initialize Conjure resource pack", e);
            }
        }
        return root;
    }

    public static void writeItemTexture(int slot, int[][] argb) throws IOException {
        PixelTexture.writePng(argb, root().resolve("assets/conjure/textures/item/item_slot_" + slot + ".png"));
    }

    public static void writeItemModel(int slot) throws IOException {
        String json = "{\n  \"parent\": \"minecraft:item/generated\",\n"
                + "  \"textures\": { \"layer0\": \"conjure:item/item_slot_" + slot + "\" }\n}";
        Files.writeString(root().resolve("assets/conjure/models/item/item_slot_" + slot + ".json"), json);
    }

    private DynamicPackManager() {}
}
