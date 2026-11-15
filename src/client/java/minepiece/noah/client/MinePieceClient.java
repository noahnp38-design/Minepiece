package minepiece.noah.client;

import minepiece.noah.client.render.RaceRendering;
import minepiece.noah.client.title.TitleBackground;
import minepiece.noah.network.OpenSelectionPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MinePieceClient implements ClientModInitializer {
	/** Mis à true à la réception du paquet ; l'écran s'ouvre dès que plus aucun autre écran n'est affiché. */
	private static boolean pendingOpen = false;

	@Override
	public void onInitializeClient() {
		RaceRendering.init();
		TitleBackground.init();
		AbilityKeys.init();
		ProfileKey.init();
		RaceMovement.init();

		ClientPlayNetworking.registerGlobalReceiver(OpenSelectionPayload.TYPE,
				(payload, context) -> pendingOpen = true);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingOpen && client.player != null && client.gui.screen() == null) {
				pendingOpen = false;
				client.gui.setScreen(new SelectionScreen());
			}
		});
	}
}
