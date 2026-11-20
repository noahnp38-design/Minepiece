package minepiece.noah.ability.giraffe;

/**
 * Constantes et fonctions d'animation PARTAGÉES par le serveur (durées des attaques) et le client (rendu).
 * Tout est calculé à partir du temps de jeu : aucun état à synchroniser en plus de GiraffeState.
 */
public final class GiraffeAnim {
	public static final int HUMAN = 0;
	public static final int HYBRID = 1;
	public static final int GIRAFFE = 2;

	public static final int ANIM_NONE = 0;
	public static final int ANIM_RANKYAKU = 1;
	public static final int ANIM_BIGAN = 2;
	public static final int ANIM_KIRIN = 3;

	// ---- Transformations (ticks)
	public static final int T_TO_HYBRID = 30;       // 1,5 s
	public static final int T_TO_GIRAFFE = 40;      // 2 s
	public static final int T_TO_HUMAN = 24;        // 1,2 s

	// ---- Rankyaku : coup de pied
	public static final int RANKYAKU_TICKS = 10;

	// ---- Bigan : 0,25 s de préparation, 0,35 s d'allongement, 0,25 s de retour
	public static final int BIGAN_PREP = 5;
	public static final int BIGAN_FLY = 7;
	public static final int BIGAN_RETURN = 5;
	public static final double BIGAN_RANGE = 8.0;

	// ---- Kirin Roundabout : 0,5 s de préparation puis 2,5 s de rotation
	public static final int KIRIN_PREP = 10;
	public static final int KIRIN_SPIN = 50;

	private GiraffeAnim() {}

	public static int transitionTicks(int from, int to) {
		if (to == HUMAN) {
			return T_TO_HUMAN;
		}
		return to == HYBRID && from == HUMAN ? T_TO_HYBRID : to == GIRAFFE ? T_TO_GIRAFFE : T_TO_HYBRID;
	}

	/** Avancement 0..1 (lissé) de la transition en cours ; 1 = terminée. */
	public static float transition(GiraffeState s, float now) {
		float t = (now - s.formStart()) / transitionTicks(s.prev(), s.form());
		t = Math.max(0.0F, Math.min(1.0F, t));
		return t * t * (3.0F - 2.0F * t);
	}

	/** Poids (0..1) d'une forme dans le mélange visuel : monte pour la forme visée, descend pour la forme quittée. */
	public static float weight(GiraffeState s, int form, float now) {
		float p = transition(s, now);
		if (s.form() == s.prev()) {
			return s.form() == form ? 1.0F : 0.0F;
		}
		if (form == s.form()) {
			return p;
		}
		if (form == s.prev()) {
			return 1.0F - p;
		}
		return 0.0F;
	}

	public static boolean transitioning(GiraffeState s, float now) {
		return s.form() != s.prev() && now - s.formStart() < transitionTicks(s.prev(), s.form());
	}

	/** Durée totale de l'animation d'une attaque, en ticks. */
	public static int animTicks(int anim) {
		return switch (anim) {
			case ANIM_RANKYAKU -> RANKYAKU_TICKS;
			case ANIM_BIGAN -> BIGAN_PREP + BIGAN_FLY + BIGAN_RETURN;
			case ANIM_KIRIN -> KIRIN_PREP + KIRIN_SPIN;
			default -> 0;
		};
	}

	/** Temps écoulé (ticks, fractionnaire) dans l'animation d'attaque, ou -1 si aucune n'est en cours. */
	public static float animTime(GiraffeState s, float now) {
		if (s.anim() == ANIM_NONE) {
			return -1.0F;
		}
		float t = now - s.animStart();
		return t < 0.0F || t > animTicks(s.anim()) ? -1.0F : t;
	}

	/** Bigan : extension du cou en blocs (0 au repos). Courbe : préparation (petite avancée), détente rapide, retour. */
	public static double biganReach(float t) {
		if (t < 0.0F) {
			return 0.0;
		}
		if (t < BIGAN_PREP) {
			return 0.6 * (t / BIGAN_PREP);
		}
		if (t < BIGAN_PREP + BIGAN_FLY) {
			float k = (t - BIGAN_PREP) / BIGAN_FLY;
			return 0.6 + (BIGAN_RANGE - 0.6) * (1.0 - (1.0 - k) * (1.0 - k));
		}
		float k = Math.min(1.0F, (t - BIGAN_PREP - BIGAN_FLY) / BIGAN_RETURN);
		return BIGAN_RANGE * (1.0 - k) * (1.0 - k);
	}

	/** Kirin : angle de rotation du corps en degrés (accélère pendant la préparation puis tourne vite). */
	public static float kirinAngle(float t) {
		if (t < 0.0F) {
			return 0.0F;
		}
		if (t < KIRIN_PREP) {
			return 2.0F * t * t;
		}
		return 200.0F + 40.0F * (t - KIRIN_PREP);
	}

	/** Kirin : longueur de cou en blocs (s'allonge progressivement pendant la rotation). */
	public static double kirinReach(float t) {
		if (t < 0.0F) {
			return 0.0;
		}
		if (t < KIRIN_PREP) {
			return 1.0 * (t / KIRIN_PREP);
		}
		float k = Math.min(1.0F, (t - KIRIN_PREP) / KIRIN_SPIN);
		double len = 1.0 + 4.0 * k;                         // 1 -> 5 blocs
		float end = KIRIN_PREP + KIRIN_SPIN;
		if (t > end - 4.0F) {                               // se rétracte sur les 4 derniers ticks
			len *= Math.max(0.0F, (end - t) / 4.0F);
		}
		return len;
	}
}
