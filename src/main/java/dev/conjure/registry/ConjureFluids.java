package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.fluid.ConjureBucketItem;
import dev.conjure.content.fluid.ConjureFluidType;
import dev.conjure.content.fluid.ConjureLiquidBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluid pool: {@value #FLUID_POOL} pre-registered fluid sets, each consisting of:
 * <ol>
 *   <li>A {@link FluidType} (NeoForge's fluid-properties object)</li>
 *   <li>A source {@link BaseFlowingFluid.Source}</li>
 *   <li>A flowing {@link BaseFlowingFluid.Flowing}</li>
 *   <li>A {@link ConjureLiquidBlock} (registered as {@code fluid_block_slot_N})</li>
 *   <li>A {@link ConjureBucketItem} (registered as {@code bucket_slot_N})</li>
 * </ol>
 *
 * <p>Each slot's {@link ConjureFluidType} uses fixed per-slot ResourceLocations
 * ({@code conjure:block/fluid_still_slot_N} and {@code conjure:block/fluid_flow_slot_N})
 * baked at registration time — not resolved from the (empty) boot {@link dev.conjure.content.SlotDefinition}.
 * {@code FluidPipeline} writes PNG textures to these paths so a client reload stitches them
 * into the block atlas.
 *
 * <p>Client-side fluid extensions ({@link net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions})
 * are injected via {@link ConjureFluidType#initializeClient} which is called by the
 * NeoForge framework during client setup — no separate event subscription is required.
 */
public final class ConjureFluids {

    public static final int FLUID_POOL = 32;

    // ------------------------------------------------------------------ registries

    /** NeoForge FluidType registry. */
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Conjure.MODID);

    /** Vanilla Fluid registry (holds source + flowing). */
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Conjure.MODID);

    /** Own DeferredRegister<Block> for the LiquidBlock entries (names: fluid_block_slot_N). */
    public static final DeferredRegister<Block> FLUID_BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Conjure.MODID);

    /** Own DeferredRegister<Item> for the BucketItem entries (names: bucket_slot_N). */
    public static final DeferredRegister<Item> BUCKET_ITEMS =
            DeferredRegister.create(Registries.ITEM, Conjure.MODID);

    // ------------------------------------------------------------------ slot lists

    public static final List<DeferredHolder<FluidType, ConjureFluidType>> FLUID_TYPE_SLOTS  = new ArrayList<>();
    public static final List<DeferredHolder<Fluid, BaseFlowingFluid.Source>> SOURCE_SLOTS   = new ArrayList<>();
    public static final List<DeferredHolder<Fluid, BaseFlowingFluid.Flowing>> FLOW_SLOTS    = new ArrayList<>();
    public static final List<DeferredHolder<Block, ConjureLiquidBlock>> LIQUID_BLOCK_SLOTS  = new ArrayList<>();
    public static final List<DeferredHolder<Item, ConjureBucketItem>> BUCKET_SLOTS          = new ArrayList<>();

    /**
     * Fixed per-slot still texture ResourceLocation: {@code conjure:block/fluid_still_slot_N}.
     * These are baked at registration time so the FluidType always points to the correct atlas
     * sprite — regardless of whether the slot has been configured yet. FluidPipeline writes the
     * PNG to the matching path so a client reload stitches it into the block atlas.
     */
    private static ResourceLocation stillRL(int slot) {
        return ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "block/fluid_still_slot_" + slot);
    }

    /** Fixed per-slot flowing texture ResourceLocation: {@code conjure:block/fluid_flow_slot_N}. */
    private static ResourceLocation flowRL(int slot) {
        return ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "block/fluid_flow_slot_" + slot);
    }

    // ------------------------------------------------------------------ static init

    static {
        for (int i = 0; i < FLUID_POOL; i++) {
            final int idx = i;
            SlotRegistry.init(SlotKind.FLUID, idx);

            // Fixed per-slot ResourceLocations — NOT resolved from the (empty) boot SlotDefinition.
            // FluidPipeline writes PNGs to these exact paths so the block atlas stitcher finds them.
            final ResourceLocation still   = stillRL(idx);
            final ResourceLocation flowing = flowRL(idx);

            // ---- FluidType ----
            DeferredHolder<FluidType, ConjureFluidType> typeHolder =
                    FLUID_TYPES.register("fluid_slot_" + idx,
                            () -> new ConjureFluidType(still, flowing, FluidType.Properties.create()));
            FLUID_TYPE_SLOTS.add(typeHolder);

            // ---- Source & Flowing fluids (cross-reference via DeferredHolder suppliers) ----
            DeferredHolder<Fluid, BaseFlowingFluid.Source>  srcRef  = DeferredHolder.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "fluid_source_slot_" + idx));
            DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowRef = DeferredHolder.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "fluid_flowing_slot_" + idx));
            DeferredHolder<Block, ConjureLiquidBlock>       blkRef  = DeferredHolder.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "fluid_block_slot_" + idx));
            DeferredHolder<Item, ConjureBucketItem>         bktRef  = DeferredHolder.create(Registries.ITEM,  ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "bucket_slot_" + idx));

            // Properties object shared between source and flowing (holds all three suppliers).
            // We use a one-element array trick to reference it in the lambda before it is built.
            final BaseFlowingFluid.Properties[] propsBox = new BaseFlowingFluid.Properties[1];
            propsBox[0] = new BaseFlowingFluid.Properties(
                    typeHolder,   // FluidType supplier
                    srcRef,       // still supplier
                    flowRef       // flowing supplier
            ).bucket(bktRef).block(blkRef);

            DeferredHolder<Fluid, BaseFlowingFluid.Source> srcHolder =
                    FLUIDS.register("fluid_source_slot_" + idx,
                            () -> new BaseFlowingFluid.Source(propsBox[0]));
            DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowHolder =
                    FLUIDS.register("fluid_flowing_slot_" + idx,
                            () -> new BaseFlowingFluid.Flowing(propsBox[0]));
            SOURCE_SLOTS.add(srcHolder);
            FLOW_SLOTS.add(flowHolder);

            // ---- LiquidBlock (ConjureLiquidBlock for runtime getName) ----
            DeferredHolder<Block, ConjureLiquidBlock> blkHolder =
                    FLUID_BLOCKS.register("fluid_block_slot_" + idx,
                            () -> new ConjureLiquidBlock(idx, (FlowingFluid) srcHolder.get(),
                                    BlockBehaviour.Properties.of()
                                            .noCollission()
                                            .strength(100.0F)
                                            .noLootTable()
                                            .liquid()
                                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
            LIQUID_BLOCK_SLOTS.add(blkHolder);

            // ---- BucketItem (ConjureBucketItem for runtime getName) ----
            DeferredHolder<Item, ConjureBucketItem> bktHolder =
                    BUCKET_ITEMS.register("bucket_slot_" + idx,
                            () -> new ConjureBucketItem(idx, srcHolder.get(),
                                    new Item.Properties()
                                            .craftRemainder(Items.BUCKET)
                                            .stacksTo(1)));
            BUCKET_SLOTS.add(bktHolder);
        }
    }

    // ------------------------------------------------------------------ registration

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        FLUID_BLOCKS.register(modBus);
        BUCKET_ITEMS.register(modBus);
    }

    private ConjureFluids() {}
}
