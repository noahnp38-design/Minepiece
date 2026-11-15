package minepiece.noah.client.title;

import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Remplace le décor du menu principal par l'image MINEPIECE - PIRATE EDITION.
 * Les boutons, les 3 icônes, le splash, la version et le copyright restent ceux de Minecraft :
 * ils sont dessinés par le jeu PAR-DESSUS cette image et restent donc pleinement fonctionnels.
 */
public final class TitleBackground {
	/** Image du décor. Pour la changer, remplace simplement ce fichier (même nom) et mets à jour les tailles ci-dessous. */
	public static final Identifier TEXTURE = MinePiece.id("textures/gui/title/background.png");
	public static final int TEXTURE_WIDTH = 1672;
	public static final int TEXTURE_HEIGHT = 941;

	/**
	 * Quand la fenêtre n'a pas le même format que l'image, on rogne l'image (au lieu de la déformer).
	 * 0.5 = rognage centré ; plus petit = on garde plus le HAUT de l'image (là où se trouve le logo).
	 */
	private static final float VERTICAL_ANCHOR = 0.3F;

	private TitleBackground() {}

	public static void init() {
		// Les évènements d'un écran sont recréés à chaque (ré)initialisation (redimensionnement inclus),
		// donc on les ré-enregistre à chaque fois : pas de doublon possible.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof TitleScreen) {
				ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, tickProgress) ->
						draw(graphics, s.width, s.height));
			}
		});
	}

	/** Dessine l'image en "cover" : elle remplit toute la fenêtre sans être étirée. */
	private static void draw(GuiGraphicsExtractor graphics, int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}
		float scale = Math.max((float) width / TEXTURE_WIDTH, (float) height / TEXTURE_HEIGHT);
		int regionWidth = Math.min(TEXTURE_WIDTH, Math.round(width / scale));
		int regionHeight = Math.min(TEXTURE_HEIGHT, Math.round(height / scale));
		int u = (TEXTURE_WIDTH - regionWidth) / 2;
		int v = Math.round((TEXTURE_HEIGHT - regionHeight) * VERTICAL_ANCHOR);

		// pipeline, texture, x, y, u, v, largeur/hauteur à l'écran, largeur/hauteur de la zone de l'image, taille de l'image
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, u, v, width, height,
				regionWidth, regionHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}
}
