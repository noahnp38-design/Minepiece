package minepiece.noah.ability.mera;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Particules du Hiken (poing de feu géant) : traînée multi-couches pendant le vol et explosion à l'impact.
 * Uniquement visuel. Les particules "dirigées" (count = 0) sont animées par le client : peu de paquets, pas de lag.
 * Palette : blanc-jaune au cœur, orange, rouge-orange à l'extérieur (style pixel / voxel, braises incluses).
 */
final class HikenFx {
	private static final DustParticleOptions YELLOW = new DustParticleOptions(0xFFD83A, 1.6F);
	private static final DustParticleOptions ORANGE = new DustParticleOptions(0xFF8A1C, 1.9F);
	private static final DustParticleOptions RED = new DustParticleOptions(0xD93A12, 1.5F);

	private HikenFx() {}

	/** Pendant le vol, appelé une fois par tick : trois couches de flammes de vitesses différentes + braises + éclats. */
	static void trail(ServerLevel level, Vec3 pos, Vec3 velocity, double radius) {
		RandomSource rnd = level.getRandom();
		Vec3 back = velocity.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 0) : velocity.normalize().scale(-1.0);
		long time = level.getGameTime();

		// Couche 1 (cœur) : flammes rapides, serrées, qui filent loin derrière le poing.
		for (int i = 0; i < 4; i++) {
			Vec3 p = scatter(rnd, pos.add(back.scale(radius * (0.8 + rnd.nextDouble() * 1.6))), radius * 0.35);
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 0, back.x, back.y, back.z, 0.30 + rnd.nextDouble() * 0.2);
		}
		// Couche 2 (corps) : petites flammes plus larges et plus lentes.
		for (int i = 0; i < 5; i++) {
			Vec3 p = scatter(rnd, pos.add(back.scale(radius * (0.6 + rnd.nextDouble() * 2.2))), radius * 0.75);
			level.sendParticles(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 0, back.x, back.y, back.z, 0.08 + rnd.nextDouble() * 0.12);
		}
		// Couche 3 (contour) : poussière rouge-orangé qui s'étire et s'estompe au loin.
		if (time % 2 == 0) {
			Vec3 p = scatter(rnd, pos.add(back.scale(radius * (1.4 + rnd.nextDouble() * 2.6))), radius * 0.9);
			level.sendParticles(rnd.nextBoolean() ? RED : ORANGE, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		// Braises qui se dispersent (montent et retombent) + gouttes de lave.
		Vec3 e = scatter(rnd, pos.add(back.scale(radius * rnd.nextDouble() * 1.5)), radius * 0.9);
		level.sendParticles(ParticleTypes.LAVA, e.x, e.y, e.z, 1, 0.1, 0.1, 0.1, 0.0);
		if (time % 2 == 1) {
			level.sendParticles(ParticleTypes.ASH, e.x, e.y, e.z, 0, back.x, 0.4, back.z, 0.05);
		}
		// Petits éclats de feu irréguliers : une gerbe qui jaillit du poing, de temps en temps.
		if (rnd.nextInt(4) == 0) {
			Vec3 o = scatter(rnd, pos, radius * 0.7);
			int n = 3 + rnd.nextInt(3);
			for (int i = 0; i < n; i++) {
				Vec3 u = randomUnit(rnd);
				level.sendParticles(ParticleTypes.FLAME, o.x, o.y, o.z, 0, u.x, u.y, u.z, 0.12 + rnd.nextDouble() * 0.12);
			}
		}
		// Chaleur : un peu de fumée sombre qui traîne tout au bout.
		if (time % 3 == 0) {
			Vec3 p = pos.add(back.scale(radius * 3.2));
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, radius * 0.3, radius * 0.3, radius * 0.3, 0.01);
		}
	}

	/**
	 * Impact : flash très court, gerbe circulaire de flammes, onde de choc orange/jaune (qui s'étend sur quelques ticks),
	 * braises qui retombent. Remplace les particules de FireExplosion pour le Hiken ; les dégâts n'y changent rien.
	 */
	static void impact(ServerLevel level, Vec3 at, double blastRadius) {
		RandomSource rnd = level.getRandom();
		double speed = Math.max(0.35, Math.min(1.1, 0.2 + blastRadius * 0.1));

		// Flash central (court).
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 24, blastRadius * 0.1, blastRadius * 0.1, blastRadius * 0.1, 0.35);
		level.sendParticles(YELLOW, at.x, at.y, at.z, 12, blastRadius * 0.12, blastRadius * 0.12, blastRadius * 0.12, 0.0);

		// Deux anneaux de flammes à plat qui partent en cercle (le gros cercle de l'explosion).
		double[] elevations = {0.0, 0.3};
		for (double el : elevations) {
			int points = 28;
			for (int i = 0; i < points; i++) {
				double a = 2.0 * Math.PI * i / points;
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 0,
						Math.cos(a) * Math.cos(el), Math.sin(el), Math.sin(a) * Math.cos(el), speed);
			}
		}
		// Boule de flammes qui s'écartent dans toutes les directions.
		for (int i = 0; i < 40; i++) {
			Vec3 u = randomUnit(rnd);
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 0, u.x, u.y, u.z, speed * (0.3 + rnd.nextDouble() * 0.9));
		}
		for (int i = 0; i < 12; i++) {
			Vec3 u = randomUnit(rnd);
			level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 0, u.x, u.y, u.z, speed * 0.5);
		}
		// Braises incandescentes qui retombent.
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 22, blastRadius * 0.35, blastRadius * 0.2, blastRadius * 0.35, 0.0);
		level.sendParticles(ParticleTypes.FALLING_LAVA, at.x, at.y + 1.0, at.z, 10, blastRadius * 0.35, blastRadius * 0.15, blastRadius * 0.35, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 10, blastRadius * 0.25, blastRadius * 0.2, blastRadius * 0.25, 0.03);

		if (MeraAbilities.canSchedule()) {
			MeraAbilities.schedule(new Shock(level, at, blastRadius));
		}
	}

	/** Onde de choc : anneaux jaune / orange / rouge qui s'étendent puis s'estompent (6 ticks, peu de particules). */
	private static final class Shock implements ActiveEffect {
		private static final int DURATION = 7;

		private final ServerLevel level;
		private final Vec3 center;
		private final double radius;
		private int age = 0;

		Shock(ServerLevel level, Vec3 center, double radius) {
			this.level = level;
			this.center = center;
			this.radius = radius;
		}

		@Override
		public boolean tick(MinecraftServer server) {
			age++;
			double t = age / (double) DURATION;
			double r = radius * 0.9 * Math.sqrt(t);
			double lift = 0.25 + 0.5 * t;
			int points = (int) Math.min(40, 14 + r * 4);
			// Plus on avance, moins il y a de particules : l'onde s'estompe progressivement.
			int step = age <= 3 ? 1 : age <= 5 ? 2 : 3;
			for (int i = 0; i < points; i += step) {
				double a = 2.0 * Math.PI * i / points + age * 0.2;
				double x = center.x + Math.cos(a) * r;
				double z = center.z + Math.sin(a) * r;
				level.sendParticles(age <= 3 ? YELLOW : ORANGE, x, center.y + lift, z, 1, 0.05, 0.05, 0.05, 0.0);
				if (i % 2 == 0) {
					double r2 = r * 0.8;
					level.sendParticles(age <= 4 ? ORANGE : RED, center.x + Math.cos(a) * r2, center.y + lift * 0.7,
							center.z + Math.sin(a) * r2, 1, 0.05, 0.05, 0.05, 0.0);
				}
			}
			return age < DURATION;
		}
	}

	private static Vec3 scatter(RandomSource rnd, Vec3 p, double spread) {
		return p.add((rnd.nextDouble() - 0.5) * 2.0 * spread, (rnd.nextDouble() - 0.5) * 2.0 * spread,
				(rnd.nextDouble() - 0.5) * 2.0 * spread);
	}

	private static Vec3 randomUnit(RandomSource rnd) {
		double x;
		double y;
		double z;
		double len;
		do {
			x = rnd.nextDouble() * 2.0 - 1.0;
			y = rnd.nextDouble() * 2.0 - 1.0;
			z = rnd.nextDouble() * 2.0 - 1.0;
			len = x * x + y * y + z * z;
		} while (len > 1.0 || len < 1.0E-4);
		double inv = 1.0 / Math.sqrt(len);
		return new Vec3(x * inv, y * inv, z * inv);
	}
}
