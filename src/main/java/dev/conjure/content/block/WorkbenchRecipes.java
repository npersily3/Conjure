package dev.conjure.content.block;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for the "workbench" recipe key schema stored in a {@link SlotDefinition}'s
 * {@code numbers}/{@code strings} maps. The generation pipeline calls {@link #write}; the block
 * entity calls {@link #of}; the JEI plugin calls {@link #all}. Keeping the keys in one place means
 * the writer and the two readers can never disagree.
 *
 * <h2>Keys</h2>
 * <ul>
 *   <li>{@code interaction} (str) — {@value #INTERACTION} marks a workbench block.
 *   <li>{@code grid_size} (num) — 0 processor, 4 = 2×2, 9 = 3×3.
 *   <li>{@code grid_0 .. grid_8} (str) — input item ids; a processor uses {@code grid_0} only.
 *   <li>{@code recipe_output} (str) — output item id.
 *   <li>{@code output_count} (num) — output stack size (≥ 1).
 *   <li>{@code fuel_item} (str) — fuel item id, "" = none.
 *   <li>{@code recipe_ticks} (num) — processing time in ticks.
 * </ul>
 *
 * <p>Legacy compatibility: slots written by the old "machine" agent ({@code interaction=="machine"}
 * with {@code recipe_input}) are read as a single-cell processor so existing dev saves keep working.
 */
public final class WorkbenchRecipes {

    /** {@code interaction} value identifying a workbench block. */
    public static final String INTERACTION = "workbench";
    /** Legacy {@code interaction} value from the pre-rename "machine" agent. */
    private static final String LEGACY_INTERACTION = "machine";

    private static final String K_INTERACTION = "interaction";
    private static final String K_GRID         = "grid_size";
    private static final String K_INPUT_PREFIX = "grid_";
    private static final String K_OUTPUT       = "recipe_output";
    private static final String K_OUTPUT_COUNT = "output_count";
    private static final String K_FUEL         = "fuel_item";
    private static final String K_TICKS        = "recipe_ticks";
    private static final String K_LEGACY_INPUT = "recipe_input";

    private WorkbenchRecipes() {}

    /** Whether {@code def} is a configured workbench (current or legacy "machine") slot. */
    public static boolean isWorkbench(SlotDefinition def) {
        if (!def.configured) return false;
        String interaction = def.str(K_INTERACTION, "");
        return INTERACTION.equals(interaction) || LEGACY_INTERACTION.equals(interaction);
    }

    /**
     * Parses the workbench recipe stored on {@code def}, or {@code null} if the slot is not a
     * workbench. Clamps {@code outputCount} and {@code ticks} to sane minimums.
     */
    public static WorkbenchRecipe of(SlotDefinition def) {
        if (!isWorkbench(def)) return null;

        boolean legacy = LEGACY_INTERACTION.equals(def.str(K_INTERACTION, ""));
        int gridSize = (int) def.num(K_GRID, 0);
        int cells = gridSize == 0 ? 1 : gridSize;

        List<String> inputs = new ArrayList<>(cells);
        for (int i = 0; i < cells; i++) {
            String v = def.str(K_INPUT_PREFIX + i, "");
            if (v.isBlank() && i == 0 && legacy) {
                v = def.str(K_LEGACY_INPUT, "");
            }
            inputs.add(v);
        }

        String output      = def.str(K_OUTPUT, "");
        int    outputCount = Math.max(1, (int) def.num(K_OUTPUT_COUNT, 1));
        String fuel        = def.str(K_FUEL, "");
        int    ticks       = Math.max(1, (int) def.num(K_TICKS, 100));

        return new WorkbenchRecipe(def.slotIndex, def.displayName, gridSize,
                inputs, output, outputCount, fuel, ticks);
    }

    /**
     * Writes a workbench recipe's fields onto {@code def}. {@code inputs} should hold one entry for
     * a processor ({@code gridSize == 0}) or {@code gridSize} entries for a 2×2 / 3×3 grid.
     */
    public static void write(SlotDefinition def, int gridSize, List<String> inputs,
                             String output, int outputCount, String fuel, int ticks) {
        def.strings.put(K_INTERACTION, INTERACTION);
        def.numbers.put(K_GRID, (double) gridSize);
        for (int i = 0; i < inputs.size(); i++) {
            def.strings.put(K_INPUT_PREFIX + i, inputs.get(i) == null ? "" : inputs.get(i));
        }
        def.strings.put(K_OUTPUT, output);
        def.numbers.put(K_OUTPUT_COUNT, (double) Math.max(1, outputCount));
        def.strings.put(K_FUEL, fuel == null ? "" : fuel);
        def.numbers.put(K_TICKS, (double) Math.max(1, ticks));
    }

    /** Every configured workbench recipe across the whole BLOCK pool (for the JEI plugin). */
    public static List<WorkbenchRecipe> all() {
        List<WorkbenchRecipe> out = new ArrayList<>();
        int pool = BlockArchetype.totalPool();
        for (int i = 0; i < pool; i++) {
            WorkbenchRecipe r = of(SlotRegistry.get(SlotKind.BLOCK, i));
            if (r != null) out.add(r);
        }
        return out;
    }
}
