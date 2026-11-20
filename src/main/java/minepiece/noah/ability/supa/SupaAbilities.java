package minepiece.noah.ability.supa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.ability.DemonCurse;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pouvoirs du Supa Supa no Mi (fruit Tailladant, Paramécia, Mr. 1). Tout est décidé par le serveur ; les lames 3D
 * (SupaVisual) ne sont que de l'affichage.
 *  Passif "Corps d'acier" : -30 % de dégâts physiques reçus, +2 de dégâts à chaque coup de mêlée (voir modifyDamage, appelée
 *                           par LivingEntityMixin : une seule fois par coup).
 *  1. Sparkling Daisy : 8 lames en croissant jaillissent du sol, en cercle de 4 blocs, l'une après l'autre (10 dégâts, une fois par cible).
 *  2. Spiral Hollow   : charge tournoyante de 1,5 s dans la direction du regard (4 dégâts toutes les 0,3 s, 5 coups max par cible).
 *  3. Atomic Spada    : 0,8 s de préparation (bras-lame levé), puis onde de coupe en croissant (9 blocs, 5 de large, 3 de haut, 18 dégâts).
 * Les recharges (voir DevilFruit) sont gérées par FruitAbilities et synchronisées au client. Aucun bloc n'est détruit.
 */
public final class SupaAbilities {
	// ---- Passif
	private static final float STEEL_FACTOR = 0.7F;       // -30 %
	private static final float MELEE_BONUS = 2.0F;        // +2 points (1 cœur) en mêlée

	// ---- Sparkling Daisy
	private static final int DAISY_BLADES = 8;
	private static final double DAISY_RADIUS = 4.0;
	private static final float DAISY_DAMAGE = 10.0F;
	private static final int DAISY_START = 3;              // délai avant la 1re lame
	private static final int DAISY_STEP = 2;               // ticks entre deux lames
	private static final int DAISY_HOLD = 14;              // ticks où la lame reste dressée
	private static final int DAISY_FADE = 6;
	private static final double DAISY_HIT_RADIUS = 2.2;
	private static final double DAISY_SCALE = 2.0;
	private static final double DAISY_TY = 0.3125;         // (8 - 3) / 16 : bas du modèle au sol, par unité d'échelle

	// ---- Spiral Hollow
	private static final int SPIRAL_TICKS = 30;            // 1,5 s
	private static final double SPIRAL_SPEED = 0.75;       // blocs / tick (le sprint fait ~0,28)
	private static final double SPIRAL_RADIUS = 1.5;
	private static final float SPIRAL_DAMAGE = 4.0F;       // 2 cœurs
	private static final int SPIRAL_INTERVAL = 6;          // 0,3 s
	private static final int SPIRAL_MAX_HITS = 5;

	// ---- Atomic Spada
	private static final int SPADA_PREP = 16;              // 0,8 s
	private static final int SPADA_SWING = 5;
	private static final double SPADA_RANGE = 9.0;
	private static final double SPADA_WIDTH = 5.0;
	private static final double SPADA_HEIGHT = 3.0;
	private static final double SPADA_SPEED = 1.5;         // blocs / tick
	private static final float SPADA_DAMAGE = 18.0F;       // 9 cœurs
	private static final double SPADA_KNOCKBACK = 2.2;
	private static final double ARM_SCALE = 2.3;
	private static final double WAVE_SCALE = 2.9;

	private static final DustParticleOptions WHITE = new DustParticleOptions(0xF4F8FF, 1.6F);
	private static final DustParticleOptions BLUE = new DustParticleOptions(0xBFE3FF, 1.4F);
	private static final DustParticleOptions SILVER = new DustParticleOptions(0xA9B2C0, 1.5F);
	private static final DustParticleOptions DUST = new DustParticleOptions(0xB8B09C, 1.8F);

	private static final List<Daisy> DAISIES = new ArrayList<>();
	private static final Map<UUID, Spin> SPINS = new HashMap<>();
	private static final Map<UUID, Spada> SPADAS = new HashMap<>();

	/** Vrai pendant que les attaques du fruit infligent leurs dégâts : ces coups ne reçoivent pas le bonus de mêlée de +2. */
	private static boolean dealingAbilityDamage = false;

