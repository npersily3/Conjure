package dev.conjure.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.conjure.Conjure;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.content.structure.StructurePlacer;
import dev.conjure.gen.GenerationService;
import dev.conjure.gen.ModService;
import dev.conjure.gen.pipeline.PipelineSupport;
import dev.conjure.persist.SlotStore;
import dev.conjure.registry.ConjureItems;
import dev.conjure.registry.ConjureStructures;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers all {@code /conjure} sub-commands.
 *
 * <ul>
 *   <li>{@code /conjure new <prompt>} — generate new content. A single concrete prompt yields one
 *       piece; a themed or plural prompt ("pagoda blocks") is decomposed into many pieces. Each
 *       piece is routed by {@link dev.conjure.ai.agents.RouterAgent} to its content pipeline.
 *   <li>{@code /conjure list} — list all currently configured item slots (index, name, prompt).
 *   <li>{@code /conjure edit <index> <prompt>} — re-run generation on an existing slot,
 *       updating its texture, name, and behavior while preserving its registry id.
 * </ul>
 *
 * <p>All model calls are dispatched to a background thread; feedback is posted back on the
 * server thread via {@link MinecraftServer#execute}.
 */
@EventBusSubscriber(modid = Conjure.MODID)
public final class ConjureCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("conjure")
                        // /conjure new <prompt>
                        .then(Commands.literal("new")
                                .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String prompt = StringArgumentType.getString(ctx, "prompt");
                                            CommandSourceStack source = ctx.getSource();
                                            MinecraftServer server = source.getServer();

                                            source.sendSuccess(
                                                    () -> Component.literal("§7Conjuring \"" + prompt + "\"… (this may take a few seconds)"),
                                                    false);

                                            // Smart mode: a single concrete prompt yields one piece;
                                            // themed/plural prompts expand into many.
                                            ModService.build(prompt, msg ->
                                                    server.execute(() -> source.sendSystemMessage(
                                                            Component.literal("§a[Conjure] §f" + msg))), false);
                                            return 1;
                                        })))

                        // /conjure list
                        .then(Commands.literal("list")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    int found = 0;
                                    source.sendSuccess(
                                            () -> Component.literal("§6=== Conjure Item Slots ==="),
                                            false);
                                    for (int i = 0; i < ConjureItems.ITEM_POOL; i++) {
                                        SlotDefinition def = SlotRegistry.get(SlotKind.ITEM, i);
                                        if (def.configured) {
                                            final int index = i;
                                            final String name = def.displayName;
                                            final String promptSnippet = def.sourcePrompt.length() > 40
                                                    ? def.sourcePrompt.substring(0, 37) + "…"
                                                    : def.sourcePrompt;
                                            source.sendSuccess(
                                                    () -> Component.literal(
                                                            "§e#" + index + " §f" + name + " §7(\"" + promptSnippet + "\")"),
                                                    false);
                                            found++;
                                        }
                                    }
                                    if (found == 0) {
                                        source.sendSuccess(
                                                () -> Component.literal("§7No item slots configured yet. Use /conjure new <prompt>."),
                                                false);
                                    } else {
                                        final int total = found;
                                        source.sendSuccess(
                                                () -> Component.literal("§7" + total + " / " + ConjureItems.ITEM_POOL + " slots configured."),
                                                false);
                                    }
                                    return found;
                                }))

                        // /conjure edit <index> <prompt>
                        .then(Commands.literal("edit")
                                .then(Commands.argument("index", IntegerArgumentType.integer(0, ConjureItems.ITEM_POOL - 1))
                                        .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    int index = IntegerArgumentType.getInteger(ctx, "index");
                                                    String prompt = StringArgumentType.getString(ctx, "prompt");
                                                    CommandSourceStack source = ctx.getSource();
                                                    MinecraftServer server = source.getServer();

                                                    SlotDefinition existing = SlotRegistry.get(SlotKind.ITEM, index);
                                                    if (!existing.configured) {
                                                        source.sendFailure(Component.literal(
                                                                "§cSlot #" + index + " has not been configured yet. Use /conjure new <prompt> first."));
                                                        return 0;
                                                    }

                                                    source.sendSuccess(
                                                            () -> Component.literal("§7Editing slot #" + index + " with \"" + prompt + "\"… (this may take a few seconds)"),
                                                            false);

                                                    GenerationService.regenerateItem(index, prompt, msg ->
                                                            server.execute(() -> source.sendSystemMessage(
                                                                    Component.literal("§a[Conjure] §f" + msg))));
                                                    return 1;
                                                }))))

                        // /conjure place <index> — place a pre-generated structure at the player's position
                        .then(Commands.literal("place")
                                .then(Commands.argument("index", IntegerArgumentType.integer(0, ConjureStructures.STRUCTURE_POOL - 1))
                                        .executes(ctx -> {
                                            int index = IntegerArgumentType.getInteger(ctx, "index");
                                            CommandSourceStack source = ctx.getSource();

                                            SlotDefinition def = SlotRegistry.get(SlotKind.STRUCTURE, index);
                                            if (!def.configured) {
                                                source.sendFailure(Component.literal(
                                                        "§cStructure slot #" + index + " has not been generated yet."
                                                        + " Use /conjure new <prompt> first."));
                                                return 0;
                                            }

                                            ServerPlayer player;
                                            try {
                                                player = source.getPlayerOrException();
                                            } catch (Exception e) {
                                                source.sendFailure(Component.literal(
                                                        "§c/conjure place must be run by a player."));
                                                return 0;
                                            }

                                            ServerLevel level = source.getLevel();
                                            // Place with the bottom-north-west corner one block north of the player
                                            BlockPos origin = player.blockPosition().north(2);

                                            try {
                                                int placed = StructurePlacer.place(level, origin, def);
                                                final String name = def.displayName;
                                                source.sendSuccess(
                                                        () -> Component.literal(
                                                                "§a[Conjure] §fPlaced '" + name
                                                                + "' (" + placed + " blocks) at "
                                                                + origin.getX() + ", " + origin.getY() + ", " + origin.getZ()),
                                                        false);
                                                return placed;
                                            } catch (Exception e) {
                                                Conjure.LOGGER.error("Conjure: failed to place structure slot {}", index, e);
                                                source.sendFailure(Component.literal(
                                                        "§cPlacement failed: " + PipelineSupport.describe(e)));
                                                return 0;
                                            }
                                        })))

                        // /conjure mod <description> — decompose a whole-mod request into many generated pieces
                        .then(Commands.literal("mod")
                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String description = StringArgumentType.getString(ctx, "description");
                                            CommandSourceStack source = ctx.getSource();
                                            MinecraftServer server = source.getServer();

                                            source.sendSuccess(
                                                    () -> Component.literal("§7Planning mod \"" + description + "\"… (this may take a minute)"),
                                                    false);

                                            ModService.buildMod(description, msg ->
                                                    server.execute(() -> source.sendSystemMessage(
                                                            Component.literal("§a[Conjure] §f" + msg))));
                                            return 1;
                                        })))

                        // /conjure regenerate <kind> <index> <prompt> — re-run a specific slot in place
                        .then(Commands.literal("regenerate")
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            String kindStr = StringArgumentType.getString(ctx, "kind");
                                                            int index = IntegerArgumentType.getInteger(ctx, "index");
                                                            String prompt = StringArgumentType.getString(ctx, "prompt");
                                                            CommandSourceStack source = ctx.getSource();
                                                            MinecraftServer server = source.getServer();

                                                            SlotKind kind = parseKind(kindStr);
                                                            if (kind == null) {
                                                                source.sendFailure(Component.literal(
                                                                        "§cUnknown kind '" + kindStr + "'. Use: item, block, fluid, entity, structure."));
                                                                return 0;
                                                            }
                                                            SlotDefinition existing = SlotRegistry.get(kind, index);
                                                            if (!existing.configured) {
                                                                source.sendFailure(Component.literal(
                                                                        "§c" + kind.name().toLowerCase() + " slot #" + index
                                                                        + " is not configured yet. Use /conjure new <prompt> first."));
                                                                return 0;
                                                            }

                                                            source.sendSuccess(() -> Component.literal(
                                                                    "§7Regenerating " + kind.name().toLowerCase() + " #" + index
                                                                    + " with \"" + prompt + "\"…"), false);
                                                            GenerationService.regenerate(kind, index, prompt, msg ->
                                                                    server.execute(() -> source.sendSystemMessage(
                                                                            Component.literal("§a[Conjure] §f" + msg))));
                                                            return 1;
                                                        })))))

                        // /conjure delete <kind> <index> — clear a single slot back to empty
                        .then(Commands.literal("delete")
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    String kindStr = StringArgumentType.getString(ctx, "kind");
                                                    int index = IntegerArgumentType.getInteger(ctx, "index");
                                                    CommandSourceStack source = ctx.getSource();

                                                    SlotKind kind = parseKind(kindStr);
                                                    if (kind == null) {
                                                        source.sendFailure(Component.literal(
                                                                "§cUnknown kind '" + kindStr + "'. Use: item, block, fluid, entity, structure."));
                                                        return 0;
                                                    }
                                                    SlotDefinition existing = SlotRegistry.get(kind, index);
                                                    if (!existing.configured) {
                                                        source.sendFailure(Component.literal(
                                                                "§c" + kind.name().toLowerCase() + " slot #" + index + " is already empty."));
                                                        return 0;
                                                    }

                                                    final String name = existing.displayName;
                                                    SlotRegistry.reset(kind, index);   // in-memory → placeholder
                                                    SlotStore.delete(kind, index);     // persisted metadata
                                                    // Leftover generated texture/model files are harmless once the slot is unconfigured.
                                                    PipelineSupport.reloadIfClient();
                                                    PipelineSupport.reloadData();

                                                    source.sendSuccess(() -> Component.literal(
                                                            "§a[Conjure] §fDeleted " + kind.name().toLowerCase() + " #" + index
                                                            + " ('" + name + "'). The slot is empty again."), false);
                                                    return 1;
                                                }))))

                        // /conjure nuke — wipe ALL generated content across every kind
                        .then(Commands.literal("nuke")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    SlotRegistry.resetAll();
                                    SlotStore.deleteAll();
                                    PipelineSupport.reloadIfClient();
                                    PipelineSupport.reloadData();
                                    source.sendSuccess(() -> Component.literal(
                                            "§c[Conjure] §fNuked all generated content. Every slot is empty again."), false);
                                    return 1;
                                })));
    }

    /** Maps a command keyword to a {@link SlotKind}, or {@code null} if unrecognised. */
    private static SlotKind parseKind(String s) {
        return switch (s.toLowerCase().trim()) {
            case "item"      -> SlotKind.ITEM;
            case "block"     -> SlotKind.BLOCK;
            case "fluid"     -> SlotKind.FLUID;
            case "entity", "mob" -> SlotKind.ENTITY;
            case "structure" -> SlotKind.STRUCTURE;
            default          -> null;
        };
    }

    private ConjureCommands() {}
}
