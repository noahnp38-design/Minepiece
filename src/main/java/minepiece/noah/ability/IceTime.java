package minepiece.noah.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ice Time (attaque 1 du Hie Hie no Mi) : explosion de froid autour du joueur.
 * 1. Éclat : tourbillon de neige et de givre qui monte autour du joueur, cristaux 3D qui jaillissent du sol.
 * 2. Onde : un anneau de givre s'étend jusqu'à RADIUS ; chaque créature atteinte est figée dans un bloc de glace 3D.
 * 3. Après : neige et brume sur la zone, les créatures prises scintillent de givre.
 * Le terrain n'est pas transformé (sauf l'eau, qui gèle temporairement comme sous les pieds du joueur).
 */
final class IceTime {
	private static final double RADIUS = 7.0;
	private static final double SPEED = 0.7;          // blocs par tick (onde en 10 ticks)
	private static final int LINGER = 30;              // 1,5 s de neige après l'onde
	private static final int SPIRAL_TICKS = 12;
	private static final float DAMAGE = 6.0F;
	private static final int FREEZE_TICKS = 50;        // figé 2,5 s
	private static final int SLOW_TICKS = 140;

	private static final DustParticleOptions CYAN = new DustParticleOptions(0x7FD8FF, 1.4F);
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xEAFBFF, 1.2F);

	private static final List<Blast> BLASTS = new ArrayList<>();

	private IceTime() {}

	private static final class Blast {
		final ServerLevel level;
		final UUID owner;
		final Vec3 center;
		final BlockPos origin;
		final Set<UUID> hit = new HashSet<>();
		final List<LivingEntity> frozen = new ArrayList<>();
		double radius = 0.0;
		int age = 0;

		Blast(ServerLevel level, ServerPlayer player) {
			this.level = level;
			this.owner = player.getUUID();
			this.center = player.position();
			this.origin = player.blockPosition();
		}
	}

	static boolean start(ServerPlayer player, ServerLevel level) {
		Blast b = new Blast(level, player);
		BLASTS.add(b);
		Vec3 c = b.center;
		// Éclat initial
		level.sendParticles(ModParticles.ICE_SHARD, c.x, c.y + 1.0, c.z, 40, 0.4, 0.6, 0.4, 0.35);
		level.sendParticles(ModParticles.ICE_SPARKLE, c.x, c.y + 1.0, c.z, 30, 0.5, 0.8, 0.5, 0.2);
		level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1.0, c.z, 40, 0.4, 0.6, 0.4, 0.25);
		level.sendParticles(ModParticles.FROST_MIST, c.x, c.y + 0.2, c.z, 16, 0.8, 0.1, 0.8, 0.06);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 12, 0.3, 0.5, 0.3, 0.15);
		IceFx.burst(level, c);
		IceFx.mistBurst(level, c, RADIUS, 1.5);
		BlockPos at = player.blockPosition();
		level.playSound(null, at, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 2.0F, 0.7F);
		level.playSound(null, at, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.6F, 0.8F);
		level.playSound(null, at, SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.5F, 0.6F);
		return true;
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Blast> it = BLASTS.iterator(); it.hasNext();) {
			if (!tick(server, it.next())) {
				it.remove();
			}
		}
	}

	private static boolean tick(MinecraftServer server, Blast b) {
		ServerLevel level = b.level;
		b.age++;
		Vec3 c = b.center;

		// ---- tourbillon de neige qui monte autour du lanceur
		if (b.age <= SPIRAL_TICKS) {
			IceFx.mistAt(level, c.add(0, 0.2 + b.age * 0.1, 0), 0.7, 2);
			for (int arm = 0; arm < 3; arm++) {
				double a = b.age * 0.55 + arm * (Math.PI * 2.0 / 3.0);
				double r = 0.9 + b.age * 0.05;
				double y = c.y + b.age * 0.2;
				double x = c.x + Math.cos(a) * r;
				double z = c.z + Math.sin(a) * r;
				level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 2, 0.05, 0.05, 0.05, 0.01);
				level.sendParticles(ModParticles.ICE_SPARKLE, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
				level.sendParticles(arm == 0 ? WHITE : CYAN, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}

		// ---- onde de givre
		if (b.radius < RADIUS) {
			b.radius = Math.min(RADIUS, b.radius + SPEED);
			IceFx.waveStep(level, b.origin, b.radius, (int) RADIUS, 1.0);
			freezeWater(level, b);
			hit(server, b);
			if (b.age % 2 == 0) {
				level.playSound(null, b.origin, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F + b.age * 0.05F);
			}
			return true;
		}

		// ---- la zone reste glaciale un instant
		int k = b.age;
		if (k % 3 == 0) {
			IceFx.ambient(level, b.origin, (int) RADIUS, 0.8);
		}
		if (k % 2 == 0) {
			for (LivingEntity e : b.frozen) {
				if (e.isAlive()) {
					IceFx.frozenTick(level, e, 1.0);
				}
			}
		}
		return k < 10 + LINGER;
	}

	private static void hit(MinecraftServer server, Blast b) {
		ServerLevel level = b.level;
		Vec3 c = b.center;
		ServerPlayer owner = server.getPlayerList().getPlayer(b.owner);
		AABB box = new AABB(c, c).inflate(b.radius + 1.0, 3.0, b.radius + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(b.owner))) {
			double dx = e.getX() - c.x;
			double dz = e.getZ() - c.z;
			if (dx * dx + dz * dz > b.radius * b.radius || !b.hit.add(e.getUUID())) {
				continue;
			}
			e.hurtServer(level, owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().freeze(), DAMAGE);
			if (!e.isAlive()) {
				continue;
			}
			e.setTicksFrozen(e.getTicksRequiredToFreeze() + FREEZE_TICKS * 2);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FREEZE_TICKS, 9));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 3));
			e.setDeltaMovement(0.0, Math.min(0.0, e.getDeltaMovement().y), 0.0);
			IceFx.encase(level, e, FREEZE_TICKS);
			IceFx.entityHit(level, e, 1.0);
			b.frozen.add(e);
			level.playSound(null, e.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.4F);
			level.playSound(null, e.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 0.9F);
		}
	}

	/** L'eau de surface sur l'anneau gèle (glace givrée qui refond toute seule). */
	private static void freezeWater(ServerLevel level, Blast b) {
		int r = (int) Math.ceil(b.radius);
		double inner = Math.max(0.0, b.radius - SPEED - 0.5);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > b.radius || d < inner) {
					continue;
				}
				for (int dy = -2; dy <= 1; dy++) {
					m.set(b.origin.getX() + dx, b.origin.getY() + dy, b.origin.getZ() + dz);
					var state = level.getBlockState(m);
					if (state.is(Blocks.WATER) && state.getValue(LiquidBlock.LEVEL) == 0 && level.getBlockState(m.above()).isAir()) {
						level.setBlockAndUpdate(m, Blocks.FROSTED_ICE.defaultBlockState());
						level.scheduleTick(m, Blocks.FROSTED_ICE, Mth.nextInt(level.getRandom(), 100, 160));
					}
				}
			}
		}
	}

	static void cleanupAll() {
		BLASTS.clear();
	}
}
