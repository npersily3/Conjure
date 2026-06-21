package dev.conjure.gen;

import java.io.IOException;

/**
 * Writes the runtime datapack JSON that makes a conjured block/mob actually appear in the world,
 * one emitter per resource type. Decoupled from generation: the resource is generated through its
 * normal pipeline (so it gets a proper texture), then one of these writes the spawn JSON. JSON
 * shapes are vanilla 1.21.1 (verified against the bundled data): ores use {@code underground_ores},
 * plants/trees use {@code vegetal_decoration}, mobs use NeoForge {@code add_spawns}.
 *
 * <p>Worldgen registries freeze at world load, so these take effect in new chunks after a rejoin.
 */
public final class WorldgenWriter {

    private static final String CF = "data/conjure/worldgen/configured_feature/";
    private static final String PF = "data/conjure/worldgen/placed_feature/";
    private static final String BM = "data/conjure/neoforge/biome_modifier/";

    private WorldgenWriter() {}

    /** Underground ore vein of {@code blockId} replacing stone. */
    public static void writeOre(String name, String blockId, int veinSize, int veinsPerChunk,
                                int minY, int maxY, String biomeTag) throws IOException {
        DynamicPackManager.write(CF + name + ".json",
                "{\"type\":\"minecraft:ore\",\"config\":{\"size\":" + veinSize
                + ",\"discard_chance_on_air_exposure\":0.0,\"targets\":[{\"state\":{\"Name\":\"" + blockId
                + "\"},\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:stone_ore_replaceables\"}}]}}");
        DynamicPackManager.write(PF + name + ".json",
                "{\"feature\":\"conjure:" + name + "\",\"placement\":["
                + "{\"type\":\"minecraft:count\",\"count\":" + veinsPerChunk + "},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:height_range\",\"height\":{\"type\":\"minecraft:uniform\","
                + "\"min_inclusive\":{\"absolute\":" + minY + "},\"max_inclusive\":{\"absolute\":" + maxY + "}}},"
                + "{\"type\":\"minecraft:biome\"}]}");
        addFeatures(name, biomeTag, "underground_ores");
    }

    /** Surface plant: a random_patch that scatters {@code blockId} on the ground (1-in-{@code rarity} chunks). */
    public static void writePlant(String name, String blockId, int tries, int rarity,
                                  String biomeTag) throws IOException {
        DynamicPackManager.write(CF + name + ".json",
                "{\"type\":\"minecraft:random_patch\",\"config\":{\"tries\":" + tries
                + ",\"xz_spread\":7,\"y_spread\":3,\"feature\":{\"feature\":{\"type\":\"minecraft:simple_block\","
                + "\"config\":{\"to_place\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\""
                + blockId + "\"}}}},\"placement\":[{\"type\":\"minecraft:block_predicate_filter\","
                + "\"predicate\":{\"type\":\"minecraft:matching_blocks\",\"blocks\":\"minecraft:air\"}}]}}}");
        DynamicPackManager.write(PF + name + ".json",
                "{\"feature\":\"conjure:" + name + "\",\"placement\":["
                + "{\"type\":\"minecraft:rarity_filter\",\"chance\":" + Math.max(1, rarity) + "},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:heightmap\",\"heightmap\":\"MOTION_BLOCKING\"},"
                + "{\"type\":\"minecraft:biome\"}]}");
        addFeatures(name, biomeTag, "vegetal_decoration");
    }

    /** Tree using {@code woodBlockId} as the trunk and vanilla oak leaves as foliage (v1: shared leaves). */
    public static void writeTree(String name, String woodBlockId, int baseHeight,
                                 String biomeTag) throws IOException {
        DynamicPackManager.write(CF + name + ".json",
                "{\"type\":\"minecraft:tree\",\"config\":{\"ignore_vines\":true,\"force_dirt\":false,"
                + "\"decorators\":[],"
                + "\"dirt_provider\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"minecraft:dirt\"}},"
                + "\"trunk_provider\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"" + woodBlockId + "\"}},"
                + "\"foliage_provider\":{\"type\":\"minecraft:simple_state_provider\",\"state\":{\"Name\":\"minecraft:oak_leaves\","
                + "\"Properties\":{\"distance\":\"7\",\"persistent\":\"false\",\"waterlogged\":\"false\"}}},"
                + "\"trunk_placer\":{\"type\":\"minecraft:straight_trunk_placer\",\"base_height\":" + baseHeight
                + ",\"height_rand_a\":2,\"height_rand_b\":0},"
                + "\"foliage_placer\":{\"type\":\"minecraft:blob_foliage_placer\",\"radius\":2,\"offset\":0,\"height\":3},"
                + "\"minimum_size\":{\"type\":\"minecraft:two_layers_feature_size\",\"limit\":1,\"lower_size\":0,\"upper_size\":1}}}");
        DynamicPackManager.write(PF + name + ".json",
                "{\"feature\":\"conjure:" + name + "\",\"placement\":["
                + "{\"type\":\"minecraft:rarity_filter\",\"chance\":6},"
                + "{\"type\":\"minecraft:in_square\"},"
                + "{\"type\":\"minecraft:heightmap\",\"heightmap\":\"MOTION_BLOCKING\"},"
                + "{\"type\":\"minecraft:biome\"}]}");
        addFeatures(name, biomeTag, "vegetal_decoration");
    }

    /** Mob spawn: a NeoForge add_spawns biome modifier for {@code entityId}. */
    public static void writeMobSpawn(String name, String entityId, int weight, int minCount,
                                     int maxCount, String biomeTag) throws IOException {
        DynamicPackManager.write(BM + name + ".json",
                "{\"type\":\"neoforge:add_spawns\",\"biomes\":\"" + biomeTag + "\",\"spawners\":"
                + "{\"type\":\"" + entityId + "\",\"weight\":" + weight + ",\"minCount\":" + minCount
                + ",\"maxCount\":" + maxCount + "}}");
    }

    private static void addFeatures(String name, String biomeTag, String step) throws IOException {
        DynamicPackManager.write(BM + name + ".json",
                "{\"type\":\"neoforge:add_features\",\"biomes\":\"" + biomeTag + "\",\"features\":\"conjure:"
                + name + "\",\"step\":\"" + step + "\"}");
    }
}
