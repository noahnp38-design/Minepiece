package minepiece.noah.client;

import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import minepiece.noah.data.Race;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Mouvements des races, côté client (le client décide du mouvement de son propre joueur) :
 *  - vol des Skypiéens / Lunarien : décollage, et on monte / descend en regardant en haut / en bas ;
 *  - planeur (Skypiéens / Lunarien) : en tombant, maintenir Espace ralentit la chute et fait avancer ;
 *  - nage rapide de l'Homme-Poisson, dans les 3 directions.
 * Le serveur valide et complète (RacePowers : endurance de vol, dégâts de chute, souffle, particules).
 */
public final class RaceMovement {
	// ---- Vol
	/** Impulsion vers le haut au décollage. */
	private static final double TAKE_OFF_BOOST = 0.35;
	/** Vitesse à laquelle la montée / descente suit l'angle du regard (0 = jamais, 1 = instantané). */
	private static final double PITCH_FOLLOW = 0.18;
	/** Vitesse verticale maximale due à l'angle du regard (blocs par tick). */
	private static final double MAX_PITCH_CLIMB = 0.9;

	// ---- Planeur
	/** Vitesse de chute maximale pendant le plané (blocs par tick). */
	private static final double GLIDE_MAX_FALL = 0.06;
	/** Poussée vers l'avant à chaque tick. */
	private static final double GLIDE_PUSH = 0.035;
	/** Vitesse horizontale maximale en plané (blocs par tick) : limite la distance parcourue. */
	private static final double GLIDE_MAX_SPEED = 0.32;

	// ---- Nage de l'Homme-Poisson. Un joueur normal nage à ~0.1 bloc par tick ; ici on atteint ~0.3 (x3), ~0.55 en sprint.
	// (La résistance de l'eau freine un peu : la vitesse réelle est inférieure à la vitesse "visée".)
	private static final double SWIM_SPEED = 0.50;
	private static final double SWIM_SPRINT_SPEED = 0.70;
	/** Vitesse à laquelle on rejoint la vitesse de nage visée (0 = jamais, 1 = instantané). */
	private static final double SWIM_RESPONSE = 0.50;

	private static boolean wasFlying;

