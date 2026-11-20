package minepiece.noah.ability.bomu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.ability.DemonCurse;
import minepiece.noah.ability.mera.MeraConfig;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pouvoirs du Bomu Bomu no Mi (fruit de l'Explosion, Paramécia, Mr. 5). Tout est décidé par le serveur.
 *  1. Bomu Shot     : bombe tirée dans la direction du regard, explose au contact (bloc ou ennemi).
 *  2. Bomu Bash     : explosion autour du joueur (son corps sert de détonateur).
 *  3. Bomu Graviton : une pluie de bombes tombe sur la zone visée et explose à l'impact.
 * Passif "Corps explosif" : le joueur est immunisé contre ses propres explosions, ses coups au corps-à-corps provoquent de
 * petites explosions. Les recharges (voir DevilFruit) sont gérées par FruitAbilities et synchronisées au client.
 * Blocs : les explosions détruisent les blocs comme celles du Mera (réglage MeraConfig.destroyBlocks).
 */
public final class BomuAbilities {
	// ---- Bomu Shot
	private static final double SHOT_SPEED = 1.6;          // blocs par tick
	private static final double SHOT_HITBOX = 0.7;
	private static final int SHOT_LIFE = 40;               // ~64 blocs
	private static final double SHOT_RADIUS = 3.2;
	private static final float SHOT_DAMAGE = 10.0F;
	// ---- Bomu Bash
	private static final double BASH_RADIUS = 4.5;
	private static final float BASH_DAMAGE = 12.0F;
	// ---- Bomu Graviton
	private static final double GRAVITON_RANGE = 40.0;
	private static final int GRAVITON_BOMBS = 8;
	private static final int GRAVITON_INTERVAL = 5;        // ticks entre deux bombes (~2 s au total)
	private static final double GRAVITON_AREA = 5.0;
	private static final double GRAVITON_HEIGHT = 16.0;
	private static final double BOMB_RADIUS = 3.0;
	private static final float BOMB_DAMAGE = 8.0F;
	// ---- Corps explosif
	private static final double PUNCH_RADIUS = 1.8;
	private static final float PUNCH_DAMAGE = 3.0F;
	private static final int PUNCH_COOLDOWN = 8;

	private static final DustParticleOptions ORANGE = new DustParticleOptions(0xFF8A1C, 1.8F);
	private static final DustParticleOptions YELLOW = new DustParticleOptions(0xFFD83A, 1.6F);
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFF8E0, 1.4F);

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static final List<Bomb> BOMBS = new ArrayList<>();
	private static final List<Rain> RAINS = new ArrayList<>();
	private static final List<Punch> PUNCHES = new ArrayList<>();
	private static final Map<UUID, Long> LAST_PUNCH = new HashMap<>();
	private static final int MAX_ACTIVE = 160;

	private BomuAbilities() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(BomuAbilities::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BomuVisual.cleanupAll();
			SHOTS.clear();
			BOMBS.clear();
			RAINS.clear();
			PUNCHES.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.getPlayer()));

		// Corps explosif : immunité à ses propres explosions + coups qui explosent.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer victim && isBomu(victim) && source.is(DamageTypeTags.IS_EXPLOSION)
					&& source.getEntity() == victim) {
				return false;
			}
			if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
					&& !source.is(DamageTypeTags.IS_EXPLOSION) && entity != attacker && isBomu(attacker)
					&& attacker.level() instanceof ServerLevel level) {
				long now = level.getGameTime();
				Long last = LAST_PUNCH.get(attacker.getUUID());
				if ((last == null || now - last >= PUNCH_COOLDOWN) && PUNCHES.size() < 40) {
					LAST_PUNCH.put(attacker.getUUID(), now);
					PUNCHES.add(new Punch(level, attacker.getUUID(), entity.position().add(0, entity.getBbHeight() * 0.5, 0)));
				}
			}
			return true;
		});
	}

	private static boolean isBomu(ServerPlayer player) {
		return player.getAttached(ModAttachments.FRUIT) == DevilFruit.BOMU_BOMU_NO_MI && !DemonCurse.isSuppressed(player);
	}

	// ------------------------------------------------------------------ lancement

	/** @return true si l'attaque démarre (la recharge est alors lancée par FruitAbilities). */
	public static boolean cast(ServerPlayer player, ServerLevel level, int slot) {
		return switch (slot) {
			case 0 -> shot(player, level);
			case 1 -> bash(player, level);
			case 2 -> graviton(player, level);
			default -> false;
		};
	}

	/** Annule les bombes en cours d'un joueur (déconnexion, mort, Malédiction du Démon...). */
	public static void cancel(ServerPlayer player) {
		UUID id = player.getUUID();
		for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext();) {
			Shot s = it.next();
			if (s.owner.equals(id)) {
				s.visual.remove();
				it.remove();
			}
		}
		for (Iterator<Bomb> it = BOMBS.iterator(); it.hasNext();) {
			Bomb b = it.next();
			if (b.owner.equals(id)) {
				b.visual.remove();
				it.remove();
			}
		}
		RAINS.removeIf(r -> r.owner.equals(id));
		LAST_PUNCH.remove(id);
	}

	private static boolean shot(ServerPlayer player, ServerLevel level) {
		if (SHOTS.size() + BOMBS.size() >= MAX_ACTIVE) {
			return false;
		}
		Vec3 dir = player.getLookAngle();
		Vec3 start = player.getEyePosition().add(dir.scale(1.0));
		SHOTS.add(new Shot(level, player.getUUID(), start, dir.scale(SHOT_SPEED)));
		level.playSound(null, start.x, start.y, start.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.sendParticles(ParticleTypes.FLAME, start.x, start.y, start.z, 10, 0.2, 0.2, 0.2, 0.05);
		return true;
	}

	private static boolean bash(ServerPlayer player, ServerLevel level) {
		Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
		explode(level, player.getUUID(), c, BASH_RADIUS, BASH_DAMAGE, true);
		return true;
	}

	private static boolean graviton(ServerPlayer player, ServerLevel level) {
		if (RAINS.size() >= 8) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 dir = player.getLookAngle();
		Vec3 target = eye.add(dir.scale(GRAVITON_RANGE));
		for (double d = 1.0; d <= GRAVITON_RANGE; d += 0.5) {
			Vec3 p = eye.add(dir.scale(d));
			BlockPos bp = BlockPos.containing(p);
			if (!level.isLoaded(bp)) {
				target = p;
				break;
			}
			boolean solid = !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
			boolean entity = !level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(0.8),
					e -> e.isAlive() && e != player && !e.isSpectator()).isEmpty();
			if (solid || entity) {
				target = p;
				break;
			}
		}
		// On vise le sol sous le point : les bombes tombent sur la zone.
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Math.floor(target.x), Math.floor(target.y), Math.floor(target.z));
		int guard = 0;
		while (guard++ < 48 && level.isLoaded(m) && level.getBlockState(m).getCollisionShape(level, m).isEmpty() && m.getY() > level.getMinY()) {
			m.move(0, -1, 0);
		}
		Vec3 ground = new Vec3(target.x, m.getY() + 1.0, target.z);
		RAINS.add(new Rain(level, player.getUUID(), ground));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.sendParticles(ParticleTypes.FLAME, ground.x, ground.y + 0.2, ground.z, 30, GRAVITON_AREA * 0.3, 0.1, GRAVITON_AREA * 0.3, 0.02);
		return true;
	}

	// ------------------------------------------------------------------ tick serveur

	private static void tick(MinecraftServer server) {
		for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext();) {
			if (!it.next().tick()) {
				it.remove();
			}
		}
		for (Iterator<Bomb> it = BOMBS.iterator(); it.hasNext();) {
			if (!it.next().tick()) {
				it.remove();
			}
		}
		for (Iterator<Rain> it = RAINS.iterator(); it.hasNext();) {
			if (!it.next().tick()) {
				it.remove();
			}
		}
		for (Iterator<Punch> it = PUNCHES.iterator(); it.hasNext();) {
			it.next().run();
			it.remove();
		}
	}

	// ------------------------------------------------------------------ explosion

	/** Explosion : effets, dégâts de zone (jamais sur le lanceur), recul, destruction de blocs. */
	private static void explode(ServerLevel level, UUID owner, Vec3 c, double radius, float damage, boolean big) {
		RandomSource rnd = level.getRandom();
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);

		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
				(float) Math.min(5.0, 1.5 + radius * 0.5), (float) Math.max(0.6, 1.2 - radius * 0.1));
		blast(level, c, radius, big);

		AABB box = new AABB(c, c).inflate(radius * 1.2);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner))) {
			Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
			double dist = center.distanceTo(c);
			if (dist > radius * 1.2) {
				continue;
			}
			double falloff = Math.max(0.3, 1.0 - dist / (radius * 1.2));
			DamageSource source = level.damageSources().explosion(null, player);
			target.hurtServer(level, source, (float) (damage * falloff));
			Vec3 away = center.subtract(c);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : away.normalize();
			double force = Math.min(2.2, 0.4 + falloff * 1.4);
			target.push(away.x * force, 0.25 + 0.35 * falloff, away.z * force);
			target.needsSync = true;
		}
		if (MeraConfig.get().destroyBlocks) {
			destroy(level, c, Math.min(radius, 4.0) * 0.8);
		}
	}

	/** Effets visuels : flash blanc-jaune, boule orange, onde de choc, éclats et braises qui retombent. */
	private static void blast(ServerLevel level, Vec3 c, double radius, boolean big) {
		RandomSource rnd = level.getRandom();
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		if (big) {
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 14, radius * 0.1, radius * 0.1, radius * 0.1, 0.3);
		level.sendParticles(WHITE, c.x, c.y, c.z, 8, radius * 0.12, radius * 0.12, radius * 0.12, 0.0);
		double speed = 0.25 + radius * 0.07;
		for (int i = 0; i < 26; i++) {
			double a = Math.PI * 2.0 * i / 26.0;
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.1, c.z, 0, Math.cos(a), 0.05, Math.sin(a), speed);
		}
		for (int i = 0; i < 30; i++) {
			double x = rnd.nextDouble() * 2.0 - 1.0;
			double y = rnd.nextDouble() * 2.0 - 1.0;
			double z = rnd.nextDouble() * 2.0 - 1.0;
			double len = Math.sqrt(x * x + y * y + z * z);
			if (len < 1.0E-3) {
				continue;
			}
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 0, x / len, y / len, z / len, speed * (0.4 + rnd.nextDouble() * 0.8));
		}
		level.sendParticles(ORANGE, c.x, c.y, c.z, 14, radius * 0.3, radius * 0.25, radius * 0.3, 0.0);
		level.sendParticles(YELLOW, c.x, c.y, c.z, 10, radius * 0.2, radius * 0.2, radius * 0.2, 0.0);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 10, radius * 0.3, radius * 0.2, radius * 0.3, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.3, c.z, 8, radius * 0.25, radius * 0.2, radius * 0.25, 0.03);
	}

	private static void destroy(ServerLevel level, Vec3 c, double radius) {
		MeraConfig cfg = MeraConfig.get();
		BlockPos center = BlockPos.containing(c);
		int r = (int) Math.ceil(radius);
		double r2 = radius * radius;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dy * dy + dz * dz > r2) {
						continue;
					}
					BlockPos pos = center.offset(dx, dy, dz);
					if (!level.isLoaded(pos)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (state.isAir() || !state.getFluidState().isEmpty()) {
						continue;
					}
					float hardness = state.getDestroySpeed(level, pos);
					if (hardness < 0.0F || hardness > cfg.maxBlockHardness || (!cfg.destroyBlockEntities && state.hasBlockEntity())) {
						continue;
					}
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		}
	}

	// ------------------------------------------------------------------ projectiles

	/** Bombe du Bomu Shot : file droit et explose au premier bloc ou ennemi touché. */
	private static final class Shot {
		final ServerLevel level;
		final UUID owner;
		final Vec3 velocity;
		final BomuVisual visual;
		Vec3 pos;
		int life = SHOT_LIFE;

		Shot(ServerLevel level, UUID owner, Vec3 pos, Vec3 velocity) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.velocity = velocity;
			this.visual = BomuVisual.create(level, pos, 1.4);
		}

		boolean tick() {
			if (life-- <= 0) {
				return detonate(pos);
			}
			int steps = Math.max(1, (int) Math.ceil(velocity.length() / 0.5));
			Vec3 step = velocity.scale(1.0 / steps);
			for (int i = 0; i < steps; i++) {
				Vec3 next = pos.add(step);
				BlockPos bp = BlockPos.containing(next);
				if (!level.isLoaded(bp)) {
					visual.remove();
					return false;
				}
				boolean blocked = !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
				boolean hitEntity = !blocked && !level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(SHOT_HITBOX),
						e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner)).isEmpty();
				if (blocked || hitEntity) {
					return detonate(blocked ? pos : next);
				}
				pos = next;
			}
			boolean model = visual.move(pos);
			RandomSource rnd = level.getRandom();
			Vec3 back = velocity.normalize().scale(-1.0);
			level.sendParticles(ParticleTypes.FLAME, pos.x + back.x * 0.6, pos.y + back.y * 0.6, pos.z + back.z * 0.6, 0, back.x, back.y, back.z, 0.18);
			level.sendParticles(ParticleTypes.SMALL_FLAME, pos.x, pos.y, pos.z, 2, 0.2, 0.2, 0.2, 0.02);
			if (!model || life % 2 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 1, 0.1, 0.1, 0.1, 0.01);
			}
			if (life % 3 == 0) {
				level.sendParticles(rnd.nextBoolean() ? ORANGE : YELLOW, pos.x, pos.y, pos.z, 1, 0.25, 0.25, 0.25, 0.0);
			}
			return true;
		}

		private boolean detonate(Vec3 at) {
			visual.remove();
			explode(level, owner, at, SHOT_RADIUS, SHOT_DAMAGE, false);
			return false;
		}
	}

	/** Bombe du Bomu Graviton : tombe de haut et explose à l'impact. */
	private static final class Bomb {
		final ServerLevel level;
		final UUID owner;
		final BomuVisual visual;
		Vec3 pos;
		double vy = -0.5;
		int life = 120;

		Bomb(ServerLevel level, UUID owner, Vec3 pos) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.visual = BomuVisual.create(level, pos, 1.7);
		}

		boolean tick() {
			if (life-- <= 0) {
				visual.remove();
				return false;
			}
			vy = Math.max(vy - 0.07, -2.2);
			int steps = Math.max(1, (int) Math.ceil(Math.abs(vy) / 0.5));
			double stepY = vy / steps;
			for (int i = 0; i < steps; i++) {
				Vec3 next = pos.add(0, stepY, 0);
				BlockPos bp = BlockPos.containing(next);
				if (!level.isLoaded(bp)) {
					visual.remove();
					return false;
				}
				boolean blocked = !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
				boolean hitEntity = !blocked && !level.getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(0.8),
						e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner)).isEmpty();
				if (blocked || hitEntity) {
					visual.remove();
					explode(level, owner, blocked ? pos : next, BOMB_RADIUS, BOMB_DAMAGE, false);
					return false;
				}
				pos = next;
			}
			visual.move(pos);
			level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.8, pos.z, 0, 0.0, 1.0, 0.0, 0.2);
			if (life % 2 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.5, pos.z, 1, 0.1, 0.1, 0.1, 0.0);
			}
			return true;
		}
	}

	/** Pluie de bombes du Bomu Graviton : une bombe tous les GRAVITON_INTERVAL ticks, au-dessus de la zone. */
	private static final class Rain {
		final ServerLevel level;
		final UUID owner;
		final Vec3 center;
		int spawned = 0;
		int age = 0;

		Rain(ServerLevel level, UUID owner, Vec3 center) {
			this.level = level;
			this.owner = owner;
			this.center = center;
		}

		boolean tick() {
			// Cercle d'avertissement au sol : de petites flammes montrent la zone visée.
			if (age % 4 == 0) {
				for (int i = 0; i < 10; i++) {
					double a = Math.PI * 2.0 * i / 10.0 + age * 0.15;
					level.sendParticles(ParticleTypes.FLAME, center.x + Math.cos(a) * GRAVITON_AREA, center.y + 0.15,
							center.z + Math.sin(a) * GRAVITON_AREA, 0, 0.0, 1.0, 0.0, 0.05);
				}
			}
			if (age % GRAVITON_INTERVAL == 0 && spawned < GRAVITON_BOMBS && BOMBS.size() < MAX_ACTIVE) {
				RandomSource rnd = level.getRandom();
				double a = rnd.nextDouble() * Math.PI * 2.0;
				double rr = Math.sqrt(rnd.nextDouble()) * GRAVITON_AREA;
				Vec3 start = new Vec3(center.x + Math.cos(a) * rr, center.y + GRAVITON_HEIGHT + rnd.nextDouble() * 3.0, center.z + Math.sin(a) * rr);
				BOMBS.add(new Bomb(level, owner, start));
				level.playSound(null, start.x, start.y, start.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.6F);
				spawned++;
			}
			age++;
			return spawned < GRAVITON_BOMBS;
		}
	}

	/** Petite explosion déclenchée par un coup au corps-à-corps (Corps explosif). */
	private static final class Punch {
		final ServerLevel level;
		final UUID owner;
		final Vec3 at;

		Punch(ServerLevel level, UUID owner, Vec3 at) {
			this.level = level;
			this.owner = owner;
			this.at = at;
		}

		void run() {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.5F);
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.08);
			level.sendParticles(ORANGE, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.0);
			level.sendParticles(YELLOW, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.0);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(PUNCH_RADIUS),
					e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner))) {
				target.hurtServer(level, level.damageSources().explosion(null, player), PUNCH_DAMAGE);
			}
		}
	}
}
