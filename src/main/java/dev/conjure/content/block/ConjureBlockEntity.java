package dev.conjure.content.block;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.registry.ConjureBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The block entity powering every MACHINE-archetype ConjureBlock.
 *
 * <p>Holds 3 inventory slots: slot 0 = input, slot 1 = fuel (unused by default, reserved for
 * future recipes that need a fuel), slot 2 = output. When the backing {@link SlotDefinition}
 * carries a recipe ({@code recipe_input}, {@code recipe_output}, {@code recipe_ticks} strings/
 * numbers), it acts as a simple furnace-style processor: consumes one item from slot 0 every
 * {@code recipe_ticks} ticks and pushes one output item into slot 2.
 *
 * <p>Ticking is wired up by {@link ConjureBlock#getTicker}.
 */
public class ConjureBlockEntity extends BlockEntity implements Container, MenuProvider {

    /** Slot indices. */
    public static final int SLOT_INPUT  = 0;
    public static final int SLOT_FUEL   = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int CONTAINER_SIZE = 3;

    // progress/max exposed to the menu via ContainerData
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX      = 1;
    public static final int DATA_COUNT    = 2;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    /** Crafting progress in ticks. */
    private int progress = 0;
    /** Cached recipe length in ticks (read from def on every tick; 0 = no recipe). */
    private int maxProgress = 0;

    /**
     * Syncs progress and max to the client side via the ContainerData protocol.
     * ContainerData is accessed by index; index 0 = progress, 1 = maxProgress.
     */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_MAX      -> maxProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_PROGRESS -> progress = value;
                case DATA_MAX      -> maxProgress = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ConjureBlockEntity(BlockPos pos, BlockState state) {
        super(ConjureBlockEntities.MACHINE_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // MenuProvider
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        SlotDefinition def = getDefinition();
        String name = def.configured ? def.displayName : "Machine";
        return Component.literal(name);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ConjureMenu(containerId, playerInventory, worldPosition, level);
    }

    // ------------------------------------------------------------------
    // Container — delegated to items list
    // ------------------------------------------------------------------

    @Override
    public int getContainerSize() { return CONTAINER_SIZE; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public int getMaxStackSize() { return 64; }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() { items.clear(); }

    // ------------------------------------------------------------------
    // ContainerData accessor for the menu
    // ------------------------------------------------------------------

    public ContainerData getContainerData() { return data; }

    // ------------------------------------------------------------------
    // NBT persistence
    // ------------------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        progress    = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("Progress",    progress);
        tag.putInt("MaxProgress", maxProgress);
    }

    // ------------------------------------------------------------------
    // Tick logic (furnace-style processing)
    // ------------------------------------------------------------------

    /**
     * Server-side tick. Called by {@link ConjureBlock#getTicker} for MACHINE blocks.
     * Reads the recipe from the slot's live {@link SlotDefinition} every tick so it
     * reacts to hot-swapped generation results without a restart.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ConjureBlockEntity be) {
        if (level.isClientSide) return;

        SlotDefinition def = be.getDefinition();
        if (!def.configured) return;

        String recipeInput  = def.str("recipe_input",  "");
        String recipeOutput = def.str("recipe_output", "");
        int    recipeTicks  = (int) def.num("recipe_ticks", 0);

        if (recipeInput.isBlank() || recipeOutput.isBlank() || recipeTicks <= 0) {
            // No recipe: reset progress so the bar doesn't show stale state.
            if (be.progress != 0) { be.progress = 0; be.setChanged(); }
            be.maxProgress = 0;
            return;
        }

        be.maxProgress = recipeTicks;

        // Check input slot has the required item
        ItemStack inputStack = be.items.get(SLOT_INPUT);
        if (inputStack.isEmpty()) {
            if (be.progress != 0) { be.progress = 0; be.setChanged(); }
            return;
        }

        // Match by registry name
        String inputId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(inputStack.getItem()).toString();
        if (!inputId.equals(recipeInput)) {
            if (be.progress != 0) { be.progress = 0; be.setChanged(); }
            return;
        }

        // Check output slot has room
        ItemStack outputStack = be.items.get(SLOT_OUTPUT);
        net.minecraft.world.item.Item outputItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.parse(recipeOutput));
        if (outputItem == net.minecraft.world.item.Items.AIR) {
            if (be.progress != 0) { be.progress = 0; be.setChanged(); }
            return;
        }

        if (!outputStack.isEmpty()) {
            if (!outputStack.is(outputItem)) return; // different item in output
            if (outputStack.getCount() >= outputStack.getMaxStackSize()) return; // full
        }

        // Advance
        be.progress++;
        be.setChanged();

        if (be.progress >= recipeTicks) {
            // Consume one input
            inputStack.shrink(1);
            if (inputStack.isEmpty()) be.items.set(SLOT_INPUT, ItemStack.EMPTY);

            // Produce one output
            if (outputStack.isEmpty()) {
                be.items.set(SLOT_OUTPUT, new ItemStack(outputItem));
            } else {
                outputStack.grow(1);
            }

            be.progress = 0;
            be.setChanged();
        }
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    /**
     * Looks up the SlotDefinition for the block currently at this position.
     * The ConjureBlock keeps a slotIndex field; we resolve it via the block state.
     */
    private SlotDefinition getDefinition() {
        if (level == null) return SlotRegistry.get(SlotKind.BLOCK, 0);
        BlockState bs = level.getBlockState(worldPosition);
        if (bs.getBlock() instanceof ConjureBlock cb) {
            return cb.def();
        }
        return SlotRegistry.get(SlotKind.BLOCK, 0);
    }
}
