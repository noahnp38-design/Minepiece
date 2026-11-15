package minepiece.noah.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
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
			case 0 -> pheasantBeak(player, level);
			case 1 -> iceTime(player, level);
			case 2 -> iceAge(player, level);
			default -> false;
		};
	}

	/** Attaque 1 : projectile de glace perçant, en ligne droite devant le joueur. */
	private static boolean pheasantBeak(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 dir = player.getLookAngle();
		Set<LivingEntity> hit = new HashSet<>();

		for (double d = 1.0; d <= 24.0; d += 0.5) {
			Vec3 pos = eye.add(dir.scale(d));
			BlockPos blockPos = BlockPos.containing(pos);
			if (!level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty()) {
				break; // le rayon s'arrête sur un mur
			}
			level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 3, 0.1, 0.1, 0.1, 0.01);

			AABB box = new AABB(pos, pos).inflate(1.0);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
				if (hit.add(target)) {
					target.hurtServer(level, level.damageSources().playerAttack(player), 8.0F);
					freeze(target, 100, 2, 100);
				}
			}
		}
		player.playSound(SoundEvents.PLAYER_HURT_FREEZE, 1.0F, 1.2F);
		return true;
	}

	/** Attaque 2 : onde de froid autour du joueur, ralentit et gèle les ennemis proches. */
	private static boolean iceTime(ServerPlayer player, ServerLevel level) {
		double radius = 6.0;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(radius), e -> e != player && e.isAlive())) {
			if (target.distanceToSqr(player) <= radius * radius) {
				target.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
				freeze(target, 200, 4, 120);
			}
		}
		ring(level, player, radius);
		level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(),
				80, 3.0, 1.0, 3.0, 0.05);
		player.playSound(SoundEvents.PLAYER_HURT_FREEZE, 1.5F, 0.7F);
		return true;
	}

	// ------------------------------------------------------------------ attaque 3 : Ice Age (maintenue)

	/** Charge minimale (0,5 s) et maximale (5 s, la vague part alors toute seule), en ticks. */
	private static final int MIN_CHARGE = 10;
	private static final int MAX_CHARGE = 100;
	/** Rayon de la vague de glace selon la charge. */
	private static final int MIN_RADIUS = 5;
	private static final int MAX_RADIUS = 16;
	/** Mets false pour que l'Ice Age ne gèle que l'eau (et plus les blocs solides). */
	private static final boolean FREEZE_SOLID_BLOCKS = true;
	/** Les blocs plus durs que ça (obsidienne...) ne sont jamais gelés ; la bedrock (-1) non plus. */
	private static final float MAX_BLOCK_HARDNESS = 10.0F;

	/** Début de charge (game time) des joueurs qui maintiennent la touche. */
	private static final Map<UUID, Long> CHARGING = new HashMap<>();
	/** Vagues de glace en train de se propager. */
	private static final List<Wave> WAVES = new ArrayList<>();

	private static final class Wave {
		final ServerLevel level;
		final UUID owner;
		final BlockPos origin;
		final int maxRadius;
		int radius = 0;

		Wave(ServerLevel level, UUID owner, BlockPos origin, int maxRadius) {
			this.level = level;
			this.owner = owner;
			this.origin = origin;
			this.maxRadius = maxRadius;
		}
	}

	/** Touche pressée : commence à charger. Le temps de recharge ne démarre qu'à la fin de la vague. */
	private static boolean iceAge(ServerPlayer player, ServerLevel level) {
		if (CHARGING.containsKey(player.getUUID())) {
			return false;
		}
		CHARGING.put(player.getUUID(), level.getGameTime());
		player.playSound(SoundEvents.PLAYER_HURT_FREEZE, 1.0F, 0.6F);
		return true;
	}

	/** Touche relâchée (appelée depuis FruitAbilities.release). */
	static void release(ServerPlayer player, int slot) {
		if (slot != 2) {
			return;
		}
		Long start = CHARGING.remove(player.getUUID());
		if (start == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		launchIceAge(player, level, (int) Math.min(MAX_CHARGE, level.getGameTime() - start));
	}

	/** Appelé une fois par tick : fait avancer les charges et les vagues. */
	static void tick(MinecraftServer server) {
		// Charges en cours
		Iterator<Map.Entry<UUID, Long>> it = CHARGING.entrySet().iterator();
		List<ServerPlayer> toFire = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.isSpectator()
					|| player.getAttached(minepiece.noah.data.ModAttachments.FRUIT) != minepiece.noah.data.DevilFruit.HIE_HIE_NO_MI
					|| !(player.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			int held = (int) (level.getGameTime() - entry.getValue());
			double fraction = Math.min(1.0, held / (double) MAX_CHARGE);
			// Le cercle de flocons grandit avec la charge
			if (held % 2 == 0) {
				ring(level, player, 1.0 + fraction * 3.0);
			}
			if (held % 20 == 0 && held > 0) {
				player.sendSystemMessage(Component.literal("Ice Age : " + (int) Math.round(fraction * 100) + " %"), true);
				player.playSound(SoundEvents.PLAYER_HURT_FREEZE, 0.6F, 0.6F + (float) fraction);
			}
			if (held >= MAX_CHARGE) {
				toFire.add(player);
				it.remove();
			}
		}
		for (ServerPlayer player : toFire) {
			if (player.level() instanceof ServerLevel level) {
				launchIceAge(player, level, MAX_CHARGE);
			}
		}

		// Vagues en cours : une couche de blocs par tick
		Iterator<Wave> waves = WAVES.iterator();
		while (waves.hasNext()) {
			Wave wave = waves.next();
			wave.radius++;
			freezeShell(wave);
			if (wave.radius >= wave.maxRadius) {
				waves.remove();
			}
		}
	}

	/** Oublie les données d'un joueur qui se déconnecte. */
	static void forget(UUID player) {
		CHARGING.remove(player);
	}

	private static void launchIceAge(ServerPlayer player, ServerLevel level, int heldTicks) {
		if (heldTicks < MIN_CHARGE) {
			// Trop court : annulé, petite recharge pour éviter le spam.
			FruitAbilities.startCooldown(player, 2, 30);
			player.sendSystemMessage(Component.literal("Ice Age : charge trop courte"), true);
			return;
		}
		double fraction = Math.min(1.0, heldTicks / (double) MAX_CHARGE);
		int radius = MIN_RADIUS + (int) Math.round((MAX_RADIUS - MIN_RADIUS) * fraction);

		// Dégâts et gel des ennemis, proportionnels à la charge
		float damage = (float) (6.0 + 8.0 * fraction);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(radius), e -> e != player && e.isAlive())) {
			if (target.distanceToSqr(player) <= (double) radius * radius) {
				target.hurtServer(level, level.damageSources().playerAttack(player), damage);
				freeze(target, 300, 5, 160);
			}
		}

		WAVES.add(new Wave(level, player.getUUID(), player.blockPosition(), radius));
		ring(level, player, radius);
		ring(level, player, radius * 0.6);
		player.playSound(SoundEvents.PLAYER_HURT_FREEZE, 2.0F, 0.5F);

		// La recharge (30 s, voir DevilFruit) démarre maintenant, au lancement de la vague
		FruitAbilities.startCooldown(player, 2, minepiece.noah.data.DevilFruit.HIE_HIE_NO_MI.cooldownTicks(2));
	}

	/** Gèle la couche de blocs à la distance wave.radius du centre (vague qui s'étend). */
	private static void freezeShell(Wave wave) {
		ServerLevel level = wave.level;
		int r = wave.radius;
		double inner = (r - 1.0) * (r - 1.0);
		double outer = (double) r * r;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(wave.owner);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					double d2 = dx * dx + dy * dy + dz * dz;
					if (d2 <= inner || d2 > outer) {
						continue;
					}
					pos.set(wave.origin.getX() + dx, wave.origin.getY() + dy, wave.origin.getZ() + dz);
					if (!level.isInWorldBounds(pos)) {
						continue;
					}
					// Ne jamais emmurer le lanceur dans la glace
					if (owner != null && owner.getBoundingBox().inflate(0.2).intersects(new AABB(pos))) {
						continue;
					}
					freezeBlock(level, pos);
				}
			}
		}
		level.playSound(null, wave.origin, SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.5F);
	}

	private static void freezeBlock(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		BlockState ice;
		if (state.is(Blocks.WATER)) {
			ice = Blocks.ICE.defaultBlockState();
		} else if (FREEZE_SOLID_BLOCKS && !state.getCollisionShape(level, pos).isEmpty()) {
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F || hardness > MAX_BLOCK_HARDNESS || level.getBlockEntity(pos) != null
					|| state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
				return;
			}
			ice = Blocks.PACKED_ICE.defaultBlockState();
		} else {
			return;
		}
		// UPDATE_CLIENTS seul : pas de mises à jour de voisinage (évite les lags sur des milliers de blocs)
		level.setBlock(pos, ice, Block.UPDATE_CLIENTS);
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
