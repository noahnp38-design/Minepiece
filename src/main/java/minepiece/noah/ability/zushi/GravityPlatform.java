package minepiece.noah.ability.zushi;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.ability.FruitAbilities;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Attaque 1 : Plateforme gravitationnelle.
 *
 * Un morceau de terrain (modèle 3D fx/zushi_platform_grass|stone|sand selon le sol) est arraché sous les pieds du
 * lanceur, émerge du sol puis s'élève en lévitant. Le joueur est posé DESSUS (il "monte" sur le modèle, ce qui
 * garantit un mouvement parfaitement synchronisé entre la roche et le joueur chez tous les clients) et la dirige :
 * - avancer / reculer / pas de côté : déplacement (sprint = plus vite), dans la direction du regard ;
 * - regarder vers le haut / le bas en avançant : monter / descendre ; saut : monter ;
 * - accroupi (Maj) : saute de la plateforme (chute ralentie) ;
 * - attaque 1 à nouveau : atterrissage en douceur.
 * La plateforme oscille légèrement, un anneau de fragments tourne autour, des particules violettes tombent dessous.
 * À la fin elle redescend, se pose et s'effrite (modèles supprimés). La recharge démarre à la fin.
 */
final class GravityPlatform {
	private static final double SCALE = 1.0;
	/** Dessus de la dalle dans le modèle (unités) : voir tools/zushi_models.py (y = 29). */
	private static final double TOP = (29.0 / 16.0 - 0.5) * SCALE;
	/** Le joueur monté a les pieds 0,6 bloc sous la position du véhicule (attache vanilla des joueurs). */
	private static final double RIDER = 0.6;
	/** Décalage du modèle : son dessus arrive exactement sous les pieds du joueur. */
	private static final double MODEL_TY = -RIDER - TOP;
	private static final double RING_TY = MODEL_TY + (15.0 / 16.0 - 0.5) * SCALE;
	private static final double RING_SCALE = 1.45;
	private static final int EMERGE_TICKS = 12;
	private static final int RISE_TICKS = 26;
	private static final double RISE_HEIGHT = 3.2;
	private static final int LAND_MAX_TICKS = 100;
	private static final int CRUMBLE_TICKS = 12;

	private enum Phase { RISE, FLY, LAND, CRUMBLE }

	private static final Map<UUID, Platform> ACTIVE = new HashMap<>();

	private GravityPlatform() {}

	private static final class Platform {
		final ServerLevel level;
		final UUID owner;
		final ZushiDisplay rock;
		final ZushiDisplay ring;
		final float yaw;
		Vec3 pos;           // position du "véhicule" (pieds du joueur + 0,6)
		Vec3 vel = Vec3.ZERO;
		Phase phase = Phase.RISE;
		int age;
		int phaseAge;
		double riseFrom;
		double ringAngle;
		boolean droppedOff;

		Platform(ServerLevel level, ServerPlayer player, String variant) {
			this.level = level;
			this.owner = player.getUUID();
			this.yaw = level.getRandom().nextFloat() * 360.0F;
			this.pos = player.position().add(0, RIDER, 0);
			this.riseFrom = pos.y;
			// le modèle part caché sous terre puis émerge sous les pieds
			this.rock = ZushiDisplay.create(level, "zushi_platform_" + variant, pos, yaw, 0.0F, SCALE, MODEL_TY - 2.8, 2, 1.5F, false);
			this.ring = ZushiDisplay.create(level, "zushi_platform_debris", pos, 0.0F, 0.0F, 0.01, RING_TY, 2, 1.0F, false);
		}

		AABB rockBox(Vec3 p) {
			double top = p.y - RIDER;
			return new AABB(p.x - 1.25, top - 1.3, p.z - 1.25, p.x + 1.25, top - 0.05, p.z + 1.25);
		}

		AABB riderBox(Vec3 p) {
			double top = p.y - RIDER;
			return new AABB(p.x - 0.3, top + 0.05, p.z - 0.3, p.x + 0.3, top + 1.8, p.z + 0.3);
		}
	}

	static boolean active(UUID player) {
		return ACTIVE.containsKey(player);
	}

