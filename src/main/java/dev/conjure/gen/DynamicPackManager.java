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

    // -------------------------------------------------------------------------
    // Blocks — a block needs four files to render: texture, block model, blockstate,
    // and a block-item model (so it shows in the inventory / in-hand). All faces share
    // one texture via minecraft:block/cube_all.
    // -------------------------------------------------------------------------

    public static void writeBlockTexture(int slot, int[][] argb) throws IOException {
        PixelTexture.writePng(argb, root().resolve("assets/conjure/textures/block/block_slot_" + slot + ".png"));
    }

    public static void writeBlockModel(int slot) throws IOException {
        String json = "{\n  \"parent\": \"minecraft:block/cube_all\",\n"
                + "  \"textures\": { \"all\": \"conjure:block/block_slot_" + slot + "\" }\n}";
        write("assets/conjure/models/block/block_slot_" + slot + ".json", json);
    }

    public static void writeBlockState(int slot) throws IOException {
        String json = "{\n  \"variants\": { \"\": { \"model\": \"conjure:block/block_slot_" + slot + "\" } }\n}";
        write("assets/conjure/blockstates/block_slot_" + slot + ".json", json);
    }

    /** Inventory/in-hand model for the block's BlockItem — inherits the block model. */
    public static void writeBlockItemModel(int slot) throws IOException {
        String json = "{ \"parent\": \"conjure:block/block_slot_" + slot + "\" }";
        write("assets/conjure/models/item/block_slot_" + slot + ".json", json);
    }

    /** Writes text to a path under the pack root, creating parent directories as needed. */
    private static void write(String relativePath, String content) throws IOException {
        Path out = root().resolve(relativePath);
        Files.createDirectories(out.getParent());
        Files.writeString(out, content);
    }

    private DynamicPackManager() {}
}
