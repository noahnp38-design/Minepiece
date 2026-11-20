package minepiece.noah.ability.zushi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.ability.FruitAbilities;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Attaque 3 : Météorite gravitationnelle (touche MAINTENUE, 3 niveaux de charge).
 *
 * Charge : particules aspirées vers le lanceur, repère violet au sol à l'endroit visé (sa taille suit le niveau).
 * Relâchement (ou charge max) : une météorite 3D (fx/zushi_meteor, taille selon le niveau) sort d'un cercle violet dans
 * le ciel, tourne sur elle-même, entourée de fragments en orbite (fx/zushi_meteor_shards), avec une traînée violette,
 * de la poussière et des éclats qui s'intensifient à l'approche du sol.
 * Impact : explosion de particules, ondes de choc circulaires (fx/zushi_shock), débris 3D projetés, nuage de poussière,
 * éclairs gravitationnels violets, secousse de caméra modérée, dégâts de zone et cratère réel (destroyTerrain).
 * La météorite reste plantée quelques secondes dans le cratère puis s'effrite. La recharge démarre au lancement.
 */
final class GravityMeteor {
	/** Durée de la chute depuis les nuages (ticks) : plus la météorite est grosse, plus sa chute est lente et lourde. */
	private static final int[] FALL_TICKS = {60, 70, 82};
	/** Rayon visuel du modèle à l'échelle 1 (blocs) : voir tools/zushi_models.py (~13 unités). */
	private static final double MODEL_RADIUS = 13.0 / 16.0;
	/** La météorite reste plantée intacte (fumante, fissures qui crépitent). */
	private static final int EMBED_TICKS = 40;
	/** Puis elle s'effrite progressivement : rétrécit pas à pas, s'enfonce, perd des morceaux qui roulent autour. */
	private static final int CRUMBLE_TICKS = 70;
	private static final int CRUMBLE_STEP = 5;
	/** Dernière bouffée de poussière qui masque la disparition des derniers fragments. */
	private static final int FADE_TICKS = 6;

	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	private static final List<Meteor> FALLING = new ArrayList<>();

	private GravityMeteor() {}

	private static final class Charge {
		final ServerLevel level;
		int ticks;
		int levelReached = 1;
		boolean released;

		Charge(ServerLevel level) {
			this.level = level;
		}
	}

	private static final class Meteor {
		final ServerLevel level;
		final UUID owner;
		final int lvl;
		final Vec3 start;
		final Vec3 target;
		/** Point de contact réel avec le terrain (= target si rien n'a été touché avant). */
		Vec3 impact;
		/** Garde-fou : l'explosion ne se déclenche qu'UNE fois. */
		boolean exploded;
		final int fallTicks;
		final double scale;
		final ZushiDisplay rock;
		final ZushiDisplay shards;
		final ZushiDisplay portal;
		/** Boule de lumière de l'explosion (créée à l'impact). */
		ZushiDisplay flash;
		final Vec3 spinAxis;
		Vec3 pos;
		int age;
		double spin;
		double shardSpin;
		int impactAge = -1;

		Meteor(ServerLevel level, ServerPlayer owner, int lvl, Vec3 target, Vec3 from) {
			ZushiConfig cfg = ZushiConfig.get();
			this.level = level;
			this.owner = owner.getUUID();
			this.lvl = lvl;
			this.target = target;
			this.impact = target;
			this.fallTicks = FALL_TICKS[lvl - 1];
			this.scale = cfg.meteorScale[lvl - 1];
			Vec3 h = new Vec3(target.x - from.x, 0, target.z - from.z);
			if (h.lengthSqr() < 16.0) {
				Vec3 look = owner.getLookAngle();
				h = new Vec3(look.x, 0, look.z);
			}
			h = h.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : h.normalize();
			// part de la couche de nuages (au moins 70 blocs au-dessus de la cible), derrière le lanceur, en diagonale
			double top = Math.max(cfg.meteorCloudHeight, target.y + 70.0);
			top = Math.min(top, level.getMaxY() - 8.0 - scale * MODEL_RADIUS);
			double height = Math.max(20.0, top - target.y);
			this.start = target.subtract(h.scale(height * 0.4)).add(0, height, 0);
			this.pos = start;
			Vec3 dir = target.subtract(start).normalize();
			Vec3 axis = dir.cross(new Vec3(0, 1, 0));
			this.spinAxis = axis.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : axis.normalize();
			float view = (float) (6.0 + 2.0 * lvl); // visible de très loin (6 = 384 blocs)
			this.rock = ZushiDisplay.create(level, "zushi_meteor", start, 0.0F, 0.0F, scale * 0.2, 0.0, 1, view, true);
			this.shards = ZushiDisplay.create(level, "zushi_meteor_shards", start, 0.0F, 0.0F, scale * 0.2, 0.0, 1, view, true);
			this.portal = ZushiDisplay.create(level, "zushi_wave", start.add(0, scale * MODEL_RADIUS + 0.5, 0), 0.0F, 0.0F,
					0.2, 0.0, 1, view, true);
		}
	}

