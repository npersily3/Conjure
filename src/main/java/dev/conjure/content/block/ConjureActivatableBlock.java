package dev.conjure.content.block;

import com.mojang.logging.LogUtils;
import dev.conjure.Config;
import dev.conjure.content.SlotDefinition;
import dev.conjure.script.ScriptContext;
import dev.conjure.script.ScriptException;
import dev.conjure.script.ScriptRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

/**
 * A {@link ConjureBlock} that carries one runtime-toggleable on/off state — the pre-registered
 * way to ship doors, lamps, switches, safes and other "stateful" blocks despite the registry
 * freeze (state properties are baked at registration, so they cannot be added per-slot later).
 *
 * <p>The {@link #ACTIVE} property drives two things: a blockstate variant swap (closed↔open /
 * unlit↔lit texture, written by {@code DynamicPackManager.writeActivatableAssets}) and, when the
 * slot opts in via the {@code activeLight} tunable, the emitted light level.
 *
 * <p>Right-click behavior:
 * <ul>
 *   <li>a slot with a behavior script runs it (the script decides whether to toggle, via
 *       {@code ctx.setBlockActive}) — this is how a safe stays locked until the right key;
 *   <li>a slot without a script simply toggles {@link #ACTIVE} — a plain lamp/switch.
 * </ul>
 */
public class ConjureActivatableBlock extends ConjureBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** On/off: open/closed for a door, lit/unlit for a lamp, unlocked/locked for a safe. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ConjureActivatableBlock(BlockArchetype archetype, int slotIndex,
                                   BlockBehaviour.Properties properties) {
        super(archetype, slotIndex, properties);
        registerDefaultState(getStateDefinition().any().setValue(ACTIVE, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    /** Light level when switched on, from the slot's {@code activeLight} tunable (default 13); 0 when off. */
    public int activeLight(BlockState state) {
        return state.getValue(ACTIVE) ? (int) Math.max(0, Math.min(15, def().num("activeLight", 13))) : 0;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {

        SlotDefinition d = def();
        if (!d.configured || !Config.INTERACTIVITY_ENABLED.get()) {
            return InteractionResult.PASS;
        }

        // Client side: report the swing was consumed; the server does the real work.
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        String scriptId = d.behaviorScriptId;
        if (scriptId != null && !scriptId.isBlank()) {
            // Scripted: the script decides whether/how to toggle (e.g. only with a key in hand).
            ScriptContext ctx = new ScriptContext(level, player, InteractionHand.MAIN_HAND,
                    pos, hitResult.getDirection());
            try {
                ScriptRuntime.get().run(scriptId, ctx);
            } catch (ScriptException e) {
                LOGGER.error("[Conjure] Script error for block slot {} (scriptId='{}'): {}",
                        slotIndex, scriptId, e.getMessage(), e);
                dev.conjure.script.ScriptErrorLog.record(scriptId, e.getMessage());
                player.displayClientMessage(
                        Component.literal("[Conjure] Script error: " + e.getMessage()), false);
                return InteractionResult.FAIL;
            }
            return InteractionResult.CONSUME;
        }

        // No script: a plain toggle block (lamp / switch).
        level.setBlockAndUpdate(pos, state.cycle(ACTIVE));
        return InteractionResult.CONSUME;
    }
}
