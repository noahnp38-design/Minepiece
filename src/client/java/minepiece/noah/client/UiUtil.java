package minepiece.noah.client;

import minepiece.noah.data.CombatStyle;
import minepiece.noah.data.Faction;
import minepiece.noah.data.Race;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Petits outils de dessin et palette de couleurs partagés par les écrans du mod. */
public final class UiUtil {
	public static final int GOLD = 0xFFE2B33C;
	public static final int GOLD_LIGHT = 0xFFF5D76E;
	public static final int TEXT = 0xFFEAF0F7;
	public static final int TEXT_DIM = 0xFF8FA3B8;

	/** Symbole des berries (la monnaie). Si ton jeu affiche un carré, remplace-le par "B". */
	public static final String BERRY = "฿";

	private UiUtil() {}

	// ------------------------------------------------------------------ couleurs

	public static int color(Race race) {
		return switch (race) {
			case HUMAN -> 0xFFD9AE7E;
			case SKYPIEAN -> 0xFFBFE3FF;
			case FISHMAN -> 0xFF4FA8DE;
			case MINK -> 0xFFF0A24A;
			case GIANT -> 0xFF9C6B3F;
			case LUNARIAN -> 0xFFE8562A;
		};
	}

	public static int color(Faction faction) {
		return switch (faction) {
			case PIRATE -> 0xFFD94545;
			case MARINE -> 0xFF3F82DB;
			case REVOLUTIONARY -> 0xFF3FB25E;
			case BOUNTY_HUNTER -> 0xFFB46CE6;
			case CIVILIAN -> 0xFFA8B0BA;
		};
	}

	public static int color(CombatStyle style) {
		return switch (style) {
			case SWORDSMAN -> 0xFF84D4EC;
			case BLACK_LEG -> 0xFFE8743C;
			case FISHMAN_KARATE -> 0xFF3FA9CC;
			case SNIPER -> 0xFFCDB44E;
		};
	}

	// ------------------------------------------------------------------ géométrie

	public static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ------------------------------------------------------------------ texte

	public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
		g.text(font, text, x, y, color, shadow);
	}

	public static void centered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color, boolean shadow) {
		g.text(font, text, cx - font.width(text) / 2, y, color, shadow);
	}

	/** Texte agrandi ou rétréci (scale 1 = taille normale). */
	public static void scaled(GuiGraphicsExtractor g, Font font, String text, float x, float y, float scale, int color, boolean shadow) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	public static void centeredScaled(GuiGraphicsExtractor g, Font font, String text, int cx, int y, float scale, int color, boolean shadow) {
		float w = font.width(text) * scale;
		scaled(g, font, text, cx - w / 2f, y, scale, color, shadow);
	}

	/** Écrit le texte à (x, y) ; s'il dépasse maxW, il est rétréci pour rentrer. */
	public static void fit(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxW, int color, boolean shadow) {
		int w = font.width(text);
		if (w <= maxW) {
			g.text(font, text, x, y, color, shadow);
		} else {
			float s = (float) maxW / w;
			scaled(g, font, text, x, y + (font.lineHeight - font.lineHeight * s) / 2f, s, color, shadow);
		}
	}

	/** 1500000 -> "1 500 000". */
	public static String formatBerries(long value) {
		String s = Long.toString(value);
		StringBuilder out = new StringBuilder();
		int n = s.length();
		for (int i = 0; i < n; i++) {
			if (i > 0 && (n - i) % 3 == 0) {
				out.append(' ');
			}
			out.append(s.charAt(i));
		}
		return out.toString();
	}

	// ------------------------------------------------------------------ décor

	/** Fond « océan de nuit » : dégradé, étoiles qui scintillent, deux vagues animées. */
	public static void drawOcean(GuiGraphicsExtractor g, int w, int h, int[] starX, int[] starY) {
		g.fillGradient(0, 0, w, h, 0xFF0B1F38, 0xFF040911);

		long t = System.currentTimeMillis();
		for (int i = 0; i < starX.length; i++) {
			double twinkle = 0.55 + 0.45 * Math.sin(t / 700.0 + i * 1.7);
			int alpha = (int) (twinkle * 150) + 20;
			g.fill(starX[i], starY[i], starX[i] + 1, starY[i] + 1, (alpha << 24) | 0xDDEBFF);
		}

		for (int layer = 0; layer < 2; layer++) {
			int base = h - 10 - layer * 9;
			int color = layer == 0 ? 0x552A6FA8 : 0x331F4E7A;
			double speed = layer == 0 ? 30.0 : 46.0;
			double length = layer == 0 ? 14.0 : 21.0;
			for (int x = 0; x < w; x += 2) {
				int y = base + (int) Math.round(Math.sin((x + t / speed) / length + layer) * 2.5);
				g.fill(x, y, x + 2, h, color);
			}
		}
	}
}
