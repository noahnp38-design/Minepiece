package minepiece.noah.ability.mera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.ability.FruitAbilities;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.sound.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pouvoirs du Mera Mera no Mi (fruit du Feu).
 * Slot 0 = Fire Gun (un appui lance 3,2 s de tir), slot 1 = Hiken (préparation puis tir), slot 2 = Dai Enkai : Entei (maintenir = charger, relâcher = tirer).
 */
public final class MeraAbilities {
	private enum Type { FIRE_GUN, HIKEN, ENTEI }

	/** Attaque en cours pour un joueur (une seule à la fois). */
	private static final class Cast {
		final Type type;
		final int slot;
		final long start;
		boolean held = true;
		int shots = 0;
		/** Boule de feu 3D de l'Entei (visuel seulement). Passée au projectile au tir, sinon supprimée avec l'attaque. */
		FireballVisual visual;

		Cast(Type type, int slot, long start) {
			this.type = type;
			this.slot = slot;
			this.start = start;
		}
	}

	private static final Map<UUID, Cast> CASTS = new HashMap<>();
	private static final List<ActiveEffect> EFFECTS = new ArrayList<>();
	private static final List<ActiveEffect> PENDING = new ArrayList<>();
	/** Pour chaque joueur : tick de jeu avant lequel le son du Fire Gun ne doit pas être relancé (évite les sons superposés). */
	private static final Map<UUID, Long> FIRE_GUN_SOUND_UNTIL = new HashMap<>();

	private MeraAbilities() {}

	public static void init() {
		MeraConfig.load();
		// Rien de visuel ne doit rester dans le monde sauvegardé.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FireballVisual.cleanupAll();
			FireGunVisual.cleanupAll();
			HikenVisual.cleanupAll();
			FIRE_GUN_SOUND_UNTIL.clear();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(FireballVisual::killStray);
	}

	public static void forget(UUID player) {
		FIRE_GUN_SOUND_UNTIL.remove(player);
		Cast cast = CASTS.remove(player);
		if (cast != null) {
			dispose(cast);
		}
	}

	/** Main droite levée pendant la charge de l'Entei (synchronisé aux clients ; ne change rien si l'état est déjà bon). */
	private static void setRaisedHand(ServerPlayer player, boolean raised) {
		if (Boolean.TRUE.equals(player.getAttached(ModAttachments.RAISE_HAND)) != raised) {
			player.setAttached(ModAttachments.RAISE_HAND, raised);
		}
	}

	/** Supprime le visuel 3D d'une attaque terminée ou annulée (sans effet si le projectile l'a repris). */
	private static void dispose(Cast cast) {
		if (cast.visual != null) {
			cast.visual.remove();
			cast.visual = null;
		}
	}

	// ------------------------------------------------------------------ passif

