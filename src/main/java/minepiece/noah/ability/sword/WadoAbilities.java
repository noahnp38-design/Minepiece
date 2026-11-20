package minepiece.noah.ability.sword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.item.ModItems;
import minepiece.noah.network.WadoCooldownPayload;
import minepiece.noah.network.WadoShakePayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pouvoirs de la Wado Ichimonji. Tout est décidé et calculé côté serveur : le client n'envoie que "touche pressée".
 *  - Attaque 1 (R par défaut) : Shishi Sonson, un dash de 6 blocs avec coupe horizontale ; les ennemis traversés sont
 *    tranchés à la fin du dash (ils n'ont pas le temps de réagir). 30 dégâts, 8 s de recharge.
 *  - Attaque 3 (V par défaut) : 90 Cleasher Slash, charge d'1 s puis onde de choc blanche de 20 blocs qui traverse
 *    les ennemis. 55 dégâts, 20 s de recharge.
 * Utilisables uniquement avec la Wado Ichimonji dans la main principale. Les touches se règlent dans les options de
 * Minecraft (catégorie MinePiece) : ce sont les mêmes que les attaques de fruit, qui reprennent la main sans le sabre.
 * Une seule attaque à la fois par joueur ; chaque attaque a sa propre recharge, affichée dans la barre d'action.
 */
public final class WadoAbilities {
	/** Numéros d'attaque (touches "Attaque 1" et "Attaque 3"). */
	public static final int SLOT_SHISHI = 0;
	public static final int SLOT_CLEASHER = 2;

	// ---------------------------------------------------------------- réglages
	private static final float SHISHI_DAMAGE = 30.0F;
	private static final int SHISHI_COOLDOWN = 160; // 8 s
	private static final int SHISHI_WINDUP = 10; // 0,5 s
	private static final double SHISHI_DISTANCE = 6.0;
	private static final double SHISHI_SPEED = 1.5; // blocs par tick : 6 blocs en 4 ticks
	private static final int SHISHI_MAX_DASH_TICKS = 8;

	private static final float CLEASHER_DAMAGE = 55.0F;
	private static final int CLEASHER_COOLDOWN = 400; // 20 s
	private static final int CLEASHER_WINDUP = 20; // 1 s
	private static final double CLEASHER_RANGE = 20.0;
	private static final double CLEASHER_SPEED = 1.5;
	private static final int SHAKE_TICKS = 8;
	private static final int SHAKE_INTENSITY = 3;

	private enum Skill { SHISHI, CLEASHER }

	/** Attaque en cours pour un joueur (une seule à la fois). */
	private static final class Cast {
		final Skill skill;
		final long start;
		boolean dashing = false;
		Vec3 dir;
		Vec3 origin;
		Vec3 last;
		double traveled = 0.0;
		int dashTicks = 0;
		final Set<UUID> hit = new LinkedHashSet<>();

		Cast(Skill skill, long start) {
			this.skill = skill;
			this.start = start;
		}
	}

	private static final Map<UUID, Cast> CASTS = new HashMap<>();
	private static final List<Wave> WAVES = new ArrayList<>();
	/** Fin de recharge (game time) par joueur : [0] = Shishi Sonson, [1] = 90 Cleasher Slash. Conservé à la déconnexion. */
	private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

	private WadoAbilities() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(WadoAbilities::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			CASTS.remove(id);
		});
		// A la connexion : on renvoie les recharges en cours au client (la HUD les affiche).
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			long[] ready = COOLDOWNS.get(player.getUUID());
			if (ready == null) {
				return;
			}
			long now = player.level().getGameTime();
			sendCooldown(player, 0, ready[0] - now, SHISHI_COOLDOWN);
			sendCooldown(player, 1, ready[1] - now, CLEASHER_COOLDOWN);
		});
	}

	public static boolean holdsWado(ServerPlayer player) {
		return player.getMainHandItem().getItem() == ModItems.WADO_ICHIMONJI;
	}

	// ---------------------------------------------------------------- touche pressée

	/**
	 * Touche "Attaque 1" ou "Attaque 3" pressée. @return true si la Wado Ichimonji est en main : la touche est alors
	 * réservée au sabre (même si l'attaque est en recharge), sinon false et l'attaque de fruit habituelle s'applique.
	 */
	public static boolean use(ServerPlayer player, int slot) {
		if (slot != SLOT_SHISHI && slot != SLOT_CLEASHER) {
			return false;
		}
		if (!holdsWado(player)) {
			return false;
		}
		if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return true;
		}
		if (CASTS.containsKey(player.getUUID())) {
			return true; // une seule attaque à la fois
		}

		Skill skill = slot == SLOT_SHISHI ? Skill.SHISHI : Skill.CLEASHER;
		int index = skill == Skill.SHISHI ? 0 : 1;
		long now = level.getGameTime();
		long[] ready = COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new long[2]);
		if (now < ready[index]) {
			long seconds = (ready[index] - now + 19) / 20;
			player.sendSystemMessage(Component.literal(
					(skill == Skill.SHISHI ? "Shishi Sonson" : "90 Cleasher Slash") + " : encore " + seconds + " s"), true);
			return true;
		}

		CASTS.put(player.getUUID(), new Cast(skill, now));
		Vec3 p = player.position();
		// Dégainage.
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 1.0F, 1.7F);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.35F, 1.9F);
		if (skill == Skill.SHISHI) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHISHI_WINDUP + 1, 6, false, false));
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CLEASHER_WINDUP + 1, 6, false, false));
			level.playSound(null, p.x, p.y, p.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);
		}
		return true;
	}

	// ---------------------------------------------------------------- tick serveur

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Cast>> it = CASTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Cast> entry = it.next();
			Cast cast = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			// Un dash lancé va jusqu'au bout ; avant, on annule si le joueur lâche le sabre (sans recharge).
			if (player == null || !player.isAlive() || player.isSpectator()
					|| (!cast.dashing && !holdsWado(player))
					|| !(player.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			boolean keep = cast.skill == Skill.SHISHI ? tickShishi(player, level, cast) : tickCleasher(player, level, cast);
			if (!keep) {
				it.remove();
			}
		}

		WAVES.removeIf(wave -> !wave.tick(server));
	}

	// ---------------------------------------------------------------- 1. Shishi Sonson

	private static boolean tickShishi(ServerPlayer player, ServerLevel level, Cast cast) {
		long age = level.getGameTime() - cast.start;
		if (!cast.dashing) {
			if (age < SHISHI_WINDUP) {
				WadoFx.aura(level, bladePos(player)); // posture de dégainage : petite aura blanche sur la lame
				return true;
			}
			// Départ du dash.
			cast.dashing = true;
			cast.dir = horizontalLook(player);
			cast.origin = player.position();
			cast.last = cast.origin;
			startCooldown(player, 0, SHISHI_COOLDOWN, level);
			WadoFx.dashStart(level, cast.origin, cast.dir);
			level.playSound(null, cast.origin.x, cast.origin.y, cast.origin.z, SoundEvents.PLAYER_ATTACK_SWEEP,
					SoundSource.PLAYERS, 1.6F, 1.3F);
			level.playSound(null, cast.origin.x, cast.origin.y, cast.origin.z, SoundEvents.TRIDENT_RETURN,
					SoundSource.PLAYERS, 1.0F, 1.8F);
		}

		// Déplacement réel depuis le tick précédent, et ennemis traversés (hitbox balayée sur toute la trajectoire).
		Vec3 pos = player.position();
		Vec3 moved = pos.subtract(cast.last);
		cast.traveled += moved.horizontalDistance();
		if (cast.dashTicks > 0) {
			AABB sweep = player.getBoundingBox().expandTowards(cast.last.subtract(pos)).inflate(0.9, 0.4, 0.9);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep,
					e -> e != player && e.isAlive() && !e.isSpectator())) {
				cast.hit.add(target.getUUID());
			}
		}

		boolean blocked = cast.dashTicks >= 2 && moved.horizontalDistance() < 0.15;
		if (cast.traveled >= SHISHI_DISTANCE - 0.05 || cast.dashTicks >= SHISHI_MAX_DASH_TICKS || blocked) {
			finishShishi(player, level, cast, pos);
			return false;
		}

		// Dash : on impose la vitesse (le client reçoit la vitesse et se déplace, les murs l'arrêtent).
		double vy = player.onGround() ? 0.0 : Math.min(0.0, player.getDeltaMovement().y);
		player.setDeltaMovement(cast.dir.x * SHISHI_SPEED, vy, cast.dir.z * SHISHI_SPEED);
		player.needsSync = true;
		WadoFx.dashFrame(level, pos, cast.dir);
		cast.last = pos;
		cast.dashTicks++;
		return true;
	}

	/** Fin du dash : le joueur s'arrête, puis les ennemis traversés sont tranchés (une seule fois chacun). */
	private static void finishShishi(ServerPlayer player, ServerLevel level, Cast cast, Vec3 end) {
		player.setDeltaMovement(0.0, player.onGround() ? 0.0 : Math.min(0.0, player.getDeltaMovement().y), 0.0);
		player.needsSync = true;

		WadoFx.slashLine(level, cast.origin, end);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.8F);

		for (UUID id : cast.hit) {
			Entity entity = level.getEntity(id);
			if (entity instanceof LivingEntity target && target.isAlive() && !target.isSpectator()) {
				hurt(level, player, target, SHISHI_DAMAGE);
				Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
				WadoFx.cut(level, center, cast.dir);
				level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 1.1F);
			}
		}
	}

	// ---------------------------------------------------------------- 2. 90 Cleasher Slash

	private static boolean tickCleasher(ServerPlayer player, ServerLevel level, Cast cast) {
		long age = level.getGameTime() - cast.start;
		if (age < CLEASHER_WINDUP) {
			// Aura blanche, énergie qui se concentre autour de la lame pendant 1 seconde.
			WadoFx.charge(level, bladePos(player), (int) age, CLEASHER_WINDUP);
			return true;
		}

		// Coupe horizontale : l'onde part devant le joueur, dans la direction visée.
		Vec3 dir = player.getLookAngle().normalize();
		Vec3 start = player.getEyePosition().add(0, -0.4, 0).add(dir.scale(1.0));
		startCooldown(player, 1, CLEASHER_COOLDOWN, level);
		WAVES.add(new Wave(player.getUUID(), start, dir));
		WadoFx.release(level, start, dir);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.9F, 1.5F);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.6F, 0.6F);
		ServerPlayNetworking.send(player, new WadoShakePayload(SHAKE_TICKS, SHAKE_INTENSITY));
		return false;
	}

	/** Onde de choc : avance de 20 blocs, traverse les ennemis (une seule fois chacun), s'arrête sur un mur. */
	private static final class Wave {
		private final UUID owner;
		private final Vec3 dir;
		private final Set<UUID> hit = new HashSet<>();
		private Vec3 pos;
		private double traveled = 0.0;

		Wave(UUID owner, Vec3 pos, Vec3 dir) {
			this.owner = owner;
			this.pos = pos;
			this.dir = dir;
		}

		boolean tick(MinecraftServer server) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player == null || !(player.level() instanceof ServerLevel level)) {
				return false;
			}
			// Petits pas de 0,5 bloc : ni mur ni ennemi traversé sans être vu.
			int steps = (int) Math.ceil(CLEASHER_SPEED / 0.5);
			Vec3 step = dir.scale(CLEASHER_SPEED / steps);
			for (int i = 0; i < steps; i++) {
				Vec3 next = pos.add(step);
				BlockPos blockPos = BlockPos.containing(next);
				if (!level.isLoaded(blockPos)) {
					return false;
				}
				BlockState state = level.getBlockState(blockPos);
				if (!state.getCollisionShape(level, blockPos).isEmpty()) {
					explode(level);
					return false;
				}
				pos = next;
				traveled += step.length();
				AABB box = new AABB(pos, pos).inflate(1.9, 1.3, 1.9);
				for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
						e -> e != player && e.isAlive() && !e.isSpectator() && !hit.contains(e.getUUID()))) {
					hit.add(target.getUUID());
					hurt(level, player, target, CLEASHER_DAMAGE);
					Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
					WadoFx.cut(level, center, dir);
					level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.9F);
				}
				if (traveled >= CLEASHER_RANGE) {
					explode(level);
					return false;
				}
			}
			WadoFx.waveFrame(level, pos, dir);
			return true;
		}

		private void explode(ServerLevel level) {
			WadoFx.impact(level, pos);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.5F, 0.7F);
		}
	}

	// ---------------------------------------------------------------- recharges et affichage

	private static void startCooldown(ServerPlayer player, int index, int ticks, ServerLevel level) {
		long[] ready = COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new long[2]);
		ready[index] = level.getGameTime() + ticks;
		sendCooldown(player, index, ticks, ticks);
	}

	/** Remet à zéro les recharges du sabre (commande de reset) et vide l'affichage. */
	public static void resetCooldowns(ServerPlayer player) {
		long[] ready = COOLDOWNS.get(player.getUUID());
		if (ready != null) {
			java.util.Arrays.fill(ready, 0L);
		}
		ServerPlayNetworking.send(player, new WadoCooldownPayload(0, 0, SHISHI_COOLDOWN));
		ServerPlayNetworking.send(player, new WadoCooldownPayload(1, 0, CLEASHER_COOLDOWN));
	}

	/** Envoie au client l'etat d'une recharge (temps restant et duree totale, en ticks). */
	private static void sendCooldown(ServerPlayer player, int index, long remaining, int total) {
		if (remaining <= 0) {
			return;
		}
		ServerPlayNetworking.send(player, new WadoCooldownPayload(index, (int) remaining, total));
	}

	// ---------------------------------------------------------------- utilitaires

	/** Inflige des dégâts au nom du joueur (fonctionne avec toutes les entités vivantes, boss compris). */
	private static void hurt(ServerLevel level, ServerPlayer player, LivingEntity target, float amount) {
		DamageSource source = level.damageSources().playerAttack(player);
		target.hurtServer(level, source, amount);
	}

	private static Vec3 horizontalLook(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	/** Position approximative de la lame du katana (main droite, devant le joueur). */
	private static Vec3 bladePos(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 right = WadoFx.rightOf(horizontalLook(player));
		return player.getEyePosition().add(look.scale(1.0)).add(right.scale(0.45)).add(0, -0.45, 0);
	}
}
