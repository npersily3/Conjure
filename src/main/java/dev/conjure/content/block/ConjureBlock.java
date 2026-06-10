package dev.conjure.content.block;

import com.mojang.logging.LogUtils;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.script.ScriptContext;
import dev.conjure.script.ScriptException;
import dev.conjure.script.ScriptRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

/**
 * A pre-registered block shell backed by a {@link SlotDefinition}. The archetype it was built
 * from (its {@link BlockBehaviour.Properties}) is fixed; texture, name, behavior and collision
 * shape are resolved at runtime. The matching {@link net.minecraft.world.item.BlockItem} is
 * registered alongside it in the item pool.
 */
public class ConjureBlock extends Block {

    private static final Logger LOGGER = LogUtils.getLogger();

    public final BlockArchetype archetype;
    private final int slotIndex;

    public ConjureBlock(BlockArchetype archetype, int slotIndex, BlockBehaviour.Properties properties) {
        super(properties);
        this.archetype = archetype;
        this.slotIndex = slotIndex;
    }

    public SlotDefinition def() {
        return SlotRegistry.get(SlotKind.BLOCK, slotIndex);
    }

    /**
     * Right-click interaction without a held item.
     *
     * <p>Wires into the 1.21.1 Block interaction pipeline:
     * {@code BlockBehaviour#useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)}
     * is the correct override point for bare right-click (no item held).
     * Server-side only; client-side gets {@link InteractionResult#SUCCESS}.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {

        SlotDefinition d = def();
        if (!d.configured) {
            return InteractionResult.PASS;
        }

        // Client side: return SUCCESS so the arm swings but do not execute the script.
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        String scriptId = d.behaviorScriptId;
        if (scriptId == null || scriptId.isBlank()) {
            // Slot configured but no behavior script assigned yet.
            return InteractionResult.PASS;
        }

        // Build ctx with the player's main hand — blocks do not have a meaningful hand.
        ScriptContext ctx = new ScriptContext(level, player, InteractionHand.MAIN_HAND);
        try {
            ScriptRuntime.get().run(scriptId, ctx);
        } catch (ScriptException e) {
            LOGGER.error("[Conjure] Script error for block slot {} (scriptId='{}'): {}",
                    slotIndex, scriptId, e.getMessage(), e);
            player.displayClientMessage(
                    Component.literal("[Conjure] Script error: " + e.getMessage()),
                    false
            );
            return InteractionResult.FAIL;
        }

        return InteractionResult.SUCCESS;
    }
}
