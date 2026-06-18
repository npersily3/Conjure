package dev.conjure.content.block;

import dev.conjure.registry.ConjureMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Container menu for {@link ConjureBlockEntity}, laid out <em>dynamically</em> from the block's live
 * {@link WorkbenchRecipe}: one input slot per ingredient (1–9, wrapped 3 per row), an optional fuel
 * slot, and an output slot. The standard 3×9 inventory + hotbar sit below at the usual coordinates.
 *
 * <p>Both sides build the identical layout because both read the same recipe, so client and server
 * agree on slot count without any extra sync packet.
 */
public class ConjureMenu extends AbstractContainerMenu {

    /** Top-left of the input grid and inter-cell spacing. */
    private static final int INPUT_X = 44, INPUT_Y = 18, CELL = 18, PER_ROW = 3;
    private static final int OUTPUT_X = 134, OUTPUT_Y = 35;
    private static final int FUEL_X = 8, FUEL_Y = 35;

    private final ConjureBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Number of ingredient slots actually shown (= menu slot indices {@code 0..inputCount-1}). */
    private final int inputCount;
    /** Total workbench slots (inputs + optional fuel + output) before the player inventory. */
    private final int wbSlotCount;

    public ConjureMenu(int containerId, Inventory playerInventory, BlockPos pos, @Nullable Level level) {
        super(ConjureMenus.MACHINE_MENU.get(), containerId);

        ConjureBlockEntity be = null;
        if (level != null) {
            BlockEntity rawBe = level.getBlockEntity(pos);
            if (rawBe instanceof ConjureBlockEntity) {
                be = (ConjureBlockEntity) rawBe;
            }
        }
        this.blockEntity = be != null ? be : new ConjureBlockEntity(pos, level != null
                ? level.getBlockState(pos)
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        this.data   = blockEntity.getContainerData();
        this.access = (level != null)
                ? ContainerLevelAccess.create(level, pos)
                : ContainerLevelAccess.NULL;

        WorkbenchRecipe recipe = blockEntity.getRecipe();
        List<String> inputs = ConjureBlockEntity.activeInputs(recipe);
        this.inputCount = inputs.size();
        boolean hasFuel = recipe != null && recipe.requiresFuel();

        // Input cells (container indices 0..inputCount-1), restricted to their required item.
        for (int i = 0; i < inputCount; i++) {
            int col = i % PER_ROW, row = i / PER_ROW;
            final String required = inputs.get(i);
            this.addSlot(new Slot(blockEntity, i, INPUT_X + col * CELL, INPUT_Y + row * CELL) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return required.isBlank() || idOf(stack).equals(required);
                }
            });
        }

        // Optional fuel slot.
        if (hasFuel) {
            final String fuelId = recipe.fuel();
            this.addSlot(new Slot(blockEntity, ConjureBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return fuelId.isBlank() || idOf(stack).equals(fuelId);
                }
            });
        }

        // Output slot (take-only).
        this.addSlot(new Slot(blockEntity, ConjureBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        });

        this.wbSlotCount = inputCount + (hasFuel ? 1 : 0) + 1;

        // Player main inventory (rows 0–2)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ------------------------------------------------------------------
    // Data accessors for ConjureScreen
    // ------------------------------------------------------------------

    public int getProgress()    { return data.get(ConjureBlockEntity.DATA_PROGRESS); }
    public int getMaxProgress() { return data.get(ConjureBlockEntity.DATA_MAX); }

    // ------------------------------------------------------------------
    // Quick-move (shift-click)
    // ------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int invStart = wbSlotCount;
            int invEnd   = wbSlotCount + 36;

            if (index < wbSlotCount) {
                // From workbench → player inventory
                if (!this.moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
            } else {
                // From player → workbench input cells (slots accept only their required item)
                if (!this.moveItemStackTo(stack, 0, inputCount, false)) return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> blockEntity.stillValid(player), true);
    }

    public ConjureBlockEntity getBlockEntity() { return blockEntity; }
}
