package minepiece.noah.ability;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pouvoirs du Hie Hie no Mi (fruit de la Glace). */
final class IceAbilities {
	private static final int WALK_RADIUS = 2;

	private IceAbilities() {}

	// ------------------------------------------------------------------ passif : glace sous les pieds

	/** Appelé chaque tick : transforme l'eau sous le joueur en glace givrée (qui refond toute seule). */
	static void tickPassive(ServerPlayer player, ServerLevel level) {
		if (!player.onGround()) {
			return;
		}
		BlockPos center = player.blockPosition().below();
		int r2 = WALK_RADIUS * WALK_RADIUS + 1;
		for (int dx = -WALK_RADIUS; dx <= WALK_RADIUS; dx++) {
			for (int dz = -WALK_RADIUS; dz <= WALK_RADIUS; dz++) {
				if (dx * dx + dz * dz > r2) {
					continue;
				}
				BlockPos pos = center.offset(dx, 0, dz);
				BlockState state = level.getBlockState(pos);
				if (state.is(Blocks.WATER) && state.getValue(LiquidBlock.LEVEL) == 0
						&& level.getBlockState(pos.above()).isAir()) {
					level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
					level.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(level.getRandom(), 60, 120));
				}
			}
		}
	}

	// ------------------------------------------------------------------ attaques

	/** @return true si l'attaque a bien été lancée (le temps de recharge démarre alors). */
	static boolean cast(ServerPlayer player, ServerLevel level, int slot) {
		return switch (slot) {
			case 0 -> IceTime.start(player, level);
			case 1 -> PheasantBeak.start(player, level);
			case 2 -> IceAge.start(player, level);
			default -> false;
		};
	}

	/** Attaque 1 : Ice Time, voir IceTime (onde de givre animée autour du joueur). */

	// ------------------------------------------------------------------ attaque 3 : Ice Age (voir IceAge)

	/** Touche relâchée : seul le Pheasant Beak se charge (l'Ice Age part tout seul). */
	static void release(ServerPlayer player, int slot) {
		if (slot == 1) {
			PheasantBeak.release(player);
		}
	}

	/** Appelé une fois par tick. */
	static void tick(MinecraftServer server) {
		IceFx.tick(server);
		IceAge.tick(server);
		PheasantBeak.tick(server);
		IceTime.tick(server);
	}

	/** Oublie les données d'un joueur qui se déconnecte / meurt. */
	static void forget(UUID player) {
		IceAge.forget(player);
		PheasantBeak.forget(player);
		IceFx.forget(player);
	}

	// ------------------------------------------------------------------ utilitaires

	private static void freeze(LivingEntity target, int extraFrozenTicks, int slownessLevel, int slownessTicks) {
		target.setTicksFrozen(target.getTicksRequiredToFreeze() + extraFrozenTicks);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slownessTicks, slownessLevel));
	}

	/** Cercle de flocons autour du joueur. */
	private static void ring(ServerLevel level, ServerPlayer player, double radius) {
		int points = 48;
		for (int i = 0; i < points; i++) {
			double angle = 2 * Math.PI * i / points;
			level.sendParticles(ParticleTypes.SNOWFLAKE,
					player.getX() + Math.cos(angle) * radius, player.getY() + 0.2, player.getZ() + Math.sin(angle) * radius,
					2, 0.1, 0.1, 0.1, 0.0);
		}
	}
}
