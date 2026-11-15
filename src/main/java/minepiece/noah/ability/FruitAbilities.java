package minepiece.noah.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.ability.mera.MeraAbilities;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Gère les pouvoirs des fruits du démon : effets passifs, attaques et temps de recharge. */
public final class FruitAbilities {
	/**
	 * Fin de recharge (en game time) de chaque attaque, par joueur. Non sauvegardé : repart à zéro au redémarrage.
	 * Volontairement conservé à la déconnexion : sinon se reconnecter remettrait les recharges à zéro.
	 */
	private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

	private FruitAbilities() {}

	public static void init() {
		LogiaProtection.init();
		MeraAbilities.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
				if (fruit == DevilFruit.HIE_HIE_NO_MI && player.level() instanceof ServerLevel level) {
					IceAbilities.tickPassive(player, level);
				} else if (fruit == DevilFruit.MERA_MERA_NO_MI) {
					MeraAbilities.tickPassive(player);
				}
			}
			MeraAbilities.tick(server);
			IceAbilities.tick(server);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MeraAbilities.forget(handler.getPlayer().getUUID());
			IceAbilities.forget(handler.getPlayer().getUUID());
		});
	}

	/** Appelé quand le client demande de lancer l'attaque n° slot (0, 1 ou 2). Tout est revérifié ici. */
	public static void use(ServerPlayer player, int slot) {
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
		if (fruit == null || slot < 0 || slot >= DevilFruit.ABILITY_COUNT) {
			return;
		}
		if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		long now = level.getGameTime();
		long[] ready = COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new long[DevilFruit.ABILITY_COUNT]);
		if (now < ready[slot]) {
			long seconds = (ready[slot] - now + 19) / 20;
			player.sendSystemMessage(Component.literal(fruit.abilityName(slot) + " : encore " + seconds + " s"), true);
			return;
		}

		boolean cast = switch (fruit) {
			case HIE_HIE_NO_MI -> IceAbilities.cast(player, level, slot);
			case MERA_MERA_NO_MI -> MeraAbilities.start(player, level, slot);
		};
		if (cast) {
			// Les attaques maintenues du Mera Mera no Mi lancent elles-mêmes leur recharge quand elles se terminent.
			// Idem pour l'Ice Age (attaque 3 du Hie Hie no Mi) : maintenue, recharge lancée au relâchement.
			boolean holdAttack = fruit == DevilFruit.MERA_MERA_NO_MI || (fruit == DevilFruit.HIE_HIE_NO_MI && slot == 2);
			if (!holdAttack) {
				ready[slot] = now + fruit.cooldownTicks(slot);
			}
			player.sendSystemMessage(Component.literal(fruit.abilityName(slot) + " !"), true);
		}
	}

	/** Touche relâchée (attaques maintenues : Fire Gun, charge de l'Entei). */
	public static void release(ServerPlayer player, int slot) {
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
		if (fruit == DevilFruit.MERA_MERA_NO_MI) {
			MeraAbilities.release(player, slot);
		} else if (fruit == DevilFruit.HIE_HIE_NO_MI) {
			IceAbilities.release(player, slot);
		}
	}

	/** Lance la recharge d'une attaque (pour les attaques qui se terminent plus tard que l'appui). */
	public static void startCooldown(ServerPlayer player, int slot, int ticks) {
		long[] ready = COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new long[DevilFruit.ABILITY_COUNT]);
		ready[slot] = player.level().getGameTime() + ticks;
	}
}
