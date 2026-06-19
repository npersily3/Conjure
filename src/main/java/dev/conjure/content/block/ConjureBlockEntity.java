package dev.conjure.content.block;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.registry.ConjureBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The block entity powering every MACHINE-archetype ConjureBlock (a "workbench").
 *
 * <p>The container is fixed-capacity but used dynamically: indices 0..{@value #MAX_INPUTS}-1 are
 * ingredient input cells, index {@value #SLOT_FUEL} is fuel, index {@value #SLOT_OUTPUT} is output.
 * The actual recipe is read live from the backing {@link SlotDefinition} via
 * {@link WorkbenchRecipes#of}: it uses one input cell per ingredient (1–9), an optional fuel item,
 * and produces {@code outputCount} of the output every {@code ticks} ticks once every ingredient
 * (and fuel, if required) is present. The menu/screen lay out only the cells the recipe uses, so the
 * GUI is dynamic and need not be a square grid.
 *
 * <p>Ticking is wired up by {@link ConjureBlock#getTicker}.
 */
public class ConjureBlockEntity extends BlockEntity implements Container, MenuProvider {

    /** Maximum ingredient cells (must match {@link MachineAgent}'s cap). */
    public static final int MAX_INPUTS  = 9;
    public static final int SLOT_FUEL   = MAX_INPUTS;      // index 9
    public static final int SLOT_OUTPUT = MAX_INPUTS + 1;  // index 10
    public static final int CONTAINER_SIZE = MAX_INPUTS + 2;

    // progress/max exposed to the menu via ContainerData
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_MAX      = 1;
    public static final int DATA_COUNT    = 2;

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    /** Crafting progress in ticks. */
    private int progress = 0;
    /** Cached recipe length in ticks (read from def on every tick; 0 = no recipe). */
    private int maxProgress = 0;

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
    // Recipe accessors (used by ConjureMenu for dynamic slot layout)
    // ------------------------------------------------------------------

    /** The live recipe for the block at this position, or {@code null} if none. */
    @Nullable
    public WorkbenchRecipe getRecipe() {
        return WorkbenchRecipes.of(getDefinition());
    }

    /**
     * The non-blank ingredient ids a recipe actually uses, capped at {@link #MAX_INPUTS}. Shared by
     * the tick loop and the menu so input cell indices line up. A {@code null} recipe yields one
     * generic cell so an unconfigured/legacy block still opens a usable 1-slot GUI.
     */
    public static List<String> activeInputs(@Nullable WorkbenchRecipe recipe) {
        if (recipe == null) return List.of("");
        List<String> out = new ArrayList<>();
        for (String id : recipe.inputs()) {
            if (id != null && !id.isBlank()) out.add(id);
            if (out.size() >= MAX_INPUTS) break;
        }
        if (out.isEmpty()) out.add("");
        return out;
    }

    // ------------------------------------------------------------------
    // MenuProvider
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        SlotDefinition def = getDefinition();
        return Component.literal(def.configured ? def.displayName : "Workbench");
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
    // Tick logic (workbench processing)
    // ------------------------------------------------------------------

    /**
     * Server-side tick. Called by {@link ConjureBlock#getTicker} for MACHINE blocks. Reads the
     * recipe from the slot's live {@link SlotDefinition} every tick so it reacts to hot-swapped
     * generation results without a restart.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ConjureBlockEntity be) {
        if (level.isClientSide) return;

        WorkbenchRecipe recipe = WorkbenchRecipes.of(be.getDefinition());
        if (recipe == null) { be.idle(); return; }

        List<String> reqs = activeInputs(recipe);
        be.maxProgress = recipe.ticks();

        // Every ingredient cell must hold its required item.
        for (int i = 0; i < reqs.size(); i++) {
            ItemStack stack = be.items.get(i);
            if (stack.isEmpty() || !matches(stack, reqs.get(i))) { be.resetProgress(); return; }
        }

        // Fuel gate. recipe.fuelAccepts handles both modes: "any" = any furnace fuel (charcoal,
        // planks, blaze rod…), or a specific id = a custom fuel the machine demands (e.g. a battery).
        if (recipe.requiresFuel()) {
            ItemStack fuel = be.items.get(SLOT_FUEL);
            if (!recipe.fuelAccepts(fuel)) { be.resetProgress(); return; }
        }

        // Output room.
        Item outItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.output()));
        if (outItem == Items.AIR) { be.resetProgress(); return; }
        int outCount = recipe.outputCount();
        ItemStack outStack = be.items.get(SLOT_OUTPUT);
        if (!outStack.isEmpty()) {
            if (!outStack.is(outItem)) return;
            if (outStack.getCount() + outCount > outStack.getMaxStackSize()) return;
        }

        // Advance and, on completion, consume one of each ingredient (+ fuel) and produce the output.
        be.progress++;
        be.setChanged();
        if (be.progress >= recipe.ticks()) {
            for (int i = 0; i < reqs.size(); i++) {
                ItemStack s = be.items.get(i);
                s.shrink(1);
                if (s.isEmpty()) be.items.set(i, ItemStack.EMPTY);
            }
            if (recipe.requiresFuel()) {
                ItemStack f = be.items.get(SLOT_FUEL);
                f.shrink(1);
                if (f.isEmpty()) be.items.set(SLOT_FUEL, ItemStack.EMPTY);
            }
            if (outStack.isEmpty()) be.items.set(SLOT_OUTPUT, new ItemStack(outItem, outCount));
            else outStack.grow(outCount);
            be.progress = 0;
            be.setChanged();
        }
    }

    private static boolean matches(ItemStack stack, String id) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
    }

    /** Stops processing but keeps the progress bar length (recipe present, ingredients missing). */
    private void resetProgress() {
        if (progress != 0) { progress = 0; setChanged(); }
    }

    /** No recipe at all: clear both progress and the bar length. */
    private void idle() {
        resetProgress();
        maxProgress = 0;
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    /** The SlotDefinition for the block currently at this position (resolved via its slot index). */
    private SlotDefinition getDefinition() {
        if (level == null) return SlotRegistry.get(SlotKind.BLOCK, 0);
        BlockState bs = level.getBlockState(worldPosition);
        if (bs.getBlock() instanceof ConjureBlock cb) {
            return cb.def();
        }
        return SlotRegistry.get(SlotKind.BLOCK, 0);
    }
}
