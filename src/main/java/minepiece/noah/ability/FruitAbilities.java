package minepiece.noah.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.ability.mera.MeraAbilities;
import minepiece.noah.ability.bomu.BomuAbilities;
import minepiece.noah.ability.supa.SupaAbilities;
import minepiece.noah.ability.giraffe.GiraffeAbilities;
import minepiece.noah.ability.suke.SukeAbilities;
import minepiece.noah.network.FruitCooldownPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
		DemonCurse.init();
		SukeAbilities.init();
		BomuAbilities.init();
		SupaAbilities.init();
		GiraffeAbilities.init();
		LogiaProtection.init();
		MeraAbilities.init();
		minepiece.noah.ability.zushi.ZushiAbilities.init();
		minepiece.noah.ability.goro.GoroAbilities.init();
		minepiece.noah.ability.ishi.IshiAbilities.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			DemonCurse.tick(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (DemonCurse.isSuppressed(player)) {
					continue;     // pouvoirs passifs aussi scellés par la Malédiction du Démon
				}
				DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
				if (fruit == DevilFruit.HIE_HIE_NO_MI && player.level() instanceof ServerLevel level) {
					IceAbilities.tickPassive(player, level);
				} else if (fruit == DevilFruit.MERA_MERA_NO_MI) {
					MeraAbilities.tickPassive(player);
				}
			}
			MeraAbilities.tick(server);
			IceAbilities.tick(server);
			// Une fois par seconde : le serveur renvoie le temps de recharge restant, pour que l'affichage (en bas à gauche)
			// reste toujours exact même si le serveur ralentit (le client compte en temps réel, le serveur en ticks).
			if (server.getTickCount() % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					resync(player);
				}
			}
		});

		// A la connexion : le client reçoit les recharges en cours (affichage).
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			long[] ready = COOLDOWNS.get(player.getUUID());
			DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
			if (ready == null || fruit == null) {
				return;
			}
			long now = player.level().getGameTime();
			for (int slot = 0; slot < DevilFruit.ABILITY_COUNT; slot++) {
				if (ready[slot] > now) {
					ServerPlayNetworking.send(player, new FruitCooldownPayload(slot, (int) (ready[slot] - now), fruit.cooldownTicks(slot)));
				}
			}
		});

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			IceAge.restoreAll(); // le terrain gelé est remis en place avant la sauvegarde
			PheasantBeak.cleanupAll();
			IceFx.cleanupAll();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			MeraAbilities.forget(handler.getPlayer().getUUID());
			IceAbilities.forget(handler.getPlayer().getUUID());
			minepiece.noah.ability.zushi.ZushiAbilities.forget(handler.getPlayer().getUUID(), handler.getPlayer());
			minepiece.noah.ability.goro.GoroAbilities.forget(handler.getPlayer().getUUID(), handler.getPlayer());
			minepiece.noah.ability.ishi.IshiAbilities.forget(handler.getPlayer().getUUID(), handler.getPlayer());
		});
	}

	/** Renvoie au client les recharges en cours (affichage). */
	private static void resync(ServerPlayer player) {
		long[] ready = COOLDOWNS.get(player.getUUID());
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
		if (ready == null || fruit == null) {
			return;
		}
		long now = player.level().getGameTime();
		for (int slot = 0; slot < DevilFruit.ABILITY_COUNT; slot++) {
			if (ready[slot] > now) {
				ServerPlayNetworking.send(player, new FruitCooldownPayload(slot, (int) (ready[slot] - now), fruit.cooldownTicks(slot)));
			}
		}
	}

	/** Remet à zéro les recharges de toutes les attaques du fruit du joueur, affichage compris. */
	public static void resetCooldowns(ServerPlayer player) {
		long[] ready = COOLDOWNS.get(player.getUUID());
		if (ready != null) {
			java.util.Arrays.fill(ready, 0L);
		}
		for (int slot = 0; slot < DevilFruit.ABILITY_COUNT; slot++) {
			ServerPlayNetworking.send(player, new FruitCooldownPayload(slot, 0, 1));
		}
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
		if (minepiece.noah.ability.goro.GoroAbilities.isKo(player.getUUID())) {
			return;   // KO (Défibrillateur) : aucune attaque
		}
		if (DemonCurse.isSuppressed(player)) {
			player.sendSystemMessage(Component.literal("Malédiction du Démon : ton fruit est scellé sous l'eau."), true);
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
			case SUKE_SUKE_NO_MI -> SukeAbilities.cast(player, level, slot);
			case BOMU_BOMU_NO_MI -> BomuAbilities.cast(player, level, slot);
			case SUPA_SUPA_NO_MI -> SupaAbilities.cast(player, level, slot);
			case USHI_USHI_NO_MI -> GiraffeAbilities.cast(player, level, slot);
			case ZUSHI_ZUSHI_NO_MI -> minepiece.noah.ability.zushi.ZushiAbilities.cast(player, level, slot);
			case GORO_GORO_NO_MI -> minepiece.noah.ability.goro.GoroAbilities.cast(player, level, slot);
			case ISHI_ISHI_NO_MI -> minepiece.noah.ability.ishi.IshiAbilities.cast(player, level, slot);
		};
		if (cast) {
			// Les attaques maintenues du Mera Mera no Mi lancent elles-mêmes leur recharge quand elles se terminent.
			// Idem pour le Pheasant Beak (recharge à l'envol) et l'Ice Age (attaque 3 du Hie Hie no Mi) : la recharge démarre quand l'onde part (voir IceAge).
			boolean holdAttack = fruit == DevilFruit.MERA_MERA_NO_MI
					|| (fruit == DevilFruit.HIE_HIE_NO_MI && (slot == 1 || slot == 2))
					|| (fruit == DevilFruit.ZUSHI_ZUSHI_NO_MI && minepiece.noah.ability.zushi.ZushiAbilities.delayedCooldown(slot))
					|| (fruit == DevilFruit.GORO_GORO_NO_MI && minepiece.noah.ability.goro.GoroAbilities.delayedCooldown(slot))
					|| (fruit == DevilFruit.ISHI_ISHI_NO_MI && minepiece.noah.ability.ishi.IshiAbilities.delayedCooldown(slot));
			if (!holdAttack) {
				ready[slot] = now + fruit.cooldownTicks(slot);
				ServerPlayNetworking.send(player, new FruitCooldownPayload(slot, fruit.cooldownTicks(slot), fruit.cooldownTicks(slot)));
			}
			player.sendSystemMessage(Component.literal(fruit.abilityName(slot) + " !"), true);
		}
	}

	/** Touches Z / X / C : attaques du Suke Suke no Mi (ignorées pour les autres fruits, qui gardent R / G / V). */
	public static void useAlt(ServerPlayer player, int slot) {
		if (player.getAttached(ModAttachments.FRUIT) == DevilFruit.SUKE_SUKE_NO_MI) {
			use(player, slot);
		}
	}

	/** Touche relâchée (attaques maintenues : Fire Gun, charge de l'Entei). */
	public static void release(ServerPlayer player, int slot) {
		if (DemonCurse.isSuppressed(player)) {
			return;
		}
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
		if (fruit == DevilFruit.MERA_MERA_NO_MI) {
			MeraAbilities.release(player, slot);
		} else if (fruit == DevilFruit.HIE_HIE_NO_MI) {
			IceAbilities.release(player, slot);
		} else if (fruit == DevilFruit.ZUSHI_ZUSHI_NO_MI) {
			minepiece.noah.ability.zushi.ZushiAbilities.release(player, slot);
		} else if (fruit == DevilFruit.GORO_GORO_NO_MI) {
			minepiece.noah.ability.goro.GoroAbilities.release(player, slot);
		}
	}

	/** Coupe proprement toutes les capacités en cours d'un joueur (sans toucher à son fruit ni à ses recharges). */
	public static void cancelActive(ServerPlayer player) {
		MeraAbilities.forget(player.getUUID());
		IceAbilities.forget(player.getUUID());
		SukeAbilities.cancel(player);
		BomuAbilities.cancel(player);
		SupaAbilities.cancel(player);
		GiraffeAbilities.cancel(player);
		minepiece.noah.ability.zushi.ZushiAbilities.forget(player.getUUID(), player);
		minepiece.noah.ability.goro.GoroAbilities.forget(player.getUUID(), player);
		minepiece.noah.ability.ishi.IshiAbilities.forget(player.getUUID(), player);
	}

	/** Lance la recharge d'une attaque (pour les attaques qui se terminent plus tard que l'appui). */
	public static void startCooldown(ServerPlayer player, int slot, int ticks) {
		long[] ready = COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new long[DevilFruit.ABILITY_COUNT]);
		ready[slot] = player.level().getGameTime() + ticks;
		ServerPlayNetworking.send(player, new FruitCooldownPayload(slot, ticks, ticks));
	}
}