	/** Corps de flammes : immunisé au feu et à la lave. */
	public static void tickPassive(ServerPlayer player) {
		if (player.isOnFire()) {
			player.clearFire();
		}
		if (player.tickCount % 20 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false));
		}
	}

	// ------------------------------------------------------------------ début / fin d'une attaque

	/** Touche pressée. @return true si l'attaque démarre (sa recharge sera lancée par l'attaque elle-même). */
	public static boolean start(ServerPlayer player, ServerLevel level, int slot) {
		if (CASTS.containsKey(player.getUUID())) {
			return false; // une seule attaque à la fois
		}
		Type type = switch (slot) {
			case 0 -> Type.FIRE_GUN;
			case 1 -> Type.HIKEN;
			case 2 -> Type.ENTEI;
			default -> null;
		};
		if (type == null) {
			return false;
		}
		CASTS.put(player.getUUID(), new Cast(type, slot, level.getGameTime()));
		if (type == Type.HIKEN) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE,
					SoundSource.PLAYERS, 1.5F, 0.6F);
		}
		return true;
	}

	/** Touche relâchée : arrête le Fire Gun, ou tire l'Entei chargé. */
	public static void release(ServerPlayer player, int slot) {
		Cast cast = CASTS.get(player.getUUID());
		if (cast != null && cast.slot == slot) {
			cast.held = false;
		}
	}

	// ------------------------------------------------------------------ tick serveur

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Cast>> it = CASTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Cast> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.isSpectator()
					|| player.getAttached(ModAttachments.FRUIT) != DevilFruit.MERA_MERA_NO_MI
					|| !(player.level() instanceof ServerLevel level)) {
				dispose(entry.getValue());
				if (player != null) {
					setRaisedHand(player, false);
				}
				it.remove();
				continue;
			}
			boolean keep = switch (entry.getValue().type) {
				case FIRE_GUN -> tickFireGun(player, level, entry.getValue());
				case HIKEN -> tickHiken(player, level, entry.getValue());
				case ENTEI -> tickEntei(player, level, entry.getValue());
			};
			if (entry.getValue().type == Type.ENTEI) {
				setRaisedHand(player, keep && entry.getValue().held);
			}
			if (!keep) {
				dispose(entry.getValue());
				it.remove();
			}
		}

		EFFECTS.addAll(PENDING);
		PENDING.clear();
		EFFECTS.removeIf(effect -> !effect.tick(server));
	}

	// ------------------------------------------------------------------ 1. Fire Gun

	private static boolean tickFireGun(ServerPlayer player, ServerLevel level, Cast cast) {
		MeraConfig cfg = MeraConfig.get();
		long age = level.getGameTime() - cast.start;
		// Un appui lance le tir ; il dure exactement fireGunDurationTicks (3,2 s), que la touche reste appuyée ou non.
		if (age >= cfg.fireGunDurationTicks) {
			FruitAbilities.startCooldown(player, cast.slot, DevilFruit.MERA_MERA_NO_MI.cooldownTicks(cast.slot));
			return false;
		}

		// Petites flammes aux deux mains pendant tout le tir.
		for (int side = -1; side <= 1; side += 2) {
			Vec3 hand = handPos(player, side);
			level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 1, 0.05, 0.05, 0.05, 0.01);
		}

		if (age % cfg.fireGunIntervalTicks == 0 && canSchedule()) {
			Vec3 hand = handPos(player, (cast.shots++ % 2 == 0) ? 1 : -1);
			// On vise le point regardé (30 blocs devant) depuis la main : les tirs arrivent dans le viseur.
			Vec3 aim = player.getEyePosition().add(player.getLookAngle().scale(30.0));
			RandomSource random = level.getRandom();
			Vec3 dir = aim.subtract(hand).normalize().add(
					(random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04)
					.normalize();
			schedule(new FireProjectile(level, player.getUUID(), FireProjectile.Kind.BULLET, hand,
					dir.scale(cfg.fireGunSpeed), 0.4, (float) cfg.fireGunDamage, 0, 0, cfg.fireGunLifeTicks));
			level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 4, 0.08, 0.08, 0.08, 0.03);
			playFireGunSound(level, player, hand, cfg);
		}
		return true;
	}

	/**
	 * Son du Fire Gun : joué une seule fois au lancement de la première balle, puis relancé seulement quand il est fini
	 * si le joueur continue de tirer (le son dure 3,8 s alors qu'une balle part toutes les 2 ticks). Ni à chaque balle, ni
	 * à chaque tick, et jamais deux fois en même temps pour un même joueur, même s'il retire tout de suite après.
	 */
	private static void playFireGunSound(ServerLevel level, ServerPlayer player, Vec3 at, MeraConfig cfg) {
		long now = level.getGameTime();
		Long until = FIRE_GUN_SOUND_UNTIL.get(player.getUUID());
		if (until != null && now < until) {
			return;
		}
		FIRE_GUN_SOUND_UNTIL.put(player.getUUID(), now + cfg.fireGunSoundIntervalTicks);
		level.playSound(null, at.x, at.y, at.z, ModSounds.FIRE_GUN, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	// ------------------------------------------------------------------ 2. Hiken

	private static boolean tickHiken(ServerPlayer player, ServerLevel level, Cast cast) {
		MeraConfig cfg = MeraConfig.get();
		int age = (int) (level.getGameTime() - cast.start);
		double t = Math.min(1.0, age / (double) cfg.hikenWindupTicks);
		Vec3 look = player.getLookAngle();

		// Préparation : la boule grossit devant le bras, le joueur est ralenti.
		double radius = Mth.lerp(t, 0.4, cfg.hikenRadius);
		Vec3 ball = handPos(player, 1).add(look.scale(0.4 + radius));
		if (age % 5 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 8, 2, false, false));
		}
		if (age < cfg.hikenWindupTicks) {
			fireball(level, ball, radius);
			if (age % 4 == 0) {
				level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 1.0F, 0.5F + (float) t);
			}
			return true;
		}

		// Lancement dans la direction visée.
		if (canSchedule()) {
			Vec3 aim = player.getEyePosition().add(look.scale(60.0));
			Vec3 dir = aim.subtract(ball).normalize();
			schedule(FireProjectile.hiken(level, player.getUUID(), ball,
					dir.scale(cfg.hikenSpeed), cfg.hikenRadius, cfg.hikenBlastRadius, (float) cfg.hikenDamage, 60));
		}
		level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0F, 0.5F);
		level.sendParticles(ParticleTypes.FLAME, ball.x, ball.y, ball.z, 40, 0.5, 0.5, 0.5, 0.15);
		FruitAbilities.startCooldown(player, cast.slot, DevilFruit.MERA_MERA_NO_MI.cooldownTicks(cast.slot));
		return false;
	}

	// ------------------------------------------------------------------ 3. Dai Enkai : Entei

	private static boolean tickEntei(ServerPlayer player, ServerLevel level, Cast cast) {
		MeraConfig cfg = MeraConfig.get();
		int age = (int) (level.getGameTime() - cast.start);
		int charge = Math.min(age, cfg.enteiMaxChargeTicks);
		double t = charge / (double) cfg.enteiMaxChargeTicks;
		double radius = Mth.lerp(t, cfg.enteiMinRadius, cfg.enteiMaxRadius);

		// La sphère 3D fait ~2,4 x le rayon de large (rayon visuel ~1,2 x) : on la monte pour qu'elle ne rentre pas dans la tête.
		Vec3 ball = player.position().add(0, player.getBbHeight() + 1.0 + radius * 1.35, 0);
		BlockPos ballPos = BlockPos.containing(ball);
		if (!level.getBlockState(ballPos).getCollisionShape(level, ballPos).isEmpty()) {
			ball = player.getEyePosition().add(0, 0.5, 0); // plafond au-dessus de la tête : la boule reste près du joueur
		}

		if (cast.held) {
			if (age > cfg.enteiMaxChargeTicks + 200) {
				return false; // sécurité : charge abandonnée (touche jamais relâchée)
			}
			// La boule 3D grossit, tourne, vibre et devient de plus en plus intense ; les particules l'habillent.
			if (cast.visual == null) {
				cast.visual = FireballVisual.create(level, ball, radius);
			}
			cast.visual.update(ball, radius, t);
			FireFx.charge(level, ball, radius, t, age);
			if (age % 5 == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 8, 1, false, false));
			}
			if (age % 8 == 0) {
				level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.BLAZE_BURN, SoundSource.PLAYERS,
						(float) (1.0 + t * 2.0), (float) (0.5 + t));
			}
			if (age % 4 == 0) {
				player.sendSystemMessage(Component.literal("Dai Enkai : Entei - charge " + (int) (t * 100) + " %"), true);
			}
			return true;
		}

		// Relâché trop tôt : annulé, sans recharge.
		if (charge < cfg.enteiMinChargeTicks) {
			return false;
		}

		// Tir : plus c'est chargé, plus c'est gros, puissant et destructeur.
		if (canSchedule()) {
			Vec3 aim = player.getEyePosition().add(player.getLookAngle().scale(80.0));
			Vec3 dir = aim.subtract(ball).normalize();
			double blast = Mth.lerp(t, cfg.enteiMinBlastRadius, cfg.enteiMaxBlastRadius);
			float damage = (float) Mth.lerp(t, cfg.enteiMinDamage, cfg.enteiMaxDamage);
			// La boule 3D devient le visuel du projectile (qui garde exactement les mêmes dégâts, hitbox et vitesse).
			FireballVisual visual = cast.visual;
			cast.visual = null;
			if (visual != null) {
				visual.launchFlash(dir);
			}
			schedule(new FireProjectile(level, player.getUUID(), FireProjectile.Kind.BIG, ball,
					dir.scale(cfg.enteiSpeed), radius, 0, blast, damage, 120, visual));
		}
		level.playSound(null, ball.x, ball.y, ball.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0F, 0.4F);
		level.sendParticles(ParticleTypes.FLAME, ball.x, ball.y, ball.z, 80, radius * 0.5, radius * 0.5, radius * 0.5, 0.2);
		FruitAbilities.startCooldown(player, cast.slot, DevilFruit.MERA_MERA_NO_MI.cooldownTicks(cast.slot));
		return false;
	}

	// ------------------------------------------------------------------ utilitaires (partagés avec les projectiles / explosions)

	static boolean canSchedule() {
		return EFFECTS.size() + PENDING.size() < MeraConfig.get().maxActiveEffects;
	}

	static void schedule(ActiveEffect effect) {
		PENDING.add(effect);
	}

	/** Inflige des dégâts de feu à une cible, au nom du joueur (ou "feu" s'il s'est déconnecté). */
	static void hurt(ServerLevel level, UUID owner, LivingEntity target, float amount, int fireSeconds) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		DamageSource source = player != null ? level.damageSources().playerAttack(player) : level.damageSources().inFire();
		target.hurtServer(level, source, (float) (amount * MeraConfig.get().damageMultiplier));
		target.igniteForSeconds(fireSeconds);
	}

	/** Boule de feu visuelle : un nuage dense de flammes + fumée + gouttes de lave, proportionnel au rayon. */
	static void fireball(ServerLevel level, Vec3 c, double radius) {
		int flames = (int) Math.min(70, 6 + radius * 10);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, flames, radius * 0.4, radius * 0.4, radius * 0.4, 0.015);
		level.sendParticles(ParticleTypes.SMALL_FLAME, c.x, c.y, c.z, flames / 2, radius * 0.5, radius * 0.5, radius * 0.5, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, (int) Math.min(24, radius * 3), radius * 0.5, radius * 0.5, radius * 0.5, 0.01);
		if (radius > 1.5) {
			level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, (int) Math.min(8, radius), radius * 0.4, radius * 0.4, radius * 0.4, 0);
		}
	}

	/** Position d'une main du joueur (side = 1 : droite, -1 : gauche). */
	private static Vec3 handPos(ServerPlayer player, int side) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = flat.cross(new Vec3(0, 1, 0)); // joueur tourné vers +z : sa droite est en -x
		return player.getEyePosition().add(look.scale(0.6)).add(right.scale(0.4 * side)).add(0, -0.35, 0);
	}
}
