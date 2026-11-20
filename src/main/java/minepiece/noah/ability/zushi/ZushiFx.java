package minepiece.noah.ability.zushi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import minepiece.noah.network.WadoShakePayload;
import minepiece.noah.particle.ModParticles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Effets visuels communs du Zushi Zushi no Mi : particules violettes / mauves / blanches (textures du mod),
 * éclairs gravitationnels, secousse de caméra et débris rocheux 3D projetés.
 *
 * Optimisation :
 * - toutes les particules passent par le serveur avec overrideLimiter = false : le réglage "Particules"
 *   (Toutes / Réduites / Minimales) de CHAQUE joueur s'applique donc chez lui ;
 * - particleDensity (config) réduit tout côté serveur ;
 * - les débris 3D sont plafonnés (maxFlyingDebris) et supprimés à l'atterrissage.
 */
public final class ZushiFx {
	private static final List<Debris> DEBRIS = new ArrayList<>();
	private static final String[] ROCKS = {"zushi_rock_a", "zushi_rock_b", "zushi_rock_c"};

	private ZushiFx() {}

	public static int n(int count) {
		if (count <= 0) {
			return 0;
		}
		double d = ZushiConfig.get().particleDensity;
		return Math.max(d > 0.0 ? 1 : 0, (int) Math.round(count * d));
	}

	/** Particules normales (visibles jusqu'à 32 blocs). */
	public static void send(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
			double dx, double dy, double dz, double speed) {
		int c = n(count);
		if (c > 0) {
			level.sendParticles(type, x, y, z, c, dx, dy, dz, speed);
		}
	}

	/** Particules visibles de loin (météorite : jusqu'à 512 blocs). */
	public static void far(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
			double dx, double dy, double dz, double speed) {
		int c = n(count);
		if (c > 0) {
			level.sendParticles(type, false, true, x, y, z, c, dx, dy, dz, speed);
		}
	}

	/** Une particule avec une vitesse précise (count = 0 : dx, dy, dz deviennent la vitesse). */
	public static void velocity(ServerLevel level, ParticleOptions type, Vec3 at, Vec3 v, boolean farView) {
		level.sendParticles(type, false, farView, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
	}

	/** Particules aspirées vers "center" depuis un disque (ou une sphère aplatie) de rayon "radius". */
	public static void pull(ServerLevel level, Vec3 center, double radius, double height, int count, boolean farView) {
		RandomSource r = level.getRandom();
		for (int i = 0, c = n(count); i < c; i++) {
			double a = r.nextDouble() * Math.PI * 2.0;
			double d = radius * (0.55 + r.nextDouble() * 0.45);
			Vec3 offset = new Vec3(Math.cos(a) * d, (r.nextDouble() - 0.3) * height, Math.sin(a) * d);
			// Comportement "portail" : la particule part de center + offset et revient vers center.
			velocity(level, ModParticles.GRAVITY_PULL, center, offset, farView);
		}
	}

	/** Cercle de particules (ondes, cercles au sol). v = vitesse verticale, outward = vitesse vers l'extérieur. */
	public static void circle(ServerLevel level, ParticleOptions type, Vec3 c, double radius, int count, double vy, double outward, boolean farView) {
		RandomSource r = level.getRandom();
		int total = n(count);
		for (int i = 0; i < total; i++) {
			double a = (i + r.nextDouble() * 0.5) / total * Math.PI * 2.0;
			double cos = Math.cos(a), sin = Math.sin(a);
			velocity(level, type, c.add(cos * radius, 0, sin * radius), new Vec3(cos * outward, vy, sin * outward), farView);
		}
	}

	/** Éclats de bloc (vrais blocs du terrain) + poussière violette. */
	public static void crumble(ServerLevel level, BlockPos pos, BlockState state, int count) {
		if (!state.isAir()) {
			send(level, new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
					count, 0.4, 0.2, 0.4, 0.15);
		}
		send(level, ModParticles.GRAVITY_DUST, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, Math.max(1, count / 3), 0.6, 0.3, 0.6, 0.02);
	}

	/** Éclair gravitationnel violet : ligne brisée de particules lumineuses entre deux points. */
	static void bolt(ServerLevel level, Vec3 from, Vec3 to, boolean farView) {
		RandomSource r = level.getRandom();
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 0.5) {
			return;
		}
		Vec3 dir = d.scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0E-4) {
			side = new Vec3(1, 0, 0);
		}
		side = side.normalize();
		Vec3 up = side.cross(dir).normalize();
		int segments = Math.max(3, (int) (len / 1.6));
		Vec3 prev = from;
		double density = Math.max(0.35, ZushiConfig.get().particleDensity);
		for (int s = 1; s <= segments; s++) {
			double t = s / (double) segments;
			Vec3 p = s == segments ? to : from.add(d.scale(t))
					.add(side.scale((r.nextDouble() - 0.5) * 1.8)).add(up.scale((r.nextDouble() - 0.5) * 1.8));
			Vec3 seg = p.subtract(prev);
			int dots = Math.max(2, (int) (seg.length() / 0.35 * density));
			for (int k = 0; k < dots; k++) {
				Vec3 q = prev.add(seg.scale(k / (double) dots));
				level.sendParticles(ModParticles.GRAVITY_FLASH, false, farView, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			if (r.nextFloat() < 0.25) {
				level.sendParticles(ModParticles.GRAVITY_SPARK, false, farView, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.02);
			}
			prev = p;
		}
	}

	/** Secousse de caméra modérée pour les joueurs proches (plus faible avec la distance). */
	public static void shake(ServerLevel level, Vec3 center, double range, int maxIntensity, int ticks) {
		if (!ZushiConfig.get().cameraShake) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceTo(center);
			if (d > range) {
				continue;
			}
			int intensity = (int) Math.round(maxIntensity * (1.0 - d / range));
			if (intensity >= 1) {
				ServerPlayNetworking.send(p, new WadoShakePayload(Math.max(4, (int) (ticks * (1.0 - d / range * 0.5))), intensity));
			}
		}
	}

