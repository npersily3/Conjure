package dev.conjure.script;

import com.mojang.logging.LogUtils;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.block.ConjureActivatableBlock;
import dev.conjure.content.item.ConjureBlockItem;
import dev.conjure.content.item.ConjureItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * The whitelisted host object injected into every Conjure behavior script as {@code ctx}.
 *
 * <p>This class is intentionally narrow — it exposes only safe, gameplay-level operations
 * to the AI-authored script. No reflection, no IO, no class-loading. The Rhino ClassShutter
 * in {@link ScriptRuntime} additionally prevents scripts from reaching any Java class at all,
 * so only what is explicitly passed as a script binding is reachable.
 *
 * <p>All methods are server-side only; callers must guard with {@code !level.isClientSide}
 * before constructing one of these.
 *
 * <p>A context may carry an optional <b>target block</b> (the block a {@code useOn} script
 * clicked, or the block running its own script) and an optional <b>target entity</b> (the mob
 * a weapon hit). Verbs that need one no-op when it is absent, so a script written for one hook
 * is harmless if reused in another.
 */
public final class ScriptContext {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Level level;
    private final Player player;
    private final InteractionHand hand;

    /** Block this script acts on: the clicked block (useOn) or the block's own position. */
    @Nullable private final BlockPos targetPos;
    /** Face of {@link #targetPos} that was clicked, for placing against (useOn only). */
    @Nullable private final Direction targetFace;
    /** Mob a weapon script hit (hurtEnemy hook). */
    @Nullable private final LivingEntity targetEntity;

    /** Right-click-air item script (no block/entity target). */
    public ScriptContext(Level level, Player player, InteractionHand hand) {
        this(level, player, hand, null, null, null);
    }

    /** Block script or item-used-on-block script: knows the targeted block + clicked face. */
    public ScriptContext(Level level, Player player, InteractionHand hand,
                         @Nullable BlockPos targetPos, @Nullable Direction targetFace) {
        this(level, player, hand, targetPos, targetFace, null);
    }

    /** Weapon script: knows the mob it hit. */
    public ScriptContext(Level level, Player player, InteractionHand hand,
                         @Nullable LivingEntity targetEntity) {
        this(level, player, hand, null, null, targetEntity);
    }

    private ScriptContext(Level level, Player player, InteractionHand hand,
                          @Nullable BlockPos targetPos, @Nullable Direction targetFace,
                          @Nullable LivingEntity targetEntity) {
        this.level = level;
        this.player = player;
        this.hand = hand;
        this.targetPos = targetPos;
        this.targetFace = targetFace;
        this.targetEntity = targetEntity;
    }

    // -------------------------------------------------------------------------
    // Messaging / info
    // -------------------------------------------------------------------------

    /** Send a chat message to the interacting player. */
    public void message(String text) {
        player.displayClientMessage(Component.literal(text), false);
    }

    /** Returns the player's display name. */
    public String getPlayerName() {
        return player.getName().getString();
    }

    // -------------------------------------------------------------------------
    // Inventory / status effects (player)
    // -------------------------------------------------------------------------

