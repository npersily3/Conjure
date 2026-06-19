package dev.conjure.content.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

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
 * @param fuel        what the fuel slot requires per craft: {@code ""} = no fuel,
 *                    {@value #FUEL_ANY} = any furnace-burnable item (Minecraft convention), or a
 *                    specific item id = a custom fuel (e.g. a mod's own battery)
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

    /** Sentinel {@link #fuel} value meaning "any furnace-burnable item" (the Minecraft convention). */
    public static final String FUEL_ANY = "any";

    /** A 1-input → 1-output furnace-style processor (no crafting grid). */
    public boolean isProcessor() {
        return gridSize == 0;
    }

    /** Whether this recipe consumes a fuel item to run. */
    public boolean requiresFuel() {
        return fuel != null && !fuel.isBlank();
    }

    /**
     * Whether {@code stack} is valid in the fuel slot. {@link #FUEL_ANY} (and {@code "*"}) accept any
     * furnace-burnable item; anything else requires that exact item id, so a mod can demand a custom
     * fuel (e.g. its own battery). The single source of truth for both the tick loop and the menu.
     */
    public boolean fuelAccepts(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (FUEL_ANY.equalsIgnoreCase(fuel) || "*".equals(fuel)) {
            return stack.getBurnTime(null) > 0;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(fuel);
    }
}
