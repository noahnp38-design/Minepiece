package minepiece.noah.ability.ishi;

/**
 * Chronologie des animations du Ishi Ishi no Mi, PARTAGÉE entre le serveur (effets, dégâts, terrain) et les clients
 * (poses, modèles 3D, vue à la 1re personne). Le serveur synchronise seulement le temps de début et le type d'animation
 * (attachements ISHI_ACTION et ISHI_TITAN) : chaque client recalcule l'avancement à partir du temps de jeu.
 */
public final class IshiAnim {
	// ---- types d'action (ISHI_ACTION = début * 256 + matière * 32 + type)
	public static final int NONE = 0;
	public static final int ERUPT = 1;
	public static final int FIST = 2;
	public static final int TITAN_ERUPT = 3;
	public static final int TITAN_PUNCH = 4;

	// ---- W : bras levé (0-8), main tendue vers le sol, tremblement et fissures (8-22), éruption à 22
	public static final int ERUPT_RAISE = 8;
	public static final int ERUPT_AT = 22;
	public static final int ERUPT_TOTAL = 42;

	// ---- X : formation du bras (0-20), armé (20-30), frappe (30-34), impact à 33, maintien, retour (38-54)
	public static final int FIST_FORM = 20;
	public static final int FIST_WIND = 30;
	public static final int FIST_STRIKE = 34;
	public static final int FIST_IMPACT = 33;
	public static final int FIST_HOLD = 38;
	public static final int FIST_TOTAL = 54;

	// ---- Titan : W (bras levé, vague de pics à 12), X (armé 0-12, frappe 12-18, impact à 17, retour 18-34)
	public static final int TITAN_ERUPT_AT = 12;
	public static final int TITAN_ERUPT_TOTAL = 34;
	public static final int TITAN_WIND = 12;
	public static final int TITAN_STRIKE = 18;
	public static final int TITAN_IMPACT = 17;
	public static final int TITAN_PUNCH_TOTAL = 36;

	// ---- V (ISHI_TITAN = début * 64 + matière * 4 + phase)
	public static final int PHASE_FORM = 1;
	public static final int PHASE_COLLAPSE = 2;
	/** Concentration (0-10), enfoncement dans le sol (10-34), formation du titan (34-110). */
	public static final int SINK_START = 10;
	public static final int SINK_END = 34;
	public static final int FORM_END = 110;
	/** Effondrement : fissures (0-10), chute des blocs (10-40), le joueur ressort du sol (40-56). */
	public static final int COLLAPSE_FALL = 10;
	public static final int COLLAPSE_END = 40;
	public static final int EMERGE_END = 56;
	/** Profondeur (blocs, échelle 1) atteinte par le joueur qui s'enfonce dans la pierre. */
	public static final float SINK_DEPTH = 2.2F;

	// ---- matières (textures des pics, du bras, du titan)
	public static final String[] MATS = {"stone", "deepslate", "andesite", "tuff", "cobble", "sandstone"};

	private IshiAnim() {}

	public static long action(long start, int mat, int kind) {
		return start * 256L + (mat & 7) * 32L + (kind & 31);
	}

	public static int kind(long v) {
		return (int) (v & 31L);
	}

	public static int actionMat(long v) {
		return (int) ((v >> 5) & 7L);
	}

	public static long actionStart(long v) {
		return v >> 8;
	}

	public static long titan(long start, int mat, int phase) {
		return start * 64L + (mat & 15) * 4L + (phase & 3);
	}

	public static int phase(long v) {
		return (int) (v & 3L);
	}

	public static int titanMat(long v) {
		return (int) ((v >> 2) & 15L);
	}

	public static long titanStart(long v) {
		return v >> 6;
	}

	/** Variante de texture du titan / du bras : pierre, deepslate ou grès. */
	public static String bodyVariant(int mat) {
		return switch (mat) {
			case 1, 3 -> "deepslate";
			case 5 -> "sandstone";
			default -> "stone";
		};
	}

	public static float smooth(float x) {
		x = Math.max(0.0F, Math.min(1.0F, x));
		return x * x * (3.0F - 2.0F * x);
	}

	/** Enfoncement du joueur dans le sol (blocs, échelle 1) au temps t de la phase de formation. */
	public static float sinkDepth(float t) {
		if (t < SINK_START) {
			return 0.0F;
		}
		return SINK_DEPTH * smooth((t - SINK_START) / (SINK_END - SINK_START));
	}

	/** Avancement de la formation du titan (0..1). */
	public static float formProgress(float t) {
		return Math.max(0.0F, Math.min(1.0F, (t - SINK_END) / (float) (FORM_END - SINK_END)));
	}
}
