package minepiece.noah.haki;

import minepiece.noah.ability.goro.GoroFx;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.network.HakiEventPayload;
import minepiece.noah.particle.ModParticles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Haki de l'Observation (touche K).
 * - Activation : le joueur incline la tête (yeux fermés), une onde de perception se propage, ses yeux brillent en rouge ;
 *   les présences dans le rayon (selon la maîtrise) deviennent visibles en contour, même derrière les murs (client).
 * - Esquive automatique : quand une attaque (coup ou projectile) arrive, si l'endurance, la recharge et la chance
 *   (maîtrise) le permettent, le joueur esquive VRAIMENT : animation adaptée à la direction (buste à gauche / à droite,
 *   en arrière, accroupi) et petit déplacement réel (la hitbox bouge avec le joueur). Jamais d'invincibilité : chaque
 *   esquive coûte de l'endurance et a une recharge ; les explosions, le feu, la chute... ne s'esquivent pas.
 * - Maj + K (maîtrise 3) : Vision du futur (esquives quasi sûres et plus élégantes, ralenti visuel à l'esquive
 *   parfaite, trajectoires des projectiles et silhouettes fantômes côté client), consommation accrue.
 * - Touche R : esquive volontaire (pas chassé rapide dans la direction tenue).
 */
public final class Kenbunshoku {
	private Kenbunshoku() {}

