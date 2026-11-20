package minepiece.noah.haki;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.ability.goro.GoroFx;
import minepiece.noah.ability.zushi.ZushiDisplay;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.network.HakiEventPayload;
import minepiece.noah.particle.ModParticles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Haki des Rois (touche L MAINTENUE) — capacité rare (voir HakiData), 1 seul niveau pour l'instant : « Éclairs du Roi ».
 * Tant que la touche est enfoncée : posture imposante, une première onde de pression, puis des éclairs noirs et rouges
 * jaillissent du joueur et frappent les ennemis autour de lui ; chaque ennemi touché s'évanouit (animations de KO), les
 * boss et les créatures trop puissantes résistent. L'énergie se vide tant que la touche est tenue ; relâcher (ou
 * épuiser son énergie) arrête l'attaque et lance la recharge.
 *
 * (Code conservé pour les futurs niveaux, inaccessible tant que le Haki des Rois n'a qu'un niveau :)
 * - Libération de volonté : posture imposante (tête levée, torse bombé), la pression se forme puis une grande onde
 *   sombre se propage (anneau 3D, distorsion, particules sombres, secousse de caméra). Les ennemis faibles (PV max sous
 *   le seuil de la maîtrise) s'effondrent (animations de KO variées, voir HakiKo) ; les plus forts résistent et ne
 *   subissent que la pression. Un joueur ne s'évanouit que s'il n'a presque aucun Haki et peu de vie ; un joueur doté du
 *   Haki des Rois résiste toujours. Effets volontairement différents de la foudre bleue du Goro Goro no Mi.
 * - Maîtrise 2 : rayon et seuil plus grands. Maîtrise 3 : la libération crache des éclairs noirs et rouges.
 * - Maj + L (maîtrise 3) : Imprégnation avancée — éclairs noirs et rouges autour des bras et des sabres (rendu client
 *   attaché aux membres / texture animée de la lame), bonus de dégâts, décharge et onde de choc à chaque impact.
 * - Clash : deux imprégnés qui se frappent mutuellement en moins de clashWindow ticks déclenchent une confrontation :
 *   gigantesques éclairs entre eux, onde de choc qui repousse les entités proches, nuages qui s'écartent au-dessus.
 */
public final class Haoshoku {
	private record Pending(ServerPlayer player, long at) {}

	private record Hit(UUID target, long time) {}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Map<UUID, Hit> LAST_HIT = new HashMap<>();
	private static final List<Clash> CLASHES = new ArrayList<>();
	/** Joueurs qui maintiennent la touche L : début du maintien. */
	private static final Map<UUID, Long> HOLD = new HashMap<>();

	private Haoshoku() {}

