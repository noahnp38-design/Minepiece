package minepiece.noah.sound;

import minepiece.noah.MinePiece;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Sons du mod. Chaque son est déclaré dans assets/minepiece/sounds.json et son fichier .ogg (mono) est dans
 * assets/minepiece/sounds/.
 */
public final class ModSounds {
	/** Fire Gun : tir de feu d'Ace (une seule fois au lancement, voir MeraAbilities.tickFireGun). */
	public static final SoundEvent FIRE_GUN = register("fire_gun");

	private ModSounds() {}

	private static SoundEvent register(String name) {
		Identifier id = MinePiece.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Appelé au démarrage : charge la classe, donc enregistre les sons. */
	public static void init() {}
}
