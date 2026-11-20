package minepiece.noah.command;

import java.util.Locale;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import minepiece.noah.data.Race;
import minepiece.noah.data.RaceEffects;
import minepiece.noah.network.OpenSelectionPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commandes du mod :
 *  /race                 -> affiche ta race et la liste des races
 *  /race <race>          -> change ta race (tout le monde)
 *  /prime                -> affiche ta prime
 *  /prime set <joueur> <montant>   (opérateurs)
 *  /prime add <joueur> <montant>   (opérateurs)
 */
public final class ModCommands {
	/** Prime maximale (évite les débordements). */
	private static final long MAX_BOUNTY = 1_000_000_000_000L;

	private ModCommands() {}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// ---- /race
			LiteralArgumentBuilder<CommandSourceStack> race = Commands.literal("race")
					.executes(ctx -> showRace(ctx.getSource()));
			for (Race r : Race.values()) {
				race.then(Commands.literal(r.name().toLowerCase(Locale.ROOT))
						.executes(ctx -> setRace(ctx.getSource(), r)));
			}
			dispatcher.register(race);
			LevelCommands.register(dispatcher);
			FruitCommands.register(dispatcher);
			BerryCommands.register(dispatcher);
			SkyIslandCommands.register(dispatcher);
			HakiCommands.register(dispatcher);

			// ---- /prime
			dispatcher.register(Commands.literal("prime")
					.executes(ctx -> showBounty(ctx.getSource()))
					.then(Commands.literal("set")
							.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
							.then(Commands.argument("joueur", EntityArgument.player())
									.then(Commands.argument("montant", LongArgumentType.longArg(0, MAX_BOUNTY))
											.executes(ctx -> changeBounty(ctx.getSource(),
													EntityArgument.getPlayer(ctx, "joueur"),
													LongArgumentType.getLong(ctx, "montant"), false)))))
					.then(Commands.literal("add")
							.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
							.then(Commands.argument("joueur", EntityArgument.player())
									.then(Commands.argument("montant", LongArgumentType.longArg(0, MAX_BOUNTY))
											.executes(ctx -> changeBounty(ctx.getSource(),
													EntityArgument.getPlayer(ctx, "joueur"),
													LongArgumentType.getLong(ctx, "montant"), true))))));
		});
	}

	// ------------------------------------------------------------------ /race

	private static int showRace(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);

		StringBuilder list = new StringBuilder();
		for (Race r : Race.values()) {
			if (list.length() > 0) {
				list.append(", ");
			}
			list.append(r.name().toLowerCase(Locale.ROOT));
		}
		String current = profile == null ? "aucune (tu n'as pas encore fait ton choix)" : profile.race().displayName();
		source.sendSuccess(() -> Component.literal("Ta race : " + current + ". Pour changer : /race <" + list + ">"), false);
		return 1;
	}

	private static int setRace(CommandSourceStack source, Race race) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);

		if (profile == null) {
			// Pas encore de profil : on rouvre l'écran de choix complet.
			ServerPlayNetworking.send(player, new OpenSelectionPayload());
			source.sendFailure(Component.literal("Fais d'abord ton choix de départ (race, faction, style)."));
			return 0;
		}
		if (profile.race() == race) {
			source.sendFailure(Component.literal("Tu es déjà " + race.displayName() + " !"));
			return 0;
		}

		player.setAttached(ModAttachments.PROFILE, new PlayerProfile(race, profile.faction(), profile.combatStyle()));
		RaceEffects.apply(player);
		source.sendSuccess(() -> Component.literal("Ta race est maintenant : " + race.displayName() + " !"), false);
		return 1;
	}

	// ------------------------------------------------------------------ /prime

	private static int showBounty(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		long bounty = ModAttachments.bounty(player);
		source.sendSuccess(() -> Component.literal("Ta prime : " + bounty + " berries"), false);
		return 1;
	}

	private static int changeBounty(CommandSourceStack source, ServerPlayer target, long amount, boolean add) {
		long current = ModAttachments.bounty(target);
		long result = add ? current + amount : amount;
		result = Math.max(0L, Math.min(MAX_BOUNTY, result));
		target.setAttached(ModAttachments.BOUNTY, result);

		long finalResult = result;
		source.sendSuccess(() -> Component.literal("Prime de " + target.getName().getString() + " : " + finalResult + " berries"), true);
		return 1;
	}
}
