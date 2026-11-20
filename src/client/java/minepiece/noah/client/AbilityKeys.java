package minepiece.noah.client;

import com.mojang.blaze3d.platform.InputConstants;
import minepiece.noah.MinePiece;
import minepiece.noah.ability.giraffe.GiraffeAbilities;
import minepiece.noah.network.UseAbilityPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/** Touches des 3 attaques de fruit du démon (Z / X / C par défaut, valables pour tous les fruits ; modifiables dans les contrôles). Le serveur décide de tout. */
public final class AbilityKeys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MinePiece.id("abilities"));
	private static final KeyMapping[] KEYS = new KeyMapping[3];
	/** Etat précédent de chaque touche (pour détecter appui / relâchement). */
	private static final boolean[] DOWN = new boolean[3];
	/** Ushi Ushi no Mi : V = forme suivante (humain, hybride, girafe), B = retour à la forme humaine. */
	private static KeyMapping formNext;
	private static KeyMapping formHuman;

	private AbilityKeys() {}

	public static void init() {
		KEYS[0] = register("key.minepiece.ability_1", InputConstants.KEY_Z);
		KEYS[1] = register("key.minepiece.ability_2", InputConstants.KEY_X);
		KEYS[2] = register("key.minepiece.ability_3", InputConstants.KEY_C);
		formNext = register("key.minepiece.form_next", InputConstants.KEY_V);
		formHuman = register("key.minepiece.form_human", InputConstants.KEY_B);

		// Le serveur reçoit "pressé" et "relâché" : nécessaire pour les attaques maintenues (Fire Gun, charge de l'Entei).
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				java.util.Arrays.fill(DOWN, false);
				return;
			}
			while (formNext.consumeClick()) {
				ClientPlayNetworking.send(new UseAbilityPayload(GiraffeAbilities.SLOT_CYCLE, true));
			}
			while (formHuman.consumeClick()) {
				ClientPlayNetworking.send(new UseAbilityPayload(GiraffeAbilities.SLOT_HUMAN, true));
			}
			for (int slot = 0; slot < KEYS.length; slot++) {
				boolean tapped = false;
				while (KEYS[slot].consumeClick()) {
					tapped = true;
				}
				boolean down = KEYS[slot].isDown();
				if (tapped && !down && !DOWN[slot]) {
					// Appui très bref, relâché avant la fin du tick : on envoie quand même appui + relâche.
					ClientPlayNetworking.send(new UseAbilityPayload(slot, true));
					ClientPlayNetworking.send(new UseAbilityPayload(slot, false));
				} else if (down != DOWN[slot]) {
					DOWN[slot] = down;
					ClientPlayNetworking.send(new UseAbilityPayload(slot, down));
				}
			}
		});
	}

	/** Nom de la touche actuellement assignee a une attaque (pour la HUD). */
	public static String label(int slot) {
		if (slot < 0 || slot >= KEYS.length || KEYS[slot] == null) {
			return "?";
		}
		return KEYS[slot].getTranslatedKeyMessage().getString();
	}

	/** Nom de la touche de changement de forme (pour la HUD). */
	public static String formLabel() {
		return formNext == null ? "?" : formNext.getTranslatedKeyMessage().getString();
	}

	private static KeyMapping register(String translationKey, int keyCode) {
		return KeyMappingHelper.registerKeyMapping(
				new KeyMapping(translationKey, keyCode, CATEGORY));
	}
}
