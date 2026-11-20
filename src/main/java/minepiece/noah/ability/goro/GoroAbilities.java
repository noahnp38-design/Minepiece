package minepiece.noah.ability.goro;

import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Goro Goro no Mi (fruit de la Foudre, Ener) : point d'entrée.
 * - attaque 1 : Sango, touche maintenue (éclair / chien de foudre / dragon de foudre) ;
 * - attaque 2 : El Thor (cumulonimbus + gigantesque éclair sur la zone visée) ;
 * - attaque 3 : Raigo, touche maintenue (sphère de foudre géante qui grossit, puis lancée) ;
 * - passif : Défibrillateur (une réanimation par vie).
 * Tout est calculé côté serveur (dégâts, terrain, modèles 3D, particules, poses) : identique pour tous les joueurs.
 */
public final class GoroAbilities {
	private GoroAbilities() {}

	public static void init() {
		GoroConfig.load();
		Defibrillator.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Sango.tick(server);
			ElThor.tick(server);
			Raigo.tick(server);
			Defibrillator.tick(server);
			GoroFx.tick();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Defibrillator.cleanupAll(server);
			cleanupAll();
		});
	}

	public static boolean cast(ServerPlayer player, ServerLevel level, int slot) {
		if (Defibrillator.isKo(player.getUUID())) {
			return false;
		}
		return switch (slot) {
			case 0 -> Sango.start(player, level);
			case 1 -> ElThor.cast(player, level);
			case 2 -> Raigo.start(player, level);
			default -> false;
		};
	}

	public static void release(ServerPlayer player, int slot) {
		if (slot == 0) {
			Sango.release(player);
		} else if (slot == 2) {
			Raigo.release(player);
		}
	}

	/** Sango et Raigo : la recharge démarre au lancement (touche relâchée), pas à l'appui. */
	public static boolean delayedCooldown(int slot) {
		return slot == 0 || slot == 2;
	}

	/** Vrai si le joueur est KO (Défibrillateur) : aucune attaque possible. */
	public static boolean isKo(UUID player) {
		return Defibrillator.isKo(player);
	}

	/** Coupe les charges en cours (KO, fruit retiré...). Les attaques déjà lancées vont au bout. */
	static void forgetAttacks(ServerPlayer player) {
		Sango.forget(player.getUUID());
		Raigo.forget(player.getUUID());
		ElThor.forget(player.getUUID(), player);
		if (GoroPose.pose(player) != GoroPose.KO && GoroPose.pose(player) != GoroPose.GET_UP) {
			GoroPose.clear(player);
		}
	}

	/** Déconnexion / annulation. */
	public static void forget(UUID id, ServerPlayer player) {
		Sango.forget(id);
		Raigo.forget(id);
		ElThor.forget(id, player);
		Defibrillator.forget(id, player);
		if (player != null) {
			GoroPose.clear(player);
		}
	}

	public static void cleanupAll() {
		Sango.cleanupAll();
		ElThor.cleanupAll();
		Raigo.cleanupAll();
		GoroFx.cleanupAll();
	}
}
