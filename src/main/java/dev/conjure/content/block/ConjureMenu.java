package dev.conjure.content.block;

import dev.conjure.registry.ConjureMenus;
import net.minecraft.core.BlockPos;
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

/**
 * Container menu for {@link ConjureBlockEntity}.
 *
 * <p>Layout (3 machine slots + standard 3×9 + hotbar):
 * <ul>
 *   <li>Slot 0 — input   (x=56, y=35)
 *   <li>Slot 1 — fuel    (x=56, y=53) — reserved, not displayed by default
 *   <li>Slot 2 — output  (x=116, y=35)
 *   <li>Slots 3–29 — player main inventory
 *   <li>Slots 30–38 — hotbar
 * </ul>
 */
public class ConjureMenu extends AbstractContainerMenu {

    public static final int MACHINE_SLOTS = ConjureBlockEntity.CONTAINER_SIZE;
    public static final int PLAYER_INV_START = MACHINE_SLOTS;
    public static final int PLAYER_INV_END   = PLAYER_INV_START + 27;
    public static final int HOTBAR_START     = PLAYER_INV_END;
    public static final int HOTBAR_END       = HOTBAR_START + 9;

    private final ConjureBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Server-side constructor (opens a real block entity). */
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

        // Machine slots
        this.addSlot(new Slot(blockEntity, ConjureBlockEntity.SLOT_INPUT,  56, 35));
        this.addSlot(new Slot(blockEntity, ConjureBlockEntity.SLOT_FUEL,   56, 53));
        this.addSlot(new Slot(blockEntity, ConjureBlockEntity.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; } // output only
        });

        // Player main inventory (rows 0–2)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory,
                        col + row * 9 + 9,
                        8 + col * 18,
                        84 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
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

            if (index < MACHINE_SLOTS) {
                // From machine → player inventory
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From player → machine input slot (slot 0)
                if (!this.moveItemStackTo(stack, ConjureBlockEntity.SLOT_INPUT, ConjureBlockEntity.SLOT_INPUT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate(
                (level, pos) -> blockEntity.stillValid(player),
                true);
    }

    public ConjureBlockEntity getBlockEntity() { return blockEntity; }
}
