package minepiece.noah.ability.zushi;

import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Zushi Zushi no Mi (fruit de la Gravité, Fujitora) : point d'entrée des 3 attaques.
 * - attaque 1 : Plateforme gravitationnelle (GravityPlatform) ; la relancer = atterrir ;
 * - attaque 2 : Écrasement gravitationnel (GravityCrush) ;
 * - attaque 3 : Météorite gravitationnelle, touche maintenue = charge sur 3 niveaux (GravityMeteor).
 * Tout est calculé côté serveur (dégâts, terrain, modèles 3D, particules) : c'est identique pour tous les joueurs.
 */
public final class ZushiAbilities {
	private ZushiAbilities() {}

	public static void init() {
		ZushiConfig.load();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			GravityPlatform.tick(server);
			GravityCrush.tick();
			GravityMeteor.tick(server);
			GravityMeteor.tickShocks();
			ZushiFx.tickDebris();
			ZushiTerrain.tick();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> cleanupAll());
		// Modèle 3D resté dans un chunk après un arrêt brutal : supprimé dès que le chunk se recharge.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (ZushiDisplay.isStale(entity)) {
				entity.discard();
			}
		});
	}

	/** Touche pressée. @return true si l'attaque part (message + recharge gérés par FruitAbilities). */
	public static boolean cast(ServerPlayer player, ServerLevel level, int slot) {
		return switch (slot) {
			case 0 -> GravityPlatform.cast(player, level);
			case 1 -> GravityCrush.cast(player, level);
			case 2 -> GravityMeteor.start(player, level);
			default -> false;
		};
	}

	/** Touche relâchée (météorite : lancement). */
	public static void release(ServerPlayer player, int slot) {
		if (slot == 2) {
			GravityMeteor.release(player);
		}
	}

	/**
	 * Attaques dont la recharge démarre plus tard que l'appui : la plateforme (à l'atterrissage) et la météorite
	 * (au lancement).
	 */
	public static boolean delayedCooldown(int slot) {
		return slot == 0 || slot == 2;
	}

	/** Mort, déconnexion, fruit retiré : coupe les capacités en cours du joueur. */
	public static void forget(UUID player, ServerPlayer online) {
		GravityPlatform.forget(player, online);
		GravityMeteor.forget(player);
		GravityCrush.forget(player);
	}

	public static void cleanupAll() {
		GravityPlatform.cleanupAll();
		GravityCrush.cleanupAll();
		GravityMeteor.cleanupAll();
		ZushiFx.cleanupAll();
		ZushiTerrain.cleanupAll();
		ZushiDisplay.cleanupAll();
	}
}
