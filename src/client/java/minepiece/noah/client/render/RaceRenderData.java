package minepiece.noah.client.render;

import minepiece.noah.data.Race;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/** Donnée attachée à l'état de rendu d'un joueur : sa race. */
public final class RaceRenderData {
	public static final RenderStateDataKey<Race> RACE = RenderStateDataKey.create(() -> "minepiece race");

	/** Pose des ailes (battement, ouverture) : calculée par WingAnimator pour les races à ailes. */
	public static final RenderStateDataKey<WingPose> WING = RenderStateDataKey.create(() -> "minepiece wing");

	/** Vrai si le joueur charge le Dai Enkai : Entei (bras droit levé). */
	public static final RenderStateDataKey<Boolean> RAISE_HAND = RenderStateDataKey.create(() -> "minepiece raise hand");

	/** Pheasant Beak : progression du gel du bras droit (0 à 1), -1 = bras normal. */
	public static final RenderStateDataKey<Float> ICE_ARM = RenderStateDataKey.create(() -> "minepiece ice arm");

	/** Goro Goro no Mi : pose animée en cours (voir ability/goro/GoroPose), 0 = aucune. */
	public static final RenderStateDataKey<Integer> GORO_POSE = RenderStateDataKey.create(() -> "minepiece goro pose");

	/** Goro Goro no Mi : ticks écoulés depuis le début de la pose (avec la fraction de tick). */
	public static final RenderStateDataKey<Float> GORO_TIME = RenderStateDataKey.create(() -> "minepiece goro time");

	private RaceRenderData() {}
}
