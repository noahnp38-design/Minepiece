package minepiece.noah.ability.suke;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.data.Faction;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pouvoirs du Suke Suke no Mi (fruit de l'Invisibilité, Paramécia). Tout est décidé par le serveur.
 *  1. Disparition totale : invisible 10 s (corps ET équipement : la partie rendu est dans client/mixin).
 *  2. Frappe fantôme    : ruée invisible vers l'avant, 6 dégâts au premier ennemi touché.
 *  3. Voile invisible   : le joueur et ses alliés dans 6 blocs sont invisibles 8 s.
 * Les recharges (25 s / 12 s / 45 s) sont gérées par FruitAbilities (DevilFruit.cooldownTicks) et synchronisées au client.
 *
 * L'invisibilité utilise l'effet de potion vanilla (sans particules) : elle expire donc toute seule, et on retient ici
 * qui l'a reçue de ce pouvoir pour pouvoir la retirer proprement (annulation, mort, déconnexion, Malédiction du Démon).
 */
public final class SukeAbilities {
	// ---- Disparition totale
	private static final int VANISH_TICKS = 200;          // 10 s
	// ---- Frappe fantôme
	private static final float STRIKE_DAMAGE = 6.0F;
	private static final double STRIKE_SPEED = 1.5;       // blocs par tick
	private static final int STRIKE_MAX_TICKS = 6;        // ~9 blocs
	private static final double STRIKE_REACH = 0.9;       // marge de la hitbox balayée
	private static final double STRIKE_KNOCKBACK = 0.7;
	// ---- Voile invisible
	private static final int VEIL_TICKS = 160;            // 8 s
	private static final double VEIL_RADIUS = 6.0;
	private static final int VEIL_REFRESH = 10;           // les alliés sont ré-invisibilisés tous les 10 ticks...
	private static final int VEIL_ALLY_TICKS = 30;        // ...pour 1,5 s : sortir de la zone fait réapparaître

	private static final DustParticleOptions TURQUOISE = new DustParticleOptions(0x40E0D0, 0.9F);
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xF2FFFF, 0.8F);

	/** Joueurs à qui CE pouvoir a donné l'invisibilité, et jusqu'à quand (game time). */
	private static final Map<UUID, Long> INVISIBLE_UNTIL = new HashMap<>();
	/** Lanceurs de Voile invisible actifs, et jusqu'à quand. */
	private static final Map<UUID, Long> VEIL_UNTIL = new HashMap<>();
	private static final Map<UUID, Dash> DASHES = new HashMap<>();

	private static final class Dash {
		final Vec3 dir;
		Vec3 last;
		int ticks;
		final boolean grantedInvisibility;

		Dash(Vec3 dir, Vec3 last, boolean grantedInvisibility) {
			this.dir = dir;
			this.last = last;
			this.grantedInvisibility = grantedInvisibility;
		}
	}

	private SukeAbilities() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SukeAbilities::tick);
		// Déconnexion : on retire l'invisibilité (sinon elle serait rendue à la reconnexion) et on oublie le joueur.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.getPlayer()));
	}

	// ------------------------------------------------------------------ lancement

	/** @return true si l'attaque démarre (la recharge est alors lancée par FruitAbilities). */
	public static boolean cast(ServerPlayer player, ServerLevel level, int slot) {
		return switch (slot) {
			case 0 -> vanish(player, level);
			case 1 -> phantomStrike(player, level);
			case 2 -> veil(player, level);
			default -> false;
		};
	}

	/** Coupe proprement tout (Malédiction du Démon, déconnexion...). Le fruit et les recharges ne sont pas touchés. */
	public static void cancel(ServerPlayer player) {
		UUID id = player.getUUID();
		Dash dash = DASHES.remove(id);
		if (dash != null) {
			player.setDeltaMovement(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0);
			player.needsSync = true;
		}
		boolean had = INVISIBLE_UNTIL.remove(id) != null;
		boolean veil = VEIL_UNTIL.remove(id) != null;
		if ((had || veil || (dash != null && dash.grantedInvisibility)) && player.hasEffect(MobEffects.INVISIBILITY)) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	/** Vrai si l'invisibilité venant de ce pouvoir est encore active pour ce joueur. */
	private static boolean invisibleNow(UUID id, long now) {
		Long until = INVISIBLE_UNTIL.get(id);
		return until != null && until > now;
	}

	private static void grantInvisibility(ServerPlayer player, int ticks, long now) {
		// Sans particules (visible=false) mais avec l'icône dans la liste des effets.
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
		INVISIBLE_UNTIL.merge(player.getUUID(), now + ticks, Math::max);
	}

	// ------------------------------------------------------------------ 1. Disparition totale

	private static boolean vanish(ServerPlayer player, ServerLevel level) {
		grantInvisibility(player, VANISH_TICKS, level.getGameTime());
		discreetBurst(level, player.position().add(0, 1.0, 0), 1.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
				SoundSource.PLAYERS, 0.7F, 1.5F);
		return true;
	}

	// ------------------------------------------------------------------ 2. Frappe fantôme

	private static boolean phantomStrike(ServerPlayer player, ServerLevel level) {
		if (DASHES.containsKey(player.getUUID())) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();

		long now = level.getGameTime();
		// Invisible pendant la ruée ; si une autre invisibilité est déjà active, on n'y touche pas.
		boolean already = invisibleNow(player.getUUID(), now) || player.hasEffect(MobEffects.INVISIBILITY);
		if (!already) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, true));
		}
		DASHES.put(player.getUUID(), new Dash(dir, player.position(), !already));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL,
				SoundSource.PLAYERS, 0.6F, 1.8F);
		return true;
	}

	/** Un tick de ruée. @return false quand elle est terminée. */
	private static boolean tickDash(ServerPlayer player, ServerLevel level, Dash dash) {
		Vec3 pos = player.position();
		Vec3 moved = pos.subtract(dash.last);

		// Hitbox balayée sur le trajet depuis le tick précédent : aucun ennemi traversé sans être touché.
		AABB sweep = player.getBoundingBox().expandTowards(dash.last.subtract(pos)).inflate(STRIKE_REACH, 0.4, STRIKE_REACH);
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep,
				x -> x != player && x.isAlive() && !x.isSpectator() && !isAlly(player, x))) {
			double d = e.distanceToSqr(player);
			if (d < best) {
				best = d;
				target = e;
			}
		}
		if (target != null) {
			strike(player, level, target, dash.dir);
			return false;
		}

		boolean blocked = dash.ticks >= 2 && moved.horizontalDistance() < 0.15;
		if (dash.ticks >= STRIKE_MAX_TICKS || blocked) {
			return false;
		}
		// Ruée : on impose la vitesse (le client la reçoit ; un mur l'arrête).
		double vy = player.onGround() ? 0.0 : Math.min(0.0, player.getDeltaMovement().y);
		player.setDeltaMovement(dash.dir.x * STRIKE_SPEED, vy, dash.dir.z * STRIKE_SPEED);
		player.needsSync = true;
		level.sendParticles(WHITE, pos.x, pos.y + 1.0, pos.z, 2, 0.15, 0.4, 0.15, 0.0);
		dash.last = pos;
		dash.ticks++;
		return true;
	}

	private static void strike(ServerPlayer player, ServerLevel level, LivingEntity target, Vec3 dir) {
		DamageSource source = level.damageSources().playerAttack(player);
		target.hurtServer(level, source, STRIKE_DAMAGE);

		// Léger recul dans le sens de la ruée.
		target.setDeltaMovement(target.getDeltaMovement().add(dir.x * STRIKE_KNOCKBACK, 0.25, dir.z * STRIKE_KNOCKBACK));
		target.needsSync = true;

		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(TURQUOISE, c.x, c.y, c.z, 22, 0.35, 0.4, 0.35, 0.05);
		level.sendParticles(WHITE, c.x, c.y, c.z, 14, 0.3, 0.35, 0.3, 0.04);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 8, 0.25, 0.3, 0.25, 0.08);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
	}

	/** Fin de la ruée : on arrête le joueur et on retire l'invisibilité donnée par la ruée (pas les autres). */
	private static void endDash(ServerPlayer player, Dash dash) {
		player.setDeltaMovement(0.0, player.onGround() ? 0.0 : Math.min(0.0, player.getDeltaMovement().y), 0.0);
		player.needsSync = true;
		if (dash.grantedInvisibility && !invisibleNow(player.getUUID(), player.level().getGameTime())) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	// ------------------------------------------------------------------ 3. Voile invisible

	private static boolean veil(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		grantInvisibility(player, VEIL_TICKS, now);
		VEIL_UNTIL.put(player.getUUID(), now + VEIL_TICKS);
		applyVeil(player, level);

		// Anneau translucide autour de la zone, bref.
		Vec3 c = player.position();
		for (int i = 0; i < 36; i++) {
			double a = i * (Math.PI * 2 / 36);
			level.sendParticles(i % 2 == 0 ? TURQUOISE : WHITE, c.x + Math.cos(a) * VEIL_RADIUS, c.y + 0.3,
					c.z + Math.sin(a) * VEIL_RADIUS, 1, 0.05, 0.25, 0.05, 0.0);
		}
		discreetBurst(level, c.add(0, 1.0, 0), 1.4);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.8F, 1.2F);
		return true;
	}

	private static void applyVeil(ServerPlayer caster, ServerLevel level) {
		AABB zone = caster.getBoundingBox().inflate(VEIL_RADIUS);
		List<ServerPlayer> nearby = level.getEntitiesOfClass(ServerPlayer.class, zone,
				p -> p != caster && p.isAlive() && !p.isSpectator()
						&& p.distanceToSqr(caster) <= VEIL_RADIUS * VEIL_RADIUS && isAlly(caster, p));
		for (ServerPlayer ally : nearby) {
			ally.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, VEIL_ALLY_TICKS, 0, false, false, true));
		}
	}

	/** Alliés : même équipe (scoreboard) ou même faction (sauf les civils, qui n'ont pas de camp). */
	private static boolean isAlly(ServerPlayer caster, LivingEntity other) {
		if (!(other instanceof ServerPlayer p)) {
			return false;
		}
		if (caster.isAlliedTo(p)) {
			return true;
		}
		PlayerProfile a = caster.getAttached(ModAttachments.PROFILE);
		PlayerProfile b = p.getAttached(ModAttachments.PROFILE);
		return a != null && b != null && a.faction() == b.faction() && a.faction() != Faction.CIVILIAN;
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer server) {
		if (INVISIBLE_UNTIL.isEmpty() && VEIL_UNTIL.isEmpty() && DASHES.isEmpty()) {
			return;
		}
		// Ruées
		Iterator<Map.Entry<UUID, Dash>> dashes = DASHES.entrySet().iterator();
		while (dashes.hasNext()) {
			Map.Entry<UUID, Dash> entry = dashes.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				dashes.remove();
				continue;
			}
			if (!tickDash(player, level, entry.getValue())) {
				endDash(player, entry.getValue());
				dashes.remove();
			}
		}

		// Voiles actifs : les alliés proches restent invisibles ; fin automatique après 8 s.
		Iterator<Map.Entry<UUID, Long>> veils = VEIL_UNTIL.entrySet().iterator();
		while (veils.hasNext()) {
			Map.Entry<UUID, Long> entry = veils.next();
			ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
			if (caster == null || !caster.isAlive() || !(caster.level() instanceof ServerLevel level)
					|| level.getGameTime() >= entry.getValue()) {
				veils.remove();
				continue;
			}
			if (level.getGameTime() % VEIL_REFRESH == 0) {
				applyVeil(caster, level);
			}
		}

		// Invisibilités personnelles : on oublie celles qui sont finies (l'effet vanilla expire de lui-même).
		Iterator<Map.Entry<UUID, Long>> inv = INVISIBLE_UNTIL.entrySet().iterator();
		while (inv.hasNext()) {
			Map.Entry<UUID, Long> entry = inv.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level().getGameTime() >= entry.getValue()) {
				inv.remove();
			}
		}
	}

	// ------------------------------------------------------------------ effets

	/** Quelques particules bleues très discrètes. */
	private static void discreetBurst(ServerLevel level, Vec3 c, double spread) {
		level.sendParticles(TURQUOISE, c.x, c.y, c.z, 10, 0.3 * spread, 0.5 * spread, 0.3 * spread, 0.01);
		level.sendParticles(WHITE, c.x, c.y, c.z, 4, 0.25 * spread, 0.45 * spread, 0.25 * spread, 0.01);
	}
}
