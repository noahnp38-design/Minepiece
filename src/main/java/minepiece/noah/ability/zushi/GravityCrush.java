package minepiece.noah.ability.zushi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import minepiece.noah.network.GravityZonePayload;
import minepiece.noah.particle.ModParticles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Attaque 2 : Écrasement gravitationnel (centré sur le lanceur, la gravité augmente progressivement).
 *
 * - 0 à 20 ticks : un cercle gravitationnel violet (modèle fx/zushi_ring) s'étend au sol, les particules sont aspirées ;
 * - toutes les 10 ticks : une onde de pression (fx/zushi_wave) descend du ciel, impulsion de dégâts croissants ;
 * - les créatures dans la zone sont plaquées au sol (vitesse forcée vers le bas, lenteur), entourées d'étincelles ;
 * - 3 paliers d'affaissement : le terrain s'enfonce VRAIMENT d'un bloc à chaque palier (destroyTerrain), avec
 *   poussière, éclats du vrai bloc et débris 3D qui se soulèvent puis retombent. Profil en cuvette.
 * La zone sous le lanceur (crushSafeRadius) reste intacte : il reste debout sur son îlot pendant que tout s'effondre.
 */
final class GravityCrush {
	private static final int RING_GROW = 16;
	private static final int PULSE = 10;
	private static final int FIRST_PULSE = 20;
	private static final int FIRST_SINK = 26;
	private static final int SINK_EVERY = 14;

	private static final List<Crush> ACTIVE = new ArrayList<>();
	/** Colonnes de terrain enfoncées au maximum par tick (évite un pic de lag avec le grand rayon). */
	private static final int COLUMNS_PER_TICK = 140;
	private static int nextId;

	private GravityCrush() {}

	private static final class Wave {
		final ZushiDisplay display;
		final int born;

		Wave(ZushiDisplay display, int born) {
			this.display = display;
			this.born = born;
		}
	}

	private static final class Crush {
		final ServerLevel level;
		final UUID owner;
		final Vec3 center;
		final int yRef;
		final double radius;
		final double safe;
		final ZushiDisplay ring;
		final List<Wave> waves = new ArrayList<>();
		final List<int[]> columns = new ArrayList<>(); // {x, z, profondeur cible}
		/** Colonnes à enfoncer pour le palier en cours (du centre vers le bord : l'effondrement se propage). */
		final ArrayDeque<int[]> pending = new ArrayDeque<>();
		final int id = ++nextId;
		int age;
		int sinks;
		double ringAngle;

