package minepiece.noah.ability.mera;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Explosion de feu : dégâts de zone, effets visuels, onde de choc et destruction de blocs étalée sur plusieurs ticks. */
final class FireExplosion {
	private FireExplosion() {}

	static void explode(ServerLevel level, UUID owner, Vec3 c, double radius, float damage) {
		explode(level, owner, c, radius, damage, true);
	}

	/** vfx = false : les particules d'explosion sont fournies par l'appelant (Hiken : HikenFx.impact). Dégâts inchangés. */
	static void explode(ServerLevel level, UUID owner, Vec3 c, double radius, float damage, boolean vfx) {
		MeraConfig cfg = MeraConfig.get();

		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
				(float) Math.min(8.0, 2.0 + radius * 0.4), (float) Math.max(0.5, 1.1 - radius * 0.03));

		if (vfx) {
			// Effets visuels (quelques gros paquets plutôt que beaucoup de petits : bon pour les performances).
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			if (radius >= 8.0) {
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 4, radius * 0.3, radius * 0.2, radius * 0.3, 0);
			}
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, (int) Math.min(300, 40 + radius * 15),
					radius * 0.35, radius * 0.3, radius * 0.35, 0.12);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, (int) Math.min(120, 20 + radius * 6),
					radius * 0.4, radius * 0.35, radius * 0.4, 0.05);
			level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, (int) Math.min(60, radius * 3),
					radius * 0.3, radius * 0.2, radius * 0.3, 0);
		}

		// Dégâts de zone : plein pot au centre, décroissants vers le bord ; la chaleur enflamme au-delà.
		AABB box = new AABB(c, c).inflate(radius * 2.0);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner))) {
			Vec3 center = target.position().add(0, target.getBbHeight() / 2.0, 0);
			double dist = center.distanceTo(c);
			if (dist <= radius) {
				double falloff = Math.max(0.25, 1.0 - dist / radius);
				MeraAbilities.hurt(level, owner, target, (float) (damage * falloff), cfg.burnSeconds);
				Vec3 away = center.subtract(c).normalize();
				double force = Math.min(3.0, 0.5 + falloff * 1.8);
				target.push(away.x * force, 0.3 + 0.4 * falloff, away.z * force);
			} else if (dist <= radius * 2.0) {
				target.igniteForSeconds(cfg.burnSeconds * 0.6F);
			}
		}

		if (vfx && MeraAbilities.canSchedule()) {
			MeraAbilities.schedule(new Shockwave(level, c, radius));
		}
		// Hiken / Dai Enkai (grosses explosions) : le sol prend feu tout autour et un brasier reste sur la zone.
		if (radius >= 3.0) {
			if (cfg.setFires) {
				igniteArea(level, c, radius, cfg);
			}
			if (cfg.blazeTicks > 0 && MeraAbilities.canSchedule()) {
				MeraAbilities.schedule(new Blaze(level, owner, c, radius * 1.5, cfg.blazeTicks));
			}
			// Colonne de flammes au centre.
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y + radius * 0.5, c.z, (int) Math.min(200, radius * 12),
					radius * 0.25, radius * 0.6, radius * 0.25, 0.08);
			level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, (int) Math.min(40, radius * 3), radius * 0.4, 0.3, radius * 0.4, 0);
		}
		if (cfg.destroyBlocks) {
			BlockDestruction destruction = BlockDestruction.create(level, c, radius);
			if (destruction != null) {
				MeraAbilities.schedule(destruction);
			}
		}
	}

	/** Pose des flammes sur le sol tout autour de l'explosion (pas dans le cratère : sur le terrain intact). */
	private static void igniteArea(ServerLevel level, Vec3 c, double radius, MeraConfig cfg) {
		RandomSource random = level.getRandom();
		double outer = radius * 1.8;
		int attempts = (int) Math.min(600, outer * outer * cfg.fireDensity);
		int placed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < attempts && placed < 300; i++) {
			if (random.nextDouble() >= cfg.fireChance * 2.0) {
				continue;
			}
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = radius * 0.5 + random.nextDouble() * (outer - radius * 0.5);
			int x = (int) Math.floor(c.x + Math.cos(a) * r);
			int z = (int) Math.floor(c.z + Math.sin(a) * r);
			int top = (int) Math.floor(c.y + radius);
			int bottom = (int) Math.floor(c.y - radius - 2);
			for (int y = top; y >= bottom; y--) {
				pos.set(x, y, z);
				if (!level.isLoaded(pos)) {
					break;
				}
				if (!level.getBlockState(pos).isAir()) {
					continue;
				}
				BlockPos below = pos.below();
				if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
					level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
					placed++;
					break;
				}
			}
		}
	}

	/** Brasier : la zone reste en feu un moment (flammes, braises, fumée) et enflamme tout ce qui s'y trouve. */
	private static final class Blaze implements ActiveEffect {
		private final ServerLevel level;
		private final UUID owner;
		private final Vec3 center;
		private final double radius;
		private final int duration;
		private int age = 0;

		Blaze(ServerLevel level, UUID owner, Vec3 center, double radius, int duration) {
			this.level = level;
			this.owner = owner;
			this.center = center;
			this.radius = radius;
			this.duration = duration;
		}

		@Override
		public boolean tick(MinecraftServer server) {
			age++;
			RandomSource random = level.getRandom();
			double fade = 1.0 - age / (double) duration;
			int count = (int) Math.max(4, Math.min(40, radius * 3 * fade));
			for (int i = 0; i < count; i++) {
				double a = random.nextDouble() * Math.PI * 2.0;
				double r = Math.sqrt(random.nextDouble()) * radius;
				double x = center.x + Math.cos(a) * r;
				double z = center.z + Math.sin(a) * r;
				double y = center.y - 0.5 + random.nextDouble() * 1.5;
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.2, 0.3, 0.2, 0.03);
				if (i % 3 == 0) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0, z, 1, 0.2, 0.3, 0.2, 0.02);
				}
				if (i % 5 == 0) {
					level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
				}
			}
			if (age % 10 == 0) {
				int burn = MeraConfig.get().burnSeconds;
				AABB box = new AABB(center, center).inflate(radius, 3.0, radius);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
						e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner))) {
					if (e.position().distanceToSqr(center.x, e.getY(), center.z) <= radius * radius) {
						e.igniteForSeconds(Math.max(4.0F, burn * 0.5F));
						if (age % 20 == 0) {
							MeraAbilities.hurt(level, owner, e, 2.0F, Math.max(4, burn / 2));
						}
					}
				}
			}
			if (age % 30 == 0) {
				level.playSound(null, center.x, center.y, center.z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 2.0F, 0.8F);
			}
			return age < duration;
		}
	}

	/** Anneau de flammes qui s'étend depuis le point d'impact. */
	private static final class Shockwave implements ActiveEffect {
		private static final int DURATION = 8;

		private final ServerLevel level;
		private final Vec3 center;
		private final double radius;
		private int age = 0;

		Shockwave(ServerLevel level, Vec3 center, double radius) {
			this.level = level;
			this.center = center;
			this.radius = radius;
		}

		@Override
		public boolean tick(MinecraftServer server) {
			age++;
			double r = radius * age / DURATION;
			int points = (int) Math.min(72, 20 + r * 4);
			for (int i = 0; i < points; i++) {
				double angle = 2 * Math.PI * i / points;
				double x = center.x + Math.cos(angle) * r;
				double z = center.z + Math.sin(angle) * r;
				level.sendParticles(ParticleTypes.FLAME, x, center.y + 0.3, z, 1, 0.1, 0.1, 0.1, 0.0);
				if (i % 2 == 0) {
					level.sendParticles(ParticleTypes.CLOUD, x, center.y + 0.5, z, 1, 0.1, 0.1, 0.1, 0.02);
				}
			}
			return age < DURATION;
		}
	}

	/** Détruit les blocs d'une sphère, du centre vers l'extérieur, en N blocs par tick (pas de gros lag). */
	private static final class BlockDestruction implements ActiveEffect {
		private final ServerLevel level;
		private final BlockPos center;
		private final double radiusSq;
		private final List<BlockPos> blocks;
		private int index = 0;

		private BlockDestruction(ServerLevel level, BlockPos center, double radius, List<BlockPos> blocks) {
			this.level = level;
			this.center = center;
			this.radiusSq = radius * radius;
			this.blocks = blocks;
		}

		static BlockDestruction create(ServerLevel level, Vec3 c, double radius) {
			MeraConfig cfg = MeraConfig.get();
			BlockPos center = BlockPos.containing(c);
			RandomSource random = level.getRandom();
			double radiusSq = radius * radius;
			int r = (int) Math.ceil(radius);

			List<BlockPos> list = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						double d2 = dx * dx + dy * dy + dz * dz;
						if (d2 > radiusSq) {
							continue;
						}
						// Bord irrégulier : plus on s'approche du bord, plus un bloc a de chances d'être épargné.
						if (d2 > radiusSq * 0.5 && random.nextDouble() < 0.35 * (d2 / radiusSq)) {
							continue;
						}
						BlockPos pos = center.offset(dx, dy, dz);
						if (level.isLoaded(pos) && breakable(level, pos, cfg)) {
							list.add(pos);
						}
					}
				}
			}
			if (list.isEmpty()) {
				return null;
			}
			list.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
			if (list.size() > cfg.maxBlocksPerExplosion) {
				list = new ArrayList<>(list.subList(0, cfg.maxBlocksPerExplosion));
			}
			return new BlockDestruction(level, center, radius, list);
		}

		private static boolean breakable(ServerLevel level, BlockPos pos, MeraConfig cfg) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				return false;
			}
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F || hardness > cfg.maxBlockHardness) {
				return false; // bedrock, obsidienne...
			}
			return cfg.destroyBlockEntities || !state.hasBlockEntity();
		}

		@Override
		public boolean tick(MinecraftServer server) {
			MeraConfig cfg = MeraConfig.get();
			int end = Math.min(blocks.size(), index + cfg.blocksPerTick);
			for (; index < end; index++) {
				BlockPos pos = blocks.get(index);
				if (!level.isLoaded(pos) || !breakable(level, pos, cfg)) {
					continue;
				}
				// Bloc de l'extérieur de la sphère : on prévient les voisins (torches, sable...). L'intérieur : pas besoin.
				boolean outer = pos.distSqr(center) > radiusSq * 0.45;
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), outer ? 3 : 2);
				if (index % 40 == 0) {
					level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
							3, 0.3, 0.3, 0.3, 0.02);
				}
			}
			if (index < blocks.size()) {
				return true;
			}
			if (cfg.setFires) {
				igniteGround(cfg);
			}
			return false;
		}

		/** Quelques flammes sur le sol sain autour du cratère. */
		private void igniteGround(MeraConfig cfg) {
			RandomSource random = level.getRandom();
			int placed = 0;
			for (BlockPos pos : blocks) {
				if (placed >= 150) {
					break;
				}
				if (random.nextDouble() >= cfg.fireChance || !level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
					continue;
				}
				BlockPos below = pos.below();
				if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
					level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
					placed++;
				}
			}
		}
	}
}
