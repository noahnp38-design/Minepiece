package minepiece.noah.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import minepiece.noah.data.Race;
import minepiece.noah.data.RaceEffects;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Abilities;

/**
 * Pouvoirs ACTIFS des races, exécutés par le serveur (donc valables en multijoueur) :
 *  - Skypiéen et Lunarien : vol libre à endurance limitée, puis planeur ;
 *  - Mink : bonus de vitesse seulement hors de l'eau ;
 *  - Homme-Poisson : respiration infinie sous l'eau, bulles quand il nage vite.
 *
 * Le côté client du vol, du planeur et de la nage rapide est dans RaceMovement (client).
 * Le serveur supprime les dégâts de chute pendant le plané, fait les particules et les sons,
 * et publie "ce joueur vole" (ModAttachments.FLYING) pour que tout le monde voie les ailes battre.
 */
public final class RacePowers {
	/** Vitesse de vol normale du jeu (0.05) et celle des races volantes (un peu plus rapide). */
	private static final float DEFAULT_FLY_SPEED = 0.05F;
	private static final float RACE_FLY_SPEED = 0.06F;

	/** Après épuisement, le vol revient quand l'endurance remonte à ce niveau (100 = 5 secondes). */
	private static final int FLIGHT_RECOVER = 100;
	/** Recharge par tick : au sol / en l'air sans voler. */
	private static final int RECHARGE_GROUND = 4;
	private static final int RECHARGE_AIR = 1;

	/** Pendant le plané, la distance de chute comptée est plafonnée : pas de dégâts à l'atterrissage. */
	private static final double GLIDE_FALL_CAP = 2.0;
	private static final double GLIDE_MIN_FALL = 0.5;

	/**
	 * Endurance de vol d'une race, en ticks (20 ticks = 1 seconde). 0 = pas de vol.
	 * Pour un vol illimité, mets une très grande valeur (par ex. 1_000_000).
	 */
	private static int maxEnergy(Race race) {
		return switch (race) {
			case SKYPIEAN -> 800;  // 40 secondes
			case LUNARIAN -> 600;  // 30 secondes
			default -> 0;
		};
	}

	/** État temporaire d'un joueur (pas sauvegardé : il repart de zéro à chaque connexion). */
	private static final class PowerState {
		final int max;
		int energy;
		boolean exhausted;
		boolean flightGranted;

		PowerState(int max) {
			this.max = max;
			this.energy = max;
		}
	}

	private static final Map<UUID, PowerState> STATES = new HashMap<>();