	private SupaAbilities() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SupaAbilities::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SupaVisual.cleanupAll();
			DAISIES.clear();
			SPINS.clear();
			SPADAS.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.getPlayer()));
	}

	public static boolean isSupa(ServerPlayer player) {
		return player.getAttached(ModAttachments.FRUIT) == DevilFruit.SUPA_SUPA_NO_MI && !DemonCurse.isSuppressed(player);
	}

	// ------------------------------------------------------------------ passif : Corps d'acier

	/**
	 * Appelée par LivingEntityMixin au début de chaque hurtServer (donc une seule fois par coup).
	 *  - Attaquant Supa Supa : +2 de dégâts à ses coups de mêlée (pas à ses attaques de fruit).
	 *  - Victime Supa Supa   : -30 % pour les dégâts PHYSIQUES uniquement (coups, flèches...) ; ni le feu, ni les explosions,
	 *    ni la magie, ni les chutes, ni le vide ne sont réduits. Rien n'est ignoré : le joueur n'est pas invincible.
	 */
	public static float modifyDamage(LivingEntity victim, ServerLevel level, DamageSource source, float amount) {
		if (amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		float out = amount;
		if (!dealingAbilityDamage && source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof ServerPlayer attacker
				&& source.getDirectEntity() == attacker && attacker != victim && isSupa(attacker)) {
			out += MELEE_BONUS;
		}
		if (victim instanceof ServerPlayer defender && isSupa(defender) && isPhysical(source)) {
			out *= STEEL_FACTOR;
			Vec3 c = defender.position().add(0, defender.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 5, 0.25, 0.35, 0.25, 0.25);
			level.sendParticles(SILVER, c.x, c.y, c.z, 3, 0.25, 0.3, 0.25, 0.0);
			if (level.getRandom().nextFloat() < 0.35F) {
				level.playSound(null, c.x, c.y, c.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.35F, 1.7F);
			}
		}
		return out;
	}

	/** Dégâts physiques : coups de créatures / joueurs et projectiles. Tout ce qui ignore l'armure (magie, vide, faim...), le feu et les explosions est exclu. */
	private static boolean isPhysical(DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		return source.getDirectEntity() instanceof LivingEntity || source.is(DamageTypeTags.IS_PROJECTILE);
	}

	// ------------------------------------------------------------------ lancement

	/** @return true si l'attaque démarre (la recharge est alors lancée par FruitAbilities). */
	public static boolean cast(ServerPlayer player, ServerLevel level, int slot) {
		return switch (slot) {
			case 0 -> daisy(player, level);
			case 1 -> spiral(player, level);
			case 2 -> spada(player, level);
			default -> false;
		};
	}

	/** Coupe les attaques en cours d'un joueur (déconnexion, mort, Malédiction du Démon...). */
	public static void cancel(ServerPlayer player) {
		UUID id = player.getUUID();
		for (Iterator<Daisy> it = DAISIES.iterator(); it.hasNext();) {
			Daisy d = it.next();
			if (d.owner.equals(id)) {
				d.dispose();
				it.remove();
			}
		}
		Spin spin = SPINS.remove(id);
		if (spin != null) {
			spin.dispose();
		}
		Spada spada = SPADAS.remove(id);
		if (spada != null) {
			spada.dispose(player);
		}
	}

	private static boolean daisy(ServerPlayer player, ServerLevel level) {
		if (DAISIES.size() >= 12) {
			return false;
		}
		Vec3 c = player.position();
		DAISIES.add(new Daisy(level, player.getUUID(), c, player.getYRot()));
		// Coup de pied au sol : onde de poussière et claquement de métal.
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 1.9F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.1, c.z, 12, 0.5, 0.05, 0.5, 0.06);
		level.sendParticles(DUST, c.x, c.y + 0.2, c.z, 10, 0.5, 0.1, 0.5, 0.0);
		return true;
	}

	private static boolean spiral(ServerPlayer player, ServerLevel level) {
		if (SPINS.containsKey(player.getUUID())) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		SPINS.put(player.getUUID(), new Spin(level, player.getUUID(), look.normalize(), player.position()));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 1.0F, 1.5F);
		return true;
	}

	private static boolean spada(ServerPlayer player, ServerLevel level) {
		if (SPADAS.containsKey(player.getUUID())) {
			return false;
		}
		SPADAS.put(player.getUUID(), new Spada(level, player));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	// ------------------------------------------------------------------ tick serveur

	private static void tick(MinecraftServer server) {
		for (Iterator<Daisy> it = DAISIES.iterator(); it.hasNext();) {
			Daisy d = it.next();
			if (!d.tick(server)) {
				d.dispose();
				it.remove();
			}
		}
		for (Iterator<Map.Entry<UUID, Spin>> it = SPINS.entrySet().iterator(); it.hasNext();) {
			Spin s = it.next().getValue();
			if (!s.tick(server)) {
				s.dispose();
				it.remove();
			}
		}
		for (Iterator<Map.Entry<UUID, Spada>> it = SPADAS.entrySet().iterator(); it.hasNext();) {
			Spada s = it.next().getValue();
			if (!s.tick(server)) {
				ServerPlayer p = server.getPlayerList().getPlayer(s.owner);
				s.dispose(p);
				it.remove();
			}
		}
	}

	// ------------------------------------------------------------------ outils

	/** Inflige les dégâts d'une attaque du fruit (jamais au lanceur). Réinitialise l'invulnérabilité pour que chaque coup compte. */
	private static void hurt(ServerLevel level, ServerPlayer owner, LivingEntity target, float damage) {
		dealingAbilityDamage = true;
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, level.damageSources().playerAttack(owner), damage);
		} finally {
			dealingAbilityDamage = false;
		}
	}

	private static void knock(LivingEntity target, Vec3 horizontalDir, double force, double up) {
		Vec3 d = new Vec3(horizontalDir.x, 0, horizontalDir.z);
		d = d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0) : d.normalize();
		target.push(d.x * force, up, d.z * force);
		target.needsSync = true;
	}

	/** Étincelles d'acier et de tranchant sur une cible touchée. */
	private static void hitFx(ServerLevel level, LivingEntity target, boolean big) {
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, big ? 18 : 8, 0.3, 0.4, 0.3, 0.35);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, big ? 14 : 6, 0.3, 0.4, 0.3, 0.3);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, big ? 8 : 3, 0.25, 0.3, 0.25, 0.1);
		level.sendParticles(WHITE, c.x, c.y, c.z, 4, 0.3, 0.35, 0.3, 0.0);
		if (big) {
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.5F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 2.0F);
	}

	private static boolean isSolid(ServerLevel level, double x, double y, double z) {
		BlockPos bp = BlockPos.containing(x, y, z);
		return !level.isLoaded(bp) || !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
	}

	/** Hauteur du sol sous un point (le premier bloc plein en dessous, 6 blocs maximum). */
	private static double groundY(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Math.floor(x), Math.floor(y), Math.floor(z));
		if (level.isLoaded(m) && !level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
			return m.getY() + 1.0;
		}
		for (int i = 0; i < 6; i++) {
			m.move(0, -1, 0);
			if (level.isLoaded(m) && !level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
				return m.getY() + 1.0;
			}
		}
		return y;
	}

	/** Yaw Minecraft (degrés) d'un vecteur horizontal : 0 = +Z, 90 = -X. */
	private static float yawOf(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	private static void setRaisedHand(ServerPlayer player, boolean raised) {
		if (Boolean.TRUE.equals(player.getAttached(ModAttachments.RAISE_HAND)) != raised) {
			player.setAttached(ModAttachments.RAISE_HAND, raised);
		}
	}

	// ------------------------------------------------------------------ 1. Sparkling Daisy

	private static final class Blade {
		final SupaVisual visual;
		final Vec3 pos;
		int age = 0;

		Blade(SupaVisual visual, Vec3 pos) {
			this.visual = visual;
			this.pos = pos;
		}
	}

	/** 8 lames qui jaillissent du sol en cercle, l'une après l'autre ; chaque cible n'est touchée qu'une fois. */
	private static final class Daisy {
		final ServerLevel level;
		final UUID owner;
		final Vec3 center;
		final List<Blade> blades = new ArrayList<>();
		final Set<UUID> hit = new HashSet<>();
		final double phase;
		int age = 0;
		int spawned = 0;

		Daisy(ServerLevel level, UUID owner, Vec3 center, float yaw) {
			this.level = level;
			this.owner = owner;
			this.center = center;
			this.phase = Math.toRadians(yaw);
		}

		boolean tick(MinecraftServer server) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			// Cercle métallique qui s'ouvre autour du joueur (les 5 premiers ticks) puis pulse.
			if (age < 5) {
				ring(DAISY_RADIUS * (age + 1) / 5.0, 40);
			} else if (age % 6 == 0 && spawned < DAISY_BLADES + 2) {
				ring(DAISY_RADIUS, 24);
			}

			int due = (age - DAISY_START) / DAISY_STEP;
			if (age >= DAISY_START && (age - DAISY_START) % DAISY_STEP == 0 && due < DAISY_BLADES) {
				spawnBlade(due);
			}

			for (Iterator<Blade> it = blades.iterator(); it.hasNext();) {
				Blade b = it.next();
				b.age++;
				if (b.age == 2 && player != null && player.isAlive()) {
					strike(player, b);
				}
				if (b.age == DAISY_HOLD) {
					b.visual.scaleTo(DAISY_SCALE, DAISY_SCALE * 0.04, DAISY_SCALE, DAISY_TY * DAISY_SCALE * 0.04, DAISY_FADE);
				}
				if (b.age < DAISY_HOLD && b.age % 3 == 0) {
					level.sendParticles(ParticleTypes.END_ROD, b.pos.x, b.pos.y + 1.4, b.pos.z, 2, 0.5, 0.9, 0.5, 0.02);
				}
				if (b.age >= DAISY_HOLD + DAISY_FADE) {
					b.visual.remove();
					it.remove();
				}
			}
			age++;
			return spawned < DAISY_BLADES || !blades.isEmpty();
		}

		private void ring(double radius, int points) {
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2.0 * i / points;
				double x = center.x + Math.cos(a) * radius;
				double z = center.z + Math.sin(a) * radius;
				double y = groundY(level, x, center.y + 0.5, z) + 0.12;
				level.sendParticles(i % 2 == 0 ? WHITE : BLUE, x, y, z, 1, 0.03, 0.0, 0.03, 0.0);
				if (i % 4 == 0) {
					level.sendParticles(ParticleTypes.END_ROD, x, y, z, 0, 0.0, 0.05, 0.0, 1.0);
				}
			}
		}

		private void spawnBlade(int i) {
			double a = phase + Math.PI * 2.0 * i / DAISY_BLADES;
			double dx = Math.cos(a);
			double dz = Math.sin(a);
			double x = center.x + dx * DAISY_RADIUS;
			double z = center.z + dz * DAISY_RADIUS;
			double y = groundY(level, x, center.y + 0.5, z);
			Vec3 p = new Vec3(x, y, z);
			float yaw = yawOf(dx, dz);
			double s0 = 0.04;
			SupaVisual v = SupaVisual.create(level, "supa_blade", p, yaw, 10.0F, DAISY_SCALE, DAISY_SCALE * s0, DAISY_SCALE,
					DAISY_TY * DAISY_SCALE * s0, 1);
			v.scaleTo(DAISY_SCALE, DAISY_SCALE, DAISY_SCALE, DAISY_TY * DAISY_SCALE, 3);
			blades.add(new Blade(v, p));
			spawned++;

			// Apparition : poussière, éclats d'acier, claquement de lame.
			level.sendParticles(ParticleTypes.POOF, x, y + 0.1, z, 6, 0.4, 0.05, 0.4, 0.05);
			level.sendParticles(DUST, x, y + 0.2, z, 6, 0.4, 0.1, 0.4, 0.0);
			level.sendParticles(WHITE, x, y + 0.8, z, 8, 0.35, 0.7, 0.35, 0.0);
			level.sendParticles(ParticleTypes.CRIT, x, y + 0.6, z, 6, 0.3, 0.6, 0.3, 0.3);
			level.sendParticles(ParticleTypes.END_ROD, x, y + 0.3, z, 0, 0.0, 0.25, 0.0, 1.0);
			level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.6F);
			level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 2.0F);
		}

		/** Dégâts de la lame sur les cibles proches (une seule fois par cible pour toute l'attaque). */
		private void strike(ServerPlayer player, Blade b) {
			AABB box = new AABB(b.pos.x - DAISY_HIT_RADIUS, b.pos.y - 1.0, b.pos.z - DAISY_HIT_RADIUS,
					b.pos.x + DAISY_HIT_RADIUS, b.pos.y + 3.5, b.pos.z + DAISY_HIT_RADIUS);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
					e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner))) {
				if (!hit.add(target.getUUID())) {
					continue;
				}
				hurt(level, player, target, DAISY_DAMAGE);
				Vec3 away = target.position().subtract(center);
				knock(target, away, 0.8, 0.4);     // léger recul
				hitFx(level, target, true);
			}
		}

		void dispose() {
			for (Blade b : blades) {
				b.visual.remove();
			}
			blades.clear();
		}
	}

	// ------------------------------------------------------------------ 2. Spiral Hollow

	private static final class Spin {
		final ServerLevel level;
		final UUID owner;
		final Vec3 dir;
		final SupaVisual visual;
		final Map<UUID, int[]> targets = new HashMap<>();   // [coups portés, dernier tick]
		Vec3 last;
		float yaw = 0.0F;
		int age = 0;
		int ending = -1;

		Spin(ServerLevel level, UUID owner, Vec3 dir, Vec3 start) {
			this.level = level;
			this.owner = owner;
			this.dir = dir;
			this.last = start;
			// Toupie de lames autour du corps : 4 étages (jambes, taille, bras, épaules), qui grandit puis tourne.
			this.visual = SupaVisual.create(level, "supa_whirl", start, 0.0F, 0.0F, 0.3, 0.3, 0.3, 0.5 * 0.3, 1);
			this.visual.scaleTo(1.0, 1.0, 1.0, 0.5, 3);
		}

		boolean tick(MinecraftServer server) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player == null || !player.isAlive() || player.isSpectator() || !isSupa(player)) {
				return false;
			}
			Vec3 pos = player.position();
			if (ending >= 0) {
				// Retour à la normale : la toupie rétrécit puis disparaît.
				visual.move(pos, yaw, 0.0F);
				ending++;
				return ending < 4;
			}

			// Au sol, une visée vers le bas devient une charge horizontale (sinon le sol arrêterait la charge aussitôt).
			Vec3 move = dir;
			if (dir.y < 0.0 && player.onGround()) {
				Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
				if (flat.lengthSqr() > 1.0E-4) {
					move = flat.normalize();
				}
			}
			yaw += 40.0F;
			visual.move(pos.add(move.scale(SPIRAL_SPEED)), yaw, 0.0F);

			// Fin : durée maximale, ou obstacle devant (un mur arrête la charge, le joueur ne le traverse jamais).
			boolean blocked = false;
			double look = SPIRAL_SPEED + 0.7;
			for (double h : new double[] {0.2, 0.9, 1.6}) {
				if (isSolid(level, pos.x + move.x * look, pos.y + h + Math.max(0.0, move.y) * look, pos.z + move.z * look)) {
					blocked = true;
				}
			}
			boolean stalled = age >= 4 && pos.distanceTo(last) < 0.2;
			if (age >= SPIRAL_TICKS || blocked || stalled) {
				stop(player);
				return true;
			}

			// Ruée : le serveur impose la vitesse (le client la reçoit ; un mur l'arrête).
			player.setDeltaMovement(move.x * SPIRAL_SPEED, move.y * SPIRAL_SPEED, move.z * SPIRAL_SPEED);
			player.fallDistance = 0;
			player.needsSync = true;

			// Lames : tout ennemi sur le trajet (hitbox balayée depuis le tick précédent, rayon 1,5).
			AABB sweep = player.getBoundingBox().expandTowards(last.subtract(pos)).inflate(SPIRAL_RADIUS, 0.5, SPIRAL_RADIUS);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep,
					x -> x != player && x.isAlive() && !x.isSpectator())) {
				int[] st = targets.computeIfAbsent(e.getUUID(), k -> new int[] {0, -SPIRAL_INTERVAL});
				if (st[0] < SPIRAL_MAX_HITS && age - st[1] >= SPIRAL_INTERVAL) {
					st[0]++;
					st[1] = age;
					hurt(level, player, e, SPIRAL_DAMAGE);
					knock(e, move, 0.25, 0.1);
					hitFx(level, e, false);
				}
			}

			// Traînée blanche et argentée, éclats de métal, sifflement des lames.
			Vec3 c = pos.add(0, 0.9, 0);
			Vec3 back = move.scale(-1.0);
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 0, back.x, back.y, back.z, 0.25);
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 3, 0.5, 0.7, 0.5, 0.2);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 2, 0.5, 0.6, 0.5, 0.3);
			level.sendParticles(WHITE, c.x, c.y, c.z, 2, 0.5, 0.7, 0.5, 0.0);
			level.sendParticles(SILVER, c.x + back.x, c.y + back.y, c.z + back.z, 2, 0.4, 0.6, 0.4, 0.0);
			if (age % 5 == 0) {
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.3F + (age % 10) * 0.02F);
			}
			last = pos;
			age++;
			return true;
		}

		private void stop(ServerPlayer player) {
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
			player.needsSync = true;
			ending = 0;
			visual.scaleTo(0.3, 0.3, 0.3, 0.5 * 0.3, 3);
			Vec3 c = player.position().add(0, 0.9, 0);
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 8, 0.4, 0.5, 0.4, 0.05);
			level.sendParticles(WHITE, c.x, c.y, c.z, 6, 0.4, 0.5, 0.4, 0.0);
		}

		void dispose() {
			visual.remove();
		}
	}

	// ------------------------------------------------------------------ 3. Atomic Spada

	private static final class Spada {
		final ServerLevel level;
		final UUID owner;
		final SupaVisual arm;
		final Set<UUID> hit = new HashSet<>();
		SupaVisual wave;
		Vec3 wavePos;
		Vec3 waveDir;
		double travelled = 0.0;
		boolean waveDone = false;
		int waveEnd = 0;
		float waveYaw;
		int age = 0;
		boolean raised = true;

		Spada(ServerLevel level, ServerPlayer player) {
			this.level = level;
			this.owner = player.getUUID();
			setRaisedHand(player, true);
			// Bras-lame : petit au départ, il grandit pendant la préparation.
			this.arm = SupaVisual.create(level, "supa_arm", shoulder(player), player.getYRot() + 100.0F, -55.0F,
					0.4, 0.4, 0.4, 0.0, 1);
			this.arm.scaleTo(ARM_SCALE, ARM_SCALE, ARM_SCALE, 0.0, 8);
		}

		private static Vec3 shoulder(ServerPlayer p) {
			double yaw = Math.toRadians(p.getYRot());
			// Droite du joueur : (-cos, -sin) pour le yaw Minecraft.
			return p.position().add(-Math.cos(yaw) * 0.4, 1.35, -Math.sin(yaw) * 0.4);
		}

		boolean tick(MinecraftServer server) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player == null || !player.isAlive() || player.isSpectator() || !isSupa(player)) {
				return false;
			}
			if (age < SPADA_PREP) {
				// Préparation : le bras-lame se recule légèrement, des éclats scintillent le long de la lame.
				float back = 100.0F + 25.0F * age / SPADA_PREP;
				arm.move(shoulder(player), player.getYRot() + back, -55.0F);
				if (age % 2 == 0) {
					Vec3 tip = shoulder(player).add(0, 1.2, 0);
					level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 1, 0.3, 0.5, 0.3, 0.02);
					level.sendParticles(BLUE, tip.x, tip.y, tip.z, 1, 0.3, 0.4, 0.3, 0.0);
				}
				if (age == SPADA_PREP / 2) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8F, 1.6F);
				}
				age++;
				return true;
			}

			int k = age - SPADA_PREP;
			if (k == 0) {
				launchWave(player);
			}
			if (k < SPADA_SWING) {
				// Coup horizontal : le bras-lame balaie de la droite vers la gauche.
				float t = (k + 1) / (float) SPADA_SWING;
				float yaw = player.getYRot() + 125.0F - t * 235.0F;
				float pitch = -55.0F + t * 55.0F;
				arm.move(shoulder(player), yaw, pitch);
			} else if (k == SPADA_SWING + 2) {
				arm.scaleTo(0.3, 0.3, 0.3, 0.0, 3);
			}
			if (raised && k >= 1) {
				setRaisedHand(player, false);
				raised = false;
			}

			boolean waveAlive = false;
			if (wave != null && !waveDone) {
				waveAlive = tickWave(player);
				if (!waveAlive) {
					waveDone = true;
					waveEnd = age;
				}
			}
			if (waveDone && age == waveEnd + 2) {
				wave.remove();
			}
			age++;
			return waveAlive || k < SPADA_SWING + 5;
		}

		private void launchWave(ServerPlayer player) {
			double yaw = Math.toRadians(player.getYRot());
			waveDir = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
			waveYaw = player.getYRot();
			wavePos = player.position().add(0, 1.5, 0).add(waveDir.scale(0.8));
			wave = SupaVisual.create(level, "supa_wave", wavePos, waveYaw, 0.0F, WAVE_SCALE * 0.5, WAVE_SCALE * 0.5, WAVE_SCALE * 0.5, 0.0, 1);
			wave.scaleTo(WAVE_SCALE, WAVE_SCALE, WAVE_SCALE, 0.0, 2);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.6F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 1.6F);
			Vec3 c = wavePos.add(waveDir.scale(0.8));
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 10, 0.6, 0.6, 0.6, 0.15);
		}

		/** Avance l'onde de coupe de 1,5 bloc : dégâts sur tout ce qu'elle traverse (une seule fois par cible). */
		private boolean tickWave(ServerPlayer player) {
			Vec3 next = wavePos.add(waveDir.scale(SPADA_SPEED));
			// Un mur arrête l'onde (elle ne traverse pas les blocs et n'en détruit aucun).
			if (isSolid(level, next.x, next.y, next.z) || isSolid(level, next.x, next.y - 0.9, next.z)) {
				burst(wavePos);
				return false;
			}
			wavePos = next;
			travelled += SPADA_SPEED;
			wave.move(wavePos, waveYaw, 0.0F);

			Vec3 center = wavePos.add(waveDir.scale(1.0));
			Vec3 right = new Vec3(-waveDir.z, 0.0, waveDir.x);
			double halfDepth = SPADA_SPEED * 0.5 + 0.9;
			AABB box = new AABB(center, center).inflate(SPADA_WIDTH / 2.0 + 1.5, SPADA_HEIGHT, SPADA_WIDTH / 2.0 + 1.5);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
					x -> x != player && x.isAlive() && !x.isSpectator())) {
				Vec3 rel = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(center);
				double f = rel.dot(waveDir);
				double s = rel.dot(right);
				double dy = e.getY() + e.getBbHeight() - (wavePos.y - SPADA_HEIGHT * 0.5);
				boolean inY = dy >= 0.0 && e.getY() <= wavePos.y + SPADA_HEIGHT * 0.5;
				if (Math.abs(f) <= halfDepth && Math.abs(s) <= SPADA_WIDTH / 2.0 + e.getBbWidth() * 0.5 && inY
						&& hit.add(e.getUUID())) {
					hurt(level, player, e, SPADA_DAMAGE);
					knock(e, waveDir, SPADA_KNOCKBACK, 0.45);        // recul important
					hitFx(level, e, true);
				}
			}

			// Croissant de lune blanc et argenté qui avance.
			for (int i = -4; i <= 4; i++) {
				double side = i * (SPADA_WIDTH / 2.0) / 4.0;
				double curve = 1.0 - Math.abs(i) / 4.0;            // le centre du croissant est en avant
				Vec3 p = wavePos.add(right.scale(side)).add(waveDir.scale(curve * 1.4));
				level.sendParticles(i % 2 == 0 ? WHITE : BLUE, p.x, p.y, p.z, 1, 0.1, 0.9, 0.1, 0.0);
				if (i % 2 == 0) {
					level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, -waveDir.x, 0.0, -waveDir.z, 0.2);
				}
			}
			level.sendParticles(ParticleTypes.CRIT, wavePos.x, wavePos.y, wavePos.z, 4, 1.2, 0.8, 1.2, 0.2);
			if (travelled >= SPADA_RANGE) {
				burst(wavePos);
				return false;
			}
			return true;
		}

		private void burst(Vec3 at) {
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 8, 0.8, 0.6, 0.8, 0.1);
			level.sendParticles(WHITE, at.x, at.y, at.z, 10, 1.0, 0.8, 1.0, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.9F);
			wave.scaleTo(WAVE_SCALE * 0.2, WAVE_SCALE * 0.2, WAVE_SCALE * 0.2, 0.0, 2);
		}

		void dispose(ServerPlayer player) {
			arm.remove();
			if (wave != null) {
				wave.remove();
			}
			if (player != null && raised) {
				setRaisedHand(player, false);
			}
		}
	}
}
