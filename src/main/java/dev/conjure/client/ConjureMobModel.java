package dev.conjure.client;

import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.entity.ConjureMob;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Shared GeckoLib model for all {@link ConjureMob} entity slots.
 *
 * <p>Uses a single static {@code conjure_mob} geo JSON and animation JSON bundled in
 * the mod jar. The texture resource is resolved per-entity: if the slot has a generated
 * skin (written to the dynamic pack by {@link dev.conjure.gen.assets.EntityAssets}), that
 * path is returned; otherwise the bundled {@code default.png} is used so unconfigured
 * slots are never invisible.
 *
 * <p>Resource paths (relative to {@code assets/conjure/}):
 * <ul>
 *   <li>Geo model: {@code geo/conjure_mob.geo.json}</li>
 *   <li>Animation: {@code animations/conjure_mob.animation.json}</li>
 *   <li>Default texture: {@code textures/entity/default.png}</li>
 *   <li>Per-slot texture: {@code textures/entity/entity_slot_N.png} (dynamic pack)</li>
 * </ul>
 */
public class ConjureMobModel extends GeoModel<ConjureMob> {

    /** Bundled static geo model shared by every Conjure mob slot. */
    private static final ResourceLocation MODEL_RL =
            ResourceLocation.fromNamespaceAndPath("conjure", "geo/conjure_mob.geo.json");

    /** Bundled animation file shared by every Conjure mob slot. */
    private static final ResourceLocation ANIM_RL =
            ResourceLocation.fromNamespaceAndPath("conjure", "animations/conjure_mob.animation.json");

    /** Fallback texture used for unconfigured slots (bundled in the mod jar). */
    private static final ResourceLocation DEFAULT_TEXTURE_RL =
            ResourceLocation.fromNamespaceAndPath("conjure", "textures/entity/default.png");

    /** Singleton — one model instance is reused for all slots' renderers. */
    public static final ConjureMobModel INSTANCE = new ConjureMobModel();

    private ConjureMobModel() {}

    // ---------------------------------------------------------------- GeoModel

    @Override
    public ResourceLocation getModelResource(ConjureMob mob) {
        return MODEL_RL;
    }

    @Override
    public ResourceLocation getAnimationResource(ConjureMob mob) {
        return ANIM_RL;
    }

    /**
     * Returns the per-slot texture if the slot has been configured and has a texture path;
     * falls back to the bundled default otherwise.
     *
     * <p>GeckoLib calls this every frame, so keep it allocation-light — only construct a
     * new {@link ResourceLocation} when the slot is genuinely configured.
     */
    @Override
    public ResourceLocation getTextureResource(ConjureMob mob) {
        SlotDefinition def = SlotRegistry.get(SlotKind.ENTITY, mob.getSlotIndex());
        if (def.configured && def.texturePath != null && !def.texturePath.isBlank()) {
            // texturePath is stored as "conjure:entity/entity_slot_N" (no "textures/" prefix,
            // no ".png" suffix — that is the MC resource-location convention for textures).
            String path = def.texturePath;
            // Convert "conjure:entity/entity_slot_N" → ResourceLocation with
            // namespace=conjure, path=textures/entity/entity_slot_N.png
            int colon = path.indexOf(':');
            if (colon >= 0) {
                String namespace = path.substring(0, colon);
                String texPath   = "textures/" + path.substring(colon + 1) + ".png";
                return ResourceLocation.fromNamespaceAndPath(namespace, texPath);
            }
        }
        return DEFAULT_TEXTURE_RL;
    }
}
