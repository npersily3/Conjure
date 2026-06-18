package dev.conjure.content.block;

import java.util.List;

/**
 * Immutable description of a generated "workbench" block's recipe — the shared contract between
 * the generation pipeline (which writes it), the {@link ConjureBlockEntity} (which runs it), and
 * the JEI plugin (which displays it). Built only via {@link WorkbenchRecipes#of}; the key schema
 * lives entirely in {@link WorkbenchRecipes} so writers and readers cannot drift.
 *
 * @param slotIndex   the BLOCK slot this recipe belongs to
 * @param displayName the block's player-facing name (recipe title in JEI)
 * @param gridSize    0 = processor (single input → output over time), 4 = 2×2, 9 = 3×3
 * @param inputs      item ids; size 1 for a processor, else {@code gridSize} cells ("" = empty)
 * @param output      output item id
 * @param outputCount how many of {@link #output} per craft (≥ 1)
 * @param fuel        fuel item id consumed per craft, or "" if the recipe needs no fuel
 * @param ticks       processing time in ticks (20 = 1 second)
 */
public record WorkbenchRecipe(
        int slotIndex,
        String displayName,
        int gridSize,
        List<String> inputs,
        String output,
        int outputCount,
        String fuel,
        int ticks) {

    /** A 1-input → 1-output furnace-style processor (no crafting grid). */
    public boolean isProcessor() {
        return gridSize == 0;
    }

    /** Whether this recipe consumes a fuel item to run. */
    public boolean requiresFuel() {
        return fuel != null && !fuel.isBlank();
    }
}
