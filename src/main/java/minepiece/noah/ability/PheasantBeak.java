package minepiece.noah.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pheasant Beak (attaque 2 du Hie Hie no Mi), attaque MAINTENUE :
 * - touche maintenue : le bras gèle (attachement ICE_ARM, dessiné côté client) et un faisan de glace géant
 *   (modèle 3D fx/ice_pheasant) se forme au bout de la main, puis GROSSIT tant que la touche est maintenue ;
 * - touche relâchée (ou charge maximale atteinte) : le faisan fonce dans la direction du regard vers la cible visée ;
 * - impact (créature, mur ou portée max) : explosion de glace, dégâts, puis vague de gel au sol (terrain gelé,
 *   créatures gelées, voir IceAge.freezeArea) dont la taille dépend de la charge.
 * La recharge démarre à l'envol.
 */
final class PheasantBeak {
	// ---- charge
	private static final int MIN_CHARGE = 12;   // l'oiseau doit au moins être formé (0,6 s)
	private static final int MAX_CHARGE = 70;   // 3,5 s : l'oiseau part tout seul
	private static final int FORM_START = 4;    // l'oiseau commence à apparaître
	private static final int ARM_HOLD = 14;     // le bras reste glacé encore un peu après l'envol
	// ---- taille (échelle du modèle : 1 = 3 blocs d'envergure)
	private static final double MIN_SCALE = 1.4;
	private static final double MAX_SCALE = 4.2;
	// ---- vol
	private static final double SPEED_SMALL = 1.8;  // blocs par tick
	private static final double SPEED_BIG = 1.3;
	private static final double RANGE = 56.0;
	private static final double HOMING = 0.16;
	// ---- impact (petite charge -> charge max)
	private static final float DAMAGE_MIN = 14.0F, DAMAGE_MAX = 28.0F;
	private static final float SPLASH_MIN = 7.0F, SPLASH_MAX = 14.0F;
	private static final double SPLASH_R_MIN = 4.0, SPLASH_R_MAX = 8.0;
	private static final int FREEZE_R_MIN = 7, FREEZE_R_MAX = 18;     // rayon du terrain gelé à l'impact
	private static final float ZONE_DAMAGE = 3.0F;                     // dégâts de la vague de gel au sol
	private static final int FREEZE_TICKS = 70;
	private static final int SLOW_TICKS = 200;

	private static final int THAW_TICKS = 24;   // durée du dégel du bras

	private static final List<Cast> CASTS = new ArrayList<>();
	/** Bras glacés après l'envol : game time de l'envol (le bras reste glacé ARM_HOLD ticks puis dégèle). */
	private static final java.util.Map<UUID, Long> ARMS = new java.util.HashMap<>();

	private PheasantBeak() {}

	private static final class Cast {
		final ServerLevel level;
		final UUID owner;
		final FxDisplay bird;
		final Set<UUID> hit = new HashSet<>();
		Vec3 pos;
		Vec3 dir;
		UUID target;
		double travelled = 0.0;
		int age = 0;
		boolean released = false;
		int launchAge = -1;
		int impactAge = -1;
		double charge = 0.0;      // 0 à 1, figée à l'envol
		double scale = 0.02;
		boolean wingsUp = true;

		Cast(ServerLevel level, ServerPlayer player) {
			this.level = level;
			this.owner = player.getUUID();
			this.dir = player.getLookAngle();
			this.pos = hand(player);
			this.bird = FxDisplay.create(level, "ice_pheasant", pos, yaw(dir), pitch(dir), 0.02, 0.02, 0.02, 0.0, 1);
		}

		boolean charging() {
			return launchAge < 0;
		}

		double hitRadius() {
			return 0.9 * scale;
		}
	}

	// ------------------------------------------------------------------ touche

