package dev.conjure.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.conjure.Conjure;
import dev.conjure.content.SlotDefinition;
import dev.conjure.content.SlotKind;
import dev.conjure.content.SlotRegistry;
import dev.conjure.gen.GenerationService;
import dev.conjure.registry.ConjureItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers all {@code /conjure} sub-commands.
 *
 * <ul>
 *   <li>{@code /conjure new <prompt>} — generate a new item slot from a natural-language prompt.
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

                                            GenerationService.generateItem(prompt, msg ->
                                                    server.execute(() -> source.sendSystemMessage(
                                                            Component.literal("§a[Conjure] §f" + msg))));
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
                                                })))));
    }

    private ConjureCommands() {}
}
