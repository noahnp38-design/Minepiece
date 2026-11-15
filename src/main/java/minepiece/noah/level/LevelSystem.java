package minepiece.noah.level;

import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Système d'expérience et de niveaux. Tout se passe côté serveur : le client reçoit seulement l'affichage.
 * Pour l'instant le niveau sert uniquement à suivre la progression (aucun bonus automatique).
 */
public final class LevelSystem {
	public static final int MAX_LEVEL = 100;
	/** Quantité maximale d'XP ajoutable en une fois (évite les débordements). */
	public static final long MAX_ADD = 1_000_000_000L;

	private LevelSystem() {}

	/** XP nécessaire pour passer du niveau donné au suivant. C'EST ICI QU'ON RÈGLE LA COURBE (niveau 1 : 220, niveau 15 : 500...). */
	public static long xpToNext(int level) {
		return 200L + 20L * level;
	}

	public static boolean isMaxLevel(int level) {
		return level >= MAX_LEVEL;
	}

	/** Progression dans le niveau actuel, entre 0.0 et 1.0. */
	public static double progress(PlayerLevel level) {
		if (isMaxLevel(level.level())) {
			return 1.0;
		}
		return Math.min(1.0, level.xp() / (double) xpToNext(level.level()));
	}

	/**
	 * Ajoute de l'XP (déclenche les montées de niveau, l'excédent est conservé).
	 * @return le nombre de niveaux gagnés
	 */
	public static int addXp(ServerPlayer player, long amount) {
		if (amount <= 0) {
			return 0;
		}
		PlayerLevel before = ModAttachments.level(player);
		int level = before.level();
		if (isMaxLevel(level)) {
			return 0;
		}

		long xp = before.xp() + Math.min(amount, MAX_ADD);
		while (!isMaxLevel(level) && xp >= xpToNext(level)) {
			xp -= xpToNext(level);
			level++;
		}
		if (isMaxLevel(level)) {
			xp = 0;
		}

		player.setAttached(ModAttachments.LEVEL, new PlayerLevel(level, xp));

		int gained = level - before.level();
		if (gained > 0) {
			player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.0F);
			player.sendSystemMessage(Component.literal("Niveau supérieur ! Tu es maintenant niveau " + level + "."));
			LevelEvents.LEVEL_UP.invoker().onLevelUp(player, before.level(), level);
		}
		return gained;
	}
}
