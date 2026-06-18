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
                // The same folder is registered as BOTH a resource pack (format 34 in 1.21.1) and a
                // data pack (format 48) so generated recipes load. supported_formats spans both so
                // each pack type validates the meta as compatible. Always (re)written to upgrade
                // packs created before datapack support landed.
                Path meta = root.resolve("pack.mcmeta");
                Files.writeString(meta,
                        "{\"pack\":{\"description\":\"Conjure generated content\",\"pack_format\":34,"
                        + "\"supported_formats\":{\"min_inclusive\":34,\"max_inclusive\":48}}}");
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

    // -------------------------------------------------------------------------
    // Shaped block variants (slab / stairs / wall). Each REUSES an existing block texture
    // (textureRef, e.g. "conjure:block/block_slot_5") — the shaped models reference vanilla
    // parents and only override the texture, so no new texture is generated. The blockstate
    // rotation tables are copied verbatim from vanilla (stone_stairs / cobblestone_wall) with the
    // model prefix swapped to ours; since "_inner"/"_outer"/"_post"/"_side" are suffixes, a single
    // replace rewrites every model reference.
    // -------------------------------------------------------------------------

    /** Vanilla stone_stairs blockstate; "minecraft:block/stone_stairs" → our model prefix. */
    private static final String STAIRS_BLOCKSTATE = """
            {"variants":{"facing=east,half=bottom,shape=inner_left":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"y":270},"facing=east,half=bottom,shape=inner_right":{"model":"minecraft:block/stone_stairs_inner"},"facing=east,half=bottom,shape=outer_left":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"y":270},"facing=east,half=bottom,shape=outer_right":{"model":"minecraft:block/stone_stairs_outer"},"facing=east,half=bottom,shape=straight":{"model":"minecraft:block/stone_stairs"},"facing=east,half=top,shape=inner_left":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"x":180},"facing=east,half=top,shape=inner_right":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"x":180,"y":90},"facing=east,half=top,shape=outer_left":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"x":180},"facing=east,half=top,shape=outer_right":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"x":180,"y":90},"facing=east,half=top,shape=straight":{"model":"minecraft:block/stone_stairs","uvlock":true,"x":180},"facing=north,half=bottom,shape=inner_left":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"y":180},"facing=north,half=bottom,shape=inner_right":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"y":270},"facing=north,half=bottom,shape=outer_left":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"y":180},"facing=north,half=bottom,shape=outer_right":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"y":270},"facing=north,half=bottom,shape=straight":{"model":"minecraft:block/stone_stairs","uvlock":true,"y":270},"facing=north,half=top,shape=inner_left":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"x":180,"y":270},"facing=north,half=top,shape=inner_right":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"x":180},"facing=north,half=top,shape=outer_left":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"x":180,"y":270},"facing=north,half=top,shape=outer_right":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"x":180},"facing=north,half=top,shape=straight":{"model":"minecraft:block/stone_stairs","uvlock":true,"x":180,"y":270},"facing=south,half=bottom,shape=inner_left":{"model":"minecraft:block/stone_stairs_inner"},"facing=south,half=bottom,shape=inner_right":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"y":90},"facing=south,half=bottom,shape=outer_left":{"model":"minecraft:block/stone_stairs_outer"},"facing=south,half=bottom,shape=outer_right":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"y":90},"facing=south,half=bottom,shape=straight":{"model":"minecraft:block/stone_stairs","uvlock":true,"y":90},"facing=south,half=top,shape=inner_left":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"x":180,"y":90},"facing=south,half=top,shape=inner_right":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"x":180,"y":180},"facing=south,half=top,shape=outer_left":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"x":180,"y":90},"facing=south,half=top,shape=outer_right":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"x":180,"y":180},"facing=south,half=top,shape=straight":{"model":"minecraft:block/stone_stairs","uvlock":true,"x":180,"y":90},"facing=west,half=bottom,shape=inner_left":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"y":90},"facing=west,half=bottom,shape=inner_right":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"y":180},"facing=west,half=bottom,shape=outer_left":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"y":90},"facing=west,half=bottom,shape=outer_right":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"y":180},"facing=west,half=bottom,shape=straight":{"model":"minecraft:block/stone_stairs","uvlock":true,"y":180},"facing=west,half=top,shape=inner_left":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"x":180,"y":180},"facing=west,half=top,shape=inner_right":{"model":"minecraft:block/stone_stairs_inner","uvlock":true,"x":180,"y":270},"facing=west,half=top,shape=outer_left":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"x":180,"y":180},"facing=west,half=top,shape=outer_right":{"model":"minecraft:block/stone_stairs_outer","uvlock":true,"x":180,"y":270},"facing=west,half=top,shape=straight":{"model":"minecraft:block/stone_stairs","uvlock":true,"x":180,"y":180}}}
            """;

    /** Vanilla cobblestone_wall blockstate; "minecraft:block/cobblestone_wall" → our model prefix. */
    private static final String WALL_BLOCKSTATE = """
            {"multipart":[{"apply":{"model":"minecraft:block/cobblestone_wall_post"},"when":{"up":"true"}},{"apply":{"model":"minecraft:block/cobblestone_wall_side","uvlock":true},"when":{"north":"low"}},{"apply":{"model":"minecraft:block/cobblestone_wall_side","uvlock":true,"y":90},"when":{"east":"low"}},{"apply":{"model":"minecraft:block/cobblestone_wall_side","uvlock":true,"y":180},"when":{"south":"low"}},{"apply":{"model":"minecraft:block/cobblestone_wall_side","uvlock":true,"y":270},"when":{"west":"low"}},{"apply":{"model":"minecraft:block/cobblestone_wall_side_tall","uvlock":true},"when":{"north":"tall"}},{"apply":{"model":"minecraft:block/cobblestone_wall_side_tall","uvlock":true,"y":90},"when":{"east":"tall"}},{"apply":{"model":"minecraft:block/cobblestone_wall_side_tall","uvlock":true,"y":180},"when":{"south":"tall"}},{"apply":{"model":"minecraft:block/cobblestone_wall_side_tall","uvlock":true,"y":270},"when":{"west":"tall"}}]}
            """;

    /** Slab: bottom/top half models + a full-cube double model. Reuses {@code textureRef}. */
    public static void writeSlabAssets(int slot, String textureRef) throws IOException {
        String id = "slab_slot_" + slot;
        write("assets/conjure/blockstates/" + id + ".json",
                "{\"variants\":{"
                + "\"type=bottom\":{\"model\":\"conjure:block/" + id + "\"},"
                + "\"type=top\":{\"model\":\"conjure:block/" + id + "_top\"},"
                + "\"type=double\":{\"model\":\"conjure:block/" + id + "_double\"}}}");
        write("assets/conjure/models/block/" + id + ".json", faceModel("minecraft:block/slab", textureRef));
        write("assets/conjure/models/block/" + id + "_top.json", faceModel("minecraft:block/slab_top", textureRef));
        write("assets/conjure/models/block/" + id + "_double.json",
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"" + textureRef + "\"}}");
        write("assets/conjure/models/item/" + id + ".json", "{\"parent\":\"conjure:block/" + id + "\"}");
    }

    /** Stairs: straight/inner/outer models + the full vanilla rotation blockstate. */
    public static void writeStairAssets(int slot, String textureRef) throws IOException {
        String id = "stairs_slot_" + slot;
        write("assets/conjure/blockstates/" + id + ".json",
                STAIRS_BLOCKSTATE.replace("minecraft:block/stone_stairs", "conjure:block/" + id));
        write("assets/conjure/models/block/" + id + ".json", faceModel("minecraft:block/stairs", textureRef));
        write("assets/conjure/models/block/" + id + "_inner.json", faceModel("minecraft:block/inner_stairs", textureRef));
        write("assets/conjure/models/block/" + id + "_outer.json", faceModel("minecraft:block/outer_stairs", textureRef));
        write("assets/conjure/models/item/" + id + ".json", "{\"parent\":\"conjure:block/" + id + "\"}");
    }

    /** Wall: post/side/side_tall models + the multipart blockstate; inventory model for the item. */
    public static void writeWallAssets(int slot, String textureRef) throws IOException {
        String id = "wall_slot_" + slot;
        write("assets/conjure/blockstates/" + id + ".json",
                WALL_BLOCKSTATE.replace("minecraft:block/cobblestone_wall", "conjure:block/" + id));
        write("assets/conjure/models/block/" + id + "_post.json", wallModel("minecraft:block/template_wall_post", textureRef));
        write("assets/conjure/models/block/" + id + "_side.json", wallModel("minecraft:block/template_wall_side", textureRef));
        write("assets/conjure/models/block/" + id + "_side_tall.json", wallModel("minecraft:block/template_wall_side_tall", textureRef));
        write("assets/conjure/models/item/" + id + ".json", wallModel("minecraft:block/wall_inventory", textureRef));
    }

    /** Model with bottom/top/side texture keys (slab + stairs parents use these). */
    private static String faceModel(String parent, String tex) {
        return "{\"parent\":\"" + parent + "\",\"textures\":{"
                + "\"bottom\":\"" + tex + "\",\"top\":\"" + tex + "\",\"side\":\"" + tex + "\"}}";
    }

    /** Model with a single "wall" texture key (wall parents use this). */
    private static String wallModel(String parent, String tex) {
        return "{\"parent\":\"" + parent + "\",\"textures\":{\"wall\":\"" + tex + "\"}}";
    }

    // -------------------------------------------------------------------------
    // Shared low-level writers — used by per-kind asset writers (fluids, entities,
    // structures) so those lanes never need to edit this file. Paths are relative to
    // the pack root, e.g. "assets/conjure/textures/entity/entity_slot_3.png".
    // -------------------------------------------------------------------------

    /** Writes text to a path under the pack root, creating parent directories as needed. */
    public static void write(String relativePath, String content) throws IOException {
        Path out = root().resolve(relativePath);
        Files.createDirectories(out.getParent());
        Files.writeString(out, content);
    }

    /** Encodes an ARGB grid to a PNG at a path under the pack root, creating parent dirs. */
    public static void writePngAt(String relativePath, int[][] argb) throws IOException {
        PixelTexture.writePng(argb, root().resolve(relativePath));
    }

    private DynamicPackManager() {}
}
