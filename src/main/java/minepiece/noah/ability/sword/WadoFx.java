package minepiece.noah.ability.sword;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Effets visuels de la Wado Ichimonji (particules seulement, aucune entité). Tout est blanc : lame, vent, croissants.
 * Les particules "dirigées" (count = 0) sont animées par le client : du mouvement sans coût serveur.
 */
final class WadoFx {
	private WadoFx() {}

	/** Vecteur horizontal à droite de la direction donnée. */
	static Vec3 rightOf(Vec3 dir) {
		Vec3 right = dir.cross(new Vec3(0, 1, 0));
		return right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
	}

	/** Grand croissant blanc de coupe (sprite vanilla de balayage, agrandi). size 0 = taille normale, 3 = très grand. */
	static void sweep(ServerLevel level, Vec3 at, double size) {
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0, -size, 0.0, 0.0, 1.0);
	}

	/**
	 * Croissant dessiné avec des particules : plusieurs arcs de cercle qui se rétrécissent vers les pointes, bombés
	 * vers l'avant (dir), à plat devant le joueur.
	 */
	static void crescent(ServerLevel level, Vec3 center, Vec3 dir, double radius, double halfAngle, int points) {
		Vec3 right = rightOf(dir);
		for (int arc = 0; arc < 3; arc++) {
			double r = radius * (1.0 - 0.07 * arc);
			double half = halfAngle * (1.0 - 0.14 * arc);
			for (int i = 0; i < points; i++) {
				double a = -half + 2.0 * half * i / (points - 1);
				Vec3 p = center.add(dir.scale(Math.cos(a) * r)).add(right.scale(Math.sin(a) * r));
				level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	/** Petite aura blanche autour de la lame (préparation). */
	static void aura(ServerLevel level, Vec3 blade) {
		level.sendParticles(ParticleTypes.END_ROD, blade.x, blade.y, blade.z, 2, 0.3, 0.3, 0.3, 0.01);
	}

	/** Particules d'énergie qui se concentrent vers la lame pendant la charge du 90 Cleasher Slash. */
	static void charge(ServerLevel level, Vec3 blade, int age, int total) {
		RandomSource rnd = level.getRandom();
		double t = age / (double) total;
		int n = 2 + (int) (t * 4);
		for (int i = 0; i < n; i++) {
			double theta = rnd.nextDouble() * Math.PI * 2.0;
			double phi = Math.acos(2.0 * rnd.nextDouble() - 1.0);
			double r = 2.4 - 1.4 * t;
			Vec3 p = blade.add(Math.sin(phi) * Math.cos(theta) * r, Math.cos(phi) * r, Math.sin(phi) * Math.sin(theta) * r);
			Vec3 toward = blade.subtract(p).normalize();
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, toward.x, toward.y, toward.z, 0.12);
		}
		level.sendParticles(ParticleTypes.END_ROD, blade.x, blade.y, blade.z, 3, 0.15, 0.15, 0.15, 0.02);
		if (age % 4 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, blade.x, blade.y - 1.0, blade.z, 3, 0.5, 0.1, 0.5, 0.02);
		}
	}

	/** Départ du dash : souffle de vent et éclat blanc. */
	static void dashStart(ServerLevel level, Vec3 at, Vec3 dir) {
		Vec3 back = dir.scale(-1.0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 0, back.x, 0.02, back.z, 0.5);
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 8, 0.4, 0.05, 0.4, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 12, 0.3, 0.5, 0.3, 0.15);
	}

	/** À chaque tick du dash : traînée blanche très rapide en croissant derrière la lame + vent. */
	static void dashFrame(ServerLevel level, Vec3 pos, Vec3 dir) {
		Vec3 chest = pos.add(0, 1.1, 0);
		Vec3 back = dir.scale(-1.0);
		crescent(level, chest.add(dir.scale(0.6)), dir, 1.7, 1.15, 9);
		sweep(level, chest.add(dir.scale(0.4)), 2.0);
		for (int i = 0; i < 6; i++) {
			Vec3 p = chest.add(back.scale(0.3 * i)).add(0, (i % 2 == 0 ? 0.25 : -0.25), 0);
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, back.x, 0.0, back.z, 0.25);
		}
		level.sendParticles(ParticleTypes.CLOUD, chest.x, chest.y - 0.4, chest.z, 3, 0.4, 0.3, 0.4, 0.02);
	}

	/** Trait blanc de la coupe sur toute la trajectoire du dash, visible une fois l'attaque terminée. */
	static void slashLine(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		int n = Math.max(2, (int) (delta.length() * 3.0));
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(delta.scale(i / (double) n)).add(0, 1.15, 0);
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.05, 0.0, 0.0);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.1, 0.2, 0.1, 0.3);
			}
		}
	}

	/** Coupure sur un ennemi touché : grand croissant, trait en diagonale, étincelles et petit éclat lumineux. */
	static void cut(ServerLevel level, Vec3 center, Vec3 dir) {
		sweep(level, center, 3.0);
		Vec3 right = rightOf(dir);
		for (int i = -5; i <= 5; i++) {
			double k = i / 5.0;
			Vec3 p = center.add(right.scale(k * 1.0)).add(0, k * 0.7, 0);
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, center.x, center.y, center.z, 16, 0.35, 0.5, 0.35, 0.3);
		level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 12, 0.3, 0.5, 0.3, 0.4);
	}

	/** Lâcher du 90 Cleasher Slash : gerbe blanche devant le joueur. */
	static void release(ServerLevel level, Vec3 at, Vec3 dir) {
		crescent(level, at, dir, 2.6, 1.3, 12);
		sweep(level, at.add(dir.scale(0.8)), 3.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.3);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 12, 0.6, 0.3, 0.6, 0.1);
	}

	/** À chaque tick de l'onde de choc : grand croissant d'énergie blanche, particules lumineuses et vent. */
	static void waveFrame(ServerLevel level, Vec3 pos, Vec3 dir) {
		Vec3 back = dir.scale(-1.0);
		RandomSource rnd = level.getRandom();
		crescent(level, pos.add(dir.scale(-0.6)), dir, 3.2, 1.2, 11);
		sweep(level, pos, 3.0);
		sweep(level, pos.add(dir.scale(-0.9)), 2.5);
		for (int i = 0; i < 5; i++) {
			Vec3 p = pos.add(back.scale(rnd.nextDouble() * 3.0)).add(
					(rnd.nextDouble() - 0.5) * 3.0, (rnd.nextDouble() - 0.5) * 1.6, (rnd.nextDouble() - 0.5) * 3.0);
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 0, back.x, back.y, back.z, 0.2 + rnd.nextDouble() * 0.2);
		}
		level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 4, 1.4, 0.8, 1.4, 0.05);
	}

	/** Fin de l'onde de choc (mur ou fin de portée) : explosion de particules blanches. */
	static void impact(ServerLevel level, Vec3 at) {
		sweep(level, at, 3.0);
		sweep(level, at.add(0, 0.5, 0), 2.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 50, 1.2, 1.0, 1.2, 0.35);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 20, 1.0, 0.8, 1.0, 0.15);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 1.0, 0.8, 1.0, 0.6);
	}
}
