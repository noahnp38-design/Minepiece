package minepiece.noah.ability.mera;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Projectile de feu simulé côté serveur (pas d'entité : aucun rendu client à écrire, aucun paquet d'entité).
 * BULLET = petite boule du Fire Gun. BIG = grosse boule qui explose (Hiken, Entei).
 */
final class FireProjectile implements ActiveEffect {
	enum Kind { BULLET, BIG }

	private final ServerLevel level;
	private final UUID owner;
	private final Kind kind;
	private final Vec3 velocity;
	private final double radius;
	private final float damage;
	private final double blastRadius;
	private final float blastDamage;
	/** Boule 3D qui accompagne le projectile (null pour les tirs sans visuel : Fire Gun, Hiken). */
	private final FireballVisual visual;
	/** Sprite animé de la balle du Fire Gun (null pour Hiken et Entei). */
	private final FireGunVisual bulletVisual;
	/** Poing de feu du Hiken (null pour Fire Gun et Entei : leur rendu ne change pas). */
	private final HikenVisual fist;
	private final boolean hiken;
	private Vec3 pos;
	private int life;

	FireProjectile(ServerLevel level, UUID owner, Kind kind, Vec3 pos, Vec3 velocity, double radius,
			float damage, double blastRadius, float blastDamage, int life) {
		this(level, owner, kind, pos, velocity, radius, damage, blastRadius, blastDamage, life, null);
	}

	/** Hiken : même comportement que BIG, avec le poing de feu, la traînée et l'explosion dédiés. */
	static FireProjectile hiken(ServerLevel level, UUID owner, Vec3 pos, Vec3 velocity, double radius,
			double blastRadius, float blastDamage, int life) {
		return new FireProjectile(level, owner, Kind.BIG, pos, velocity, radius, 0, blastRadius, blastDamage, life, null, true);
	}

	FireProjectile(ServerLevel level, UUID owner, Kind kind, Vec3 pos, Vec3 velocity, double radius,
			float damage, double blastRadius, float blastDamage, int life, FireballVisual visual) {
		this(level, owner, kind, pos, velocity, radius, damage, blastRadius, blastDamage, life, visual, false);
	}

	private FireProjectile(ServerLevel level, UUID owner, Kind kind, Vec3 pos, Vec3 velocity, double radius,
			float damage, double blastRadius, float blastDamage, int life, FireballVisual visual, boolean hiken) {
		this.hiken = hiken;
		this.fist = hiken ? HikenVisual.create(level, pos, velocity, radius) : null;
		this.visual = visual;
		this.bulletVisual = kind == Kind.BULLET ? FireGunVisual.create(level, pos, velocity, radius) : null;
		this.level = level;
		this.owner = owner;
		this.kind = kind;
		this.pos = pos;
		this.velocity = velocity;
		this.radius = radius;
		this.damage = damage;
		this.blastRadius = blastRadius;
		this.blastDamage = blastDamage;
		this.life = life;
	}

	@Override
	public boolean tick(MinecraftServer server) {
		if (life-- <= 0) {
			if (kind == Kind.BIG) {
				FireExplosion.explode(level, owner, pos, blastRadius, blastDamage, !hiken);
				if (hiken) {
					HikenFx.impact(level, pos, blastRadius);
				}
				if (visual != null) {
					visual.burst(pos, blastRadius);
				}
			}
			removeBullet();
			return false;
		}

		// Déplacement par petits pas (max 0,5 bloc) pour ne jamais traverser un mur ou un ennemi.
		int steps = Math.max(1, (int) Math.ceil(velocity.length() / 0.5));
		Vec3 step = velocity.scale(1.0 / steps);
		for (int i = 0; i < steps; i++) {
			Vec3 next = pos.add(step);
			BlockPos blockPos = BlockPos.containing(next);
			if (!level.isLoaded(blockPos)) {
				if (visual != null) {
					visual.remove();
				}
				removeBullet();
				return false;
			}
			BlockState state = level.getBlockState(blockPos);
			boolean blocked = !state.getCollisionShape(level, blockPos).isEmpty();
			LivingEntity target = blocked ? null : findTarget(next);
			if (blocked || target != null) {
				impact(blocked ? pos : next, target);
				return false;
			}
			pos = next;
		}

		trail();
		return true;
	}

	private LivingEntity findTarget(Vec3 at) {
		AABB box = new AABB(at, at).inflate(radius);
		List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner));
		return found.isEmpty() ? null : found.get(0);
	}

	private void impact(Vec3 at, LivingEntity target) {
		removeBullet();
		if (kind == Kind.BIG) {
			FireExplosion.explode(level, owner, at, blastRadius, blastDamage, !hiken);
			if (hiken) {
				HikenFx.impact(level, at, blastRadius);
			}
			if (visual != null) {
				visual.burst(at, blastRadius);
			}
			return;
		}
		if (target != null) {
			MeraAbilities.hurt(level, owner, target, damage, 4);
		}
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.06);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.5F, 1.3F);
	}

	private void removeBullet() {
		if (bulletVisual != null) {
			bulletVisual.remove();
		}
		if (fist != null) {
			fist.remove();
		}
	}

	private void trail() {
		if (kind == Kind.BULLET) {
			if (bulletVisual != null && bulletVisual.update(pos)) {
				// Le sprite animé porte la flamme et la traînée : il ne reste que les braises autour.
				FireGunVisual.embers(level, pos, velocity);
			} else {
				// Secours (rendu 3D indisponible) : ancien rendu en particules.
				level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 3, 0.08, 0.08, 0.08, 0.01);
				level.sendParticles(ParticleTypes.SMALL_FLAME, pos.x, pos.y, pos.z, 2, 0.12, 0.12, 0.12, 0.02);
			}
		} else {
			if (hiken && fist != null && fist.update(pos)) {
				HikenFx.trail(level, pos, velocity, radius);
			} else if (visual != null) {
				visual.update(pos, radius, 1.0);
				FireFx.trail(level, pos, velocity, radius);
			} else {
				MeraAbilities.fireball(level, pos, radius);
			}
			if (life % 4 == 0) {
				level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLAZE_BURN, SoundSource.PLAYERS,
						(float) Math.min(4.0, 1.0 + radius * 0.4), 0.6F);
			}
		}
	}
}
