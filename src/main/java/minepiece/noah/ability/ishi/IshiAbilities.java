package minepiece.noah.ability.ishi;

import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ishi Ishi no Mi (fruit de la Pierre, Pica) — Paramecia : point d'entrée.
 * - attaque 1 (W) : Éruption rocheuse — pics de pierre qui surgissent du sol visé (forêt de pics en Titan) ;
 * - attaque 2 (X) : Poing colossal — le bras se change en pierre et frappe (poing du titan en Titan) ;
 * - attaque 3 (V) : Titan de pierre — fusion avec le terrain puis corps gigantesque ; V à nouveau pour se libérer.
 * Tout est décidé côté serveur (dégâts, terrain, modèles 3D temporaires) ; les clients animent les modèles du joueur
 * (bras, poing, titan) à partir de deux attachements synchronisés : identique pour tous les joueurs.
 */
public final class IshiAbilities {
	private IshiAbilities() {}

	public static void init() {
		IshiConfig.load();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			RockEruption.tick(server);
			ColossalFist.tick(server);
			StoneTitan.tick(server);
			IshiFx.tick();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> cleanupAll());
		// le titan encaisse les coups à la place du joueur
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p) || StoneTitan.allowDamage(p, source, amount));
	}

	public static boolean cast(ServerPlayer player, ServerLevel level, int slot) {
		UUID id = player.getUUID();
		boolean titan = StoneTitan.isTitan(id);
		boolean active = StoneTitan.isActive(id);
		if (slot == 2) {
			return StoneTitan.toggle(player, level);
		}
		if (titan && !active) {
			player.sendSystemMessage(Component.literal("§7Le titan n'est pas encore prêt."), true);
			return false;
		}
		return switch (slot) {
			case 0 -> RockEruption.cast(player, level, active);
			case 1 -> ColossalFist.cast(player, level, active);
			default -> false;
		};
	}

	/** Titan : la recharge démarre à la fin de l'effondrement (V sert aussi à se libérer). */
	public static boolean delayedCooldown(int slot) {
		return slot == 2;
	}

	public static void forget(UUID id, ServerPlayer player) {
		RockEruption.forget(id);
		ColossalFist.forget(id);
		StoneTitan.forget(id, player);
		if (player != null) {
			player.removeAttached(minepiece.noah.data.ModAttachments.ISHI_ACTION);
		}
	}

	public static void cleanupAll() {
		RockEruption.cleanupAll();
		ColossalFist.cleanupAll();
		StoneTitan.cleanupAll();
		IshiFx.cleanupAll();
	}
}