    /**
     * Give {@code count} items with registry id {@code itemId} to the player.
     * Unknown ids are ignored; count is clamped to 1–64; overflow drops at the player's feet.
     */
    public void giveItem(String itemId, int count) {
        Item item = item(itemId);
        if (item == null) return;
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(count, 64)));
        if (!player.addItem(stack)) player.drop(stack, false);
    }

    /** Restore {@code amount} HP to the player (2 = 1 heart). Negative ignored. */
    public void heal(double amount) {
        if (amount > 0) player.heal((float) amount);
    }

    /** Deal {@code amount} magic damage to the player (2 = 1 heart). */
    public void damage(double amount) {
        if (amount > 0) player.hurt(player.damageSources().magic(), (float) amount);
    }

    /** Apply a potion effect to the player. {@code amp} 0 = level I; clamped 0–4, 1–600 s. */
    public void giveEffect(String effectId, int seconds, int amplifier) {
        MobEffectInstance fx = effect(effectId, seconds, amplifier);
        if (fx != null) player.addEffect(fx);
    }

    /** Set the player on fire for {@code seconds} (clamped 0–60). */
    public void ignite(int seconds) {
        player.setRemainingFireTicks(Math.max(0, Math.min(seconds, 60)) * 20);
    }

    /** Consume one of the held item (single-use). Creative players are unaffected. */
    public void consumeHeld() {
        if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
    }

    // -------------------------------------------------------------------------
    // Movement (player) — velocity is resynced to the client so it actually moves
    // -------------------------------------------------------------------------

    /** Launch the player straight up. {@code power} clamped 0–4 (≈ jump-boost to firework). */
    public void launch(double power) {
        addMotion(0, Math.max(0, Math.min(power, 4)), 0);
    }

    /** Dash the player in their look direction. {@code power} clamped 0–4, with a small lift. */
    public void dashForward(double power) {
        double p = Math.max(0, Math.min(power, 4));
        Vec3 look = player.getLookAngle();
        addMotion(look.x * p, Math.max(0.1, look.y * p * 0.5 + 0.2), look.z * p);
    }

    // -------------------------------------------------------------------------
    // Combat — act on the mob a weapon hit (no-op if there is none)
    // -------------------------------------------------------------------------

    /** Deal extra {@code amount} damage to the hit mob (2 = 1 heart). */
    public void damageTarget(double amount) {
        if (targetEntity != null && amount > 0) {
            targetEntity.hurt(player.damageSources().playerAttack(player), (float) amount);
        }
    }

    /** Set the hit mob on fire for {@code seconds} (clamped 0–60). */
    public void igniteTarget(int seconds) {
        if (targetEntity != null) {
            targetEntity.setRemainingFireTicks(Math.max(0, Math.min(seconds, 60)) * 20);
        }
    }

    /** Knock the hit mob away from the player. {@code power} clamped 0–4. */
    public void knockbackTarget(double power) {
        if (targetEntity != null) {
            double p = Math.max(0, Math.min(power, 4));
            targetEntity.knockback(p, player.getX() - targetEntity.getX(),
                    player.getZ() - targetEntity.getZ());
        }
    }

    /** Apply a potion effect to the hit mob. {@code amp} 0 = level I; clamped 0–4, 1–600 s. */
    public void effectTarget(String effectId, int seconds, int amplifier) {
        MobEffectInstance fx = effect(effectId, seconds, amplifier);
        if (targetEntity != null && fx != null) targetEntity.addEffect(fx);
    }

    // -------------------------------------------------------------------------
    // World effects
    // -------------------------------------------------------------------------

    /** Spawn a lightning bolt at the player (visual + fire/damage as vanilla). */
    public void lightning() {
        if (level instanceof ServerLevel sl) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
            if (bolt != null) {
                bolt.moveTo(player.getX(), player.getY(), player.getZ());
                sl.addFreshEntity(bolt);
            }
        }
    }

    /** Explosion at the player that damages entities but never breaks blocks. {@code power} 0–8. */
    public void explode(double power) {
        float r = (float) Math.max(0, Math.min(power, 8));
        level.explode(player, player.getX(), player.getY(), player.getZ(), r,
                Level.ExplosionInteraction.NONE);
    }

    /** Burst of HEART particles above the player. */
    public void spawnParticleHere() {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5, player.getZ(),
                    8, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /** Play a sound at the player. Unknown ids are ignored. */
    public void playSound(String soundId) {
        ResourceLocation loc = ResourceLocation.tryParse(soundId);
        if (loc == null) { LOGGER.warn("[Conjure script] playSound: invalid id '{}'", soundId); return; }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(loc);
        if (sound == null) { LOGGER.warn("[Conjure script] playSound: unknown sound '{}'", soundId); return; }
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    // -------------------------------------------------------------------------
    // Target block — the clicked block (useOn) or the block running this script.
    // The on/off state only exists on ACTIVATABLE blocks (doors, lamps, safes…).
    // -------------------------------------------------------------------------

    /** True when this script was invoked against a block (a useOn item or a block's own script). */
    public boolean hasTargetBlock() {
        return targetPos != null;
    }

    /** True when this script was invoked by a weapon hitting a mob. */
    public boolean hasTargetEntity() {
        return targetEntity != null;
    }

    /** True if the target block is an ACTIVATABLE block currently switched on (open/lit/unlocked). */
    public boolean getBlockActive() {
        if (targetPos == null) return false;
        BlockState st = level.getBlockState(targetPos);
        return st.hasProperty(ConjureActivatableBlock.ACTIVE) && st.getValue(ConjureActivatableBlock.ACTIVE);
    }

    /** Switch the target ACTIVATABLE block on/off (open/close a door, lock/unlock a safe). No-op otherwise. */
    public void setBlockActive(boolean active) {
        if (targetPos == null) return;
        BlockState st = level.getBlockState(targetPos);
        if (st.getBlock() instanceof ConjureActivatableBlock && st.hasProperty(ConjureActivatableBlock.ACTIVE)) {
            level.setBlockAndUpdate(targetPos, st.setValue(ConjureActivatableBlock.ACTIVE, active));
            level.playSound(null, targetPos,
                    net.minecraft.sounds.SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f,
                    active ? 0.9f : 0.5f);
        }
    }

    /** Break the target block, dropping its items. No-op if there is no target. */
    public void breakTargetBlock() {
        if (targetPos != null) level.destroyBlock(targetPos, true);
    }

    /** Replace the target block with {@code blockId} (e.g. "minecraft:gold_block"). No-op if invalid. */
    public void setTargetBlock(String blockId) {
        if (targetPos == null) return;
        Block block = block(blockId);
        if (block != null) level.setBlockAndUpdate(targetPos, block.defaultBlockState());
    }

    /** Place {@code blockId} against the clicked face, only if that space is empty. */
    public void placeOnFace(String blockId) {
        if (targetPos == null || targetFace == null) return;
        Block block = block(blockId);
        if (block == null) return;
        BlockPos at = targetPos.relative(targetFace);
        if (level.getBlockState(at).canBeReplaced()) {
            level.setBlockAndUpdate(at, block.defaultBlockState());
        }
    }

    // -------------------------------------------------------------------------
    // Cross-object tunables — let an item react to the block it clicked and vice
    // versa (e.g. a key whose "keyId" must match the safe's "keyId").
    // -------------------------------------------------------------------------

    /** A string tunable on the held item's slot definition, or "" if absent. */
    public String heldStr(String key) {
        SlotDefinition d = heldDef();
        return d == null ? "" : d.str(key, "");
    }

    /** A number tunable on the held item's slot definition, or 0 if absent. */
    public double heldNum(String key) {
        SlotDefinition d = heldDef();
        return d == null ? 0 : d.num(key, 0);
    }

    /** A string tunable on the target block's slot definition, or "" if absent. */
    public String targetBlockStr(String key) {
        SlotDefinition d = targetBlockDef();
        return d == null ? "" : d.str(key, "");
    }

    /** A number tunable on the target block's slot definition, or 0 if absent. */
    public double targetBlockNum(String key) {
        SlotDefinition d = targetBlockDef();
        return d == null ? 0 : d.num(key, 0);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void addMotion(double dx, double dy, double dz) {
        Vec3 v = player.getDeltaMovement().add(dx, dy, dz);
        player.setDeltaMovement(v);
        player.hurtMarked = true;
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
    }

    @Nullable
    private SlotDefinition heldDef() {
        Item item = player.getItemInHand(hand).getItem();
        if (item instanceof ConjureItem ci) return ci.slotDef();
        if (item instanceof ConjureBlockItem cbi) return cbi.slotDef();
        return null;
    }

    @Nullable
    private SlotDefinition targetBlockDef() {
        if (targetPos == null) return null;
        Block b = level.getBlockState(targetPos).getBlock();
        return (b instanceof dev.conjure.content.block.ConjureBlock cb) ? cb.def() : null;
    }

    @Nullable
    private static Item item(String itemId) {
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) { LOGGER.warn("[Conjure script] invalid item id '{}'", itemId); return null; }
        Item item = BuiltInRegistries.ITEM.get(loc);
        if (item == null || item == Items.AIR) { LOGGER.warn("[Conjure script] unknown item '{}'", itemId); return null; }
        return item;
    }

    @Nullable
    private static Block block(String blockId) {
        ResourceLocation loc = ResourceLocation.tryParse(blockId);
        if (loc == null) { LOGGER.warn("[Conjure script] invalid block id '{}'", blockId); return null; }
        Block block = BuiltInRegistries.BLOCK.get(loc);
        return block == net.minecraft.world.level.block.Blocks.AIR ? null : block;
    }

    @Nullable
    private static MobEffectInstance effect(String effectId, int seconds, int amplifier) {
        ResourceLocation loc = ResourceLocation.tryParse(effectId);
        if (loc == null) { LOGGER.warn("[Conjure script] giveEffect: invalid id '{}'", effectId); return null; }
        Holder<MobEffect> e = BuiltInRegistries.MOB_EFFECT.getHolder(loc).orElse(null);
        if (e == null) { LOGGER.warn("[Conjure script] giveEffect: unknown effect '{}'", effectId); return null; }
        int ticks = Math.max(1, Math.min(seconds, 600)) * 20;
        int amp = Math.max(0, Math.min(amplifier, 4));
        return new MobEffectInstance(e, ticks, amp);
    }
}
