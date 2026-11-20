package minepiece.noah.economy;

import minepiece.noah.data.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

/**
 * Argent du joueur : les berries (monnaie du mod, pour les futurs shops). Tout se passe côté serveur ; le client reçoit
 * seulement le solde (affiché dans le menu K, onglet Profil).
 *
 * Utilisation dans un shop :
 *   if (BerryBank.remove(player, prix)) { donner l'objet; }   // false = pas assez d'argent, rien n'est retiré
 *   BerryBank.add(player, gain);
 */
public final class BerryBank {
	/** Solde maximal (évite les débordements). */
	public static final long MAX = 1_000_000_000_000L;

	private BerryBank() {}

	public static long get(ServerPlayer player) {
		Long v = player.getAttached(ModAttachments.BERRIES);
		return v == null ? 0L : v;
	}

	public static void set(ServerPlayer player, long amount) {
		player.setAttached(ModAttachments.BERRIES, Math.max(0L, Math.min(MAX, amount)));
	}

	/** Ajoute des berries (plafonné à MAX). @return le nouveau solde. */
	public static long add(ServerPlayer player, long amount) {
		long result = Math.max(0L, Math.min(MAX, get(player) + Math.max(0L, amount)));
		player.setAttached(ModAttachments.BERRIES, result);
		return result;
	}

	public static boolean canAfford(ServerPlayer player, long amount) {
		return amount >= 0 && get(player) >= amount;
	}

	/** Retire des berries. @return false (sans rien retirer) si le joueur n'a pas assez. */
	public static boolean remove(ServerPlayer player, long amount) {
		if (!canAfford(player, amount)) {
			return false;
		}
		player.setAttached(ModAttachments.BERRIES, get(player) - amount);
		return true;
	}
}