		Crush(ServerLevel level, ServerPlayer player) {
			ZushiConfig cfg = ZushiConfig.get();
			this.level = level;
			this.owner = player.getUUID();
			this.center = player.position();
			this.yRef = player.blockPosition().getY() - 1;
			this.radius = cfg.crushRadius;
			this.safe = cfg.crushSafeRadius;
			this.ring = ZushiDisplay.create(level, "zushi_ring", center.add(0, 0.06, 0), 0.0F, 0.0F, 0.05, 0.0, 1, 2.0F, true);
			int r = (int) Math.ceil(radius);
			int maxDepth = cfg.crushDepth;
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > radius || d < safe) {
						continue;
					}
					// cuvette : bord net autour de l'îlot du lanceur, profondeur max, puis remontée vers le bord
					double k;
					if (d < safe + 1.5) {
						k = 0.7 + 0.3 * (d - safe) / 1.5;
					} else if (d < radius * 0.6) {
						k = 1.0;
					} else {
						k = 1.0 - (d - radius * 0.6) / (radius * 0.4);
					}
					int depth = (int) Math.round(maxDepth * Math.max(0.0, k));
					if (depth > 0) {
						columns.add(new int[] {player.getBlockX() + dx, player.getBlockZ() + dz, depth});
					}
				}
			}
		}
	}

	static boolean cast(ServerPlayer player, ServerLevel level) {
		Crush c = new Crush(level, player);
		ACTIVE.add(c);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.6F, 0.5F);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.5F);
		ZushiFx.circle(level, ModParticles.GRAVITY_SPARK, c.center.add(0, 0.2, 0), 1.0, 24, 0.0, 0.5, false);
		zone(c, true);
		return true;
	}

	/** Prévient les clients proches (début / fin) : ils créent eux-mêmes les petites particules de gravité de la zone. */
	private static void zone(Crush c, boolean active) {
		GravityZonePayload payload = new GravityZonePayload(c.id, c.center.x, c.center.y, c.center.z, (float) c.radius, active);
		for (ServerPlayer p : c.level.players()) {
			if (p.position().distanceToSqr(c.center) < (c.radius + 96) * (c.radius + 96) || !active) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	static void tick() {
		for (Iterator<Crush> it = ACTIVE.iterator(); it.hasNext();) {
			Crush c = it.next();
			if (!tick(c)) {
				zone(c, false);
				c.ring.remove();
				for (Wave w : c.waves) {
					w.display.remove();
				}
				it.remove();
			}
		}
	}

	private static boolean tick(Crush c) {
		ZushiConfig cfg = ZushiConfig.get();
		c.age++;
		ServerLevel level = c.level;
		RandomSource r = level.getRandom();
		double ringScale = c.radius / 1.5;  // le modèle du cercle fait 3 blocs de diamètre à l'échelle 1

		if (c.age == 1) {
			c.ring.setTransform(0, 1, 0, 0, ringScale, 0.0, RING_GROW);
		} else if (c.age % 10 == 0 && c.age < cfg.crushDurationTicks) {
			c.ringAngle += 30.0;
			c.ring.setTransform(0, 1, 0, c.ringAngle, ringScale * (1.0 + 0.03 * Math.sin(c.age)), 0.0, 10);
		}

		// ---- ondes de pression qui descendent du ciel
		if (c.age % PULSE == 6 && c.age < cfg.crushDurationTicks - 4) {
			ZushiDisplay w = ZushiDisplay.create(level, "zushi_wave", c.center.add(0, 14, 0), 0.0F, 0.0F, ringScale * 0.55, 0.0, 9, 2.0F, true);
			c.waves.add(new Wave(w, c.age));
			ZushiFx.circle(level, ModParticles.GRAVITY_SPARK, c.center.add(0, 12, 0), c.radius * 0.7, 26, -0.55, 0.0, false);
		}
		for (Iterator<Wave> it = c.waves.iterator(); it.hasNext();) {
			Wave w = it.next();
			int a = c.age - w.born;
			if (a == 1) {
				w.display.move(c.center.add(0, 0.3, 0), 0.0F, 0.0F);   // descente interpolée sur 9 ticks
				w.display.scaleTo(ringScale * 1.05, 0.0, 9);
			} else if (a >= 10) {
				w.display.remove();
				it.remove();
			}
		}

		// ---- particules aspirées vers le centre + poussière au bord
		ZushiFx.pull(level, c.center.add(0, 0.4, 0), c.radius, 1.2, 4, false);
		if (c.age % 2 == 0) {
			double a = r.nextDouble() * Math.PI * 2;
			ZushiFx.send(level, ModParticles.GRAVITY_DUST, c.center.x + Math.cos(a) * c.radius, c.center.y + 0.2,
					c.center.z + Math.sin(a) * c.radius, 2, 0.5, 0.1, 0.5, 0.01);
		}

		// ---- créatures écrasées
		double strength = Math.min(1.0, c.age / (double) FIRST_PULSE);
		boolean pulse = c.age >= FIRST_PULSE && (c.age - FIRST_PULSE) % PULSE == 0 && c.age <= cfg.crushDurationTicks;
		int pulseIndex = (c.age - FIRST_PULSE) / PULSE;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(c.owner);
		// jusqu'à 40 blocs de haut : les créatures volantes au-dessus de la zone sont aussi happées
		AABB box = new AABB(c.center.x - c.radius, c.center.y - 6, c.center.z - c.radius,
				c.center.x + c.radius, c.center.y + 40, c.center.z + c.radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(c.owner))) {
			double dx = e.getX() - c.center.x, dz = e.getZ() - c.center.z;
			if (dx * dx + dz * dz > c.radius * c.radius) {
				continue;
			}
			if (e instanceof Player p && (p.isCreative())) {
				continue;
			}
			Vec3 v = e.getDeltaMovement();
			int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ());
			boolean airborne = e.getY() - groundY > 1.5;
			if (airborne) {
				// volant / en l'air : arraché du ciel et plaqué violemment au sol
				if (e instanceof Player p && p.isFallFlying()) {
					p.stopFallFlying();
				}
				e.setDeltaMovement(v.x * 0.1, Math.min(v.y, -2.2), v.z * 0.1);
				if (c.age % 3 == 0) {
					ZushiFx.send(level, ModParticles.GRAVITY_SPARK, e.getX(), e.getY() + e.getBbHeight() + 0.5, e.getZ(), 3, 0.3, 0.6, 0.3, 0.0);
				}
			} else {
				// au sol : immobilisé, impossible de sauter
				e.setDeltaMovement(v.x * 0.05, Math.min(v.y, -1.0 * strength - 0.4), v.z * 0.05);
			}
			e.needsSync = true;
			e.resetFallDistance();
			if (c.age % 10 == 1) {
				// lenteur VII et plus : quasiment immobile une fois la gravité montée en puissance
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 14, 4 + (int) (3 * strength), false, false, true));
			}
			if (c.age % 4 == 0) {
				ZushiFx.circle(level, ModParticles.GRAVITY_SPARK, e.position().add(0, e.getBbHeight() + 0.4, 0),
						e.getBbWidth() * 0.9 + 0.3, 7, -0.25, 0.0, false);
				ZushiFx.pull(level, e.position().add(0, 0.1, 0), 1.2, 0.3, 1, false);
			}
			if (pulse) {
				float damage = (float) ((cfg.crushPulseDamage + cfg.crushPulseDamageStep * pulseIndex) * cfg.damageMultiplier);
				DamageSource src = owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().generic();
				e.hurtServer(level, src, damage);
				ZushiFx.send(level, ModParticles.GRAVITY_DUST, e.getX(), e.getY() + 0.1, e.getZ(), 4, 0.4, 0.05, 0.4, 0.03);
			}
		}
		if (pulse) {
			level.playSound(null, BlockPos.containing(c.center), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.6F);
			ZushiFx.circle(level, ModParticles.GRAVITY_DUST, c.center.add(0, 0.2, 0), c.radius * 0.95, 20, 0.02, 0.15, false);
		}

		// ---- affaissement progressif du terrain (cratère)
		if (c.age >= FIRST_SINK && (c.age - FIRST_SINK) % SINK_EVERY == 0 && c.sinks < cfg.crushDepth && c.pending.isEmpty()) {
			c.sinks++;
			sink(c, owner);
		}
		processSink(c, owner, cfg.destroyTerrain);

		if (c.age >= cfg.crushDurationTicks && c.pending.isEmpty()) {
			// fin : le cercle se referme, dernière bouffée de poussière
			ZushiFx.circle(level, ModParticles.GRAVITY_SPARK, c.center.add(0, 0.3, 0), c.radius * 0.5, 30, 0.25, -0.1, false);
			level.playSound(null, BlockPos.containing(c.center), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
			return false;
		}
		return true;
	}

	/** Un palier : les colonnes dont la profondeur cible est atteinte descendront d'un bloc, du centre vers le bord. */
	private static void sink(Crush c, ServerPlayer owner) {
		ServerLevel level = c.level;
		RandomSource r = level.getRandom();
		List<int[]> todo = new ArrayList<>();
		for (int[] col : c.columns) {
			if (col[2] >= c.sinks) {
				todo.add(col);
			}
		}
		double cx = c.center.x, cz = c.center.z;
		todo.sort(java.util.Comparator.comparingDouble(col -> (col[0] + 0.5 - cx) * (col[0] + 0.5 - cx) + (col[1] + 0.5 - cz) * (col[1] + 0.5 - cz)));
		c.pending.addAll(todo);
		// débris 3D qui se soulèvent puis retombent (plus nombreux avec le grand rayon)
		int rocks = 6 + c.sinks * 2;
		for (int i = 0; i < rocks; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = c.safe + 1 + r.nextDouble() * (c.radius - c.safe - 1);
			Vec3 from = new Vec3(c.center.x + Math.cos(a) * d, c.yRef + 1.2 - c.sinks, c.center.z + Math.sin(a) * d);
			Vec3 vel = new Vec3((r.nextDouble() - 0.5) * 0.12, 0.55 + r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 0.12);
			ZushiFx.throwRock(level, from, vel, 0.6 + r.nextDouble() * 0.5);
		}
		level.playSound(null, BlockPos.containing(c.center), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0F, 0.5F);
		level.playSound(null, BlockPos.containing(c.center), SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
		ZushiFx.shake(level, c.center, c.radius + 8, 2, 8);
	}

	/** Enfonce quelques colonnes par tick : l'effondrement se propage en vague depuis l'îlot du lanceur. */
	private static void processSink(Crush c, ServerPlayer owner, boolean destroy) {
		if (c.pending.isEmpty()) {
			return;
		}
		ServerLevel level = c.level;
		RandomSource r = level.getRandom();
		int fx = 0;
		double lastD = 0;
		for (int i = 0; i < COLUMNS_PER_TICK && !c.pending.isEmpty(); i++) {
			int[] col = c.pending.poll();
			BlockPos ground = destroy ? ZushiTerrain.sinkColumn(level, col[0], col[1], c.yRef - (c.sinks - 1), owner) : null;
			BlockPos at = ground != null ? ground : new BlockPos(col[0], c.yRef - c.sinks + 1, col[1]);
			if (r.nextFloat() < 0.1F && fx < 14) {
				fx++;
				// poussière qui accompagne l'effondrement + éclats du vrai bloc
				ZushiFx.crumble(level, at, level.getBlockState(at), 6);
			}
			lastD = Math.sqrt((col[0] + 0.5 - c.center.x) * (col[0] + 0.5 - c.center.x) + (col[1] + 0.5 - c.center.z) * (col[1] + 0.5 - c.center.z));
		}
		// front de l'effondrement : anneau de poussière à la distance atteinte
		ZushiFx.circle(level, ModParticles.GRAVITY_DUST, new Vec3(c.center.x, c.yRef + 1.1 - c.sinks, c.center.z), lastD, 10, 0.04, 0.05, false);
	}

	static void forget(UUID owner) {
		// l'écrasement est une zone : il se termine tout seul même si le lanceur part
	}

	static void cleanupAll() {
		for (Crush c : ACTIVE) {
			zone(c, false);
			c.ring.remove();
			for (Wave w : c.waves) {
				w.display.remove();
			}
		}
		ACTIVE.clear();
	}
}
