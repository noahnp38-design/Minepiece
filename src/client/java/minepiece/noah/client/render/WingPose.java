package minepiece.noah.client.render;

/**
 * Pose des ailes d'un joueur à un instant donné.
 *
 * @param phase   angle du battement (en radians) : il avance plus vite quand le joueur va vite
 * @param amp     amplitude du battement (0 = ailes immobiles)
 * @param open    ouverture des ailes (0 = repliées au repos, 1 = grandes ouvertes en vol)
 * @param stagger décalage entre l'aile gauche et l'aile droite (0 = battent ensemble, plus = en alternance)
 */
public record WingPose(float phase, float amp, float open, float stagger) {
	public static final WingPose REST = new WingPose(0.0F, 0.0F, 0.0F, 0.0F);
}
