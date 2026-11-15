package minepiece.noah.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /fruit                      -> affiche ton fruit du démon
 * /fruit remove <joueur>      -> retire le pouvoir d'un joueur (administrateurs)
 */
public final class FruitCommands {
	private FruitCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("fruit")
				.executes(ctx -> show(ctx.getSource()))
				.then(Commands.literal("remove")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("joueur", EntityArgument.player())
								.executes(ctx -> remove(ctx.getSource(), EntityArgument.getPlayer(ctx, "joueur"))))));
	}

	private static int show(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
		String text = fruit == null ? "Sans fruit" : fruit.displayName();
		source.sendSuccess(() -> Component.literal("Ton fruit du démon : " + text), false);
		return 1;
	}

	private static int remove(CommandSourceStack source, ServerPlayer target) {
		if (!target.hasAttached(ModAttachments.FRUIT)) {
			source.sendFailure(Component.literal(target.getName().getString() + " n'a pas de fruit du démon."));
			return 0;
		}
		target.removeAttached(ModAttachments.FRUIT);
		target.sendSystemMessage(Component.literal("Tu as perdu ton pouvoir de fruit du démon."));
		source.sendSuccess(() -> Component.literal("Fruit retiré à " + target.getName().getString()), true);
		return 1;
	}
}
