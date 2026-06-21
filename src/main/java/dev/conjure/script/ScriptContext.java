package dev.conjure.script;

import com.mojang.logging.LogUtils;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.block.ConjureActivatableBlock;
import dev.conjure.content.item.ConjureBlockItem;
import dev.conjure.content.item.ConjureItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;

import javax.annotation.Nullable;

/**
 * The whitelisted host object injected into every Conjure behavior script as {@code ctx}.
 *
 * <p>This class exposes a curated API of gameplay-level operations plus raw MC object
 * accessors so scripts can call any Minecraft API directly on the returned objects.
 * The Rhino ClassShutter in {@link ScriptRuntime} allows {@code net.minecraft.*} classes
 * so that objects returned by the raw accessors are fully usable from script.
 *
 * <p>All methods are server-side only; callers must guard with {@code !level.isClientSide}
 * before constructing one of these.
 *
 * <h2>Trigger values</h2>
 * {@link #trigger()} returns one of:
 * <ul>
 *   <li>{@code "use"}        — right-click in the air</li>
 *   <li>{@code "useOnBlock"} — right-click on a block</li>
 *   <li>{@code "hitEntity"}  — weapon hit a living entity</li>
 *   <li>{@code "swing"}      — left-click swing (empty air, fired via network)</li>
 * </ul>
 */
public final class ScriptContext {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Level level;
    private final Player player;
    private final InteractionHand hand;
    private final String trigger;

    /** Block this script acts on: the clicked block (useOn) or the block's own position. */
    @Nullable private final BlockPos targetPos;
    /** Face of {@link #targetPos} that was clicked, for placing against (useOn only). */
    @Nullable private final Direction targetFace;
    /** Mob a weapon script hit (hitEntity hook). */
    @Nullable private final LivingEntity targetEntity;

    // -------------------------------------------------------------------------
    // Constructors — existing signatures kept for block-shell callers
    // -------------------------------------------------------------------------

    /** Right-click-air item script (no block/entity target). Trigger defaults to "use". */
    public ScriptContext(Level level, Player player, InteractionHand hand) {
        this(level, player, hand, "use", null, null, null);
    }

    /** Block script or item-used-on-block script: knows the targeted block + clicked face.
     *  Trigger defaults to "useOnBlock" — block shells call this form. */
    public ScriptContext(Level level, Player player, InteractionHand hand,
                         @Nullable BlockPos targetPos, @Nullable Direction targetFace) {
        this(level, player, hand, "useOnBlock", targetPos, targetFace, null);
    }

    /** Weapon script: knows the mob it hit. Trigger defaults to "hitEntity". */
    public ScriptContext(Level level, Player player, InteractionHand hand,
                         @Nullable LivingEntity targetEntity) {
        this(level, player, hand, "hitEntity", null, null, targetEntity);
    }

    /** Full constructor with explicit trigger. Use this when the caller can name the trigger. */
    public ScriptContext(Level level, Player player, InteractionHand hand, String trigger) {
        this(level, player, hand, trigger, null, null, null);
    }

    /** Full constructor with trigger + block target. */
    public ScriptContext(Level level, Player player, InteractionHand hand, String trigger,
                         @Nullable BlockPos targetPos, @Nullable Direction targetFace) {
        this(level, player, hand, trigger, targetPos, targetFace, null);
    }

    /** Full constructor with trigger + entity target. */
    public ScriptContext(Level level, Player player, InteractionHand hand, String trigger,
                         @Nullable LivingEntity targetEntity) {
        this(level, player, hand, trigger, null, null, targetEntity);
    }

    private ScriptContext(Level level, Player player, InteractionHand hand, String trigger,
                          @Nullable BlockPos targetPos, @Nullable Direction targetFace,
                          @Nullable LivingEntity targetEntity) {
        this.level = level;
        this.player = player;
        this.hand = hand;
        this.trigger = trigger != null ? trigger : "use";
        this.targetPos = targetPos;
        this.targetFace = targetFace;
        this.targetEntity = targetEntity;
    }

    // -------------------------------------------------------------------------
    // Trigger
    // -------------------------------------------------------------------------

    /**
     * Returns the interaction trigger that created this context. One of:
     * {@code "use"}, {@code "useOnBlock"}, {@code "hitEntity"}, {@code "swing"}.
     */
    public String trigger() {
        return trigger;
    }

