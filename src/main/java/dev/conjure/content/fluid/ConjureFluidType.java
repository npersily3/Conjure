package dev.conjure.content.fluid;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.function.Consumer;

/**
 * A slot-driven {@link FluidType} for the Conjure fluid pool.
 *
 * <p>Still and flowing textures are supplied at construction time (resolved from the
 * {@link dev.conjure.content.SlotDefinition} strings map at registration); the client
 * extension delegate is registered separately via {@link ConjureFluidClientExtensions} and
 * {@code RegisterClientExtensionsEvent}.
 */
public class ConjureFluidType extends FluidType {

    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;

    public ConjureFluidType(ResourceLocation stillTexture, ResourceLocation flowingTexture, FluidType.Properties properties) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
    }

    public ResourceLocation getStillTexture() {
        return stillTexture;
    }

    public ResourceLocation getFlowingTexture() {
        return flowingTexture;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new ConjureFluidClientExtensions(stillTexture, flowingTexture));
    }
}
