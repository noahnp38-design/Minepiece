package minepiece.noah.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import minepiece.noah.haki.HakiData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /haki                                   -> tes maîtrises de Haki et ton expérience
 * /haki set <joueur> <armement 0-5|observation 0-3|rois 0-1>   (opérateurs)
 * /haki eveil <joueur>                    -> éveille le Haki des Rois (opérateurs)
 */
public final class HakiCommands {
	private static final String[] TYPES = {"armement", "observation", "rois"};

	private HakiCommands() {}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var set = Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (int t = 0; t < 3; t++) {
			final int type = t;
			set.then(Commands.argument("joueur", EntityArgument.player())
					.then(Commands.literal(TYPES[t])
							.then(Commands.argument("niveau", IntegerArgumentType.integer(0, HakiData.MAX[type]))
									.executes(ctx -> setLevel(ctx.getSource(), EntityArgument.getPlayer(ctx, "joueur"), type,
											IntegerArgumentType.getInteger(ctx, "niveau"))))));
		}
		dispatcher.register(Commands.literal("haki")
				.executes(ctx -> show(ctx.getSource()))
				.then(set)
				.then(Commands.literal("eveil").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("joueur", EntityArgument.player())
								.executes(ctx -> setLevel(ctx.getSource(), EntityArgument.getPlayer(ctx, "joueur"), HakiData.KING,
										Math.max(1, HakiData.storedMastery(EntityArgument.getPlayer(ctx, "joueur"), HakiData.KING)))))));
	}

	private static int show(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		StringBuilder sb = new StringBuilder("§6Haki");
		for (int t = 0; t < 3; t++) {
			int m = HakiData.mastery(p, t);
			sb.append("\n§7").append(HakiData.name(t)).append(" §8: §f").append(m).append("/").append(HakiData.MAX[t]).append(" §e").append(HakiData.levelName(t, m))
					.append(" §8(XP ").append(HakiData.xp(p, t)).append(")");
		}
		sb.append("\n§8Touches : J Armement, K Observation (Maj+K Vision du futur), L Rois (maintenir), R esquive");
		source.sendSuccess(() -> Component.literal(sb.toString()), false);
		return 1;
	}

	private static int setLevel(CommandSourceStack source, ServerPlayer target, int type, int level) {
		HakiData.setMastery(target, type, level);
		source.sendSuccess(() -> Component.literal("§7" + target.getName().getString() + " : Haki " + HakiData.name(type) + " = §f" + level
				+ " §e" + HakiData.levelName(type, level)), true);
		return 1;
	}
}