	/** Touche pressée : début de la charge. */
	static boolean start(ServerPlayer player, ServerLevel level) {
		Charge c = CHARGES.get(player.getUUID());
		if (c != null && !c.released) {
			return false;
		}
		CHARGES.put(player.getUUID(), new Charge(level));
		level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.8F, 0.6F);
		return true;
	}

	static void release(ServerPlayer player) {
		Charge c = CHARGES.get(player.getUUID());
		if (c != null) {
			c.released = true;
		}
	}

	static void forget(UUID player) {
		CHARGES.remove(player);
	}

	static void tick(net.minecraft.server.MinecraftServer server) {
		ZushiConfig cfg = ZushiConfig.get();
		for (Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Charge> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Charge c = e.getValue();
			if (player == null || !player.isAlive() || player.level() != c.level) {
				it.remove();
				continue;
			}
			c.ticks++;
			int lvl = c.ticks >= cfg.meteorLevel3Ticks ? 3 : c.ticks >= cfg.meteorLevel2Ticks ? 2 : 1;
			if (lvl > c.levelReached) {
				c.levelReached = lvl;
				c.level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.5F + lvl * 0.15F);
				ZushiFx.circle(c.level, ModParticles.GRAVITY_SPARK, player.position().add(0, 0.2, 0), 1.2 + lvl * 0.4, 20, 0.15, 0.2, false);
			}
			chargeEffects(player, c, lvl);
			if (c.ticks % 10 == 1) {
				player.sendSystemMessage(Component.literal("Météorite gravitationnelle : charge " + lvl + "/3"), true);
			}
			if (c.released || c.ticks >= cfg.meteorMaxChargeTicks) {
				it.remove();
				launch(player, c.level, lvl);
			}
		}

		for (Iterator<Meteor> it = FALLING.iterator(); it.hasNext();) {
			Meteor m = it.next();
			if (!tickMeteor(m)) {
				m.rock.remove();
				m.shards.remove();
				m.portal.remove();
				if (m.flash != null) {
					m.flash.remove();
				}
				it.remove();
			}
		}
	}

	private static Vec3 aim(ServerPlayer player, ServerLevel level) {
		double range = ZushiConfig.get().meteorRange;
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		Vec3 p = hit.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(Math.min(range, 40.0))) : hit.getLocation();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x), (int) Math.floor(p.z));
		if (hit.getType() == HitResult.Type.MISS || p.y > ground + 0.5) {
			p = new Vec3(p.x, ground, p.z);
		}
		return p;
	}

	private static void chargeEffects(ServerPlayer player, Charge c, int lvl) {
		ServerLevel level = c.level;
		Vec3 hand = player.position().add(0, player.getBbHeight() + 0.6, 0);
		ZushiFx.pull(level, hand, 2.0 + lvl * 0.6, 1.5, 2 + lvl, false);
		ZushiFx.send(level, ModParticles.GRAVITY_SPARK, hand.x, hand.y, hand.z, lvl, 0.25, 0.25, 0.25, 0.01);
		if (c.ticks % 3 == 0) {
			// repère au sol : la taille de la zone d'impact suit le niveau de charge
			Vec3 t = aim(player, level).add(0, 0.15, 0);
			double radius = ZushiConfig.get().meteorRadius[lvl - 1];
			ZushiFx.circle(level, ModParticles.GRAVITY_SPARK, t, radius, 10 + lvl * 4, 0.02, 0.0, true);
			ZushiFx.pull(level, t, radius * 0.6, 0.3, 1, true);
		}
		if (c.ticks % 12 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.4F, 0.6F + lvl * 0.2F);
		}
	}

	private static void launch(ServerPlayer player, ServerLevel level, int lvl) {
		Vec3 target = aim(player, level);
		Meteor m = new Meteor(level, player, lvl, target, player.position());
		FALLING.add(m);
		FruitAbilities.startCooldown(player, 2, DevilFruit.ZUSHI_ZUSHI_NO_MI.cooldownTicks(2));
		player.sendSystemMessage(Component.literal("Météorite gravitationnelle ! (niveau " + lvl + ")"), true);
		// apparition : les nuages se déchirent, un cercle violet s'ouvre et la météorite en sort
		m.portal.scaleTo(m.scale * 1.6, 0.0, 10);
		m.rock.scaleTo(m.scale, 0.0, 8);
		m.shards.scaleTo(m.scale, 0.0, 8);
		cloudBurst(m, 1.0);
		level.playSound(null, player.getX(), player.getY() + 10, player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3.0F, 0.4F);
		level.playSound(null, player.getX(), player.getY() + 10, player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 0.5F);
		level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	/** @return false quand tout est terminé */
	private static boolean tickMeteor(Meteor m) {
		m.age++;
		ServerLevel level = m.level;
		if (m.impactAge >= 0) {
			return afterImpact(m);
		}
		if (m.age == 20) {
			m.portal.scaleTo(0.01, 0.0, 12);
		}
		// la météorite traverse la couche de nuages : elle les arrache sur son passage
		if (m.age < 16 && m.age % 3 == 0) {
			cloudBurst(m, 0.5);
		}
		double t = Math.min(1.0, m.age / (double) m.fallTicks);
		double ease = Math.pow(t, 1.7);
		Vec3 prev = m.pos;
		m.pos = m.start.add(m.target.subtract(m.start).scale(ease));
		// collision réelle avec le terrain, adaptée à la taille : on balaie le trajet de ce tick bloc par bloc
		Vec3 contact = m.age > 2 ? sweep(m, prev, m.pos) : null;
		if (contact != null) {
			m.impact = contact;
		}
		m.rock.move(m.pos, 0.0F, 0.0F);
		m.shards.move(m.pos, 0.0F, 0.0F);
		// rotation sur elle-même (bascule vers l'avant) + fragments en orbite, interpolées côté client
		if (m.age % 2 == 0) {
			double grow = m.scale * Math.min(1.0, 0.2 + 0.8 * m.age / 8.0); // apparition progressive (8 ticks)
			m.spin += 14.0 - 3.0 * m.lvl;   // rotation lente : une masse énorme
			m.shardSpin += 34.0;
			m.rock.setTransform(m.spinAxis.x, m.spinAxis.y, m.spinAxis.z, m.spin, grow, 0.0, 2);
			m.shards.setTransform(0.25, 1.0, 0.1, m.shardSpin, grow, 0.0, 2);
		}
		trail(m, prev, t);
		if (contact != null || t >= 1.0) {
			impact(m);
		}
		return true;
	}

	/**
	 * Cherche le premier contact entre la météorite (sphère de rayon ~75 % du modèle) et un bloc solide sur le segment
	 * prev -> next. Les feuillages sont traversés. Les chunks non chargés ne sont jamais chargés (ignorés).
	 * @return le point d'impact au sol, ou null ; m.pos est ramené au point de contact.
	 */
	private static Vec3 sweep(Meteor m, Vec3 prev, Vec3 next) {
		double rc = m.scale * MODEL_RADIUS * 0.75;
		Vec3 seg = next.subtract(prev);
		double len = seg.length();
		if (len < 1.0E-4) {
			return null;
		}
		Vec3 dir = seg.scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3[] probes = {
				new Vec3(0, -rc, 0),                                      // dessous
				dir.scale(rc),                                            // avant
				dir.scale(rc * 0.6).add(0, -rc * 0.6, 0),                 // avant-bas
				side.scale(rc * 0.6).add(0, -rc * 0.6, 0),                // flancs bas
				side.scale(-rc * 0.6).add(0, -rc * 0.6, 0),
		};
		int steps = Math.max(1, (int) Math.ceil(len));
		for (int i = 1; i <= steps; i++) {
			Vec3 center = prev.add(seg.scale(i / (double) steps));
			for (Vec3 off : probes) {
				Vec3 probe = center.add(off);
				BlockPos bp = BlockPos.containing(probe);
				if (!m.level.isLoaded(bp)) {
					continue;
				}
				BlockState st = m.level.getBlockState(bp);
				if (st.isSolid() && !st.is(BlockTags.LEAVES)) {
					m.pos = center;
					// point d'impact : sur le dessus du bloc touché, sous le centre de la météorite
					return new Vec3(probe.x, bp.getY() + 1.0, probe.z);
				}
			}
		}
		return null;
	}

	/** Trou dans les nuages : grosse bouffée de nuages blancs et de poussière mauve autour du point de départ. */
	private static void cloudBurst(Meteor m, double strength) {
		double r = m.scale * MODEL_RADIUS * 1.8;
		int n = (int) ((40 + 30 * m.lvl) * strength);
		ZushiFx.far(m.level, net.minecraft.core.particles.ParticleTypes.CLOUD, m.pos.x, m.pos.y, m.pos.z, n, r, r * 0.25, r, 0.05);
		ZushiFx.far(m.level, ModParticles.GRAVITY_DUST, m.pos.x, m.pos.y, m.pos.z, n / 2, r, r * 0.3, r, 0.03);
		ZushiFx.far(m.level, ModParticles.GRAVITY_SPARK, m.pos.x, m.pos.y, m.pos.z, n / 2, r * 0.7, r * 0.3, r * 0.7, 0.08);
	}

	/** Traînée : étincelles violettes, poussière, éclats ; plus intense à l'approche du sol. */
	private static void trail(Meteor m, Vec3 prev, double t) {
		ServerLevel level = m.level;
		RandomSource r = level.getRandom();
		double radius = m.scale * MODEL_RADIUS;
		double intensity = (0.5 + t * 1.2) * Math.max(1.0, m.scale / 4.0); // plus grosse = traînée plus dense
		Vec3 seg = m.pos.subtract(prev);
		Vec3 back = seg.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : seg.normalize().scale(-1);
		int points = Math.max(2, (int) (seg.length() / 0.8));
		for (int i = 0; i < points; i++) {
			Vec3 p = prev.add(seg.scale(i / (double) points));
			ZushiFx.far(level, ModParticles.GRAVITY_SPARK, p.x, p.y, p.z, (int) ((1 + m.lvl) * intensity), radius * 0.5, radius * 0.5, radius * 0.5, 0.02);
			if (i % 2 == 0) {
				ZushiFx.far(level, ModParticles.GRAVITY_DUST, p.x, p.y, p.z, (int) (1 + m.lvl * intensity), radius * 0.6, radius * 0.6, radius * 0.6, 0.01);
			}
		}
		// lignes de vitesse : étincelles projetées vers l'arrière
		for (int i = 0, n = (int) (2 + m.lvl * intensity); i < ZushiFx.n(n); i++) {
			Vec3 at = m.pos.add((r.nextDouble() - 0.5) * radius * 2, (r.nextDouble() - 0.5) * radius * 2, (r.nextDouble() - 0.5) * radius * 2);
			ZushiFx.velocity(level, ModParticles.GRAVITY_FLASH, at, back.scale(0.9 + r.nextDouble() * 0.6), true);
		}
		ZushiFx.far(level, ModParticles.ROCK_CHIP, m.pos.x, m.pos.y, m.pos.z, (int) (1 + intensity * m.lvl), radius, radius, radius, 0.05);
		// à l'approche : la gravité aspire et soulève la poussière à l'endroit de l'impact
		if (t > 0.55) {
			ZushiFx.pull(level, m.target.add(0, 0.3, 0), ZushiConfig.get().meteorRadius[m.lvl - 1], 0.4, 2 + m.lvl, true);
			if (m.age % 2 == 0) {
				ZushiFx.circle(level, ModParticles.GRAVITY_DUST, m.target.add(0, 0.2, 0), radius * 1.6, 8 + 4 * m.lvl, 0.05, 0.25, true);
			}
		}
		if (m.age % 6 == 0) {
			level.playSound(null, m.pos.x, m.pos.y, m.pos.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 12.0F, 0.5F);
		}
		if (m.age % 14 == 0) {
			level.playSound(null, m.pos.x, m.pos.y, m.pos.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 12.0F, 0.3F);
		}
	}

	private static void impact(Meteor m) {
		if (m.exploded) {
			return;
		}
		m.exploded = true;
		ZushiConfig cfg = ZushiConfig.get();
		ServerLevel level = m.level;
		RandomSource r = level.getRandom();
		int lvl = m.lvl;
		Vec3 c = m.impact;
		double radius = cfg.meteorRadius[lvl - 1];
		double modelR = m.scale * MODEL_RADIUS;
		m.impactAge = 0;
		m.shards.remove();
		// la météorite reste à moitié enfoncée dans le cratère
		m.rock.move(c.add(0, -modelR * 0.5, 0), 0.0F, 0.0F);

		// ---- 1. explosion centrale lumineuse (boule de lumière 3D qui gonfle en 3 ticks puis s'éteint)
		double flashScale = radius * 0.6 / 0.7;
		m.flash = ZushiDisplay.create(level, "zushi_flash", c.add(0, Math.min(modelR, radius) * 0.3, 0), 0.0F, 0.0F, 0.5, 0.0, 1,
				(float) (4 + 2 * lvl), true);
		m.flash.setTransform(0.3, 1, 0.2, 35, flashScale, 0.0, 3);
		ZushiFx.far(level, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1.5, c.z, 2 + lvl, radius * 0.25, 1.0, radius * 0.25, 0.0);
		ZushiFx.far(level, ModParticles.GRAVITY_FLASH, c.x, c.y + 1.5, c.z, 60 * lvl, modelR, modelR * 0.6, modelR, 0.9);

		// ---- 2. dégâts importants + entités repoussées par le souffle (jamais le lanceur)
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(m.owner);
		DamageSource src = owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().generic();
		AABB box = new AABB(c, c).inflate(radius, radius * 0.8, radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(m.owner))) {
			double d = e.position().distanceTo(c);
			if (d > radius) {
				continue;
			}
			float dmg = (float) (cfg.meteorDamage[lvl - 1] * (1.0 - 0.45 * d / radius) * cfg.damageMultiplier);
			e.hurtServer(level, src, dmg);
			Vec3 push = e.position().subtract(c);
			push = push.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(push.x, 0, push.z).normalize();
			double k = (1.0 - d / radius * 0.7) * (1.3 + 0.6 * lvl);
			e.setDeltaMovement(e.getDeltaMovement().add(push.x * k, 0.7 + 0.3 * k, push.z * k));
			e.needsSync = true;
		}

		// ---- 3. vrais morceaux du terrain projetés (copies visuelles qui gardent la texture des blocs)
		double craterR = cfg.meteorCraterRadius[lvl - 1];
		int blocks = 26 + 14 * lvl;   // 40 / 54 / 68 blocs projetés à l'impact (d'autres suivent pendant le creusement)
		for (int i = 0; i < blocks; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(r.nextDouble()) * Math.max(2.0, craterR);
			int bx = (int) Math.floor(c.x + Math.cos(a) * d);
			int bz = (int) Math.floor(c.z + Math.sin(a) * d);
			BlockPos top = BlockPos.containing(bx, c.y, bz);
			if (!level.isLoaded(top)) {
				continue;
			}
			int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
			if (Math.abs(gy - c.y) > craterR + 2) {
				gy = (int) Math.floor(c.y) - 1;
			}
			BlockPos bp = new BlockPos(bx, gy, bz);
			BlockState st = level.getBlockState(bp);
			double speed = 0.45 + r.nextDouble() * 0.55 + lvl * 0.1;
			Vec3 vel = new Vec3(Math.cos(a) * speed, 0.75 + r.nextDouble() * 0.7, Math.sin(a) * speed);
			Vec3 from = new Vec3(bx + 0.5, gy + 1.3, bz + 0.5);
			if (cfg.destroyTerrain && st.isSolid() && ZushiTerrain.movable(level, bp, st) && ZushiTerrain.allowed(level, bp, owner)) {
				ZushiFx.throwBlock(level, st, from, vel, 0.7 + r.nextDouble() * 0.35);
			} else {
				ZushiFx.throwRock(level, from, vel, 0.6 + r.nextDouble() * 0.5 + lvl * 0.15);
			}
		}
		// fragments rocheux qui restent en suspension (la gravité les retient) avant de retomber
		for (int i = 0, n = 4 + 2 * lvl; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = radius * (0.3 + r.nextDouble() * 0.6);
			Vec3 from = c.add(Math.cos(a) * d, 1.0, Math.sin(a) * d);
			ZushiFx.floatRock(level, from, new Vec3(0, 0.25 + r.nextDouble() * 0.25, 0), 0.5 + r.nextDouble() * 0.6, 45 + r.nextInt(30));
		}

		// ---- 4. grand cratère réel (creusé sur plusieurs ticks, protections respectées)
		ZushiTerrain.crater(level, c, craterR, owner);

		// ---- 5. particules violettes / mauves, grand nuage de poussière, éclats
		ZushiFx.far(level, ModParticles.GRAVITY_SPARK, c.x, c.y + 1, c.z, 80 * lvl, radius * 0.45, 1.5, radius * 0.45, 0.6);
		ZushiFx.far(level, ModParticles.GRAVITY_DUST, c.x, c.y + 1.5, c.z, 90 * lvl, radius * 0.6, 2.0, radius * 0.6, 0.1);
		ZushiFx.far(level, ParticleTypes.CLOUD, c.x, c.y + 2, c.z, 40 * lvl, radius * 0.5, 1.5, radius * 0.5, 0.12);
		ZushiFx.far(level, ModParticles.ROCK_CHIP, c.x, c.y + 1, c.z, 50 * lvl, radius * 0.35, 0.8, radius * 0.35, 0.7);
		BlockPos below = BlockPos.containing(c).below();
		ZushiFx.crumble(level, below, level.getBlockState(below), 40 * lvl);
		ZushiFx.pull(level, c.add(0, 1.0, 0), radius * 1.2, 2.0, 30 * lvl, true);

		// ---- 6. onde de choc : premier anneau immédiat (la suite s'étend sur le terrain dans afterImpact)
		ZushiFx.circle(level, ModParticles.GRAVITY_DUST, c.add(0, 0.4, 0), 1.0, 40 + 16 * lvl, 0.03, 0.7 + 0.2 * lvl, true);
		ZushiFx.circle(level, ModParticles.GRAVITY_SPARK, c.add(0, 0.8, 0), 1.0, 36 + 12 * lvl, 0.0, 1.0 + 0.25 * lvl, true);

		// ---- éclairs gravitationnels
		lightning(m, 3 + lvl * 2);

		// ---- secousse de caméra (légère à modérée selon la distance)
		ZushiFx.shake(level, c, 30.0 + 20.0 * lvl, 2 + lvl, 12 + 6 * lvl);

		// ---- sons
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 6.0F + lvl * 3, 0.45F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 6.0F, 0.5F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0F + lvl, 0.7F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5.0F, 0.5F);
	}

	private static void lightning(Meteor m, int bolts) {
		ServerLevel level = m.level;
		RandomSource r = level.getRandom();
		double radius = ZushiConfig.get().meteorRadius[m.lvl - 1];
		Vec3 c = m.impact;
		for (int i = 0; i < bolts; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			Vec3 ground = c.add(Math.cos(a) * radius * (0.4 + r.nextDouble() * 0.7), 0.3, Math.sin(a) * radius * (0.4 + r.nextDouble() * 0.7));
			Vec3 sky = c.add(Math.cos(a) * radius * 0.3, 8 + r.nextDouble() * (6 + 3 * m.lvl), Math.sin(a) * radius * 0.3);
			ZushiFx.bolt(level, i % 2 == 0 ? sky : c.add(0, 1, 0), ground, true);
		}
	}

	/** Ondes de choc 3D, nuage de poussière qui persiste, éclairs résiduels, puis la météorite s'effrite. */
	private static boolean afterImpact(Meteor m) {
		ServerLevel level = m.level;
		RandomSource r = level.getRandom();
		int a = ++m.impactAge;
		Vec3 c = m.impact;
		double radius = ZushiConfig.get().meteorRadius[m.lvl - 1];
		double ringBase = radius / 1.5; // modèle de 3 blocs de diamètre à l'échelle 1

		// boule de lumière : gonfle (3 ticks), puis s'éteint en se contractant, puis supprimée
		if (a == 4 && m.flash != null) {
			m.flash.setTransform(0.3, 1, 0.2, 80, 0.01, 0.0, 8);
		} else if (a == 13 && m.flash != null) {
			m.flash.remove();
			m.flash = null;
		}
		if (a == 1) {
			ZushiDisplay shock = ZushiDisplay.create(level, "zushi_shock", c.add(0, 0.3, 0), 0.0F, 0.0F, 0.2, 0.0, 1, 4.0F, true);
			shock.scaleTo(ringBase * 2.8, 0.0, 14);
			SHOCKS.add(new Timed(shock, 15));
		} else if (a == 5) {
			ZushiDisplay shock = ZushiDisplay.create(level, "zushi_shock", c.add(0, 0.6, 0), 0.0F, 0.0F, 0.2, 0.0, 1, 4.0F, true);
			shock.setTransform(0, 1, 0, 40, ringBase * 2.0, 0.0, 12);
			SHOCKS.add(new Timed(shock, 13));
			lightning(m, 2 + m.lvl);
		}
		// onde de choc qui s'étend SUR le terrain : un anneau de poussière et d'étincelles qui grandit pendant 16 ticks
		if (a <= 16) {
			double ringR = radius * 1.8 * a / 16.0;
			Vec3 ringC = c.add(0, 0.3, 0);
			int n = (int) Math.min(90, 10 + ringR * 2.2);
			ZushiFx.circle(level, ModParticles.GRAVITY_DUST, ringC, ringR, n, 0.06, 0.12, true);
			if (a % 2 == 0) {
				ZushiFx.circle(level, ModParticles.GRAVITY_SPARK, ringC.add(0, 0.4, 0), ringR, n / 2, 0.02, 0.15, true);
				ZushiFx.circle(level, ModParticles.ROCK_CHIP, ringC.add(0, 0.2, 0), ringR, n / 4, 0.25, 0.05, true);
			}
		}
		// grand nuage de poussière qui persiste
		if (a < 60 && a % 2 == 0) {
			double d = radius * (0.3 + r.nextDouble() * 0.8);
			double ang = r.nextDouble() * Math.PI * 2;
			ZushiFx.far(level, ModParticles.GRAVITY_DUST, c.x + Math.cos(ang) * d, c.y + 0.8, c.z + Math.sin(ang) * d,
					3 + m.lvl, radius * 0.3, 0.8, radius * 0.3, 0.015);
		}
		if (a < EMBED_TICKS && a % 4 == 0) {
			Vec3 top = c.add(0, m.scale * MODEL_RADIUS * 0.5, 0);
			ZushiFx.send(level, ModParticles.GRAVITY_SPARK, top.x, top.y, top.z, 3, m.scale * 0.6, 0.4, m.scale * 0.6, 0.03);
		}
		if (a == 12 || a == 26) {
			lightning(m, 1 + m.lvl / 2);
		}
		if (a > EMBED_TICKS && a <= EMBED_TICKS + CRUMBLE_TICKS && (a - EMBED_TICKS) % CRUMBLE_STEP == 0) {
			crumbleStep(m, (a - EMBED_TICKS) / (double) CRUMBLE_TICKS);
		}
		if (a == EMBED_TICKS + CRUMBLE_TICKS) {
			// derniers fragments : grosse bouffée de poussière, le reste disparaît dedans
			double modelR = m.scale * MODEL_RADIUS;
			Vec3 center = c.add(0, -modelR * 0.5, 0);
			ZushiFx.far(level, ModParticles.GRAVITY_DUST, center.x, center.y + modelR * 0.4, center.z, 40 + 20 * m.lvl,
					modelR * 0.4, modelR * 0.3, modelR * 0.4, 0.04);
			ZushiFx.far(level, ParticleTypes.CLOUD, center.x, center.y + modelR * 0.4, center.z, 10 + 6 * m.lvl,
					modelR * 0.3, modelR * 0.2, modelR * 0.3, 0.03);
			m.rock.setTransform(m.spinAxis.x, m.spinAxis.y, m.spinAxis.z, m.spin, 0.01, -modelR * 0.6, FADE_TICKS);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 2.5F, 0.6F);
		}
		return a < EMBED_TICKS + CRUMBLE_TICKS + FADE_TICKS;
	}

	/**
	 * Une étape de l'effritement (f de 0 à 1) : la météorite rétrécit et s'enfonce en douceur (animation interpolée sur
	 * CRUMBLE_STEP ticks, donc continue), des morceaux de météorite se détachent de sa surface et roulent autour, avec
	 * poussière et éclats.
	 */
	private static void crumbleStep(Meteor m, double f) {
		ServerLevel level = m.level;
		RandomSource r = level.getRandom();
		double modelR = m.scale * MODEL_RADIUS;
		double ease = f * f * (3 - 2 * f);
		double size = m.scale * (1.0 - 0.85 * ease);
		double rr = modelR * (1.0 - 0.85 * ease);
		Vec3 center = m.impact.add(0, -modelR * 0.5 - modelR * 0.25 * ease, 0);
		m.spin += 3.0;
		m.rock.setTransform(m.spinAxis.x, m.spinAxis.y, m.spinAxis.z, m.spin, size, -modelR * 0.25 * ease, CRUMBLE_STEP);
		// morceaux qui se détachent (sur la partie visible, au-dessus du sol)
		for (int i = 0, n = 1 + m.lvl; i < n; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double up = 0.2 + r.nextDouble() * 0.6;
			Vec3 dir = new Vec3(Math.cos(a) * (1 - up), up, Math.sin(a) * (1 - up)).normalize();
			Vec3 from = center.add(dir.scale(rr * 0.9));
			Vec3 vel = new Vec3(dir.x * 0.18, 0.12 + r.nextDouble() * 0.12, dir.z * 0.18);
			ZushiFx.throwChunk(level, from, vel, Math.max(0.5, m.scale * 0.13 * (0.7 + r.nextDouble() * 0.6)));
		}
		Vec3 top = center.add(0, rr * 0.6, 0);
		ZushiFx.far(level, ModParticles.GRAVITY_DUST, top.x, top.y, top.z, 6 + 3 * m.lvl, rr * 0.6, rr * 0.3, rr * 0.6, 0.02);
		ZushiFx.far(level, ModParticles.ROCK_CHIP, top.x, top.y, top.z, 8 + 4 * m.lvl, rr * 0.6, rr * 0.3, rr * 0.6, 0.08);
		ZushiFx.send(level, ModParticles.GRAVITY_SPARK, top.x, top.y, top.z, 4, rr * 0.5, rr * 0.3, rr * 0.5, 0.02);
		if (r.nextInt(2) == 0) {
			level.playSound(null, top.x, top.y, top.z, SoundEvents.BASALT_BREAK, SoundSource.BLOCKS, 1.6F, 0.5F + r.nextFloat() * 0.3F);
		}
	}

	// ondes de choc : petits modèles temporaires supprimés au bout de quelques ticks
	private static final List<Timed> SHOCKS = new ArrayList<>();

	private static final class Timed {
		final ZushiDisplay display;
		int left;

		Timed(ZushiDisplay display, int left) {
			this.display = display;
			this.left = left;
		}
	}

	static void tickShocks() {
		for (Iterator<Timed> it = SHOCKS.iterator(); it.hasNext();) {
			Timed t = it.next();
			if (--t.left <= 0) {
				t.display.remove();
				it.remove();
			}
		}
	}

	static void cleanupAll() {
		for (Meteor m : FALLING) {
			m.rock.remove();
			m.shards.remove();
			m.portal.remove();
			if (m.flash != null) {
				m.flash.remove();
			}
		}
		FALLING.clear();
		for (Timed t : SHOCKS) {
			t.display.remove();
		}
		SHOCKS.clear();
		CHARGES.clear();
	}
}
