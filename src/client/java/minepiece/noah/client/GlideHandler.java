package minepiece.noah.client;

import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Planeur des Skypiéens et des Hommes (côté client : le client décide du mouvement de son joueur).
 * En l'air, en tombant, tant qu'on maintient Espace : la chute est ralentie et on avance dans la
 * direction où on regarde. Le serveur fait de son côté les particules, les sons et supprime les
 * dégâts de chute (RacePowers.tickGlide).
 */
public final class GlideHandler {
	/** Vitesse de chute maximale pendant le plané (blocs par tick). */
	private static final double MAX_FALL_SPEED = 0.06;
	/** Poussée vers l'avant à chaque tick. */
	private static final double FORWARD_PUSH = 0.035;
	/** Vitesse horizontale maximale (blocs par tick) : limite la distance parcourue. */
	private static final double MAX_HORIZONTAL_SPEED = 0.32;

	private GlideHandler() {}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LocalPlayer player = client.player;
			if (player == null || client.gui.screen() != null || player.isSpectator()) {
				return;
			}
			PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
			if (profile == null || !profile.race().canGlide()) {
				return;
			}
			// Mêmes conditions que RacePowers.tickGlide côté serveur.
			if (player.onGround() || player.isInWater() || player.isFallFlying()
					|| player.getAbilities().flying || player.isPassenger()) {
				return;
			}
			if (!client.options.keyJump.isDown()) {
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

			double x = motion.x + dirX * FORWARD_PUSH;
			double z = motion.z + dirZ * FORWARD_PUSH;
			double horizontal = Math.sqrt(x * x + z * z);
			if (horizontal > MAX_HORIZONTAL_SPEED) {
				x *= MAX_HORIZONTAL_SPEED / horizontal;
				z *= MAX_HORIZONTAL_SPEED / horizontal;
			}

			player.setDeltaMovement(x, Math.max(motion.y, -MAX_FALL_SPEED), z);
			player.fallDistance = Math.min(player.fallDistance, 2.0);
		});
	}
}
