package minepiece.noah.ability.mera;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Particules d'accompagnement de la boule de feu géante (voir FireballVisual pour l'objet 3D).
 * Tout est envoyé en quelques paquets par tick : les particules "dirigées" (count = 0) sont animées par le client,
 * ce qui donne du mouvement sans coût serveur.
 */
final class FireFx {
	private FireFx() {}

	/** Pendant la charge : anneaux de flammes qui tournent, braises qui montent, arcs de feu, mini-explosions. */
	static void charge(ServerLevel level, Vec3 c, double radius, double intensity, int age) {
		RandomSource rnd = level.getRandom();

		// Deux anneaux de flammes inclinés, qui tournent en sens inverse.
		if (age % 2 == 0) {
			double ringRadius = radius * 1.25;
			for (int ring = 0; ring < 2; ring++) {
				double tilt = ring == 0 ? 0.45 : -0.6;
				double dir = ring == 0 ? 1.0 : -1.0;
				int points = 12;
				for (int i = 0; i < points; i++) {
					double a = age * 0.22 * dir + i * 2.0 * Math.PI / points;
					level.sendParticles(ParticleTypes.FLAME,
							c.x + Math.cos(a) * ringRadius,
							c.y + Math.sin(a) * Math.sin(tilt) * ringRadius,
							c.z + Math.sin(a) * Math.cos(tilt) * ringRadius,
							1, 0, 0, 0, 0.0);
				}
			}
		}

		// Braises qui s'élèvent naturellement autour de la boule.
		int embers = 1 + (int) (intensity * 3);
		for (int i = 0; i < embers; i++) {
			Vec3 u = randomUnit(rnd);
			Vec3 p = c.add(u.scale(radius * (1.0 + rnd.nextDouble() * 0.35)));
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 0, 0.0, 1.0, 0.0, 0.05 + rnd.nextDouble() * 0.05);
			if (rnd.nextInt(3) == 0) {
				level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
			}
		}

		// Arcs de feu : de courtes protubérances solaires qui partent de la surface.
		if (age % 4 == 0) {
			int arcs = 1 + (int) (intensity * 2);
			for (int n = 0; n < arcs; n++) {
				Vec3 u = randomUnit(rnd);
				Vec3 t = u.cross(randomUnit(rnd));
				if (t.lengthSqr() < 1.0E-4) {
					continue;
				}
				t = t.normalize();
				int points = 8;
				for (int k = 0; k < points; k++) {
					double s = k / (double) (points - 1);
					double phi = s * 0.7;
					double h = 1.0 + 0.4 * Math.sin(Math.PI * s);
					Vec3 d = u.scale(Math.cos(phi)).add(t.scale(Math.sin(phi)));
					Vec3 p = c.add(d.scale(radius * h));
					level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
				}
			}
		}

		// Petites explosions à la surface, de plus en plus fréquentes.
		if (intensity > 0.25 && age % 10 == 0) {
			Vec3 p = c.add(randomUnit(rnd).scale(radius * 1.05));
			level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
					0.8F, 0.5F + rnd.nextFloat() * 0.4F);
		}
	}

	/** Pendant le vol : traînée de flammes étirée derrière la boule. */
	static void trail(ServerLevel level, Vec3 pos, Vec3 velocity, double radius) {
		RandomSource rnd = level.getRandom();
		Vec3 back = velocity.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 0) : velocity.normalize().scale(-1.0);
		int n = (int) Math.min(14, 4 + radius * 2);
		for (int i = 0; i < n; i++) {
			Vec3 p = pos.add(back.scale(rnd.nextDouble() * radius * 2.5)).add(
					(rnd.nextDouble() - 0.5) * radius * 0.8,
					(rnd.nextDouble() - 0.5) * radius * 0.8,
					(rnd.nextDouble() - 0.5) * radius * 0.8);
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 0, back.x, back.y, back.z, 0.05 + rnd.nextDouble() * 0.1);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 2, radius * 0.4, radius * 0.4, radius * 0.4, 0.01);
		if (level.getGameTime() % 3 == 0) {
			level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 1, radius * 0.4, radius * 0.4, radius * 0.4, 0.0);
		}
	}

	/** Lancement : éclats blancs. */
	static void launch(ServerLevel level, Vec3 c, double radius) {
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, radius * 0.6, radius * 0.6, radius * 0.6, 0.2);
	}

	/** Impact : éclats blancs, anneaux de flammes qui partent dans toutes les directions, gerbe sphérique. */
	static void burst(ServerLevel level, Vec3 at, double blastRadius) {
		RandomSource rnd = level.getRandom();
		double speed = Math.max(0.3, Math.min(1.2, 0.25 + blastRadius * 0.05));

		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 60, blastRadius * 0.15, blastRadius * 0.15, blastRadius * 0.15, 0.6);

		// Trois anneaux (à plat, légèrement vers le haut, fortement vers le haut) : onde de choc de flammes.
		double[] elevations = {0.0, 0.35, 0.8};
		for (double el : elevations) {
			int points = 40;
			for (int i = 0; i < points; i++) {
				double a = 2.0 * Math.PI * i / points;
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 0,
						Math.cos(a) * Math.cos(el), Math.sin(el), Math.sin(a) * Math.cos(el), speed);
			}
		}
		// Gerbe sphérique : des flammes dans toutes les directions.
		for (int i = 0; i < 60; i++) {
			Vec3 u = randomUnit(rnd);
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 0, u.x, u.y, u.z, speed * (0.4 + rnd.nextDouble() * 0.8));
		}
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, (int) Math.min(40, blastRadius * 2),
				blastRadius * 0.2, blastRadius * 0.15, blastRadius * 0.2, 0.0);
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
