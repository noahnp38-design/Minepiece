package minepiece.noah.particle;

import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Particules du mod (Hie Hie no Mi, Zushi Zushi no Mi). Les textures sont dans assets/minepiece/textures/particle,
 * la liste des images dans assets/minepiece/particles/*.json, le comportement est enregistré côté client (MinePieceClient).
 */
public final class ModParticles {
	private ModParticles() {}

	/** Éclat de glace qui retombe (comportement d'un flocon). */
	public static final SimpleParticleType ICE_SHARD = register("ice_shard");
	/** Étincelle de givre lumineuse qui flotte puis s'éteint. */
	public static final SimpleParticleType ICE_SPARKLE = register("ice_sparkle");
	/** Bouffée de brume glaciale. */
	public static final SimpleParticleType FROST_MIST = register("frost_mist");

	// ---- Zushi Zushi no Mi (violet, mauve, blanc lumineux)
	/** Étincelle gravitationnelle violette (flotte puis s'éteint). */
	public static final SimpleParticleType GRAVITY_SPARK = register("gravity_spark");
	/** Énergie aspirée vers un point (part d'un décalage et revient vers le centre). */
	public static final SimpleParticleType GRAVITY_PULL = register("gravity_pull");
	/** Poussière gris-mauve soulevée par la gravité. */
	public static final SimpleParticleType GRAVITY_DUST = register("gravity_dust");
	/** Éclat de roche qui retombe. */
	public static final SimpleParticleType ROCK_CHIP = register("rock_chip");
	/** Point lumineux blanc-violet (éclairs gravitationnels, lignes de vitesse). */
	public static final SimpleParticleType GRAVITY_FLASH = register("gravity_flash");
	/**
	 * Petite particule de gravité qui descend lentement (Écrasement gravitationnel). Créée côté client dans les zones
	 * actives (GravityZonePayload), elle disparaît dès que la zone se termine.
	 */
	public static final SimpleParticleType GRAVITY_MOTE = register("gravity_mote");

	// ---- Ishi Ishi no Mi (roche grise naturelle)
	/** Poussière de pierre grise (nuage qui s'étale). */
	public static final SimpleParticleType STONE_DUST = register("stone_dust");
	/** Petit fragment de pierre anguleux qui retombe. */
	public static final SimpleParticleType STONE_FRAGMENT = register("stone_fragment");
	/** Éclat de roche projeté (allongé). */
	public static final SimpleParticleType STONE_SHARD = register("stone_shard");
	/** Fragment ASPIRÉ vers un point (envoyé avec une vitesse, count 0) : la pierre qui s'assemble autour du bras. */
	public static final SimpleParticleType STONE_PULL = register("stone_pull");

	// ---- Goro Goro no Mi (foudre bleue d'Enel : bleu électrique, cyan, blanc bleuté)
	/** Petite étincelle cyan (scintille, toujours lumineuse). */
	public static final SimpleParticleType ELECTRIC_SPARK = register("electric_spark");
	/** Électricité bleue. */
	public static final SimpleParticleType ELECTRIC_BLUE = register("electric_blue");
	/** Petit arc électrique bleu en zigzag (orientation aléatoire, grésille). */
	public static final SimpleParticleType ELECTRIC_ARC = register("electric_arc");
	/** Grand éclair ramifié (très bref). */
	public static final SimpleParticleType ELECTRIC_BRANCH = register("electric_branch");
	/**
	 * Particule de charge : envoyée avec une vitesse (count 0), elle part de position + vitesse et est ASPIRÉE vers la
	 * position (main, sphère, nuage).
	 */
	public static final SimpleParticleType ELECTRIC_CHARGE = register("electric_charge");
	// ---- Haki (éclairs noirs et rouges du Haki des Rois, pression, matière sombre de l'Armement, silhouettes)
	/** Texture 1 : éclair noir et rouge (centre presque noir, contours rouge intense). */
	public static final SimpleParticleType HAKI_BOLT = register("haki_bolt");
	/** Texture 2 : arc électrique ramifié noir et rouge. */
	public static final SimpleParticleType HAKI_ARC = register("haki_arc");
	/** Texture 3 : décharge de Haki (impulsion, lignes qui se dispersent). */
	public static final SimpleParticleType HAKI_DISCHARGE = register("haki_discharge");
	/** Texture 4 : aura d'imprégnation (petits arcs autour des membres et des armes). */
	public static final SimpleParticleType HAKI_AURA = register("haki_aura");
	/** Texture 5 : clash de Haki des Rois (décharges massives). */
	public static final SimpleParticleType HAKI_CLASH = register("haki_clash");
	/** Pression / distorsion de l'air (Haki des Rois, émission). */
	public static final SimpleParticleType HAKI_PRESSURE = register("haki_pressure");
	/** Énergie sombre (activation de l'Armement, destruction interne). */
	public static final SimpleParticleType HAKI_DARK = register("haki_dark");
	/** Silhouette fantôme (traînée d'esquive, Vision du futur). */
	public static final SimpleParticleType HAKI_GHOST = register("haki_ghost");
	/** Fumée sombre des cumulonimbus. */
	public static final SimpleParticleType STORM_SMOKE = register("storm_smoke");
	/** Poussière des explosions (brun-gris). */
	public static final SimpleParticleType STORM_DUST = register("storm_dust");

	private static SimpleParticleType register(String name) {
		return Registry.register(BuiltInRegistries.PARTICLE_TYPE, MinePiece.id(name), FabricParticleTypes.simple());
	}

	public static void init() {}
}
