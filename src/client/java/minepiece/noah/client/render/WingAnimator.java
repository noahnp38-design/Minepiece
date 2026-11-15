package minepiece.noah.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import minepiece.noah.data.Race;
import net.minecraft.world.entity.Entity;

/**
 * Calcule le battement d'ailes de chaque joueur (Skypiéen, Lunarien).
 *
 * Le battement doit être continu : si on utilisait simplement "temps x vitesse", l'aile sauterait à chaque
 * changement de vitesse. On garde donc, pour chaque joueur, la phase du battement et on l'avance à chaque image.
 * Les données sont dans une WeakHashMap : elles disparaissent toutes seules quand le joueur n'existe plus.
 */
public final class WingAnimator {
	// ---- Réglages du battement (radians par tick)
	private static final float FLAP_BASE = 0.45F;      // en vol, immobile
	private static final float FLAP_PER_SPEED = 0.8F;  // en plus, par bloc/tick de vitesse
	private static final float FLAP_MAX = 1.0F;
	private static final float GLIDE_RATE = 0.14F;     // en l'air sans voler : ailes ouvertes, léger frémissement
	private static final float GROUND_RATE = 0.06F;    // au sol : respiration lente

	private static final float AMP_FLY = 0.50F;
	private static final float AMP_FLY_EXTRA = 0.25F;  // amplitude en plus à grande vitesse
	private static final float AMP_GLIDE = 0.10F;
	private static final float OPEN_GLIDE = 0.75F;

	/** Décalage gauche / droite : le Skypiéen bat en alternance marquée, le Lunarien plus en puissance. */
	private static float stagger(Race race) {
		return race == Race.SKYPIEAN ? 1.1F : 0.35F;
	}

	private static final class Data {
		float phase;
		float lastAge = Float.NaN;
		float amp;
		float open;
		float speed;
	}

	private static final Map<Entity, Data> DATA = new WeakHashMap<>();

	private WingAnimator() {}

	/**
	 * Appelée une fois par image et par joueur à ailes.
	 *
	 * @param ageInTicks temps de l'image (avec décimales)
	 * @param flying     le joueur vole (drapeau synchronisé par le serveur)
	 * @param airborne   le joueur est en l'air (ni au sol, ni dans l'eau)
	 * @param speed      vitesse réelle, en blocs par tick
	 */
	public static WingPose update(Entity entity, Race race, float ageInTicks, boolean flying, boolean airborne, float speed) {
		Data d = DATA.computeIfAbsent(entity, e -> new Data());

		float dt = Float.isNaN(d.lastAge) ? 0.0F : ageInTicks - d.lastAge;
		if (dt < 0.0F || dt > 10.0F) {
			dt = 0.0F; // changement de monde, pause, première image...
		}
		d.lastAge = ageInTicks;

		// Vitesse lissée, pour ne pas trembler d'un tick à l'autre.
		d.speed += (speed - d.speed) * Math.min(1.0F, dt * 0.3F);

		float rate;
		float ampTarget;
		float openTarget;
		if (flying) {
			rate = Math.min(FLAP_MAX, FLAP_BASE + FLAP_PER_SPEED * d.speed);
			ampTarget = AMP_FLY + Math.min(AMP_FLY_EXTRA, d.speed * 0.6F);
			openTarget = 1.0F;
		} else if (airborne) {
			rate = GLIDE_RATE;
			ampTarget = AMP_GLIDE;
			openTarget = OPEN_GLIDE;
		} else {
			rate = GROUND_RATE;
			ampTarget = 0.0F;   // à l'atterrissage, le battement s'arrête
			openTarget = 0.0F;  // et les ailes se replient
		}

		d.phase += dt * rate;
		if (d.phase > 6283.0F) {
			d.phase -= 6283.0F; // garde le nombre petit (précision des float)
		}
		d.amp += (ampTarget - d.amp) * Math.min(1.0F, dt * 0.25F);
		d.open += (openTarget - d.open) * Math.min(1.0F, dt * 0.2F);

		return new WingPose(d.phase, d.amp, d.open, stagger(race));
	}
}
