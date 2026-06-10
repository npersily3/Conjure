package dev.conjure.registry;

import dev.conjure.Conjure;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.fluid.ConjureFluidType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
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
 *   <li>A {@link LiquidBlock} (registered as {@code fluid_block_slot_N})</li>
 *   <li>A {@link BucketItem} (registered as {@code bucket_slot_N})</li>
 * </ol>
 *
 * <p>Textures default to vanilla water still/flow sprites; overrides are read from
 * {@code SlotRegistry.get(SlotKind.FLUID, i).strings} at registration time
 * (keys {@code "still_texture"} and {@code "flow_texture"}).
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

    public static final List<DeferredHolder<FluidType, ConjureFluidType>> FLUID_TYPE_SLOTS = new ArrayList<>();
    public static final List<DeferredHolder<Fluid, BaseFlowingFluid.Source>> SOURCE_SLOTS  = new ArrayList<>();
    public static final List<DeferredHolder<Fluid, BaseFlowingFluid.Flowing>> FLOW_SLOTS   = new ArrayList<>();
    public static final List<DeferredHolder<Block, LiquidBlock>> LIQUID_BLOCK_SLOTS        = new ArrayList<>();
    public static final List<DeferredHolder<Item, BucketItem>> BUCKET_SLOTS                = new ArrayList<>();

    // Default water-like textures; overridden per-slot via SlotDefinition strings.
    private static final ResourceLocation DEFAULT_STILL   =
            ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation DEFAULT_FLOWING =
            ResourceLocation.withDefaultNamespace("block/water_flow");

    // ------------------------------------------------------------------ static init

    static {
        for (int i = 0; i < FLUID_POOL; i++) {
            final int idx = i;
            SlotRegistry.init(SlotKind.FLUID, idx);

            // Resolve texture paths from slot definition (may be empty at boot time).
            ResourceLocation still   = resolveTexture(idx, "still_texture",  DEFAULT_STILL);
            ResourceLocation flowing = resolveTexture(idx, "flow_texture",    DEFAULT_FLOWING);

            // ---- FluidType ----
            DeferredHolder<FluidType, ConjureFluidType> typeHolder =
                    FLUID_TYPES.register("fluid_slot_" + idx, () ->
                            new ConjureFluidType(
                                    resolveTexture(idx, "still_texture",  DEFAULT_STILL),
                                    resolveTexture(idx, "flow_texture",    DEFAULT_FLOWING),
                                    FluidType.Properties.create()));
            FLUID_TYPE_SLOTS.add(typeHolder);

            // ---- Source & Flowing fluids (cross-reference via DeferredHolder suppliers) ----
            DeferredHolder<Fluid, BaseFlowingFluid.Source>  srcRef  = DeferredHolder.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "fluid_source_slot_" + idx));
            DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowRef = DeferredHolder.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "fluid_flowing_slot_" + idx));
            DeferredHolder<Block, LiquidBlock>              blkRef  = DeferredHolder.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "fluid_block_slot_" + idx));
            DeferredHolder<Item, BucketItem>                bktRef  = DeferredHolder.create(Registries.ITEM,  ResourceLocation.fromNamespaceAndPath(Conjure.MODID, "bucket_slot_" + idx));

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

            // ---- LiquidBlock ----
            DeferredHolder<Block, LiquidBlock> blkHolder =
                    FLUID_BLOCKS.register("fluid_block_slot_" + idx,
                            () -> new LiquidBlock((FlowingFluid) srcHolder.get(),
                                    BlockBehaviour.Properties.of()
                                            .noCollission()
                                            .strength(100.0F)
                                            .noLootTable()
                                            .liquid()
                                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
            LIQUID_BLOCK_SLOTS.add(blkHolder);

            // ---- BucketItem ----
            DeferredHolder<Item, BucketItem> bktHolder =
                    BUCKET_ITEMS.register("bucket_slot_" + idx,
                            () -> new BucketItem(srcHolder.get(),
                                    new Item.Properties()
                                            .craftRemainder(Items.BUCKET)
                                            .stacksTo(1)));
            BUCKET_SLOTS.add(bktHolder);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceLocation resolveTexture(int slotIndex, String key, ResourceLocation fallback) {
        String raw = SlotRegistry.get(SlotKind.FLUID, slotIndex).str(key, "");
        if (raw.isEmpty()) return fallback;
        try {
            return ResourceLocation.parse(raw);
        } catch (Exception e) {
            return fallback;
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
