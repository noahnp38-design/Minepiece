package minepiece.noah.client.haki;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/** Données de Haki copiées dans l'état de rendu des entités (calculées une fois par image dans extractRenderState). */
public final class HakiRenderData {
	/** Recouvrement du Koka (0..1), -1 = aucun. */
	public static final RenderStateDataKey<Float> ARM_COVER = RenderStateDataKey.create(() -> "minepiece haki cover");
	/** Koka : recouvrement des 4 parties, intensité de leurs reflets, image de l'éclat (voir HakiLayer.covers). */
	public static final RenderStateDataKey<float[]> KOKA = RenderStateDataKey.create(() -> "minepiece haki koka");
	/** Image d'animation des arcs noirs et rouges (imprégnation des Rois), -1 = aucun. */
	public static final RenderStateDataKey<Integer> KING_FRAME = RenderStateDataKey.create(() -> "minepiece haki king frame");
	/** Yeux rouges (Observation). */
	public static final RenderStateDataKey<Boolean> EYES = RenderStateDataKey.create(() -> "minepiece haki eyes");
	/** Pose de Haki (avec le bit PERFECT) et temps écoulé (ticks). */
	public static final RenderStateDataKey<Integer> POSE = RenderStateDataKey.create(() -> "minepiece haki pose");
	public static final RenderStateDataKey<Float> POSE_TIME = RenderStateDataKey.create(() -> "minepiece haki pose time");
	/** KO du Haki des Rois (toutes les entités vivantes) : variante (1..4, +8 = se relève) et temps écoulé. */
	public static final RenderStateDataKey<Integer> KO = RenderStateDataKey.create(() -> "minepiece haki ko");
	public static final RenderStateDataKey<Float> KO_TIME = RenderStateDataKey.create(() -> "minepiece haki ko time");

	private HakiRenderData() {}
}
