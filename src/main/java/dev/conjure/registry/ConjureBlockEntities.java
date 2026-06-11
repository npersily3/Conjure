package dev.conjure.registry;

// PARENT: add to Conjure.java constructor:
//   ConjureBlockEntities.BLOCK_ENTITIES.register(modBus);

import dev.conjure.Conjure;
import dev.conjure.content.block.BlockArchetype;
import dev.conjure.content.block.ConjureBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers ONE shared {@link BlockEntityType} bound to every MACHINE-archetype ConjureBlock.
 *
 * <p>Because Minecraft freezes registries at startup, we cannot register a BlockEntityType per
 * machine slot. Instead a single type covers all MACHINE blocks — the BE just reads its own
 * block to find the correct {@link dev.conjure.content.SlotDefinition}.
 *
 * <p>PARENT: call {@code ConjureBlockEntities.BLOCK_ENTITIES.register(modBus)} in Conjure.java.
 */
public final class ConjureBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Conjure.MODID);

    /**
     * One type for all MACHINE-archetype blocks. Initialised lazily once ConjureBlocks' static
     * block has run, so the BLOCK_SLOTS list is fully populated.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConjureBlockEntity>> MACHINE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("machine_block_entity", () -> {
                // Collect all MACHINE-archetype blocks from the already-built BLOCK_SLOTS list.
                List<Block> machineBlocks = new ArrayList<>();
                for (var holder : ConjureBlocks.BLOCK_SLOTS) {
                    var block = holder.get();
                    if (block.archetype == BlockArchetype.MACHINE) {
                        machineBlocks.add(block);
                    }
                }
                if (machineBlocks.isEmpty()) {
                    throw new IllegalStateException(
                            "[Conjure] No MACHINE-archetype blocks found — check BlockArchetype.MACHINE.count > 0");
                }
                return BlockEntityType.Builder
                        .of(ConjureBlockEntity::new, machineBlocks.toArray(new Block[0]))
                        .build(null);
            });

    private ConjureBlockEntities() {}
}