	// ------------------------------------------------------------------ débris 3D projetés

	private static final class Debris {
		final ZushiDisplay display;
		final ServerLevel level;
		/** Bloc réel copié du terrain (null = fragment rocheux du mod). */
		final BlockState block;
		Vec3 pos;
		Vec3 vel;
		float yaw;
		final float spin;
		int age;
		final int life;
		/** Ticks de suspension (lévitation gravitationnelle) avant de retomber. */
		final int hover;
		/** Débris de pierre "neutre" (Ishi Ishi no Mi) : poussière grise, pas d'étincelles violettes. */
		boolean plain;

		Debris(ServerLevel level, ZushiDisplay display, BlockState block, Vec3 pos, Vec3 vel, float spin, int life, int hover) {
			this.level = level;
			this.display = display;
			this.block = block;
			this.pos = pos;
			this.vel = vel;
			this.spin = spin;
			this.life = life;
			this.hover = hover;
		}
	}

	private static int count(boolean blocks) {
		int n = 0;
		for (Debris d : DEBRIS) {
			if ((d.block != null) == blocks) {
				n++;
			}
		}
		return n;
	}

	/** Lance un débris rocheux 3D (se soulève / est projeté, tournoie, retombe puis se brise). */
	public static void throwRock(ServerLevel level, Vec3 from, Vec3 velocity, double scale) {
		floatRock(level, from, velocity, scale, 0);
	}

	/** Morceau de météorite (roche sombre à fissures violettes) qui se détache et roule. */
	public static void throwChunk(ServerLevel level, Vec3 from, Vec3 velocity, double scale) {
		floatRock(level, "zushi_rock_c", from, velocity, scale, 0);
	}

	/** Fragment rocheux qui reste en suspension "hover" ticks (la gravité le retient) puis retombe. */
	public static void floatRock(ServerLevel level, Vec3 from, Vec3 velocity, double scale, int hover) {
		floatRock(level, ROCKS[level.getRandom().nextInt(ROCKS.length)], from, velocity, scale, hover);
	}

	private static void floatRock(ServerLevel level, String model, Vec3 from, Vec3 velocity, double scale, int hover) {
		if (count(false) >= ZushiConfig.get().maxFlyingDebris) {
			return;
		}
		RandomSource r = level.getRandom();
		ZushiDisplay d = ZushiDisplay.create(level, model, from, r.nextFloat() * 360.0F, 0.0F, scale, 0.0, 1, 1.5F, false);
		if (!d.isActive()) {
			return;
		}
		DEBRIS.add(new Debris(level, d, null, from, velocity, (r.nextFloat() - 0.5F) * 40.0F, hover + 30 + r.nextInt(25), hover));
	}

	/**
	 * Projette une copie VISUELLE d'un vrai bloc du terrain (même texture), qui tournoie, retombe puis se brise.
	 * Ce n'est pas un bloc qui tombe (pas de falling_block) : rien n'est posé au sol, l'entité est supprimée à l'atterrissage.
	 */
	public static void throwBlock(ServerLevel level, BlockState state, Vec3 from, Vec3 velocity, double scale) {
		throwBlock(level, state, from, velocity, scale, false);
	}

