package minepiece.noah.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/** Donnée du Ushi Ushi no Mi attachée à l'état de rendu d'un joueur (calculée à chaque image par AvatarRendererMixin). */
public final class GiraffeRenderData {
	/**
	 * @param wHybrid  poids visuel de la forme hybride (0..1, animé pendant les transformations)
	 * @param wGiraffe poids visuel de la forme girafe complète (0..1)
	 * @param animTime temps écoulé (ticks) dans l'animation d'attaque en cours, -1 si aucune
	 * @param anim     attaque en cours (GiraffeAnim.ANIM_*)
	 * @param scale    taille actuelle du joueur (attribut SCALE), pour convertir des blocs en pixels de modèle
	 * @param realInvisible vrai si le joueur est réellement invisible (Suke Suke) : alors on ne dessine rien
	 * @param transition avancement 0..1 de la transformation en cours (1 = terminée)
	 */
	public record View(float wHybrid, float wGiraffe, float animTime, int anim, float scale, boolean realInvisible, float transition) {
		public static final View NONE = new View(0.0F, 0.0F, -1.0F, 0, 1.0F, false, 1.0F);

		public boolean active() {
			return wHybrid > 0.001F || wGiraffe > 0.001F;
		}
	}

	public static final RenderStateDataKey<View> VIEW = RenderStateDataKey.create(() -> "minepiece giraffe");

	private GiraffeRenderData() {}
}
