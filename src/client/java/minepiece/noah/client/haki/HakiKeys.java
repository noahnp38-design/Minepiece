package minepiece.noah.client.haki;

import com.mojang.blaze3d.platform.InputConstants;
import minepiece.noah.MinePiece;
import minepiece.noah.client.ProfileKey;
import minepiece.noah.network.HakiKeyPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Touches du Haki (catégorie "MinePiece - Haki", toutes modifiables dans Options > Commandes) :
 * J Armement, K Observation, L Rois (à MAINTENIR : éclairs tant que la touche est enfoncée), R esquive.
 * Maj + K = Vision du futur. Conflits : L est aussi la touche vanilla des Progrès et le profil MinePiece utilisait K (il passe sur O) ;
 * si deux touches restent identiques, l'appui est réservé au Haki pour ne pas ouvrir les deux à la fois.
 */
public final class HakiKeys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MinePiece.id("haki"));
	private static KeyMapping armament;
	private static KeyMapping observation;
	private static KeyMapping conqueror;
	private static KeyMapping dodge;
	/** L est maintenue (Haki des Rois : éclairs tant que la touche est enfoncée). */
	private static boolean kingDown;

	private HakiKeys() {}

	public static void init() {
		armament = register("key.minepiece.haki_armament", InputConstants.KEY_J);
		observation = register("key.minepiece.haki_observation", InputConstants.KEY_K);
		conqueror = register("key.minepiece.haki_conqueror", InputConstants.KEY_L);
		dodge = register("key.minepiece.haki_dodge", InputConstants.KEY_R);

		// début du tick : AVANT que Minecraft (Progrès) et le profil ne lisent les mêmes touches
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				kingDown = false;
				return;
			}
			// relâchement de L (même si un menu s'est ouvert entre-temps)
			if (kingDown && (!conqueror.isDown() || client.gui.screen() != null)) {
				kingDown = false;
				send(4, 0);
			}
			if (client.gui.screen() != null) {
				return;
			}
			if (pressed(armament)) {
				send(0, 0);
			}
			if (pressed(observation)) {
				if (ProfileKey.mapping() != null && observation.same(ProfileKey.mapping())) {
					drain(ProfileKey.mapping());
				}
				send(1, 0);
			}
			boolean kingClick = pressed(conqueror);
			if (kingClick || (conqueror.isDown() && !kingDown)) {
				if (conqueror.same(client.options.keyAdvancements)) {
					drain(client.options.keyAdvancements);
				}
				if (!kingDown) {
					kingDown = true;
					send(2, 0);
					if (!conqueror.isDown()) {
						kingDown = false;   // appui très bref : début puis fin
						send(4, 0);
					}
				}
			}
			if (pressed(dodge)) {
				var o = client.options;
				int dir = o.keyLeft.isDown() ? 0 : o.keyRight.isDown() ? 1 : o.keyUp.isDown() ? 3 : 2;
				send(3, dir);
			}
		});
	}

	private static boolean pressed(KeyMapping k) {
		boolean p = false;
		while (k.consumeClick()) {
			p = true;
		}
		return p;
	}

	private static void drain(KeyMapping k) {
		while (k.consumeClick()) {
			// rien : l'appui revient au Haki
		}
	}

	private static void send(int action, int dir) {
		if (Minecraft.getInstance().getConnection() != null) {
			ClientPlayNetworking.send(new HakiKeyPayload(action, dir));
		}
	}

	public static String label(int i) {
		KeyMapping k = switch (i) {
			case 0 -> armament;
			case 1 -> observation;
			case 2 -> conqueror;
			default -> dodge;
		};
		return k == null ? "?" : k.getTranslatedKeyMessage().getString();
	}

	private static KeyMapping register(String key, int code) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(key, code, CATEGORY));
	}
}
