package dev.conjure.compat.jei;

import dev.conjure.content.block.WorkbenchRecipe;
import dev.conjure.content.block.WorkbenchRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * JEI integration for Conjure. Registers the {@link WorkbenchRecipeCategory} and feeds it every
 * configured workbench recipe ({@link WorkbenchRecipes#all()}), and registers each workbench block
 * as a catalyst so it shows its recipe in JEI.
 *
 * <p>JEI re-runs this registration whenever the client reloads resources, and Conjure reloads
 * resources after each generation, so live-conjured workbenches show up without a relaunch.
 */
@JeiPlugin
public class ConjureJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("conjure", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() { return UID; }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new WorkbenchRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(WorkbenchRecipeCategory.TYPE, WorkbenchRecipes.all());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (WorkbenchRecipe recipe : WorkbenchRecipes.all()) {
            Item blockItem = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("conjure", "block_slot_" + recipe.slotIndex()));
            if (blockItem != Items.AIR) {
                registration.addRecipeCatalyst(new ItemStack(blockItem), WorkbenchRecipeCategory.TYPE);
            }
        }
    }
}
