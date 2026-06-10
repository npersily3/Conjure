package dev.conjure.registry;

import net.neoforged.bus.api.IEventBus;

/**
 * Fluid pool — SCAFFOLD.
 *
 * <p>Each "fluid slot" is really a set of four registered objects:
 * a source {@code Fluid}, a flowing {@code Fluid}, a {@code FluidType} (whose still/flow
 * textures come from {@code IClientFluidTypeExtensions} and so are read from the slot
 * definition at client setup), a {@code LiquidBlock}, and a {@code BucketItem}. Because that
 * texture wiring is resolved at client-setup rather than per-frame, fluids are the least
 * "live-swappable" of the pools — like blocks, they're bucketed and reloaded.
 *
 * <p>Next implementation pass: pre-register {@link #FLUID_POOL} such sets via
 * {@code DeferredRegister}s for {@code Registries.FLUID},
 * {@code NeoForgeRegistries.Keys.FLUID_TYPES}, the liquid blocks, and the bucket items.
 */
public final class ConjureFluids {

    public static final int FLUID_POOL = 32;

    public static void register(IEventBus modBus) {
        // TODO(fluids): build and register the BaseFlowingFluid source/flowing pairs,
        // FluidType (with slot-driven client texture extensions), LiquidBlock, BucketItem.
    }

    private ConjureFluids() {}
}
