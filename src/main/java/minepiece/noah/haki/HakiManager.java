package minepiece.noah.haki;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.data.ModAttachments;
import minepiece.noah.network.HakiEventPayload;
import minepiece.noah.network.HakiKeyPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * Point d'entrée du système de Haki (serveur) : touches, énergie (jauge), recharges, événements de combat.
 * - J : Armement ; K : Observation (Maj + K : Vision du futur) ; L : Rois (maintenir) ;
 *   R : esquive volontaire. Le serveur revérifie tout (maîtrise, énergie, recharge).
 * - Énergie : chaque Haki actif consomme en continu, les techniques coûtent un montant fixe ; elle remonte quand plus rien
 *   n'est dépensé. À zéro, tous les Hakis se coupent.
 * - Compatible avec les fruits et les sabres : rien n'est bloqué, les bonus s'ajoutent (attributs temporaires).
 */
public final class HakiManager {
	static final int CD_EMISSION = 0;
	static final int CD_DASH = 1;
	static final int CD_AUTODODGE = 2;
	static final int CD_KING = 3;
	static final int CD_INTERNAL = 4;
	static final int CD_CLASH = 5;

	private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
	private static final Map<UUID, double[]> ENERGY = new HashMap<>();   // [énergie, dernier tick de dépense]
	/** Dégâts de la destruction interne en cours (évite qu'ils redéclenchent les effets de coup). */
	static boolean internalHit;
	private static MinecraftServer server;

	private HakiManager() {}

