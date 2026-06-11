package dev.conjure.client;

import dev.conjure.content.entity.ConjureMob;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib entity renderer for all {@link ConjureMob} slots.
 *
 * <p>Delegates model lookup and texture routing to the shared singleton
 * {@link ConjureMobModel#INSTANCE}. One renderer instance is created per entity-type slot
 * (via the {@link EntityRendererProvider.Context} factory pattern required by Minecraft),
 * but they all reference the same model object.
 */
public class ConjureMobRenderer extends GeoEntityRenderer<ConjureMob> {

    public ConjureMobRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, ConjureMobModel.INSTANCE);
    }
}
