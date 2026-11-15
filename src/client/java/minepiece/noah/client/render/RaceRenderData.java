package minepiece.noah.client.render;

import minepiece.noah.data.Race;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/** Donnée attachée à l'état de rendu d'un joueur : sa race. */
public final class RaceRenderData {
	public static final RenderStateDataKey<Race> RACE = RenderStateDataKey.create(() -> "minepiece race");

	private RaceRenderData() {}
}