	/** Touche pressée : début de la charge. */
	static boolean start(ServerPlayer player, ServerLevel level) {
		for (Cast c : CASTS) {
			if (c.owner.equals(player.getUUID()) && c.charging()) {
				return false;
			}
		}
		CASTS.add(new Cast(level, player));
		ARMS.remove(player.getUUID());
		player.setAttached(ModAttachments.ICE_ARM, level.getGameTime());
		level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	/** Touche relâchée : l'oiseau part (dès qu'il a fini de se former si on relâche très tôt). */
	static void release(ServerPlayer player) {
		for (Cast c : CASTS) {
			if (c.owner.equals(player.getUUID()) && c.charging()) {
				c.released = true;
			}
		}
	}

	/** Annule la charge d'un joueur (mort, déconnexion). Un oiseau déjà lancé termine son vol. */
	static void forget(UUID player) {
		for (Iterator<Cast> it = CASTS.iterator(); it.hasNext();) {
			Cast c = it.next();
			if (c.owner.equals(player) && c.charging()) {
				c.bird.remove();
				it.remove();
			}
		}
	}

	static void cleanupAll() {
		for (Cast c : CASTS) {
			c.bird.remove();
		}
		CASTS.clear();
		ARMS.clear();
	}

	// ------------------------------------------------------------------ tick

	static void tick(MinecraftServer server) {
		tickArms(server);
		for (Iterator<Cast> it = CASTS.iterator(); it.hasNext();) {
			Cast c = it.next();
			if (!tick(server, c)) {
				c.bird.remove();
				it.remove();
			}
		}
	}

	private static boolean tick(MinecraftServer server, Cast c) {
		ServerPlayer player = server.getPlayerList().getPlayer(c.owner);
		c.age++;

		if (c.impactAge >= 0) {
			// Éclatement : l'oiseau gonfle puis se brise.
			int k = c.age - c.impactAge;
			if (k == 1) {
				double s = c.scale * 1.35;
				c.bird.scaleTo(s, s, s, 0.0, 2);
			} else if (k == 3) {
				c.bird.scaleTo(0.05, 0.05, 0.05, 0.0, 3);
			}
			return k < 7;
		}

		if (c.charging()) {
			if (player == null || !player.isAlive() || player.isSpectator()
					|| player.getAttached(ModAttachments.FRUIT) != DevilFruit.HIE_HIE_NO_MI) {
				if (player != null) {
					thawNow(player);
				}
				return false;
			}
			charge(player, c);
			if ((c.released && c.age >= MIN_CHARGE) || c.age >= MAX_CHARGE) {
				launch(player, c);
			}
			return true;
		}
		return fly(c, player);
	}

	/** Charge : le bras gèle, l'oiseau se forme puis grossit au bout de la main. */
	private static void charge(ServerPlayer player, Cast c) {
		ServerLevel level = c.level;
		RandomSource rnd = level.getRandom();
		c.dir = player.getLookAngle();
		Vec3 hand = hand(player);

		c.charge = Math.max(0.0, Math.min(1.0, (c.age - MIN_CHARGE) / (double) (MAX_CHARGE - MIN_CHARGE)));
		double form = Math.min(1.0, Math.max(0.0, (c.age - FORM_START) / (double) (MIN_CHARGE - FORM_START)));
		double target = form < 1.0 ? MIN_SCALE * form : MIN_SCALE + (MAX_SCALE - MIN_SCALE) * c.charge;
		if (c.age >= FORM_START && (c.age % 3 == 0 || Math.abs(target - c.scale) > 0.4)) {
			c.scale = Math.max(0.05, target);
			c.bird.scaleTo(c.scale, c.scale, c.scale, 0.0, 3);
		}
		// Plus il est grand, plus il se tient loin devant (il ne doit pas traverser le joueur).
		c.pos = hand.add(c.dir.scale(0.6 + c.scale * 1.3)).add(0, c.scale * 0.25, 0);
		c.bird.move(c.pos, yaw(c.dir), pitch(c.dir));

		// Brume froide qui s'échappe du bras et de l'oiseau.
		IceFx.mistAt(level, hand, 0.25, 1);
		if (c.age >= FORM_START && c.age % 2 == 0) {
			IceFx.mistAt(level, c.pos, 0.5 * c.scale, 2);
		}
		// Givre autour du bras, froid aspiré vers la main.
		level.sendParticles(ModParticles.ICE_SPARKLE, hand.x, hand.y, hand.z, 3, 0.25, 0.25, 0.25, 0.01);
		level.sendParticles(ModParticles.ICE_SHARD, hand.x, hand.y, hand.z, 1, 0.3, 0.3, 0.3, 0.02);
		int pull = 3 + (int) (c.charge * 5);
		double spread = 3.0 + c.scale * 2.0;
		for (int i = 0; i < pull; i++) {
			Vec3 from = c.pos.add((rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread * 0.6, (rnd.nextDouble() - 0.5) * spread);
			Vec3 v = c.pos.subtract(from);
			level.sendParticles(ParticleTypes.SNOWFLAKE, from.x, from.y, from.z, 0, v.x, v.y, v.z, 0.12);
		}
		if (c.age >= FORM_START) {
			double s = c.scale;
			level.sendParticles(ModParticles.FROST_MIST, c.pos.x, c.pos.y, c.pos.z, 2, 0.6 * s, 0.3 * s, 0.6 * s, 0.01);
			level.sendParticles(ModParticles.ICE_SHARD, c.pos.x, c.pos.y, c.pos.z, 2, 1.2 * s, 0.4 * s, 1.2 * s, 0.05);
		}
		if (c.age % 5 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.8F,
					0.6F + (float) c.charge * 1.0F);
		}
		if (c.age % 10 == 0 && c.age >= MIN_CHARGE) {
			player.sendSystemMessage(Component.literal("Pheasant Beak : " + (int) Math.round(c.charge * 100) + " %"), true);
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F,
					0.6F + (float) c.charge);
		}
	}

