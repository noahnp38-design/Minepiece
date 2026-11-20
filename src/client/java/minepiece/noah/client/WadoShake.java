package minepiece.noah.client;

import java.util.concurrent.ThreadLocalRandom;

import minepiece.noah.network.WadoShakePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Tremblement de caméra (impacts du Zushi Zushi no Mi) : quelques ticks de petites secousses de la vue, qui s'atténuent.
 * Chaque secousse est annulée au tick suivant : la visée n'est donc jamais décalée durablement.
 */
public final class WadoShake {
	private static int total = 1;
	private static int left = 0;
	private static int intensity = 0;
	private static float offYaw = 0.0F;
	private static float offPitch = 0.0F;

	private WadoShake() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WadoShakePayload.TYPE, (payload, context) -> {
			total = Math.max(1, payload.ticks());
			left = total;
			intensity = payload.intensity();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			var player = client.player;
			if (player == null) {
				left = 0;
				offYaw = 0.0F;
				offPitch = 0.0F;
				return;
			}
			// On retire d'abord la secousse précédente.
			if (offYaw != 0.0F || offPitch != 0.0F) {
				player.setYRot(player.getYRot() - offYaw);
				player.setXRot(player.getXRot() - offPitch);
				offYaw = 0.0F;
				offPitch = 0.0F;
			}
			if (left > 0) {
				float fade = left / (float) total;
				ThreadLocalRandom rnd = ThreadLocalRandom.current();
				offYaw = (rnd.nextFloat() - 0.5F) * 2.0F * intensity * 0.5F * fade;
				offPitch = (rnd.nextFloat() - 0.5F) * 2.0F * intensity * 0.5F * fade;
				player.setYRot(player.getYRot() + offYaw);
				player.setXRot(player.getXRot() + offPitch);
				left--;
			}
		});
	}
}