	/** Touche de l'attaque 1 : lance la plateforme, ou la fait atterrir si elle est déjà là. */
	static boolean cast(ServerPlayer player, ServerLevel level) {
		Platform existing = ACTIVE.get(player.getUUID());
		if (existing != null) {
			if (existing.phase == Phase.FLY || existing.phase == Phase.RISE) {
				existing.phase = Phase.LAND;
				existing.phaseAge = 0;
				player.sendSystemMessage(Component.literal("Plateforme gravitationnelle : atterrissage"), true);
			}
			return false;
		}
		if (player.isPassenger()) {
			player.sendSystemMessage(Component.literal("Impossible : tu es déjà sur une monture."), true);
			return false;
		}
		Platform p = new Platform(level, player, variant(level, player.blockPosition().below()));
		Entity vehicle = p.rock.entity();
		if (vehicle == null || !player.startRiding(vehicle, true, true)) {
			p.rock.remove();
			p.ring.remove();
			return false;
		}
		ACTIVE.put(player.getUUID(), p);
		player.setAttached(ModAttachments.GRAVITY_RIDE, Boolean.TRUE);
		p.rock.scaleTo(SCALE, MODEL_TY, EMERGE_TICKS);           // émerge du sol
		p.ring.setTransform(0, 1, 0, 0, RING_SCALE, RING_TY, EMERGE_TICKS + 6);
		BlockPos below = player.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		ZushiFx.crumble(level, below, ground, 30);
		ZushiFx.send(level, ModParticles.GRAVITY_DUST, p.pos.x, p.pos.y - RIDER, p.pos.z, 14, 1.4, 0.2, 1.4, 0.04);
		ZushiFx.circle(level, ModParticles.GRAVITY_SPARK, p.pos.add(0, -RIDER + 0.1, 0), 1.8, 18, 0.05, 0.08, false);
		level.playSound(null, player.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.4F, 0.5F);
		level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	/** Sol sous le joueur : dessus herbe, pierre ou sable. */
	private static String variant(ServerLevel level, BlockPos pos) {
		for (int i = 0; i < 4; i++) {
			BlockState s = level.getBlockState(pos.below(i));
			if (s.isAir()) {
				continue;
			}
			if (s.is(BlockTags.SAND) || s.is(net.minecraft.world.level.block.Blocks.SANDSTONE)) {
				return "sand";
			}
			if (s.is(BlockTags.DIRT) || s.is(BlockTags.LEAVES)) {
				return "grass";
			}
			return "stone";
		}
		return "stone";
	}

	static void tick(net.minecraft.server.MinecraftServer server) {
		for (Iterator<Platform> it = ACTIVE.values().iterator(); it.hasNext();) {
			Platform p = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(p.owner);
			if (!tick(p, player)) {
				finish(p, player);
				it.remove();
			}
		}
	}

	/** @return false quand la plateforme a disparu */
	private static boolean tick(Platform p, ServerPlayer player) {
		p.age++;
		p.phaseAge++;
		ZushiConfig cfg = ZushiConfig.get();
		Entity vehicle = p.rock.entity();
		boolean riding = player != null && vehicle != null && player.getVehicle() == vehicle && player.level() == p.level;
		if (p.phase != Phase.CRUMBLE && (!riding || !player.isAlive())) {
			// le joueur a sauté (Maj), est mort, s'est téléporté ou s'est déconnecté
			p.droppedOff = true;
			startCrumble(p, player);
		}
		if (vehicle == null && p.phase != Phase.CRUMBLE) {
			return false;
		}

		switch (p.phase) {
			case RISE -> {
				if (p.phaseAge > EMERGE_TICKS) {
					double t = Math.min(1.0, (p.phaseAge - EMERGE_TICKS) / (double) RISE_TICKS);
					double ease = 1.0 - (1.0 - t) * (1.0 - t);
					Vec3 next = new Vec3(p.pos.x, p.riseFrom + RISE_HEIGHT * ease, p.pos.z);
					if (p.level.noCollision(p.riderBox(next))) {
						p.pos = next;
					}
					if (t >= 1.0) {
						p.phase = Phase.FLY;
						p.phaseAge = 0;
					}
				}
			}
			case FLY -> {
				if (p.phaseAge >= cfg.platformDurationTicks) {
					p.phase = Phase.LAND;
					p.phaseAge = 0;
					if (player != null) {
						player.sendSystemMessage(Component.literal("Plateforme gravitationnelle : la roche redescend"), true);
					}
				} else {
					fly(p, player, cfg);
				}
			}
			case LAND -> {
				Vec3 next = p.pos.add(0, -0.3, 0);
				// se pose quand le haut de la roche arrive près du sol (le dessous s'enfonce dans la terre)
				boolean blocked = !p.level.noCollision(p.rockBox(next).setMinY(next.y - RIDER - 0.9));
				if (blocked || p.phaseAge > LAND_MAX_TICKS) {
					startCrumble(p, player);
				} else {
					p.pos = next;
				}
			}
			case CRUMBLE -> {
				if (p.phaseAge >= CRUMBLE_TICKS) {
					return false;
				}
				if (p.phaseAge % 3 == 0) {
					ZushiFx.send(p.level, ModParticles.ROCK_CHIP, p.pos.x, p.pos.y - RIDER - 0.8, p.pos.z, 10, 1.0, 0.5, 1.0, 0.1);
					ZushiFx.send(p.level, ModParticles.GRAVITY_DUST, p.pos.x, p.pos.y - RIDER - 0.6, p.pos.z, 4, 1.0, 0.4, 1.0, 0.02);
				}
				return true;
			}
		}

		// légère oscillation de lévitation (le joueur oscille avec la roche)
		double bob = p.phase == Phase.FLY ? Math.sin(p.age * 0.14) * 0.06 : 0.0;
		p.rock.move(p.pos.add(0, bob, 0), p.yaw, 0.0F);
		p.ring.move(p.pos.add(0, bob, 0), 0.0F, 0.0F);
		// rotation lente de l'anneau de débris : un quart de tour en 4 s, interpolé côté client
		if (p.age % 10 == 0) {
			p.ringAngle += 22.0;
			p.ring.setTransform(0, 1, 0, p.ringAngle, RING_SCALE, RING_TY + Math.sin(p.age * 0.05) * 0.15, 10);
		}
		effects(p);
		if (player != null) {
			player.resetFallDistance();
		}
		return true;
	}

	private static void fly(Platform p, ServerPlayer player, ZushiConfig cfg) {
		Input in = player.getLastClientInput();
		double fwd = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
		double str = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
		double yawRad = Math.toRadians(player.getYRot());
		double sin = Math.sin(yawRad), cos = Math.cos(yawRad);
		double dx = str * cos - fwd * sin;
		double dz = fwd * cos + str * sin;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len > 1.0) {
			dx /= len;
			dz /= len;
		}
		double speed = in.sprint() ? cfg.platformSprintSpeed : cfg.platformSpeed;
		double vy = in.jump() ? cfg.platformVerticalSpeed : 0.0;
		if (fwd > 0) {
			vy += -Math.sin(Math.toRadians(player.getXRot())) * speed * 0.8;
		}
		vy = Math.max(-cfg.platformVerticalSpeed * 1.5, Math.min(cfg.platformVerticalSpeed * 1.5, vy));
		// plafond : hauteur max au-dessus du sol
		int ground = p.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.pos.x), (int) Math.floor(p.pos.z));
		if (p.pos.y - RIDER > ground + cfg.platformMaxHeight || p.pos.y + 2 > p.level.getMaxY()) {
			vy = Math.min(vy, 0.0);
		}
		Vec3 target = new Vec3(dx * speed, vy, dz * speed);
		p.vel = p.vel.add(target.subtract(p.vel).scale(0.18));

		// collisions, axe par axe (la roche et le joueur ne traversent pas les blocs)
		Vec3 pos = p.pos;
		Vec3 v = p.vel;
		Vec3 tryX = pos.add(v.x, 0, 0);
		if (free(p, tryX)) pos = tryX; else v = new Vec3(0, v.y, v.z);
		Vec3 tryY = pos.add(0, v.y, 0);
		if (free(p, tryY)) pos = tryY; else v = new Vec3(v.x, 0, v.z);
		Vec3 tryZ = pos.add(0, 0, v.z);
		if (free(p, tryZ)) pos = tryZ; else v = new Vec3(v.x, v.y, 0);
		p.pos = pos;
		p.vel = v;
	}

	private static boolean free(Platform p, Vec3 pos) {
		if (!p.level.noCollision(p.riderBox(pos))) {
			return false;
		}
		// si la roche est déjà coincée dans un bloc (montée bloquée par un plafond), on ne la bloque pas pour autant
		return !p.level.noCollision(p.rockBox(p.pos)) || p.level.noCollision(p.rockBox(pos));
	}

	/** Particules violettes sous la plateforme, poussière près du sol. */
	private static void effects(Platform p) {
		ServerLevel level = p.level;
		RandomSource r = level.getRandom();
		double bottom = p.pos.y - RIDER - 2.3;
		ZushiFx.send(level, ModParticles.GRAVITY_SPARK, p.pos.x, bottom + 0.6, p.pos.z, 2, 0.7, 0.35, 0.7, 0.0);
		if (p.age % 2 == 0) {
			Vec3 c = new Vec3(p.pos.x, bottom + 0.8, p.pos.z);
			ZushiFx.pull(level, c, 1.6, 0.6, 1, false);
		}
		if (p.age % 5 == 0) {
			ZushiFx.send(level, ModParticles.ROCK_CHIP, p.pos.x + (r.nextDouble() - 0.5) * 1.6, bottom + 0.4,
					p.pos.z + (r.nextDouble() - 0.5) * 1.6, 1, 0.1, 0.0, 0.1, 0.0);
		}
		// près du sol : la gravité soulève la poussière
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(p.pos.x), (int) Math.floor(p.pos.z));
		if (bottom - ground < 4.0 && p.age % 3 == 0) {
			ZushiFx.circle(level, ModParticles.GRAVITY_DUST, new Vec3(p.pos.x, ground + 0.1, p.pos.z), 1.2, 5, 0.03, 0.12, false);
		}
		if (p.age % 40 == 0) {
			level.playSound(null, BlockPos.containing(p.pos), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6F, 0.6F);
		}
	}

	private static void startCrumble(Platform p, ServerPlayer player) {
		if (p.phase == Phase.CRUMBLE) {
			return;
		}
		p.phase = Phase.CRUMBLE;
		p.phaseAge = 0;
		dismount(p, player);
		// la roche s'effrite : elle rétrécit en s'enfonçant, l'anneau disparaît
		p.rock.scaleTo(SCALE * 0.15, MODEL_TY - 1.2, CRUMBLE_TICKS);
		p.ring.setTransform(0, 1, 0, p.ringAngle + 60.0, 0.01, RING_TY, CRUMBLE_TICKS);
		ZushiFx.send(p.level, ModParticles.ROCK_CHIP, p.pos.x, p.pos.y - RIDER - 0.6, p.pos.z, 30, 1.2, 0.6, 1.2, 0.15);
		ZushiFx.send(p.level, ModParticles.GRAVITY_DUST, p.pos.x, p.pos.y - RIDER - 0.6, p.pos.z, 12, 1.2, 0.5, 1.2, 0.03);
		ZushiFx.circle(p.level, ModParticles.GRAVITY_SPARK, p.pos.add(0, -RIDER - 0.5, 0), 1.5, 16, -0.05, 0.12, false);
		p.level.playSound(null, BlockPos.containing(p.pos), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
		p.level.playSound(null, BlockPos.containing(p.pos), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	private static void dismount(Platform p, ServerPlayer player) {
		if (player == null) {
			return;
		}
		player.removeAttached(ModAttachments.GRAVITY_RIDE);
		Entity vehicle = p.rock.entity();
		if (vehicle != null && player.getVehicle() == vehicle) {
			player.stopRiding();
		}
		player.resetFallDistance();
		if (p.droppedOff) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
		}
	}

	private static void finish(Platform p, ServerPlayer player) {
		dismount(p, player);
		p.rock.remove();
		p.ring.remove();
		if (player != null) {
			FruitAbilities.startCooldown(player, 0, DevilFruit.ZUSHI_ZUSHI_NO_MI.cooldownTicks(0));
		}
	}

	/** Déconnexion / annulation : la plateforme disparaît tout de suite. */
	static void forget(UUID owner, ServerPlayer player) {
		Platform p = ACTIVE.remove(owner);
		if (p != null) {
			dismount(p, player);
			p.rock.remove();
			p.ring.remove();
		}
	}

	static void cleanupAll() {
		for (Platform p : ACTIVE.values()) {
			p.rock.remove();
			p.ring.remove();
		}
		ACTIVE.clear();
	}
}