	/** Touche L enfoncée : début des éclairs du Roi. */
	static void press(ServerPlayer p) {
		int m = HakiData.mastery(p, HakiData.KING);
		if (m < 1) {
			p.sendSystemMessage(Component.literal("§8Tu ne possèdes pas le Haki des Rois."), true);
			return;
		}
		if (HOLD.containsKey(p.getUUID())) {
			return;
		}
		HakiConfig cfg = HakiConfig.get();
		if (!HakiManager.ready(p, HakiManager.CD_KING)) {
			p.sendSystemMessage(Component.literal("§8Haki des Rois : encore " + (HakiManager.remaining(p, HakiManager.CD_KING) / 20 + 1) + " s."), true);
			return;
		}
		if (HakiManager.energy(p) < cfg.kingStartCost) {
			p.sendSystemMessage(Component.literal("§8Pas assez d'énergie de Haki."), true);
			return;
		}
		HakiManager.spend(p, cfg.kingStartCost);
		ServerLevel level = (ServerLevel) p.level();
		HOLD.put(p.getUUID(), level.getGameTime());
		HakiData.setFlag(p, HakiData.F_KING_HOLD, true);
		HakiPose.set(p, HakiPose.KING_HOLD);
		// la volonté se libère : onde de pression d'ouverture
		Vec3 c = p.position();
		HakiFx.ring(level, "haki_ring_pressure", c.add(0, 0.2, 0), 2.0, cfg.kingHoldRadius * 2.0, 14);
		ZushiFx.circle(level, ModParticles.HAKI_PRESSURE, c.add(0, 0.5, 0), 2.0, 20, 0.0, 0.8, true);
		ZushiFx.shake(level, c, cfg.kingHoldRadius * 1.5, 2, 10);
		HakiFx.burst(level, c.add(0, 1.4, 0), 6, 4.5, 0.1);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.0F, 0.55F);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 0.5F);
		p.sendSystemMessage(Component.literal("§4Haki des Rois"), true);
	}

	/** Touche L relâchée. */
	static void releaseKey(ServerPlayer p) {
		stopHold(p);
	}

	private static void stopHold(ServerPlayer p) {
		if (HOLD.remove(p.getUUID()) == null) {
			return;
		}
		long now = p.level().getGameTime();
		HakiData.setFlag(p, HakiData.F_KING_HOLD, false);
		// retour progressif à la posture normale (la pose imposante se relâche)
		HakiPose.setAt(p, HakiPose.KING_ROAR, now - 20);
		HakiManager.cooldown(p, HakiManager.CD_KING, HakiConfig.get().kingCooldown);
		p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.5F);
	}

	/** Maintien : salves d'éclairs noirs et rouges qui assomment les ennemis autour. */
	private static void holdTick(ServerPlayer p, long start) {
		HakiConfig cfg = HakiConfig.get();
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		long age = now - start;
		RandomSource r = level.getRandom();
		Vec3 chest = p.position().add(0, 1.3 * p.getBbHeight() / 1.8, 0);
		// aura : pression sombre, éclairs qui crépitent autour du corps
		if (age % 2 == 0) {
			ZushiFx.circle(level, ModParticles.HAKI_PRESSURE, p.position().add(0, 0.4, 0), 1.6 + r.nextDouble(), 4, 0.02, 0.4, true);
			ZushiFx.send(level, ModParticles.HAKI_AURA, chest.x, chest.y, chest.z, 4, 0.5, 0.8, 0.5, 0.0);
		}
		if (age % 4 == 1) {
			Vec3 dir = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.7, r.nextDouble() - 0.5).normalize();
			HakiFx.bolt(level, chest, chest.add(dir.scale(2.5 + r.nextDouble() * 3.0)), 0.07, 3, 1);
		}
		if (age % 20 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F, 0.5F);
		}
		if (age % cfg.kingBoltInterval != 0) {
			return;
		}
		// cibles : ennemis les plus proches dans le rayon, pas encore évanouis
		double rad = cfg.kingHoldRadius;
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(rad),
				e -> e != p && e.isAlive() && !e.isSpectator() && !HakiKo.isKo(e) && e.distanceToSqr(p) <= rad * rad
						&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand));
		targets.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		int n = 0;
		for (LivingEntity e : targets) {
			if (n >= cfg.kingBoltTargets) {
				break;
			}
			n++;
			strike(p, level, chest, e);
		}
		if (n == 0) {
			// personne : les éclairs frappent le sol autour
			double a = r.nextDouble() * Math.PI * 2, d = 4 + r.nextDouble() * (rad - 4);
			Vec3 g = p.position().add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
			HakiFx.bolt(level, chest, g, 0.12, 4, 2);
			ZushiFx.send(level, ModParticles.HAKI_DISCHARGE, g.x, g.y + 0.3, g.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.4F + r.nextFloat() * 0.4F);
	}

	/** Un éclair du Roi frappe une cible : les faibles s'évanouissent, les puissants résistent. */
	private static void strike(ServerPlayer p, ServerLevel level, Vec3 from, LivingEntity e) {
		HakiConfig cfg = HakiConfig.get();
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.55, 0);
		HakiFx.bolt(level, from, c, 0.14, 4, 2);
		ZushiFx.send(level, ModParticles.HAKI_DISCHARGE, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		ZushiFx.send(level, ModParticles.HAKI_ARC, c.x, c.y, c.z, 4, 0.3, 0.4, 0.3, 0.0);
		if (e instanceof ServerPlayer tp) {
			ServerPlayNetworking.send(tp, new HakiEventPayload(HakiEventPayload.PRESSURE, 1));
			int other = HakiData.mastery(tp, HakiData.ARM) + HakiData.mastery(tp, HakiData.OBS);
			if (HakiData.mastery(tp, HakiData.KING) >= 1 || tp.isCreative() || !cfg.kingKoPlayers || other >= 5) {
				// volonté assez forte : il résiste, simple pression
				tp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
				return;
			}
			HakiKo.knockOut(tp, cfg.kingKoTicks / 2);
			return;
		}
		if (isBoss(e) || e.getMaxHealth() > cfg.kingHoldKoHealth) {
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
			e.hurtServer(level, level.damageSources().indirectMagic(p, p), 2.0F);
			return;
		}
		HakiKo.knockOut(e, cfg.kingKoTicks);
		ZushiFx.send(level, ModParticles.HAKI_DARK, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 8, 0.3, 0.2, 0.3, 0.05);
	}

	/** Ancienne libération en onde (conservée pour les niveaux futurs). */
	static void pressWave(ServerPlayer p) {
		int m = HakiData.mastery(p, HakiData.KING);
		if (p.isShiftKeyDown()) {
			toggleInfusion(p, m);
			return;
		}
		HakiConfig cfg = HakiConfig.get();
		if (!HakiManager.ready(p, HakiManager.CD_KING)) {
			p.sendSystemMessage(Component.literal("§8Haki des Rois : encore " + (HakiManager.remaining(p, HakiManager.CD_KING) / 20 + 1) + " s."), true);
			return;
		}
		if (HakiManager.energy(p) < cfg.kingCost) {
			p.sendSystemMessage(Component.literal("§8Pas assez d'énergie de Haki."), true);
			return;
		}
		HakiManager.spend(p, cfg.kingCost);
		HakiManager.cooldown(p, HakiManager.CD_KING, cfg.kingCooldown);
		HakiPose.set(p, HakiPose.KING_ROAR);
		ServerLevel level = (ServerLevel) p.level();
		// la pression commence à se former autour du joueur
		ZushiFx.circle(level, ModParticles.HAKI_PRESSURE, p.position().add(0, 0.3, 0), 1.5, 10, 0.02, -0.05, true);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.6F);
		PENDING.add(new Pending(p, level.getGameTime() + 12));
	}

	private static void toggleInfusion(ServerPlayer p, int m) {
		if (m < 3) {
			p.sendSystemMessage(Component.literal("§8Imprégnation : maîtrise 3 du Haki des Rois requise."), true);
			return;
		}
		boolean on = !HakiData.has(p, HakiData.F_INFUSE);
		if (on && HakiManager.energy(p) < 15.0) {
			p.sendSystemMessage(Component.literal("§8Pas assez d'énergie de Haki."), true);
			return;
		}
		HakiData.setFlag(p, HakiData.F_INFUSE, on);
		Busoshoku.modifiers(p);
		ServerLevel level = (ServerLevel) p.level();
		if (on) {
			HakiPose.set(p, HakiPose.INFUSE);
			for (boolean right : new boolean[] {true, false}) {
				Vec3 h = HakiFx.hand(p, right);
				HakiFx.bolt(level, h, h.add(0, 1.4, 0).add(GoroFx.bodyRight(p).scale(right ? 0.6 : -0.6)), 0.06, 3, 2);
			}
			level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.6F);
			p.sendSystemMessage(Component.literal("§4Haki des Rois §8: §cimprégnation"), true);
		} else {
			level.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
			Pending pd = it.next();
			ServerPlayer p = pd.player();
			if (p.isRemoved() || !p.isAlive()) {
				it.remove();
				continue;
			}
			if (p.level().getGameTime() >= pd.at()) {
				release(p);
				it.remove();
			}
		}
		CLASHES.removeIf(c -> !c.tick());
		for (Iterator<Map.Entry<UUID, Long>> it = HOLD.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> en = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
			if (p == null) {
				it.remove();
				continue;
			}
			if (!p.isAlive() || HakiKo.isKo(p) || !HakiData.has(p, HakiData.F_KING_HOLD)) {
				// mort, évanoui, ou énergie épuisée (drapeau coupé) : arrêt après la boucle
				stopHoldLater.add(p);
				continue;
			}
			holdTick(p, en.getValue());
		}
		for (ServerPlayer p : stopHoldLater) {
			stopHold(p);
		}
		stopHoldLater.clear();
	}

	private static final List<ServerPlayer> stopHoldLater = new ArrayList<>();

	/** L'onde du Haki des Rois. */
	private static void release(ServerPlayer p) {
		HakiConfig cfg = HakiConfig.get();
		ServerLevel level = (ServerLevel) p.level();
		int m = HakiData.mastery(p, HakiData.KING);
		double radius = HakiConfig.at(cfg.kingRadius, m);
		double koHealth = HakiConfig.at(cfg.kingKoHealth, m);
		Vec3 c = p.position();
		HakiFx.ring(level, "haki_ring_pressure", c.add(0, 0.2, 0), 2.0, radius * 2.0, 16);
		HakiFx.ring(level, "haki_ring_pressure", c.add(0, 1.2, 0), 1.0, radius * 1.4, 12);
		ZushiFx.circle(level, ModParticles.HAKI_PRESSURE, c.add(0, 0.5, 0), 2.0, 24, 0.0, 0.9, true);
		ZushiFx.circle(level, ModParticles.HAKI_DARK, c.add(0, 1.0, 0), 1.0, 30, 0.05, 1.4, true);
		ZushiFx.shake(level, c, radius * 1.5, 3, 14);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.5F);
		if (m >= 3) {
			HakiFx.burst(level, c.add(0, 1.2, 0), 8, 6.0, 0.12);
		}
		int fainted = 0;
		AABB box = new AABB(c, c).inflate(radius, radius * 0.6, radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != p && e.isAlive() && !e.isSpectator())) {
			if (e.position().distanceTo(c) > radius) {
				continue;
			}
			if (e instanceof ServerPlayer tp) {
				ServerPlayNetworking.send(tp, new HakiEventPayload(HakiEventPayload.PRESSURE, m));
				if (HakiData.mastery(tp, HakiData.KING) >= 1) {
					// deux volontés de Rois : il résiste, la pression se brise sur lui
					ZushiFx.send(level, ModParticles.HAKI_ARC, tp.getX(), tp.getY() + 1, tp.getZ(), 6, 0.4, 0.6, 0.4, 0.0);
					continue;
				}
				int other = HakiData.mastery(tp, HakiData.ARM) + HakiData.mastery(tp, HakiData.OBS);
				if (cfg.kingKoPlayers && other <= 2 && tp.getHealth() <= koHealth * 0.25 && !tp.isCreative()) {
					HakiKo.knockOut(tp, cfg.kingKoTicks / 2);
					fainted++;
				} else {
					tp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
					tp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0));
				}
				continue;
			}
			if (isBoss(e) || e.getMaxHealth() > koHealth) {
				// trop puissant : résiste, simple pression
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
				ZushiFx.send(level, ModParticles.HAKI_DARK, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
				continue;
			}
			HakiKo.knockOut(e, cfg.kingKoTicks);
			fainted++;
			ZushiFx.send(level, ModParticles.HAKI_DARK, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 8, 0.3, 0.2, 0.3, 0.05);
		}
		HakiData.addXp(p, HakiData.KING, 2 + Math.min(10, fainted));
		if (fainted > 0) {
			p.sendSystemMessage(Component.literal("§4Haki des Rois §8: §7" + fainted + " ennemi(s) évanoui(s)"), true);
		}
	}

	private static boolean isBoss(LivingEntity e) {
		EntityType<?> t = e.getType();
		return t == EntityTypes.ENDER_DRAGON || t == EntityTypes.WITHER || t == EntityTypes.WARDEN || t == EntityTypes.ELDER_GUARDIAN;
	}

	/** Coup porté avec l'imprégnation : décharge, onde de choc, clash éventuel. */
	static void onMeleeHit(ServerPlayer p, LivingEntity target) {
		if (!HakiData.has(p, HakiData.F_INFUSE)) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.55, 0);
		Vec3 dir = c.subtract(p.getEyePosition()).normalize();
		HakiFx.burst(level, c, 3, 2.2, 0.06);
		HakiFx.ringFacing(level, "haki_ring_pressure", c, dir, 0.4, 3.0, 6);
		target.setDeltaMovement(target.getDeltaMovement().add(dir.x * 0.6, 0.2, dir.z * 0.6));
		target.needsSync = true;
		level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.35F, 1.8F);
		HakiData.addXp(p, HakiData.KING, 1);
		// clash : la cible imprégnée avait frappé l'attaquant juste avant
		long now = level.getGameTime();
		if (target instanceof ServerPlayer tp && HakiData.has(tp, HakiData.F_INFUSE)) {
			Hit back = LAST_HIT.get(tp.getUUID());
			if (back != null && back.target().equals(p.getUUID()) && now - back.time() <= HakiConfig.get().clashWindow
					&& HakiManager.ready(p, HakiManager.CD_CLASH) && HakiManager.ready(tp, HakiManager.CD_CLASH)) {
				HakiManager.cooldown(p, HakiManager.CD_CLASH, 60);
				HakiManager.cooldown(tp, HakiManager.CD_CLASH, 60);
				CLASHES.add(new Clash(level, p, tp));
			}
		}
		LAST_HIT.put(p.getUUID(), new Hit(target.getUUID(), now));
	}

	static void forget(ServerPlayer p) {
		HOLD.remove(p.getUUID());
		LAST_HIT.remove(p.getUUID());
		PENDING.removeIf(pd -> pd.player() == p);
	}

	static void cleanupAll() {
		for (Clash c : CLASHES) {
			c.removeClouds();
		}
		CLASHES.clear();
		PENDING.clear();
		HOLD.clear();
		LAST_HIT.clear();
	}

	/** Confrontation de deux Haki des Rois imprégnés (environ 3 s). */
	private static final class Clash {
		private static final int LIFE = 60;
		final ServerLevel level;
		final ServerPlayer a;
		final ServerPlayer b;
		final Vec3 center;
		final List<ZushiDisplay> clouds = new ArrayList<>();
		final List<Vec3> cloudDir = new ArrayList<>();
		int age;

		Clash(ServerLevel level, ServerPlayer a, ServerPlayer b) {
			this.level = level;
			this.a = a;
			this.b = b;
			this.center = a.position().add(b.position()).scale(0.5).add(0, 1.2, 0);
			// séparation des nuages : couronne de nuages temporaires au-dessus, qui s'écartent du centre
			RandomSource r = level.getRandom();
			double top = Math.min(level.getMaxY() - 10, center.y + 26);
			for (int i = 0; i < 8; i++) {
				double ang = i / 8.0 * Math.PI * 2 + r.nextDouble() * 0.3;
				Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
				Vec3 pos = new Vec3(center.x, top, center.z).add(d.scale(5));
				ZushiDisplay cl = ZushiDisplay.create(level, i % 2 == 0 ? "haki_cloud_a" : "haki_cloud_b", pos,
						(float) (r.nextDouble() * 360), 0.0F, 4.0 + r.nextDouble() * 2.0, 0.0, 20, 8.0F, false);
				clouds.add(cl);
				cloudDir.add(d);
			}
			level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.2F, 1.4F);
		}

		boolean tick() {
			age++;
			RandomSource r = level.getRandom();
			double fade = 1.0 - age / (double) LIFE;
			Vec3 pa = a.position().add(0, 1.2, 0);
			Vec3 pb = b.position().add(0, 1.2, 0);
			Vec3 mid = pa.add(pb).scale(0.5);
			if (age % 2 == 0 && fade > 0.2) {
				// gigantesques éclairs noirs et rouges qui jaillissent entre eux
				for (int i = 0; i < 2 + (int) (3 * fade); i++) {
					Vec3 dir = new Vec3(r.nextDouble() - 0.5, (r.nextDouble() - 0.3) * 0.8, r.nextDouble() - 0.5).normalize();
					HakiFx.bolt(level, mid, mid.add(dir.scale(4 + r.nextDouble() * 10 * fade)), 0.16 * fade + 0.05, 3, 2);
				}
				ZushiFx.far(level, ModParticles.HAKI_CLASH, mid.x, mid.y, mid.z, 3, 1.5, 1.2, 1.5, 0.0);
				HakiFx.bolt(level, pa, pb, 0.12, 2, 1);
			}
			if (age % 10 == 1 && fade > 0.3) {
				HakiFx.ring(level, "haki_ring_pressure", mid.add(0, -1.0, 0), 1.0, 22.0 * fade, 10);
				ZushiFx.shake(level, mid, 30.0, 3, 8);
				for (ServerPlayer sp : level.getEntitiesOfClass(ServerPlayer.class, new AABB(mid, mid).inflate(20))) {
					ServerPlayNetworking.send(sp, new HakiEventPayload(HakiEventPayload.CLASH, 1));
				}
				// l'onde de choc repousse les entités proches (sauf les deux combattants)
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(mid, mid).inflate(8), e -> e != a && e != b)) {
					Vec3 away = e.position().subtract(mid).multiply(1, 0, 1);
					away = away.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : away.normalize();
					e.setDeltaMovement(e.getDeltaMovement().add(away.x * 0.9, 0.3, away.z * 0.9));
					e.needsSync = true;
				}
				// les deux adversaires sont légèrement repoussés l'un de l'autre
				Vec3 ab = pb.subtract(pa).multiply(1, 0, 1);
				ab = ab.lengthSqr() < 1.0E-3 ? new Vec3(1, 0, 0) : ab.normalize();
				for (Player pl : new Player[] {a, b}) {
					Vec3 d = pl == a ? ab.scale(-0.5) : ab.scale(0.5);
					pl.setDeltaMovement(pl.getDeltaMovement().add(d.x, 0.1, d.z));
					pl.needsSync = true;
				}
				level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0F, 0.6F);
			}
			ZushiFx.far(level, ModParticles.HAKI_PRESSURE, mid.x, mid.y, mid.z, 2, 2.0, 1.0, 2.0, 0.05);
			// nuages : s'écartent du centre puis se dissipent
			if (age % 20 == 1) {
				for (int i = 0; i < clouds.size(); i++) {
					ZushiDisplay cl = clouds.get(i);
					Vec3 target = new Vec3(center.x, cl.entity() != null ? cl.entity().getY() : center.y + 26, center.z)
							.add(cloudDir.get(i).scale(5 + 34 * Math.min(1.0, (age + 20) / (double) LIFE)));
					cl.move(target, cl.entity() != null ? cl.entity().getYRot() : 0.0F, 0.0F);
				}
			}
			if (age >= LIFE) {
				removeClouds();
				return false;
			}
			return true;
		}

		void removeClouds() {
			for (ZushiDisplay cl : clouds) {
				cl.remove();
			}
			clouds.clear();
		}
	}
}
