package dev.conjure.content.entity;

import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Generic scriptable mob for the Conjure entity pool.
 *
 * <p>All "personality" — display name, attributes, attack patterns — is driven at
 * runtime by the {@link SlotDefinition} stored in {@link SlotRegistry}.  The JVM-
 * baked parts (hitbox dimensions, tracking range) are fixed by the size bucket the
 * entity was assigned to during registration.
 *
 * <p>Attribute defaults read from the slot definition:
 * <ul>
 *   <li>{@code numbers["max_health"]}    — default 20.0</li>
 *   <li>{@code numbers["move_speed"]}    — default 0.25</li>
 *   <li>{@code numbers["attack_damage"]} — default 2.0</li>
 *   <li>{@code numbers["follow_range"]}  — default 16.0</li>
 * </ul>
 *
 * <p>TODO(GeckoLib): Replace the placeholder renderer/model with a GeckoLib animated
 * rig once the dependency is added; texture path will then come from
 * {@code slotDef.texturePath}.
 */
public class ConjureMob extends PathfinderMob {

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

    // -------------------------------------------------------- attribute builder

    /**
     * Returns an attribute builder seeded with the current slot definition values.
     * Called by {@code ConjureEntities} during attribute registration.  The builder is
     * intentionally not slot-index–specific so it can serve as a generic default.
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
}