	private static void launch(ServerPlayer player, Cast c) {
		ServerLevel level = c.level;
		c.launchAge = c.age;
		ARMS.put(player.getUUID(), level.getGameTime());
		c.dir = player.getLookAngle();
		LivingEntity target = findTarget(player, level);
		c.target = target == null ? null : target.getUUID();
		c.scale = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * c.charge;
		c.bird.scaleTo(c.scale, c.scale, c.scale, 0.0, 2);

		float vol = 1.5F + (float) c.charge * 1.5F;
		level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, vol, 0.7F - (float) c.charge * 0.2F);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, vol, 0.7F);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, vol, 0.5F);
		Vec3 h = hand(player);
		level.sendParticles(ModParticles.ICE_SHARD, h.x, h.y, h.z, 30, 0.4, 0.4, 0.4, 0.3);
		level.sendParticles(ParticleTypes.SNOWFLAKE, h.x, h.y, h.z, 25, 0.3, 0.3, 0.3, 0.2);
		player.sendSystemMessage(Component.literal("Pheasant Beak ! (" + (int) Math.round(c.charge * 100) + " %)"), true);

		FruitAbilities.startCooldown(player, 1, DevilFruit.HIE_HIE_NO_MI.cooldownTicks(1));
	}

	/** Vol : avance, suit la cible, bat des ailes, traînée ; @return false quand tout est terminé. */
	private static boolean fly(Cast c, ServerPlayer owner) {
		ServerLevel level = c.level;
		if (c.target != null && level.getEntity(c.target) instanceof LivingEntity t && t.isAlive()) {
			Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(c.pos).normalize();
			c.dir = c.dir.scale(1.0 - HOMING).add(to.scale(HOMING)).normalize();
		}

		double speed = SPEED_SMALL + (SPEED_BIG - SPEED_SMALL) * c.charge;
		int steps = 4;
		for (int i = 0; i < steps; i++) {
			Vec3 next = c.pos.add(c.dir.scale(speed / steps));
			BlockPos bp = BlockPos.containing(next);
			if (!level.isLoaded(bp) || !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
				impact(c, owner);
				return true;
			}
			c.pos = next;
			c.travelled += speed / steps;
			AABB box = new AABB(c.pos, c.pos).inflate(c.hitRadius());
			if (!level.getEntitiesOfClass(LivingEntity.class, box,
					e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(c.owner)).isEmpty()) {
				impact(c, owner);
				return true;
			}
		}
		if (c.travelled >= RANGE) {
			impact(c, owner);
			return true;
		}

		c.bird.move(c.pos, yaw(c.dir), pitch(c.dir));
		if (c.age % 3 == 0) {
			c.wingsUp = !c.wingsUp;
			c.bird.setModel(c.wingsUp ? "ice_pheasant" : "ice_pheasant_b");
		}
		trail(c);
		if (c.age % 6 == 0) {
			level.playSound(null, BlockPos.containing(c.pos), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.8F + (float) c.charge, 1.3F);
		}
		return true;
	}

	/** Traînée : givre derrière la queue et longues lignes blanches au bout des ailes. */
	private static void trail(Cast c) {
		ServerLevel level = c.level;
		double s = c.scale;
		Vec3 back = c.pos.subtract(c.dir.scale(1.6 * s));
		Vec3 right = c.dir.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0E-4) {
			right = new Vec3(1, 0, 0);
		}
		right = right.normalize();
		level.sendParticles(ModParticles.ICE_SHARD, back.x, back.y, back.z, 3 + (int) (2 * s), 0.3 * s, 0.2 * s, 0.3 * s, 0.02);
		IceFx.mistAt(level, back, 0.35 * s, 2 + (int) s);
		level.sendParticles(ParticleTypes.SNOWFLAKE, c.pos.x, c.pos.y, c.pos.z, 4 + (int) (2 * s), 0.8 * s, 0.3 * s, 0.8 * s, 0.02);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 tip = c.pos.add(right.scale(1.4 * s * side)).subtract(c.dir.scale(0.5 * s));
			level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.0);
			level.sendParticles(ModParticles.ICE_SPARKLE, tip.x, tip.y, tip.z, 2, 0.2, 0.2, 0.2, 0.0);
		}
	}

	// ------------------------------------------------------------------ impact

	private static void impact(Cast c, ServerPlayer owner) {
		ServerLevel level = c.level;
		c.impactAge = c.age;
		Vec3 p = c.pos;
		double f = c.charge;
		c.bird.move(p, yaw(c.dir), pitch(c.dir));

		float damage = (float) (DAMAGE_MIN + (DAMAGE_MAX - DAMAGE_MIN) * f);
		float splash = (float) (SPLASH_MIN + (SPLASH_MAX - SPLASH_MIN) * f);
		double splashR = SPLASH_R_MIN + (SPLASH_R_MAX - SPLASH_R_MIN) * f;

		DamageSource source = owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().freeze();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(splashR),
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(c.owner))) {
			double d = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(p);
			if (d > splashR || !c.hit.add(e.getUUID())) {
				continue;
			}
			boolean direct = d <= c.hitRadius() + e.getBbWidth();
			e.hurtServer(level, source, direct ? damage : splash);
			if (!e.isAlive()) {
				continue;
			}
			int freeze = direct ? FREEZE_TICKS : FREEZE_TICKS / 2;
			e.setTicksFrozen(e.getTicksRequiredToFreeze() + freeze * 2);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, freeze, 9));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 2));
			e.setDeltaMovement(c.dir.x * (0.5 + f), 0.15, c.dir.z * (0.5 + f));
			IceFx.encase(level, e, freeze);
			IceFx.entityHit(level, e, 1.0);
		}

		// Grande zone gelée autour de l'impact : sol, eau, créatures (même système que l'Ice Age, dégel compris).
		int freezeRadius = (int) Math.round(FREEZE_R_MIN + (FREEZE_R_MAX - FREEZE_R_MIN) * f);
		IceAge.freezeArea(level, c.owner, p, freezeRadius, ZONE_DAMAGE);

		IceFx.burst(level, p);
		IceFx.mistBurst(level, p, freezeRadius, 1.5);
		int n = (int) (1.0 + f);
		double s = 1.0 + f;
		level.sendParticles(ModParticles.ICE_SHARD, p.x, p.y, p.z, 90 * n, 1.2 * s, 1.0 * s, 1.2 * s, 0.45);
		level.sendParticles(ModParticles.ICE_SPARKLE, p.x, p.y, p.z, 40 * n, 1.5 * s, 1.2 * s, 1.5 * s, 0.15);
		level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 60 * n, 1.5 * s, 1.0 * s, 1.5 * s, 0.3);
		level.sendParticles(ModParticles.FROST_MIST, p.x, p.y, p.z, 20 * n, 1.8 * s, 0.8 * s, 1.8 * s, 0.04);
		BlockPos at = BlockPos.containing(p);
		level.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0F + (float) f * 2.0F, 0.5F);
		level.playSound(null, at, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 2.5F, 0.6F);
		level.playSound(null, at, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 2.0F, 0.6F);
	}

	// ------------------------------------------------------------------ bras glacé : maintien puis dégel

	/**
	 * Attachement ICE_ARM : valeur positive = début du gel ; valeur négative = -(début du dégel). Le client en déduit la
	 * progression (0 à 1 puis 1 à 0) pour la vue extérieure comme pour la vue à la première personne.
	 */
	private static void tickArms(MinecraftServer server) {
		if (ARMS.isEmpty()) {
			return;
		}
		for (java.util.Iterator<java.util.Map.Entry<UUID, Long>> it = ARMS.entrySet().iterator(); it.hasNext();) {
			java.util.Map.Entry<UUID, Long> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			long now = player.level().getGameTime();
			long elapsed = now - e.getValue();
			if (elapsed == ARM_HOLD) {
				player.setAttached(ModAttachments.ICE_ARM, -now);
				player.level().playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
			}
			if (elapsed > ARM_HOLD && elapsed < ARM_HOLD + THAW_TICKS && player.level() instanceof ServerLevel level) {
				// La glace fond : gouttes d'eau et éclats qui tombent du bras.
				Vec3 h = hand(player);
				level.sendParticles(ParticleTypes.FALLING_WATER, h.x, h.y - 0.1, h.z, 2, 0.15, 0.1, 0.15, 0.0);
				if (elapsed % 3 == 0) {
					level.sendParticles(ModParticles.ICE_SHARD, h.x, h.y, h.z, 1, 0.15, 0.15, 0.15, 0.01);
				}
			}
			if (elapsed >= ARM_HOLD + THAW_TICKS) {
				player.removeAttached(ModAttachments.ICE_ARM);
				it.remove();
			}
		}
	}

	/** Dégel immédiat (charge annulée). */
	private static void thawNow(ServerPlayer player) {
		ARMS.put(player.getUUID(), player.level().getGameTime() - ARM_HOLD + 1);
	}

	// ------------------------------------------------------------------ utilitaires

	/** Créature la plus proche de la ligne de visée (cône étroit, 40 blocs), ou null. */
	private static LivingEntity findTarget(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		AABB box = player.getBoundingBox().expandTowards(look.scale(40.0)).inflate(4.0);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double dist = to.length();
			if (dist < 1.0 || dist > 40.0) {
				continue;
			}
			double cos = to.normalize().dot(look);
			if (cos < 0.96) {   // ~16°
				continue;
			}
			double score = dist * (2.0 - cos);
			if (score < bestScore && player.hasLineOfSight(e)) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	/** Position de la main droite levée vers l'avant. */
	private static Vec3 hand(ServerPlayer p) {
		double yaw = Math.toRadians(p.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return p.getEyePosition().add(right.scale(0.35)).add(0, -0.35, 0).add(p.getLookAngle().scale(0.9));
	}

	private static float yaw(Vec3 d) {
		return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
	}

	private static float pitch(Vec3 d) {
		return (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, d.y))));
	}
}