	public static void init() {
		HakiConfig.load();
		PayloadTypeRegistry.serverboundPlay().register(HakiKeyPayload.TYPE, HakiKeyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HakiEventPayload.TYPE, HakiEventPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HakiKeyPayload.TYPE, (payload, context) -> key(context.player(), payload.action(), payload.dir()));

		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerTickEvents.END_SERVER_TICK.register(HakiManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			HakiKo.wakeAll();
			Haoshoku.cleanupAll();
			for (ServerPlayer p : s.getPlayerList().getPlayers()) {
				Busoshoku.forget(p);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> HakiKo.onLoad(entity));

		// Observation : esquive automatique AVANT les dégâts
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p) || !Kenbunshoku.tryAutoDodge(p, source));
		// Armement / Rois : effets des coups portés
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (internalHit || blocked || damage <= 0.0F) {
				return;
			}
			if (source.getDirectEntity() instanceof ServerPlayer p && source.is(DamageTypes.PLAYER_ATTACK) && p != entity) {
				Busoshoku.onMeleeHit(p, entity, damage);
				Haoshoku.onMeleeHit(p, entity);
			}
		});
		// un joueur évanoui ne peut pas attaquer
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				HakiKo.isKo(player) ? InteractionResult.FAIL : InteractionResult.PASS);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> join(handler.getPlayer()));
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			HakiData.refreshFlags(newPlayer);
			Busoshoku.forget(newPlayer);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> forget(handler.getPlayer()));
	}

	// ------------------------------------------------------------------ touches

	private static void key(ServerPlayer p, int action, int dir) {
		if (action == 4) {
			Haoshoku.releaseKey(p);   // toujours accepté (même évanoui)
			return;
		}
		if (!p.isAlive() || HakiKo.isKo(p)) {
			return;
		}
		switch (action) {
			case 0 -> Busoshoku.toggle(p);
			case 1 -> Kenbunshoku.toggle(p);
			case 2 -> Haoshoku.press(p);
			case 3 -> Kenbunshoku.dash(p, Math.max(0, Math.min(3, dir)));
			case 4 -> Haoshoku.releaseKey(p);
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ énergie et recharges

	public static double energy(ServerPlayer p) {
		return state(p)[0];
	}

	private static double[] state(ServerPlayer p) {
		return ENERGY.computeIfAbsent(p.getUUID(), k -> new double[] {HakiConfig.get().energyMax, 0});
	}

	static void spend(ServerPlayer p, double amount) {
		double[] s = state(p);
		s[0] = Math.max(0.0, s[0] - amount);
		s[1] = p.level().getGameTime();
		sync(p, s[0]);
	}

	private static void sync(ServerPlayer p, double value) {
		Float cur = p.getAttached(ModAttachments.HAKI_ENERGY);
		if (cur == null || Math.abs(cur - value) >= 0.5 || (value == 0.0 && cur != 0.0F) || value >= HakiConfig.get().energyMax && cur < value) {
			p.setAttached(ModAttachments.HAKI_ENERGY, (float) value);
		}
	}

	static boolean ready(ServerPlayer p, int cd) {
		return remaining(p, cd) <= 0;
	}

	static long remaining(ServerPlayer p, int cd) {
		long[] c = COOLDOWNS.get(p.getUUID());
		return c == null ? 0 : c[cd] - p.level().getGameTime();
	}

	static void cooldown(ServerPlayer p, int cd, int ticks) {
		COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[6])[cd] = p.level().getGameTime() + ticks;
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer s) {
		HakiKo.tick(s);
		Haoshoku.tick(s);
		HakiConfig cfg = HakiConfig.get();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			long now = p.level().getGameTime();
			int f = HakiData.flags(p);
			double drain = 0.0;
			if ((f & HakiData.F_ARM) != 0) {
				drain += cfg.armDrainPerSecond * (1.15 - 0.15 * HakiData.mastery(p, HakiData.ARM));
			}
			if ((f & HakiData.F_OBS) != 0) {
				drain += cfg.obsDrainPerSecond;
				if (now % 200 == 0) {
					HakiData.addXp(p, HakiData.OBS, 1);
				}
			}
			if ((f & HakiData.F_FUTURE) != 0) {
				drain += cfg.futureDrainPerSecond;
			}
			if ((f & HakiData.F_INFUSE) != 0) {
				drain += cfg.infuseDrainPerSecond;
			}
			if ((f & HakiData.F_KING_HOLD) != 0) {
				drain += cfg.kingHoldDrainPerSecond;
			}
			double[] st = state(p);
			if (drain > 0.0) {
				st[0] = Math.max(0.0, st[0] - drain / 20.0);
				st[1] = now;
				if (st[0] <= 0.0) {
					exhaust(p);
				}
			} else if (now - (long) st[1] > cfg.energyRegenDelay) {
				st[0] = Math.min(cfg.energyMax, st[0] + cfg.energyRegenPerSecond / 20.0);
			}
			if (now % 4 == 0) {
				sync(p, st[0]);
			}
			Busoshoku.swordTick(p, now);
			// Vision du futur / Observation : yeux qui brillent, Armement en formation : énergie sombre aux poings
			Long armT = p.getAttached(ModAttachments.HAKI_ARM_TIME);
			if ((f & HakiData.F_ARM) != 0 && armT != null) {
				if (now - armT < 16 && now % 2 == 0) {
					HakiFx.darkHands((net.minecraft.server.level.ServerLevel) p.level(), p, 2);
				}
				Busoshoku.hardenTick(p, now - armT);
			}
		}
	}

	/** Énergie épuisée : tous les Hakis se coupent. */
	private static void exhaust(ServerPlayer p) {
		boolean arm = HakiData.has(p, HakiData.F_ARM);
		p.setAttached(ModAttachments.HAKI_FLAGS, 0);
		HakiData.refreshFlags(p);
		if (arm) {
			p.setAttached(ModAttachments.HAKI_ARM_TIME, p.level().getGameTime());
		}
		Busoshoku.modifiers(p);
		p.sendSystemMessage(Component.literal("§8Ton Haki est épuisé..."), true);
		p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 0.8F);
	}

	// ------------------------------------------------------------------ connexion

	private static void join(ServerPlayer p) {
		// éveil (rare) du Haki des Rois : tiré une seule fois par joueur
		if (!Boolean.TRUE.equals(p.getAttached(ModAttachments.HAKI_KING_ROLLED))) {
			p.setAttached(ModAttachments.HAKI_KING_ROLLED, Boolean.TRUE);
			if (HakiData.storedMastery(p, HakiData.KING) == 0 && p.getRandom().nextDouble() < HakiConfig.get().kingAwakenChance) {
				HakiData.setMastery(p, HakiData.KING, 1);
				p.sendSystemMessage(Component.literal("§4Tu sens s'éveiller en toi le Haki des Rois..."));
			}
		}
		p.removeAttached(ModAttachments.HAKI_FLAGS);
		HakiData.refreshFlags(p);
		Busoshoku.modifiers(p);
		Busoshoku.forget(p);
		sync(p, energy(p));
	}

	private static void forget(ServerPlayer p) {
		Busoshoku.forget(p);
		HakiKo.forget(p);
		Haoshoku.forget(p);
		COOLDOWNS.remove(p.getUUID());
		ENERGY.remove(p.getUUID());
	}

	/** Pour l'affichage (commande /haki). */
	static MinecraftServer server() {
		return server;
	}
}
