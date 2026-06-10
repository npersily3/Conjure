package dev.conjure.content.fluid;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * Client-side texture and tint provider for a Conjure fluid slot.
 *
 * <p>Textures default to the vanilla water still/flow atlas sprites. Runtime
 * datapack / resource-pack overrides can supply different sprite paths via the
 * slot definition's {@code strings} map (keys {@code "still_texture"} and
 * {@code "flow_texture"}), which are baked in at mod-bus registration time.
 */
@OnlyIn(Dist.CLIENT)
public class ConjureFluidClientExtensions implements IClientFluidTypeExtensions {

    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;

    public ConjureFluidClientExtensions(ResourceLocation stillTexture, ResourceLocation flowingTexture) {
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
    }

    @Override
    public ResourceLocation getStillTexture() {
        return stillTexture;
    }

    @Override
    public ResourceLocation getFlowingTexture() {
        return flowingTexture;
    }

    /** 0xFFFFFFFF = white / no tint; runtime content can push a new FluidType if colour changes. */
    @Override
    public int getTintColor() {
        return 0xFFFFFFFF;
    }
}