	static void toggle(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		if (p.isShiftKeyDown()) {
			toggleFuture(p);
			return;
		}
		boolean on = !HakiData.has(p, HakiData.F_OBS);
		if (on && HakiManager.energy(p) < 6.0) {
			p.sendSystemMessage(Component.literal("§8Pas assez d'énergie de Haki."), true);
			return;
		}
		HakiData.setFlag(p, HakiData.F_OBS, on);
		if (!on) {
			HakiData.setFlag(p, HakiData.F_FUTURE, false);
			level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 0.5F);
			return;
		}
		HakiPose.set(p, HakiPose.OBS_FOCUS);
		double radius = HakiConfig.at(HakiConfig.get().obsRadius, HakiData.mastery(p, HakiData.OBS));
		HakiFx.ring(level, "haki_ring_kenbun", p.position().add(0, 0.1, 0), 1.0, radius * 2.0, 14);
		HakiFx.ring(level, "haki_ring_kenbun", p.position().add(0, 1.0, 0), 0.5, radius * 1.2, 10);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
		p.sendSystemMessage(Component.literal("§7Haki de l'Observation §8: §f" + HakiData.levelName(HakiData.OBS, HakiData.mastery(p, HakiData.OBS))), true);
	}

	private static void toggleFuture(ServerPlayer p) {
		if (HakiData.mastery(p, HakiData.OBS) < 3) {
			p.sendSystemMessage(Component.literal("§8Vision du futur : maîtrise 3 de l'Observation requise."), true);
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		boolean on = !HakiData.has(p, HakiData.F_FUTURE);
		if (on && HakiManager.energy(p) < 15.0) {
			p.sendSystemMessage(Component.literal("§8Pas assez d'énergie de Haki."), true);
			return;
		}
		HakiData.setFlag(p, HakiData.F_OBS, HakiData.has(p, HakiData.F_OBS) || on);
		HakiData.setFlag(p, HakiData.F_FUTURE, on);
		if (on) {
			HakiPose.set(p, HakiPose.OBS_FOCUS);
			HakiFx.ring(level, "haki_ring_kenbun", p.position().add(0, 1.6, 0), 0.3, 6.0, 8);
			level.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.6F);
			p.sendSystemMessage(Component.literal("§cVision du futur"), true);
		}
	}

	/** Esquive automatique : appelé AVANT les dégâts ; true = l'attaque est évitée. */
	static boolean tryAutoDodge(ServerPlayer p, DamageSource src) {
		if (!HakiData.has(p, HakiData.F_OBS) || HakiKo.isKo(p) || p.isSleeping() || p.isPassenger()) {
			return false;
		}
		if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.IS_FIRE)
				|| src.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		Entity direct = src.getDirectEntity();
		Entity attacker = src.getEntity();
		if (!(direct instanceof Projectile) && !(direct instanceof LivingEntity)) {
			return false;
		}
		HakiConfig cfg = HakiConfig.get();
		boolean future = HakiData.has(p, HakiData.F_FUTURE);
		if (!HakiManager.ready(p, HakiManager.CD_AUTODODGE) || HakiManager.energy(p) < cfg.autoDodgeCost) {
			return false;
		}
		double chance = future ? cfg.futureDodgeChance : HakiConfig.at(cfg.dodgeChance, HakiData.mastery(p, HakiData.OBS));
		// un adversaire qui maîtrise aussi l'Observation (Vision du futur) est plus difficile à lire
		if (attacker instanceof ServerPlayer ap && HakiData.has(ap, HakiData.F_FUTURE)) {
			chance *= 0.5;
		}
		if (p.getRandom().nextDouble() >= chance) {
			return false;
		}
		HakiManager.spend(p, cfg.autoDodgeCost);
		HakiManager.cooldown(p, HakiManager.CD_AUTODODGE, future ? cfg.futureDodgeCooldown : cfg.autoDodgeCooldown);
		HakiData.addXp(p, HakiData.OBS, 3);
		dodgeFrom(p, direct, future);
		return true;
	}

	/** Choisit l'esquive selon la direction d'où vient le coup, joue l'animation et déplace réellement le joueur. */
	private static void dodgeFrom(ServerPlayer p, Entity source, boolean perfect) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
		Vec3 right = GoroFx.bodyRight(p);
		Vec3 to = source.position().subtract(p.position()).multiply(1, 0, 1);
		if (source instanceof Projectile) {
			Vec3 v = source.getDeltaMovement().multiply(1, 0, 1);
			to = v.lengthSqr() > 1.0E-4 ? v.scale(-1) : to;   // le projectile arrive du côté opposé à sa vitesse
		}
		to = to.lengthSqr() < 1.0E-4 ? fwd : to.normalize();
		double side = to.dot(right);
		double front = to.dot(fwd);
		int pose;
		Vec3 push;
		if (Math.abs(side) > Math.abs(front) * 0.7) {
			// attaque venant de la droite -> buste vers la gauche (et inversement)
			pose = side > 0 ? HakiPose.DODGE_LEFT : HakiPose.DODGE_RIGHT;
			push = right.scale(side > 0 ? -0.55 : 0.55);
		} else if (front > 0 && !(source instanceof Projectile) && p.getRandom().nextBoolean()) {
			// coup horizontal de face : on se baisse, l'attaque passe au-dessus (pas de déplacement)
			pose = HakiPose.DODGE_DUCK;
			push = Vec3.ZERO;
		} else if (front > 0) {
			pose = HakiPose.DODGE_BACK;
			push = fwd.scale(-0.5);
		} else {
			// par derrière : pas de côté
			boolean left = p.getRandom().nextBoolean();
			pose = left ? HakiPose.DODGE_LEFT : HakiPose.DODGE_RIGHT;
			push = right.scale(left ? -0.6 : 0.6);
		}
		Vec3 from = p.position();
		HakiPose.set(p, pose | (perfect ? HakiPose.PERFECT : 0));
		if (push != Vec3.ZERO) {
			p.setDeltaMovement(push.x * (perfect ? 1.2 : 1.0), Math.max(0.08, p.getDeltaMovement().y), push.z * (perfect ? 1.2 : 1.0));
			p.needsSync = true;
		}
		// image rémanente là où le coup était attendu
		ZushiFx.send(level, ModParticles.HAKI_GHOST, from.x, from.y + 1.0, from.z, 1, 0, 0, 0, 0);
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, perfect ? 0.6F : 1.4F);
		if (perfect) {
			ServerPlayNetworking.send(p, new HakiEventPayload(HakiEventPayload.PERFECT_DODGE, 1));
			ZushiFx.send(level, ModParticles.HAKI_GHOST, from.x + push.x * 0.6, from.y + 1.0, from.z + push.z * 0.6, 1, 0, 0, 0, 0);
		}
	}

	/** Touche R : esquive volontaire (pas chassé) dans la direction choisie (0 gauche, 1 droite, 2 arrière, 3 avant). */
	static void dash(ServerPlayer p, int dir) {
		HakiConfig cfg = HakiConfig.get();
		if (HakiData.mastery(p, HakiData.OBS) < 1 || HakiKo.isKo(p) || p.isPassenger() || p.isSleeping()) {
			return;
		}
		if (!HakiManager.ready(p, HakiManager.CD_DASH) || HakiManager.energy(p) < cfg.dashCost) {
			return;
		}
		HakiManager.spend(p, cfg.dashCost);
		HakiManager.cooldown(p, HakiManager.CD_DASH, cfg.dashCooldown);
		Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
		Vec3 right = GoroFx.bodyRight(p);
		Vec3 d;
		int pose;
		switch (dir) {
			case 0 -> { d = right.scale(-1); pose = HakiPose.DASH_LEFT; }
			case 1 -> { d = right; pose = HakiPose.DASH_RIGHT; }
			case 3 -> { d = fwd; pose = HakiPose.DASH_FORWARD; }
			default -> { d = fwd.scale(-1); pose = HakiPose.DASH_BACK; }
		}
		double k = cfg.dashStrength * (p.onGround() ? 1.0 : 0.6);
		p.setDeltaMovement(d.x * k, p.onGround() ? 0.16 : p.getDeltaMovement().y, d.z * k);
		p.needsSync = true;
		HakiPose.set(p, pose);
		ServerLevel level = (ServerLevel) p.level();
		// petite traînée visuelle (silhouettes) + poussière
		Vec3 from = p.position();
		for (int i = 0; i < 3; i++) {
			Vec3 q = from.add(d.scale(i * 0.45));
			ZushiFx.send(level, ModParticles.HAKI_GHOST, q.x, q.y + 1.0, q.z, 1, 0, 0, 0, 0);
		}
		ZushiFx.send(level, net.minecraft.core.particles.ParticleTypes.CLOUD, from.x, from.y + 0.1, from.z, 6, 0.3, 0.05, 0.3, 0.03);
		level.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 1.6F);
		if (HakiData.has(p, HakiData.F_FUTURE)) {
			p.setInvulnerableTime(Math.max(p.getInvulnerableTime(), 6));   // Vision du futur : l'esquive volontaire est parfaite
		}
	}
}
