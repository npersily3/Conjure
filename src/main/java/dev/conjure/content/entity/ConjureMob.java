package dev.conjure.content.entity;

import dev.conjure.Config;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Generic scriptable mob for the Conjure entity pool.
 *
 * <p>All "personality" — display name, attributes, attack patterns — is driven at
 * runtime by the {@link SlotDefinition} stored in {@link SlotRegistry}.  The JVM-
 * baked parts (hitbox dimensions, tracking range) are fixed by the size bucket the
 * entity was assigned to during registration.
 *
 * <p>Implements {@link GeoEntity} so GeckoLib can drive idle/walk animations from
 * the shared {@code conjure_mob.animation.json} file. Animation controllers are
 * only registered when {@link Config#ENTITY_ANIMATIONS} is enabled.
 *
 * <p>Attribute defaults read from the slot definition:
 * <ul>
 *   <li>{@code numbers["max_health"]}    — default 20.0</li>
 *   <li>{@code numbers["move_speed"]}    — default 0.25</li>
 *   <li>{@code numbers["attack_damage"]} — default 2.0</li>
 *   <li>{@code numbers["follow_range"]}  — default 16.0</li>
 * </ul>
 */
public class ConjureMob extends PathfinderMob implements GeoEntity {

    // GeckoLib animations --------------------------------------------------------

    /** Shared walk/idle animations driven by the single shared animation JSON. */
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.conjure_mob.idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.conjure_mob.walk");

    /** Per-instance GeckoLib cache — one per living entity instance. */
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // Entity data ----------------------------------------------------------------

    /** Global slot index within SlotKind.ENTITY. Set once at construction. */
    private final int slotIndex;

    public ConjureMob(EntityType<? extends ConjureMob> entityType, Level level, int slotIndex) {
        super(entityType, level);
        this.slotIndex = slotIndex;
    }

    // ------------------------------------------------------------------ goals

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ spawn

    /**
     * Applies per-slot attribute values (health, speed, damage, follow range) from the
     * {@link SlotDefinition} when the mob first spawns into the world. These are base-value
     * overrides on the live {@link AttributeInstance}, so they survive the registry freeze
     * and can change between generation runs.
     */
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                       MobSpawnType spawnType, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnData);
        applySlotAttributes();
        return result;
    }

    /**
     * Reads attribute values from the slot definition and applies them as base values.
     * Called during spawn and can also be called any time the slot definition is updated.
     */
    public void applySlotAttributes() {
        SlotDefinition def = SlotRegistry.get(SlotKind.ENTITY, slotIndex);
        setBaseAttributeValue(Attributes.MAX_HEALTH,     def.num("max_health",    20.0));
        setBaseAttributeValue(Attributes.MOVEMENT_SPEED, def.num("move_speed",    0.25));
        setBaseAttributeValue(Attributes.ATTACK_DAMAGE,  def.num("attack_damage", 2.0));
        setBaseAttributeValue(Attributes.FOLLOW_RANGE,   def.num("follow_range",  16.0));
        // Clamp current health to new max
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    private void setBaseAttributeValue(Holder<Attribute> attribute, double value) {
        AttributeInstance inst = this.getAttribute(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    // -------------------------------------------------------- attribute builder

    /**
     * Returns an attribute builder seeded with the current slot definition values.
     * Called by {@code ConjureEntities} during attribute registration.
     */
    public static AttributeSupplier.Builder createAttributes(int slotIndex) {
        SlotDefinition def = SlotRegistry.get(SlotKind.ENTITY, slotIndex);
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,    def.num("max_health",    20.0))
                .add(Attributes.MOVEMENT_SPEED, def.num("move_speed",   0.25))
                .add(Attributes.ATTACK_DAMAGE,  def.num("attack_damage", 2.0))
                .add(Attributes.FOLLOW_RANGE,   def.num("follow_range", 16.0));
    }

    // --------------------------------------------------------------- display

    @Override
    public Component getName() {
        SlotDefinition def = SlotRegistry.get(SlotKind.ENTITY, slotIndex);
        return def.configured
                ? Component.literal(def.displayName)
                : super.getName();
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    // -------------------------------------------------------- GeoEntity impl

    /**
     * Registers animation controllers. An idle/walk controller is added only when
     * {@link Config#ENTITY_ANIMATIONS} is enabled; otherwise no controllers are registered
     * so GeckoLib has zero overhead on servers or animation-disabled setups.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (!Config.ENTITY_ANIMATIONS.get()) {
            return;
        }
        controllers.add(new AnimationController<>(this, "idle_walk", state -> {
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(IDLE_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public double getTick(Object entity) {
        return tickCount;
    }
}
