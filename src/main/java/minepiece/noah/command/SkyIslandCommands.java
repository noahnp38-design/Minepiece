package minepiece.noah.command;

import com.mojang.brigadier.CommandDispatcher;
import minepiece.noah.world.SkyIslandData;
import minepiece.noah.world.SkyIslands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * /skypiea locate (opérateurs) : donne les coordonnées de l'île volante la plus proche (Overworld), même si elle
 * n'est pas encore générée — il suffit d'aller à cet endroit pour que les chunks la créent.
 */
public final class SkyIslandCommands {
	private SkyIslandCommands() {}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("skypiea")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("locate").executes(ctx -> locate(ctx.getSource()))));
	}

	private static int locate(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		if (level.dimension() != Level.OVERWORLD) {
			source.sendFailure(Component.literal("Les îles volantes n'existent que dans l'Overworld."));
			return 0;
		}
		SkyIslandData data = SkyIslandData.get();
		if (data == null) {
			source.sendFailure(Component.literal("Données de l'île Skypiea introuvables (voir les logs)."));
			return 0;
		}
		BlockPos from = BlockPos.containing(source.getPosition());
		SkyIslands.Island island = SkyIslands.locate(level, from, 8);
		if (island == null) {
			source.sendFailure(Component.literal("Aucune île volante trouvée à proximité (aucun grand océan assez profond ?)."));
			return 0;
		}
		BlockPos c = island.center(data);
		int top = island.baseY() + data.yMin + data.height;
		int dist = (int) Math.sqrt(c.distSqr(from));
		source.sendSuccess(() -> Component.literal("§bÎle volante Skypiea §7: centre §f" + c.getX() + " " + c.getZ()
				+ " §7(à " + dist + " blocs). Pour y aller : §f/tp @s " + c.getX() + " " + Math.min(top, level.getMaxY()) + " " + c.getZ()), false);
		return 1;
	}
}