	private RacePowers() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				STATES.remove(handler.getPlayer().getUUID()));
	}

	/**
	 * Supprime immédiatement tous les pouvoirs en cours (appelée quand la race change).
	 * Les pouvoirs de la nouvelle race repartent au tick suivant.
	 */
	public static void reset(ServerPlayer player) {
		PowerState state = STATES.remove(player.getUUID());
		if (state != null && state.flightGranted) {
			revokeFlight(player);
		}
		setFlyingFlag(player, false);
	}

	// ------------------------------------------------------------------ boucle principale

	private static void tick(ServerPlayer player) {
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
		Race race = profile == null ? null : profile.race();
		if (race == null) {
			return;
		}

		if (race.canFly()) {
			tickFlight(player, race);
		}
		if (race.canGlide()) {
			tickGlide(player);
		}
		switch (race) {
			case MINK -> RaceEffects.updateLandBonuses(player, race);
			case FISHMAN -> tickFishman(player);
			default -> { }
		}
	}

	// ------------------------------------------------------------------ Skypiéen et Lunarien : vol

	private static void tickFlight(ServerPlayer player, Race race) {
		Abilities abilities = player.getAbilities();

		// Publie l'état de vol pour les animations d'ailes (seulement quand il change).
		setFlyingFlag(player, abilities.flying && !player.isSpectator());

		// En créatif / spectateur, c'est le jeu qui gère le vol.
		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		PowerState state = STATES.computeIfAbsent(player.getUUID(), id -> new PowerState(maxEnergy(race)));
		boolean flying = abilities.flying;

		if (flying) {
			state.energy = Math.max(0, state.energy - 1);
			if (player.tickCount % 8 == 0) {
				flapEffects(player, race);
			}
		} else if (state.energy < state.max) {
			state.energy = Math.min(state.max, state.energy + (player.onGround() ? RECHARGE_GROUND : RECHARGE_AIR));
		}

		if (state.energy <= 0) {
			state.exhausted = true;
		} else if (state.exhausted && state.energy >= Math.min(FLIGHT_RECOVER, state.max)) {
			state.exhausted = false;
		}

		// Le jeu synchronise lui-même ces capacités avec le client (onUpdateAbilities).
		boolean allowed = !state.exhausted;
		boolean speedWrong = allowed && abilities.getFlyingSpeed() != RACE_FLY_SPEED;
		if (abilities.mayfly != allowed || (!allowed && abilities.flying) || speedWrong) {
			abilities.mayfly = allowed;
			if (!allowed) {
				abilities.flying = false;
			} else {
				abilities.setFlyingSpeed(RACE_FLY_SPEED);
			}
			player.onUpdateAbilities();
		}
		state.flightGranted = allowed;

		// Jauge d'endurance dans la barre d'action
		if (player.tickCount % 5 == 0 && (flying || state.exhausted)) {
			player.sendSystemMessage(Component.literal(energyBar(state)), true);
		}
	}

	private static String energyBar(PowerState state) {
		int filled = Math.round(10f * state.energy / state.max);
		String color = state.exhausted ? "§c" : "§b";
		return "§fAiles : " + color + "|".repeat(filled) + "§8" + "|".repeat(10 - filled);
	}

	private static void flapEffects(ServerPlayer player, Race race) {
		if (player.level() instanceof ServerLevel level) {
			ParticleOptions particle = race == Race.LUNARIAN ? ParticleTypes.SMALL_FLAME : ParticleTypes.CLOUD;
			level.sendParticles(particle, player.getX(), player.getY() + 0.8, player.getZ(),
					2, 0.3, 0.2, 0.3, 0.01);
		}
		player.playSound(SoundEvents.PHANTOM_FLAP, 0.3F, race == Race.LUNARIAN ? 1.1F : 1.5F);
	}

	/** Retire le vol donné par la race (sauf en créatif / spectateur, où ce n'est pas à nous de le faire). */
	private static void revokeFlight(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		Abilities abilities = player.getAbilities();
		if (abilities.mayfly || abilities.flying || abilities.getFlyingSpeed() != DEFAULT_FLY_SPEED) {
			abilities.mayfly = false;
			abilities.flying = false;
			abilities.setFlyingSpeed(DEFAULT_FLY_SPEED);
			player.onUpdateAbilities();
		}
	}

	/** Met à jour le drapeau synchronisé "le joueur vole" uniquement s'il change (évite des paquets inutiles). */
	private static void setFlyingFlag(ServerPlayer player, boolean flying) {
		if (Boolean.TRUE.equals(player.getAttached(ModAttachments.FLYING)) != flying) {
			player.setAttached(ModAttachments.FLYING, flying);
		}
	}

	// ------------------------------------------------------------------ planeur

	private static void tickGlide(ServerPlayer player) {
		boolean airborne = !player.onGround() && !player.isInWater() && !player.isFallFlying()
				&& !player.getAbilities().flying && !player.isPassenger();
		if (!airborne || !player.getLastClientInput().jump() || player.fallDistance <= GLIDE_MIN_FALL) {
			return;
		}

		// Le joueur plane : pas de dégâts de chute.
		if (player.fallDistance > GLIDE_FALL_CAP) {
			player.fallDistance = GLIDE_FALL_CAP;
		}

		if (player.tickCount % 5 == 0 && player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(),
					1, 0.2, 0.1, 0.2, 0.0);
		}
		if (player.tickCount % 20 == 0) {
			player.playSound(SoundEvents.ELYTRA_FLYING, 0.35F, 1.3F);
		}
	}

	// ------------------------------------------------------------------ Homme-Poisson

	private static void tickFishman(ServerPlayer player) {
		// Respiration infinie : l'air est toujours au maximum, donc jamais de noyade.
		int maxAir = player.getMaxAirSupply();
		if (player.getAirSupply() < maxAir) {
			player.setAirSupply(maxAir);
		}

		if (player.tickCount % 6 == 0 && player.isSwimming() && player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + 0.5, player.getZ(),
					3, 0.25, 0.2, 0.25, 0.02);
		}
	}
}
