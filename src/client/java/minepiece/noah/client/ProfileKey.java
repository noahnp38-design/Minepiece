package minepiece.noah.client;

import com.mojang.blaze3d.platform.InputConstants;
import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

/** Touche qui ouvre l'interface du profil (K par défaut, modifiable dans les contrôles). */
public final class ProfileKey {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MinePiece.id("general"));
	private static KeyMapping key;

	private ProfileKey() {}

	public static void init() {
		key = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.minepiece.profile", InputConstants.KEY_K, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (key.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new ProfileScreen());
				}
			}
		});
	}

	/** Vrai si cette touche est celle du profil (pour refermer l'écran avec la même touche). */
	public static boolean matches(KeyEvent event) {
		return key != null && key.matches(event);
	}

	/** Nom de la touche actuelle, par exemple "K". */
	public static String keyName() {
		return key == null ? "K" : key.getTranslatedKeyMessage().getString();
	}
}
