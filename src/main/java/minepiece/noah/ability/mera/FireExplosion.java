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
		MeraConfig cfg = MeraConfig.get();

		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS,
				(float) Math.min(8.0, 2.0 + radius * 0.4), (float) Math.max(0.5, 1.1 - radius * 0.03));

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

		// Dégâts de zone : plein pot au centre, décroissants vers le bord ; la chaleur enflamme au-delà.
		AABB box = new AABB(c, c).inflate(radius * 1.5);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner))) {
			Vec3 center = target.position().add(0, target.getBbHeight() / 2.0, 0);
			double dist = center.distanceTo(c);
			if (dist <= radius) {
				double falloff = Math.max(0.25, 1.0 - dist / radius);
				MeraAbilities.hurt(level, owner, target, (float) (damage * falloff), 8);
				Vec3 away = center.subtract(c).normalize();
				double force = Math.min(3.0, 0.5 + falloff * 1.8);
				target.push(away.x * force, 0.3 + 0.4 * falloff, away.z * force);
			} else if (dist <= radius * 1.5) {
				target.igniteForSeconds(4.0F);
			}
		}

		if (MeraAbilities.canSchedule()) {
			MeraAbilities.schedule(new Shockwave(level, c, radius));
		}
		if (cfg.destroyBlocks) {
			BlockDestruction destruction = BlockDestruction.create(level, c, radius);
			if (destruction != null) {
				MeraAbilities.schedule(destruction);
			}
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
