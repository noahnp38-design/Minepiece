package minepiece.noah.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import minepiece.noah.economy.BerryBank;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /berry                              -> affiche ton argent
 * /berry set|add|remove <joueur> <n>  -> modifie l'argent d'un joueur (administrateurs)
 */
public final class BerryCommands {
	private BerryCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("berry")
				.executes(ctx -> show(ctx.getSource()))
				.then(change("set"))
				.then(change("add"))
				.then(change("remove")));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> change(String mode) {
		return Commands.literal(mode)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("joueur", EntityArgument.player())
						.then(Commands.argument("montant", LongArgumentType.longArg(0, BerryBank.MAX))
								.executes(ctx -> apply(ctx.getSource(), EntityArgument.getPlayer(ctx, "joueur"),
										LongArgumentType.getLong(ctx, "montant"), mode))));
	}

	private static int show(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		long balance = BerryBank.get(player);
		source.sendSuccess(() -> Component.literal("Tes berries : " + balance), false);
		return 1;
	}

	private static int apply(CommandSourceStack source, ServerPlayer target, long amount, String mode) {
		switch (mode) {
			case "set" -> BerryBank.set(target, amount);
			case "add" -> BerryBank.add(target, amount);
			default -> BerryBank.set(target, Math.max(0L, BerryBank.get(target) - amount));
		}
		long balance = BerryBank.get(target);
		source.sendSuccess(() -> Component.literal("Berries de " + target.getName().getString() + " : " + balance), true);
		return 1;
	}
}
