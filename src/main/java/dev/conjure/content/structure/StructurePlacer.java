package dev.conjure.content.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.conjure.Conjure;
import dev.conjure.content.SlotDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Places a Conjure structure into the world from a {@link SlotDefinition} that holds a
 * pre-generated layout produced by {@link dev.conjure.gen.pipeline.StructurePipeline}.
 *
 * <h2>Layout storage format</h2>
 * <p>The pipeline stores data on the slot definition using the {@code strings} and
 * {@code numbers} maps (no new fields on SlotDefinition):
 * <ul>
 *   <li>{@code strings.get("palette")} — JSON array of Minecraft block-id strings,
 *       e.g. {@code ["minecraft:air","minecraft:stone","minecraft:oak_log"]}</li>
 *   <li>{@code strings.get("layers")} — JSON array-of-arrays-of-arrays; outer dimension = Y,
 *       middle = Z row, inner = X column; each element is a palette index (int)</li>
 *   <li>{@code numbers.get("sizeX")}, {@code "sizeY"}, {@code "sizeZ"}} — bounding box dims</li>
 * </ul>
 *
 * <h2>Placement</h2>
 * <p>Blocks are placed at {@code origin.offset(x, y, z)}. Air palette entries are skipped so
 * the surroundings are not filled. Unknown block ids fall back to stone with a warning logged.
 */
public final class StructurePlacer {

    private StructurePlacer() {}

    /**
     * Places the structure encoded in {@code def} with its south-west-bottom corner at
     * {@code origin}. Must be called from the server thread.
     *
     * @param level  the world to place into
     * @param origin bottom-north-west corner (where the player is standing, roughly)
     * @param def    the configured slot definition
     * @return the number of blocks placed
     * @throws IllegalArgumentException if the layout data is missing or malformed
     */
    public static int place(ServerLevel level, BlockPos origin, SlotDefinition def) {
        String paletteJson = def.strings.get("palette");
        String layersJson  = def.strings.get("layers");
        if (paletteJson == null || layersJson == null) {
            throw new IllegalArgumentException(
                    "Structure slot #" + def.slotIndex + " has no layout data — generate it first.");
        }

        // Parse palette
        JsonArray rawPalette = JsonParser.parseString(paletteJson).getAsJsonArray();
        List<BlockState> palette = new ArrayList<>(rawPalette.size());
        for (JsonElement el : rawPalette) {
            String id = el.getAsString();
            BlockState state = resolveBlock(id);
            palette.add(state);
        }

        // Parse layers [y][z][x]
        JsonArray rawLayers = JsonParser.parseString(layersJson).getAsJsonArray();
        int placed = 0;

        for (int y = 0; y < rawLayers.size(); y++) {
            JsonArray rows = rawLayers.get(y).getAsJsonArray();
            for (int z = 0; z < rows.size(); z++) {
                JsonArray cols = rows.get(z).getAsJsonArray();
                for (int x = 0; x < cols.size(); x++) {
                    int paletteIdx = cols.get(x).getAsInt();
                    if (paletteIdx < 0 || paletteIdx >= palette.size()) continue;
                    BlockState state = palette.get(paletteIdx);
                    // Skip air
                    if (state.isAir()) continue;

                    BlockPos target = origin.offset(x, y, z);
                    level.setBlock(target, state, 3);
                    placed++;
                }
            }
        }

        return placed;
    }

    /**
     * Resolves a Minecraft block id string to a {@link BlockState}.
     * Returns air for the string "minecraft:air". Falls back to stone and logs a warning
     * for unrecognized ids.
     */
    private static BlockState resolveBlock(String id) {
        if (id == null || id.isBlank() || "minecraft:air".equals(id)) {
            return Blocks.AIR.defaultBlockState();
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            Conjure.LOGGER.warn("Conjure StructurePlacer: unparseable block id '{}', substituting stone.", id);
            return Blocks.STONE.defaultBlockState();
        }
        Optional<Block> opt = BuiltInRegistries.BLOCK.getOptional(rl);
        if (opt.isEmpty()) {
            Conjure.LOGGER.warn("Conjure StructurePlacer: unknown block id '{}', substituting stone.", id);
            return Blocks.STONE.defaultBlockState();
        }
        return opt.get().defaultBlockState();
    }
}
