package minepiece.noah.haki;

import minepiece.noah.data.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Poses animées du Haki, jouées sur le modèle du joueur (visibles en 3e personne et par tous les joueurs).
 * Valeur synchronisée : temps de jeu * 32 + pose ; le client calcule l'avancement depuis le temps de jeu, l'animation
 * s'arrête d'elle-même après sa durée (aucun nettoyage nécessaire).
 * Bit PERFECT (16) : esquive "parfaite" de la Vision du futur (même mouvement, plus ample et plus élégant).
 */
public final class HakiPose {
	public static final int NONE = 0;
	public static final int ARM_FOCUS = 1;       // concentration : poings serrés devant, le Haki recouvre les bras
	public static final int OBS_FOCUS = 2;       // tête inclinée, yeux fermés, perception
	public static final int KING_ROAR = 3;       // posture imposante, tête levée, torse bombé
	public static final int DODGE_LEFT = 4;      // buste vers la gauche (attaque venant de la droite)
	public static final int DODGE_RIGHT = 5;
	public static final int DODGE_BACK = 6;      // buste en arrière, bras écartés, petit pas en arrière
	public static final int DODGE_DUCK = 7;      // jambes pliées, tête baissée, l'attaque passe au-dessus
	public static final int DASH_LEFT = 8;       // esquive volontaire (touche R)
	public static final int DASH_RIGHT = 9;
	public static final int DASH_BACK = 10;
	public static final int DASH_FORWARD = 11;
	public static final int EMISSION = 12;       // coup de poing tendu (émission)
	public static final int INFUSE = 13;         // imprégnation du Haki des Rois (bras chargés)
	public static final int KING_HOLD = 14;      // Haki des Rois maintenu : posture imposante tant que la touche est enfoncée
	public static final int PERFECT = 16;

	private HakiPose() {}

	/** Durée (ticks) de chaque pose : même table côté client. */
	public static int duration(int pose) {
		return switch (pose & 15) {
			case ARM_FOCUS -> 22;
			case OBS_FOCUS -> 18;
			case KING_ROAR -> 32;
			case DODGE_LEFT, DODGE_RIGHT, DODGE_BACK, DODGE_DUCK -> (pose & PERFECT) != 0 ? 14 : 11;
			case DASH_LEFT, DASH_RIGHT, DASH_BACK, DASH_FORWARD -> 11;
			case EMISSION -> 10;
			case INFUSE -> 18;
			case KING_HOLD -> 1_000_000;
			default -> 0;
		};
	}

	public static void set(ServerPlayer p, int pose) {
		p.setAttached(ModAttachments.HAKI_POSE, p.level().getGameTime() * 32L + pose);
	}

	/** Pose dont l'animation a démarré à "start" (pour reprendre une pose au milieu, ex. retour à la normale). */
	public static void setAt(ServerPlayer p, int pose, long start) {
		p.setAttached(ModAttachments.HAKI_POSE, start * 32L + pose);
	}

	/** Pose en cours (avec le bit PERFECT) ou NONE si elle est terminée. */
	public static int current(Entity e) {
		Long v = e.getAttached(ModAttachments.HAKI_POSE);
		if (v == null) {
			return NONE;
		}
		int pose = (int) (v & 31L);
		long age = e.level().getGameTime() - (v >> 5);
		return age <= duration(pose) ? pose : NONE;
	}
}
