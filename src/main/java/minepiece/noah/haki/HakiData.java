package minepiece.noah.haki;

import minepiece.noah.data.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Progression et état du Haki d'un joueur.
 * - Maîtrises : Armement 1 à 5 (avant-bras, bras, jambes, tête, corps entier), Observation 1 à 3, Rois 0 ou 1 (pour
 *   l'instant). Armement et Observation commencent au niveau 1, montent avec l'XP
 *   (coups portés avec l'Armement, esquives réussies, temps d'Observation) ; le Haki des Rois est à 0 tant qu'il n'est
 *   pas éveillé (tirage rare au premier passage, ou /haki eveil), puis monte avec ses propres utilisations.
 * - En créatif (réglage creativeAllMastery) : tout est au niveau 3 pour tester toutes les techniques.
 */
public final class HakiData {
	public static final int ARM = 0;
	public static final int OBS = 1;
	public static final int KING = 2;

	public static final int F_ARM = 1;
	public static final int F_OBS = 2;
	public static final int F_FUTURE = 4;
	public static final int F_INFUSE = 8;
	/** Haki des Rois maintenu (touche L enfoncée : éclairs qui assomment). */
	public static final int F_KING_HOLD = 16;
	/** Maîtrise maximale de chaque Haki (Armement 5 : avant-bras, bras, jambes, tête, corps entier ; Rois 1 pour l'instant). */
	public static final int[] MAX = {5, 3, 1};

	private static final String[] NAMES = {"Armement", "Observation", "Rois"};
	private static final String[][] LEVELS = {
			{"-", "Avant-bras", "Bras", "Jambes", "Tête", "Corps entier"},
			{"-", "Détection des présences", "Esquives améliorées", "Vision du futur"},
			{"Non éveillé", "Éclairs du Roi"}};

	private HakiData() {}

	public static String name(int type) {
		return NAMES[type];
	}

	public static String levelName(int type, int mastery) {
		return LEVELS[type][Math.max(0, Math.min(MAX[type], mastery))];
	}

	/** Maîtrise enregistrée (sans le bonus du créatif). */
	public static int storedMastery(Entity p, int type) {
		Integer packed = p.getAttached(ModAttachments.HAKI_MASTERY);
		if (packed == null) {
			return type == KING ? 0 : 1;
		}
		return Math.min(MAX[type], (packed >> (type * 4)) & 15);
	}

	/** Maîtrise utilisée par les techniques (créatif = 3 partout si le réglage est actif). */
	public static int mastery(Player p, int type) {
		if (p.isCreative() && HakiConfig.get().creativeAllMastery) {
			return MAX[type];
		}
		return storedMastery(p, type);
	}

	public static void setMastery(ServerPlayer p, int type, int value) {
		int packed = 0;
		for (int t = 0; t < 3; t++) {
			int v = t == type ? Math.max(0, Math.min(MAX[t], value)) : storedMastery(p, t);
			packed |= v << (t * 4);
		}
		p.setAttached(ModAttachments.HAKI_MASTERY, packed);
		refreshFlags(p);
	}

	public static int xp(ServerPlayer p, int type) {
		Long v = p.getAttached(ModAttachments.HAKI_XP);
		return v == null ? 0 : (int) ((v >> (type * 20)) & 0xFFFFF);
	}

	/** Ajoute de l'expérience ; passe au niveau suivant quand le seuil est atteint. */
	public static void addXp(ServerPlayer p, int type, int amount) {
		int m = storedMastery(p, type);
		if (amount <= 0 || m >= MAX[type] || (type == KING && m == 0)) {
			return;
		}
		Long v = p.getAttached(ModAttachments.HAKI_XP);
		long packed = v == null ? 0L : v;
		int xp = Math.min(0xFFFFF, xp(p, type) + amount);
		packed = (packed & ~(0xFFFFFL << (type * 20))) | ((long) xp << (type * 20));
		p.setAttached(ModAttachments.HAKI_XP, packed);
		HakiConfig cfg = HakiConfig.get();
		int[] steps = type == ARM ? cfg.armXp : type == OBS ? cfg.obsXp : cfg.kingXp;
		int need = steps[Math.min(steps.length - 1, m - 1)];
		if (xp >= need) {
			setMastery(p, type, m + 1);
			p.sendSystemMessage(Component.literal("§6Haki de " + (type == KING ? "s " : "l'") + NAMES[type] + " §7: maîtrise §f"
					+ (m + 1) + " §7- §e" + levelName(type, m + 1)));
			p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
	}

	// ------------------------------------------------------------------ état actif (synchronisé à tous pour le rendu)

	public static int flags(Entity p) {
		Integer f = p.getAttached(ModAttachments.HAKI_FLAGS);
		return f == null ? 0 : f;
	}

	public static boolean has(Entity p, int flag) {
		return (flags(p) & flag) != 0;
	}

	public static void setFlag(ServerPlayer p, int flag, boolean on) {
		int f = flags(p) & 31;
		f = on ? f | flag : f & ~flag;
		// bits 5-7 : maîtrise de l'Armement (parties recouvertes), bits 8-9 : maîtrise de l'Observation (rayon)
		p.setAttached(ModAttachments.HAKI_FLAGS, f | (Math.min(7, mastery(p, ARM)) << 5) | (Math.min(3, mastery(p, OBS)) << 8));
	}

	/** Réécrit les maîtrises visibles (étendue du recouvrement, rayon de détection) dans les drapeaux. */
	public static void refreshFlags(ServerPlayer p) {
		setFlag(p, 0, true);
	}

	public static double energy(Entity p) {
		Float e = p.getAttached(ModAttachments.HAKI_ENERGY);
		return e == null ? HakiConfig.get().energyMax : e;
	}
}
