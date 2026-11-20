package minepiece.noah.client.crew;

import minepiece.noah.crew.CrewLogo;
import minepiece.noah.network.CrewActionPayload;
import minepiece.noah.network.CrewSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Dernier état de l'équipage reçu du serveur (affiché par l'onglet Équipage). */
public final class ClientCrew {
	private static CrewSyncPayload data;
	/** Dernière réponse du serveur (texte, réussite, numéro pour savoir si elle est nouvelle, heure). */
	private static String message;
	private static boolean messageOk;
	private static int messageId;
	private static long messageTime;

	private ClientCrew() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CrewSyncPayload.TYPE, (payload, context) -> data = payload);
		ClientPlayNetworking.registerGlobalReceiver(minepiece.noah.network.CrewMessagePayload.TYPE, (payload, context) -> {
			message = payload.text();
			messageOk = payload.ok();
			messageId++;
			messageTime = System.currentTimeMillis();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			data = null;
			message = null;
		});
	}

	/** null tant que le serveur n'a rien envoyé. */
	public static CrewSyncPayload get() {
		return data;
	}

	public static int messageId() {
		return messageId;
	}

	public static String message() {
		return message;
	}

	public static boolean messageOk() {
		return messageOk;
	}

	/** Message reçu il y a moins de ms millisecondes, sinon null. */
	public static String recentMessage(long ms) {
		return message != null && System.currentTimeMillis() - messageTime < ms ? message : null;
	}

	public static void send(CrewActionPayload action) {
		ClientPlayNetworking.send(action);
	}

	/** Dessine un logo 12 x 12 ; cell = taille d'un pixel du logo à l'écran. */
	public static void drawLogo(GuiGraphicsExtractor g, byte[] logo, int x, int y, int cell) {
		if (logo == null || logo.length != CrewLogo.CELLS) {
			return;
		}
		for (int i = 0; i < CrewLogo.CELLS; i++) {
			int c = logo[i];
			if (c <= 0 || c >= CrewLogo.PALETTE.length) {
				continue;
			}
			int px = x + (i % CrewLogo.SIZE) * cell;
			int py = y + (i / CrewLogo.SIZE) * cell;
			g.fill(px, py, px + cell, py + cell, CrewLogo.PALETTE[c]);
		}
	}
}
