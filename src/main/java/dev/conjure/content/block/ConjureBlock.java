package dev.conjure.content.block;

import com.mojang.logging.LogUtils;
import dev.conjure.Config;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.registry.ConjureBlockEntities;
import dev.conjure.script.ScriptContext;
import dev.conjure.script.ScriptException;
import dev.conjure.script.ScriptRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * A pre-registered block shell backed by a {@link SlotDefinition}. The archetype it was built
 * from (its {@link BlockBehaviour.Properties}) is fixed; texture, name, behavior and collision
 * shape are resolved at runtime. The matching {@link dev.conjure.content.item.ConjureBlockItem} is
 * registered alongside it in the item pool.
 *
 * <p>MACHINE-archetype blocks also implement {@link EntityBlock} and return a
 * {@link ConjureBlockEntity}; other archetypes return {@code null} from {@link #newBlockEntity}.
 */
public class ConjureBlock extends Block implements EntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    public final BlockArchetype archetype;
    public final int slotIndex;

    public ConjureBlock(BlockArchetype archetype, int slotIndex, BlockBehaviour.Properties properties) {
        super(properties);
        this.archetype = archetype;
        this.slotIndex = slotIndex;
    }

    public SlotDefinition def() {
        return SlotRegistry.get(SlotKind.BLOCK, slotIndex);
    }

    // ------------------------------------------------------------------
    // Display name
    // ------------------------------------------------------------------

    @Override
    public MutableComponent getName() {
        SlotDefinition d = def();
        return Component.literal(d.configured ? d.displayName : "Empty Block Slot #" + slotIndex);
    }

    // ------------------------------------------------------------------
    // EntityBlock — BlockEntity support (MACHINE only)
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (archetype == BlockArchetype.MACHINE) {
            return new ConjureBlockEntity(pos, state);
        }
        return null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (!level.isClientSide && archetype == BlockArchetype.MACHINE) {
            return createTickerHelper(blockEntityType,
                    ConjureBlockEntities.MACHINE_BLOCK_ENTITY.get(),
                    ConjureBlockEntity::serverTick);
        }
        return null;
    }

    /**
     * Helper matching BaseEntityBlock.createTickerHelper — returns the ticker only if
     * the queried type matches the expected type, preventing a ClassCastException.
     */
    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> beType,
            BlockEntityType<E> expectedType,
            BlockEntityTicker<? super E> ticker) {
        return expectedType == beType ? (BlockEntityTicker<A>) ticker : null;
    }

    // ------------------------------------------------------------------
    // Right-click interaction
    // ------------------------------------------------------------------

    /**
     * Right-click interaction without a held item.
     *
     * <p>Wires into the 1.21.1 Block interaction pipeline:
     * {@code BlockBehaviour#useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)}
     * is the correct override point for bare right-click (no item held).
     *
     * <p>With {@link Config#INTERACTIVITY_ENABLED} on:
     * <ul>
     *   <li>{@code interaction=machine} → opens the ConjureBlockEntity menu
     *   <li>{@code interaction=script} → runs the behaviorScriptId in Rhino
     *   <li>anything else / not set → PASS
     * </ul>
     * When interactivity is disabled, all blocks fall through to PASS.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {

        SlotDefinition d = def();
        if (!d.configured) {
            return InteractionResult.PASS;
        }

        if (!Config.INTERACTIVITY_ENABLED.get()) {
            // Interactivity globally off: treat as plain block.
            return InteractionResult.PASS;
        }

        String interaction = d.str("interaction", "");

        if (WorkbenchRecipes.isWorkbench(d)) {
            // Client side: tell MC the interaction was consumed (arm swing).
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            // Server side: open the workbench GUI.
            if (player instanceof ServerPlayer sp) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof ConjureBlockEntity cbe) {
                    sp.openMenu(cbe, buf -> buf.writeBlockPos(pos));
                }
            }
            return InteractionResult.CONSUME;
        }

        if ("script".equals(interaction)) {
            // Client side: arm swing only.
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            String scriptId = d.behaviorScriptId;
            if (scriptId == null || scriptId.isBlank()) {
                return InteractionResult.PASS;
            }
            ScriptContext ctx = new ScriptContext(level, player, InteractionHand.MAIN_HAND);
            try {
                ScriptRuntime.get().run(scriptId, ctx);
            } catch (ScriptException e) {
                LOGGER.error("[Conjure] Script error for block slot {} (scriptId='{}'): {}",
                        slotIndex, scriptId, e.getMessage(), e);
                dev.conjure.script.ScriptErrorLog.record(scriptId, e.getMessage());
                player.displayClientMessage(
                        Component.literal("[Conjure] Script error: " + e.getMessage()),
                        false
                );
                return InteractionResult.FAIL;
            }
            return InteractionResult.SUCCESS;
        }

        // interaction="" or unrecognised value: plain block, no interaction.
        return InteractionResult.PASS;
    }

}
