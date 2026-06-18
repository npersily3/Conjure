package dev.conjure.compat.jei;

import dev.conjure.content.block.WorkbenchRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * JEI category for Conjure "workbench" blocks. Each {@link WorkbenchRecipe} is rendered with one
 * input slot per ingredient (wrapped 3 per row), an optional fuel slot, and an output slot, mirroring
 * the in-game dynamic GUI. The recipe's block name and processing time are drawn as text.
 *
 * <p>The set of recipes is supplied by {@link dev.conjure.content.block.WorkbenchRecipes#all()} in
 * {@link ConjureJeiPlugin}. Because JEI re-runs plugin registration on a client resource reload (and
 * Conjure triggers exactly that after every generation), newly conjured workbenches appear here
 * without a relaunch.
 */
public class WorkbenchRecipeCategory implements IRecipeCategory<WorkbenchRecipe> {

    public static final RecipeType<WorkbenchRecipe> TYPE =
            RecipeType.create("conjure", "workbench", WorkbenchRecipe.class);

    private static final int WIDTH = 150, HEIGHT = 92;
    private static final int INPUT_X = 6, INPUT_Y = 16, CELL = 18, PER_ROW = 3;
    private static final int OUTPUT_X = 124, OUTPUT_Y = 34;
    private static final int FUEL_X = 6, FUEL_Y = 72;

    private final IDrawable background;
    private final IDrawable icon;

    public WorkbenchRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Items.CRAFTING_TABLE));
    }

    @Override
    public RecipeType<WorkbenchRecipe> getRecipeType() { return TYPE; }

    @Override
    public Component getTitle() { return Component.literal("Conjure Workbenches"); }

    @Override
    public IDrawable getIcon() { return icon; }

    @Override
    public int getWidth()  { return WIDTH; }

    @Override
    public int getHeight() { return HEIGHT; }

    @SuppressWarnings("removal")
    @Override
    public IDrawable getBackground() { return background; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WorkbenchRecipe recipe, IFocusGroup focuses) {
        List<String> inputs = recipe.inputs();
        int cell = 0;
        for (String id : inputs) {
            if (id == null || id.isBlank()) continue;
            int col = cell % PER_ROW, row = cell / PER_ROW;
            builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X + col * CELL, INPUT_Y + row * CELL)
                    .addItemStack(stackFor(id, 1));
            cell++;
        }
        if (recipe.requiresFuel()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, FUEL_X, FUEL_Y)
                    .addItemStack(stackFor(recipe.fuel(), 1));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .addItemStack(stackFor(recipe.output(), recipe.outputCount()));
    }

    @Override
    public void draw(WorkbenchRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slotsView,
                     GuiGraphics g, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, recipe.displayName(), 4, 2, 0x404040, false);
        g.drawString(font, "→", OUTPUT_X - 16, OUTPUT_Y + 4, 0x404040, false); // arrow → output
        String time = String.format("%.1fs", recipe.ticks() / 20.0);
        g.drawString(font, time, OUTPUT_X - 20, OUTPUT_Y + 22, 0x808080, false);
        if (recipe.requiresFuel()) {
            g.drawString(font, "fuel", FUEL_X + CELL + 2, FUEL_Y + 5, 0x808080, false);
        }
    }

    private static ItemStack stackFor(String id, int count) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
