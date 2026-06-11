package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.client.ConjureMobRenderer;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.entity.ConjureMob;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity (mob) pool pre-registration.
 *
 * <p>Slots are divided into three hitbox-size buckets so that the JVM-baked
 * {@link EntityType} dimensions and tracking ranges are appropriate for the
 * eventual content assigned to each slot:
 *
 * <ul>
 *   <li><b>SMALL</b> ({@value #SMALL} slots)  — 0.6 × 0.8 hitbox, tracking range 8</li>
 *   <li><b>MEDIUM</b> ({@value #MEDIUM} slots) — 0.9 × 1.4 hitbox, tracking range 8</li>
 *   <li><b>LARGE</b> ({@value #LARGE} slots)   — 1.4 × 2.0 hitbox, tracking range 10</li>
 * </ul>
 *
 * <p>Attributes (max health, movement speed, attack damage, follow range) are read
 * from {@link SlotRegistry} and therefore can be updated at runtime by swapping the
 * {@link dev.conjure.content.SlotDefinition} for that slot.
 *
 * <p>The client-side renderer is a {@link dev.conjure.client.ConjureMobRenderer} (GeckoLib
 * {@code GeoEntityRenderer}) backed by the shared {@link dev.conjure.client.ConjureMobModel}.
 * Per-slot textures come from {@code SlotDefinition#texturePath}; unconfigured slots fall
 * back to the bundled {@code textures/entity/default.png}.
 */
public final class ConjureEntities {

    public static final int SMALL  = 48;   // ~0.6 wide hitbox
    public static final int MEDIUM = 48;   // ~0.9 wide
    public static final int LARGE  = 32;   // ~1.4 wide

    public static int totalPool() {
        return SMALL + MEDIUM + LARGE;
    }

    // ------------------------------------------------------------------ registry

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Conjure.MODID);

    // ------------------------------------------------------------------ slot lists

    /** All registered entity types in slot order (SMALL 0…47, MEDIUM 48…95, LARGE 96…127). */
    public static final List<DeferredHolder<EntityType<?>, EntityType<ConjureMob>>> ENTITY_TYPE_SLOTS =
            new ArrayList<>();

    // ------------------------------------------------------------------ static init

    static {
        // SMALL bucket: global slot index 0 … SMALL-1
        for (int i = 0; i < SMALL; i++) {
            registerSlot(i, 0.6F, 0.8F, 8);
        }
        // MEDIUM bucket: global slot index SMALL … SMALL+MEDIUM-1
        for (int i = 0; i < MEDIUM; i++) {
            registerSlot(SMALL + i, 0.9F, 1.4F, 8);
        }
        // LARGE bucket: global slot index SMALL+MEDIUM … totalPool()-1
        for (int i = 0; i < LARGE; i++) {
            registerSlot(SMALL + MEDIUM + i, 1.4F, 2.0F, 10);
        }
    }

    private static void registerSlot(int globalIdx, float width, float height, int trackingRange) {
        SlotRegistry.init(SlotKind.ENTITY, globalIdx);
        // Capture final for lambdas
        final int idx = globalIdx;
        DeferredHolder<EntityType<?>, EntityType<ConjureMob>> holder =
                ENTITY_TYPES.register("entity_slot_" + globalIdx,
                        () -> EntityType.Builder.<ConjureMob>of(
                                        (entityType, level) -> new ConjureMob(entityType, level, idx),
                                        MobCategory.CREATURE)
                                .sized(width, height)
                                .clientTrackingRange(trackingRange)
                                .build("entity_slot_" + idx));
        ENTITY_TYPE_SLOTS.add(holder);
    }

    // ------------------------------------------------------------------ registration

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }

    // ------------------------------------------------------------------ event handlers

    /**
     * Registers default attribute suppliers for all Conjure mob entity types.
     * Fired on the mod event bus during common setup.
     */
    @EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class CommonEvents {

        @SubscribeEvent
        public static void onAttributeCreation(EntityAttributeCreationEvent event) {
            for (int i = 0; i < ENTITY_TYPE_SLOTS.size(); i++) {
                final int idx = i;
                event.put(ENTITY_TYPE_SLOTS.get(i).get(),
                        ConjureMob.createAttributes(idx).build());
            }
        }
    }

    /**
     * Client-only: registers the GeckoLib-backed {@link ConjureMobRenderer} for all Conjure
     * mob entity types. Each slot gets its own renderer instance (required by the Minecraft
     * renderer provider API), but all instances share the singleton {@link dev.conjure.client.ConjureMobModel}.
     */
    @EventBusSubscriber(modid = Conjure.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientEvents {

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            for (DeferredHolder<EntityType<?>, EntityType<ConjureMob>> holder : ENTITY_TYPE_SLOTS) {
                event.registerEntityRenderer(holder.get(), ConjureMobRenderer::new);
            }
        }
    }

    private ConjureEntities() {}
}
