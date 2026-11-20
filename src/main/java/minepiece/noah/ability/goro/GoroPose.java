package minepiece.noah.ability.goro;

import minepiece.noah.data.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Pose du joueur pour les animations du Goro Goro no Mi, synchronisée à tous les clients (attachement GORO_POSE :
 * temps de jeu du début * 8 + pose). Chaque client calcule l'avancement de l'animation à partir du temps de jeu.
 */
public final class GoroPose {
	public static final int NONE = 0;
	/** Sango : bras droit tendu vers l'avant (charge et lancer). */
	public static final int ARM_FORWARD = 1;
	/** El Thor : bras droit levé vers le ciel. */
	public static final int ARM_UP = 2;
	/** Raigo : les deux mains levées (charge). */
	public static final int BOTH_UP = 3;
	/** Raigo : lancer à deux mains vers l'avant. */
	public static final int THROW = 4;
	/** Défibrillateur : chute en arrière puis allongé sur le dos. */
	public static final int KO = 5;
	/** Défibrillateur : se relève. */
	public static final int GET_UP = 6;

	/** Durées des animations (ticks), partagées avec le client. */
	public static final int KO_FALL_TICKS = 10;
	public static final int GET_UP_TICKS = 16;
	public static final int THROW_TICKS = 10;

	private GoroPose() {}

	public static void set(ServerPlayer player, int pose) {
		player.setAttached(ModAttachments.GORO_POSE, player.level().getGameTime() * 8L + pose);
	}

	public static void clear(ServerPlayer player) {
		player.removeAttached(ModAttachments.GORO_POSE);
	}

	public static int pose(Player player) {
		Long v = player.getAttached(ModAttachments.GORO_POSE);
		return v == null ? NONE : (int) (v & 7L);
	}

	/** Ticks écoulés depuis le début de la pose (avec la fraction de tick côté client). */
	public static float elapsed(Player player, float partial) {
		Long v = player.getAttached(ModAttachments.GORO_POSE);
		return v == null ? -1.0F : (float) (player.level().getGameTime() - (v >> 3)) + partial;
	}
}
