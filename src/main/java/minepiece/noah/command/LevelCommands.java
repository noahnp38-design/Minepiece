package minepiece.noah.command;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerLevel;
import minepiece.noah.level.LevelSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /xpadd <quantité>             -> te donne de l'XP
 * /xpadd <joueur> <quantité>    -> donne de l'XP à un joueur (ou @a, @p...)
 * Réservé aux administrateurs (niveau de permission "gamemaster").
 */
public final class LevelCommands {
	private LevelCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		SuggestionProvider<CommandSourceStack> amounts = (ctx, builder) -> {
			builder.suggest(100);
			builder.suggest(500);
			builder.suggest(1000);
			builder.suggest(5000);
			return builder.buildFuture();
		};

		dispatcher.register(Commands.literal("xpadd")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("quantite", LongArgumentType.longArg(1, LevelSystem.MAX_ADD))
						.suggests(amounts)
						.executes(ctx -> give(ctx.getSource(),
								List.of(ctx.getSource().getPlayerOrException()),
								LongArgumentType.getLong(ctx, "quantite"))))
				.then(Commands.argument("joueur", EntityArgument.players())
						.then(Commands.argument("quantite", LongArgumentType.longArg(1, LevelSystem.MAX_ADD))
								.suggests(amounts)
								.executes(ctx -> give(ctx.getSource(),
										EntityArgument.getPlayers(ctx, "joueur"),
										LongArgumentType.getLong(ctx, "quantite"))))));
	}

	private static int give(CommandSourceStack source, Collection<ServerPlayer> targets, long amount) {
		for (ServerPlayer target : targets) {
			LevelSystem.addXp(target, amount);
			target.sendSystemMessage(Component.literal("+" + amount + " XP"), true);
		}

		if (targets.size() == 1) {
			ServerPlayer target = targets.iterator().next();
			PlayerLevel level = ModAttachments.level(target);
			String progress = LevelSystem.isMaxLevel(level.level())
					? "niveau max"
					: level.xp() + " / " + LevelSystem.xpToNext(level.level()) + " XP";
			source.sendSuccess(() -> Component.literal("+" + amount + " XP pour " + target.getName().getString()
					+ " : niveau " + level.level() + " (" + progress + ")"), true);
		} else {
			source.sendSuccess(() -> Component.literal("+" + amount + " XP donnés à " + targets.size() + " joueurs"), true);
		}
		return targets.size();
	}
}