	private RaceMovement() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(RaceMovement::tick);
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || player.isSpectator()) {
			wasFlying = false;
			return;
		}
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
		if (profile == null) {
			wasFlying = false;
			return;
		}

		Race race = profile.race();
		boolean noScreen = client.gui.screen() == null; // pas de commandes quand un menu est ouvert

		if (race.canFly()) {
			flight(player, client.options, noScreen);
		} else {
			wasFlying = false;
		}
		if (race.canGlide() && noScreen) {
			glide(player, client.options);
		}
		if (race == Race.FISHMAN && noScreen) {
			swim(player, client.options);
		}
	}

	// ------------------------------------------------------------------ vol

	private static void flight(LocalPlayer player, Options options, boolean noScreen) {
		boolean flying = player.getAbilities().flying;
		boolean survival = !player.isCreative();

		// Décollage : petite poussée vers le haut quand le vol commence.
		if (flying && !wasFlying && survival) {
			Vec3 m = player.getDeltaMovement();
			player.setDeltaMovement(m.x, Math.max(m.y, 0.0) + TAKE_OFF_BOOST, m.z);
		}
		wasFlying = flying;

		if (!flying || !survival || !noScreen) {
			return;
		}

		// Vol naturel : en avançant, le regard dirige la montée / la descente (comme un oiseau).
		// Espace et Maj gardent leur rôle (monter / descendre sur place).
		if (options.keyUp.isDown() && !options.keyJump.isDown() && !options.keyShift.isDown()) {
			Vec3 look = player.getLookAngle();
			double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
			if (lookHorizontal < 0.05) {
				return; // regarde presque droit vers le haut / bas : pas de direction claire
			}
			Vec3 m = player.getDeltaMovement();
			double speed = Math.sqrt(m.x * m.x + m.z * m.z);
			double target = clamp(speed * look.y / lookHorizontal, -MAX_PITCH_CLIMB, MAX_PITCH_CLIMB);
			player.setDeltaMovement(m.x, m.y + (target - m.y) * PITCH_FOLLOW, m.z);
		}
	}

	// ------------------------------------------------------------------ planeur

	private static void glide(LocalPlayer player, Options options) {
		// Mêmes conditions que RacePowers.tickGlide côté serveur.
		if (player.onGround() || player.isInWater() || player.isFallFlying()
				|| player.getAbilities().flying || player.isPassenger()) {
			return;
		}
		if (!options.keyJump.isDown()) {
			return;
		}
		Vec3 motion = player.getDeltaMovement();
		if (motion.y >= 0.0) {
			return; // on monte encore (saut) : pas de plané
		}

		// Direction horizontale du regard
		Vec3 look = player.getLookAngle();
		double length = Math.sqrt(look.x * look.x + look.z * look.z);
		double dirX = length > 1.0E-4 ? look.x / length : 0.0;
		double dirZ = length > 1.0E-4 ? look.z / length : 0.0;

		double x = motion.x + dirX * GLIDE_PUSH;
		double z = motion.z + dirZ * GLIDE_PUSH;
		double horizontal = Math.sqrt(x * x + z * z);
		if (horizontal > GLIDE_MAX_SPEED) {
			x *= GLIDE_MAX_SPEED / horizontal;
			z *= GLIDE_MAX_SPEED / horizontal;
		}

		player.setDeltaMovement(x, Math.max(motion.y, -GLIDE_MAX_FALL), z);
		player.fallDistance = Math.min(player.fallDistance, 2.0);
	}

	// ------------------------------------------------------------------ nage

	private static void swim(LocalPlayer player, Options options) {
		if (!player.isInWater() || player.getAbilities().flying || player.isFallFlying() || player.isPassenger()) {
			return;
		}

		double forward = axis(options.keyUp.isDown(), options.keyDown.isDown());
		double strafe = axis(options.keyRight.isDown(), options.keyLeft.isDown());
		double vertical = axis(options.keyJump.isDown(), options.keyShift.isDown());
		if (forward == 0.0 && strafe == 0.0 && vertical == 0.0) {
			return; // aucune touche : le jeu freine le joueur normalement
		}

		// Avant / arrière : dans la direction du regard (haut et bas compris) ; côtés : à l'horizontale.
		Vec3 look = player.getLookAngle();
		double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		double rightX = lookHorizontal > 1.0E-4 ? -look.z / lookHorizontal : 0.0;
		double rightZ = lookHorizontal > 1.0E-4 ? look.x / lookHorizontal : 0.0;

		double dx = look.x * forward + rightX * strafe;
		double dy = look.y * forward + vertical;
		double dz = look.z * forward + rightZ * strafe;

		// En surface, on ne se propulse pas hors de l'eau.
		if (!player.isUnderWater() && dy > 0.0) {
			dy = 0.0;
		}

		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-4) {
			return;
		}
		double speed = options.keySprint.isDown() ? SWIM_SPRINT_SPEED : SWIM_SPEED;
		double scale = speed / length;
		Vec3 target = new Vec3(dx * scale, dy * scale, dz * scale);

		Vec3 motion = player.getDeltaMovement();
		if (motion.length() >= speed) {
			return; // déjà aussi vite (ex. courant, saut dans l'eau) : on ne freine pas
		}
		player.setDeltaMovement(
				motion.x + (target.x - motion.x) * SWIM_RESPONSE,
				motion.y + (target.y - motion.y) * SWIM_RESPONSE,
				motion.z + (target.z - motion.z) * SWIM_RESPONSE);
	}

	// ------------------------------------------------------------------ outils

	private static double axis(boolean positive, boolean negative) {
		return (positive ? 1.0 : 0.0) - (negative ? 1.0 : 0.0);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