	/**
	 * Ishi Ishi no Mi : copie visuelle d'un bloc ou fragment rocheux du mod (model != null) projeté, avec des effets gris
	 * (poussière de pierre, éclats) au lieu des étincelles gravitationnelles.
	 */
	public static void throwStone(ServerLevel level, BlockState state, String model, Vec3 from, Vec3 velocity, double scale) {
		if (model != null) {
			if (count(false) >= ZushiConfig.get().maxFlyingDebris) {
				return;
			}
			RandomSource r = level.getRandom();
			ZushiDisplay d = ZushiDisplay.create(level, model, from, r.nextFloat() * 360.0F, 0.0F, scale, 0.0, 1, 2.0F, false);
			if (!d.isActive()) {
				return;
			}
			Debris db = new Debris(level, d, null, from, velocity, (r.nextFloat() - 0.5F) * 45.0F, 35 + r.nextInt(25), 0);
			db.plain = true;
			DEBRIS.add(db);
		} else {
			throwBlock(level, state, from, velocity, scale, true);
		}
	}

	private static void throwBlock(ServerLevel level, BlockState state, Vec3 from, Vec3 velocity, double scale, boolean plain) {
		if (count(true) >= ZushiConfig.get().maxBlockDebris || state.isAir()) {
			return;
		}
		RandomSource r = level.getRandom();
		ZushiDisplay d = ZushiDisplay.createBlock(level, NbtUtils.writeBlockState(state).toString(), from, scale, 2.0F);
		if (!d.isActive()) {
			return;
		}
		Debris db = new Debris(level, d, state, from, velocity, (r.nextFloat() - 0.5F) * 50.0F, 45 + r.nextInt(30), 0);
		db.plain = plain;
		DEBRIS.add(db);
	}

	static void tickDebris() {
		for (Iterator<Debris> it = DEBRIS.iterator(); it.hasNext();) {
			Debris d = it.next();
			d.age++;
			if (d.age <= d.hover) {
				// suspension : flotte lentement, légère oscillation
				d.vel = new Vec3(d.vel.x * 0.9, d.vel.y * 0.9 + Math.sin(d.age * 0.2) * 0.004, d.vel.z * 0.9);
			} else {
				d.vel = new Vec3(d.vel.x * 0.98, d.vel.y - 0.07, d.vel.z * 0.98);
			}
			Vec3 next = d.pos.add(d.vel);
			BlockPos bp = BlockPos.containing(next);
			boolean loaded = d.level.isLoaded(bp);
			boolean landed = d.vel.y < 0 && loaded && d.level.getBlockState(bp).isSolid();
			if (landed || d.age >= d.life || !d.display.isActive() || !loaded) {
				if (loaded) {
					if (d.block != null) {
						send(d.level, new BlockParticleOption(ParticleTypes.BLOCK, d.block), d.pos.x, d.pos.y, d.pos.z, 10, 0.3, 0.2, 0.3, 0.15);
					}
					if (d.plain) {
						send(d.level, ModParticles.STONE_FRAGMENT, d.pos.x, d.pos.y, d.pos.z, 6, 0.3, 0.2, 0.3, 0.12);
						send(d.level, ModParticles.STONE_DUST, d.pos.x, d.pos.y + 0.2, d.pos.z, 3, 0.4, 0.2, 0.4, 0.02);
					} else {
						send(d.level, ModParticles.ROCK_CHIP, d.pos.x, d.pos.y, d.pos.z, 6, 0.3, 0.2, 0.3, 0.12);
						send(d.level, ModParticles.GRAVITY_DUST, d.pos.x, d.pos.y + 0.2, d.pos.z, 3, 0.4, 0.2, 0.4, 0.02);
					}
				}
				d.display.remove();
				it.remove();
				continue;
			}
			d.pos = next;
			d.yaw += d.spin;
			d.display.move(d.pos, d.yaw, (float) (Math.sin(d.age * 0.35) * 70.0));
			if (d.plain) {
				if (d.age % 4 == 0) {
					send(d.level, ModParticles.STONE_DUST, d.pos.x, d.pos.y, d.pos.z, 1, 0.1, 0.1, 0.1, 0.0);
				}
			} else if (d.age % 3 == 0) {
				send(d.level, ModParticles.GRAVITY_SPARK, d.pos.x, d.pos.y, d.pos.z, 1, 0.1, 0.1, 0.1, 0.0);
			}
		}
	}

	static void cleanupAll() {
		for (Debris d : DEBRIS) {
			d.display.remove();
		}
		DEBRIS.clear();
	}
}
