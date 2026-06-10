package dev.conjure.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.conjure.Conjure;
import dev.conjure.gen.GenerationService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers {@code /conjure new "<prompt>"}. The model call is dispatched to a worker thread;
 * feedback is posted back on the server thread when generation finishes.
 */
@EventBusSubscriber(modid = Conjure.MODID)
public final class ConjureCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("conjure")
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
                                        }))));
    }

    private ConjureCommands() {}
}