    // -------------------------------------------------------------------------
    // Raw MC object accessors — scripts can call any MC API on the returned objects
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link Level} this script is running in.
     * Cast to {@code ServerLevel} for server-only operations such as spawning entities.
     */
    public Level getLevel() {
        return level;
    }

    /** Returns the {@link Player} who triggered this script. */
    public Player getPlayer() {
        return player;
    }

    /**
     * Returns the {@link LivingEntity} that was hit, or {@code null} if none.
     * Non-null only when {@link #trigger()} is {@code "hitEntity"}.
     */
    @Nullable
    public LivingEntity getTargetEntity() {
        return targetEntity;
    }

    /**
     * Returns the {@link BlockPos} of the target block, or {@code null} if none.
     * Non-null when {@link #trigger()} is {@code "useOnBlock"} or in a block's own script.
     */
    @Nullable
    public BlockPos getTargetPos() {
        return targetPos;
    }

    /** Returns the {@link InteractionHand} that triggered this script. */
    public InteractionHand getHand() {
        return hand;
    }

    // -------------------------------------------------------------------------
    // Generic velocity — replaces the removed launch / dashForward verbs
    // -------------------------------------------------------------------------

    /**
     * Apply a velocity impulse to the player and resync the client so the movement is visible.
     * Values are added to the player's current delta movement, not set absolutely.
     *
     * @param x east/west impulse
     * @param y upward impulse (positive = up)
     * @param z north/south impulse
     */
    public void applyVelocity(double x, double y, double z) {
        addMotion(x, y, z);
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

    /** Consume one of the held item (single-use). Creative players are unaffected. */
    public void consumeHeld() {
        if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
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
    // Target entity — present when trigger() == "hitEntity"
    // -------------------------------------------------------------------------

    /** True when this script was invoked by a weapon hitting a mob. */
    public boolean hasTargetEntity() {
        return targetEntity != null;
    }

    // -------------------------------------------------------------------------
    // Target block — the clicked block (useOnBlock) or the block running this script.
    // -------------------------------------------------------------------------

    /** True when this script was invoked against a block (a useOnBlock item or a block's own script). */
    public boolean hasTargetBlock() {
        return targetPos != null;
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
    // Reusable named effects
    // -------------------------------------------------------------------------

    /**
     * Load and execute a named reusable effect script against this context.
     * Effect scripts live at {@code <gamedir>/conjure/generated/effects/<name>.js}.
     * <p>
     * // ponytail: the nested run opens a fresh Rhino Context so the budget resets
     * // per effect — fine for one nesting level; deeply chained effects would multiply.
     *
     * @param name effect name (no extension)
     */
    public void applyEffect(String name) {
        try {
            ScriptRuntime.get().runEffect(name, this);
        } catch (ScriptException e) {
            LOGGER.warn("[Conjure script] applyEffect('{}') failed: {}", name, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Generic helpers — safe wrappers so scripts rarely need raw MC API
    // -------------------------------------------------------------------------

    /** Deal magic damage to every living entity within {@code radius} of the player (loops internally). */
    public void damageNearby(double radius, double amount) {
        for (LivingEntity e : near(radius)) hurtEntity(e, amount);
    }

    /** Apply a potion effect to every living entity within {@code radius} of the player. */
    public void effectNearby(double radius, String effectId, int seconds, int amplifier) {
        for (LivingEntity e : near(radius)) effectEntity(e, effectId, seconds, amplifier);
    }

    /** Knock every living entity within {@code radius} away from the player. */
    public void knockbackNearby(double radius, double power) {
        for (LivingEntity e : near(radius)) knockbackEntity(e, power);
    }

    /** Living entities within {@code radius} of the player (excluding the player). */
    private List<LivingEntity> near(double radius) {
        double r = Math.max(0, Math.min(radius, 32));
        AABB box = new AABB(player.getX() - r, player.getY() - r, player.getZ() - r,
                player.getX() + r, player.getY() + r, player.getZ() + r);
        return level.getEntities(EntityTypeTest.forClass(LivingEntity.class), box, e -> e != player);
    }

    /** Deal {@code amount} magic damage to {@code entity} (2 = 1 heart). No-op if null/≤0. */
    public void hurtEntity(LivingEntity entity, double amount) {
        if (entity != null && amount > 0) {
            entity.hurt(player.damageSources().magic(), (float) amount);
        }
    }

    /** Apply a potion effect to {@code entity}. {@code amp} 0 = level I; clamped 0–4, 1–600 s. */
    public void effectEntity(LivingEntity entity, String effectId, int seconds, int amplifier) {
        if (entity == null) return;
        MobEffectInstance fx = effect(effectId, seconds, amplifier);
        if (fx != null) entity.addEffect(fx);
    }

    /** Knock {@code entity} away from the player. {@code power} clamped 0–4. */
    public void knockbackEntity(LivingEntity entity, double power) {
        if (entity == null) return;
        double p = Math.max(0, Math.min(power, 4));
        entity.knockback(p, player.getX() - entity.getX(), player.getZ() - entity.getZ());
    }

    /** Set the block at world coords to {@code blockId} (e.g. "minecraft:gold_block"). */
    public void setBlockAt(int x, int y, int z, String blockId) {
        Block b = block(blockId);
        if (b == null) return;
        level.setBlockAndUpdate(new BlockPos(x, y, z), b.defaultBlockState());
    }

    /** Spawn a simple particle (e.g. "minecraft:heart") at coords; {@code count} clamped 1–256. */
    public void particle(String particleId, double x, double y, double z, int count) {
        if (!(level instanceof ServerLevel sl)) return;
        ResourceLocation loc = ResourceLocation.tryParse(particleId);
        if (loc == null) { LOGGER.warn("[Conjure script] particle: invalid id '{}'", particleId); return; }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(loc);
        if (!(type instanceof SimpleParticleType spt)) {
            LOGGER.warn("[Conjure script] particle: '{}' unknown or not a simple particle", particleId);
            return;
        }
        int n = Math.max(1, Math.min(count, 256));
        sl.sendParticles(spt, x, y, z, n, 0.3, 0.3, 0.3, 0.0);
    }

    /** Summon an entity by id (e.g. "minecraft:zombie") at coords. No-op if unknown / not server. */
    public void summon(String entityId, double x, double y, double z) {
        if (!(level instanceof ServerLevel sl)) return;
        ResourceLocation loc = ResourceLocation.tryParse(entityId);
        if (loc == null) { LOGGER.warn("[Conjure script] summon: invalid id '{}'", entityId); return; }
        EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.get(loc);
        if (et == null) { LOGGER.warn("[Conjure script] summon: unknown entity '{}'", entityId); return; }
        et.spawn(sl, new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)),
                MobSpawnType.COMMAND);
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
