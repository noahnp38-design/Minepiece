package minepiece.noah.client.chest;

import java.util.List;

import minepiece.noah.client.UiUtil;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Écran du Coffre à Fruits : bande HORIZONTALE de fruits (pas de roue) qui défile sous un indicateur fixe.
 *
 * Bande virtuelle : la case n°i est calculée à la volée à partir de la graine envoyée par le serveur (aucune liste
 * géante, aucun objet par case), seules ~10 cases visibles sont dessinées par image. La case n° STOP est le fruit
 * gagnant choisi par le serveur ; la position suit une courbe accélération rapide / longue décélération qui
 * s'arrête exactement au centre de cette case.
 */
public class FruitChestScreen extends Screen {
	private static final double DURATION = 7.0; // secondes
	private static final int STOP = 64;         // indice de la case gagnante
	private static final int CELL = 44;         // largeur d'une case (pixels GUI)
	private static final double[] CURVE = buildCurve();

	private final DevilFruit[] fruits = DevilFruit.values();
	private final ItemStack[] stacks = new ItemStack[fruits.length];
	private final String[] rarities = new String[fruits.length];
	private final int[] cumulative = new int[fruits.length];
	private final int winner;
	private final long seed;
	private final long totalWeight;

	private long startNanos = 0;
	private long finishNanos = 0;
	private boolean finished = false;
	private long lastCell = Long.MIN_VALUE;

	public FruitChestScreen(int winner, long seed, List<Integer> weights) {
		super(Component.literal("Coffre à Fruits"));
		this.winner = Math.max(0, Math.min(fruits.length - 1, winner));
		this.seed = seed;
		long sum = 0;
		for (int i = 0; i < fruits.length; i++) {
			stacks[i] = new ItemStack(ModItems.of(fruits[i]));
			String tier = ModItems.tierOf(fruits[i]);
			rarities[i] = tier != null ? tier : stacks[i].getRarity().name();
			int w = i < weights.size() ? Math.max(0, weights.get(i)) : 0;
			sum += w;
			cumulative[i] = (int) Math.min(Integer.MAX_VALUE, sum);
		}
		this.totalWeight = sum;
	}

	// ------------------------------------------------------------------ courbe d'animation

	/** f(t) : 0 -> 1, démarrage rapide (≈10 % du temps) puis décélération douce jusqu'à l'arrêt. */
	private static double[] buildCurve() {
		int n = 512;
		double[] c = new double[n + 1];
		double sum = 0;
		for (int i = 1; i <= n; i++) {
			double t = (i - 0.5) / n;
			double ramp = Math.min(1.0, t / 0.10);
			ramp = ramp * ramp * (3 - 2 * ramp);
			double v = ramp * (1 - t) * (1 - t);
			sum += v;
			c[i] = sum;
		}
		for (int i = 1; i <= n; i++) {
			c[i] /= sum;
		}
		return c;
	}

	private static double curve(double t) {
		if (t <= 0) {
			return 0;
		}
		if (t >= 1) {
			return 1;
		}
		double x = t * (CURVE.length - 1);
		int i = (int) x;
		double f = x - i;
		return CURVE[i] + (CURVE[i + 1] - CURVE[i]) * f;
	}

	// ------------------------------------------------------------------ bande virtuelle

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Fruit affiché dans la case i (déterministe : même résultat à chaque image). */
	private int cellFruit(long i) {
		if (i == STOP) {
			return winner;
		}
		long h = mix(seed + i * 0x9E3779B97F4A7C15L) >>> 1;
		if (totalWeight <= 0) {
			return (int) (h % fruits.length);
		}
		long r = h % totalWeight;
		for (int k = 0; k < cumulative.length; k++) {
			if (r < cumulative[k]) {
				return k;
			}
		}
		return fruits.length - 1;
	}

	// ------------------------------------------------------------------ couleurs

	private static int rarityColor(String r) {
		return switch (r) {
			case "UNCOMMON" -> 0xFFFFFF55;
			case "RARE" -> 0xFF5B8CFF;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "EPIC" -> 0xFFFF55FF;
			default -> 0xFFFFFFFF;
		};
	}

	private static String rarityLabel(String r) {
		return switch (r) {
			case "UNCOMMON" -> "Peu commun";
			case "RARE" -> "Rare";
			case "EPIC" -> "Épique";
			case "COMMON" -> "Commun";
			case "LEGENDARY" -> "Légendaire";
			default -> r.substring(0, 1) + r.substring(1).toLowerCase();
		};
	}

	private static int sparkleCount(String r) {
		return switch (r) {
			case "UNCOMMON" -> 10;
			case "RARE" -> 16;
			case "EPIC" -> 26;
			case "LEGENDARY" -> 40;
			case "COMMON" -> 6;
			default -> 30;
		};
	}

	private static int withAlpha(int rgb, double a) {
		int al = (int) Math.max(0, Math.min(255, a * 255));
		return (al << 24) | (rgb & 0xFFFFFF);
	}

	// ------------------------------------------------------------------ écran

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** On ne peut pas fermer (ni annuler) avant la fin ; de toute façon le fruit est déjà décidé côté serveur. */
	@Override
	public boolean shouldCloseOnEsc() {
		return finished;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (finished && (event.key() == 257 || event.key() == 32 || event.key() == 335)) { // Entrée / Espace
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private int panelW() {
		return Math.min(this.width - 16, 460);
	}

	private int panelH() {
		return 186;
	}

	private int panelX() {
		return (this.width - panelW()) / 2;
	}

	private int panelY() {
		return Math.max(2, (this.height - panelH()) / 2);
	}

	private boolean closeEnabled() {
		return finished && (System.nanoTime() - finishNanos) > 300_000_000L;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && closeEnabled()) {
			int bx = this.width / 2 - 45;
			int by = panelY() + panelH() - 24;
			if (UiUtil.inside((int) event.x(), (int) event.y(), bx, by, 90, 16)) {
				this.onClose();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		long now = System.nanoTime();
		if (startNanos == 0) {
			startNanos = now;
		}
		double t = (now - startNanos) / 1.0e9 / DURATION;
		double pos = STOP * curve(t);
		if (t >= 1 && !finished) {
			finished = true;
			finishNanos = now;
			playFinishSound();
		}

		g.fillGradient(0, 0, this.width, this.height, 0xB0080810, 0xC0040408);
		super.extractRenderState(g, mx, my, delta);

		int pw = panelW();
		int ph = panelH();
		int px = panelX();
		int py = panelY();
		int cx = this.width / 2;

		drawFrame(g, px, py, pw, ph);

		// ---- titre
		g.pose().pushMatrix();
		g.pose().translate(cx, py + 9);
		g.pose().scale(1.5F, 1.5F);
		g.centeredText(this.font, "COFFRE À FRUITS", 0, 0, UiUtil.GOLD_LIGHT);
		g.pose().popMatrix();
		int tw = (int) (this.font.width("COFFRE À FRUITS") * 1.5F / 2) + 8;
		g.fill(px + 14, py + 17, cx - tw, py + 18, 0xFF8A6A20);
		g.fill(cx + tw, py + 17, px + pw - 14, py + 18, 0xFF8A6A20);

		// ---- bande
		int sx = px + 14;
		int sy = py + 34;
		int sw = pw - 28;
		int sh = CELL + 8;
		drawStrip(g, sx, sy, sw, sh, cx, pos, t);

		// ---- bas : attente ou résultat
		int y0 = sy + sh + 10;
		if (!finished) {
			int dots = (int) ((now / 400_000_000L) % 4);
			g.centeredText(this.font, "Tirage en cours" + ".".repeat(dots), cx, y0 + 26, UiUtil.TEXT);
			g.centeredText(this.font, "Bonne chance !", cx, y0 + 42, UiUtil.TEXT_DIM);
		} else {
			drawResult(g, px, pw, y0, (now - finishNanos) / 1.0e9);
		}

		drawCloseButton(g, mx, my, cx, py + ph - 24);
	}

	private void drawFrame(GuiGraphicsExtractor g, int px, int py, int pw, int ph) {
		g.fill(px, py, px + pw, py + ph, 0xE8140F1A);
		g.outline(px, py, pw, ph, UiUtil.GOLD);
		g.outline(px + 3, py + 3, pw - 6, ph - 6, 0xFF6E5418);
		int[][] corners = {{px, py}, {px + pw - 7, py}, {px, py + ph - 7}, {px + pw - 7, py + ph - 7}};
		for (int[] c : corners) {
			g.fill(c[0], c[1], c[0] + 7, c[1] + 7, UiUtil.GOLD);
			g.fill(c[0] + 2, c[1] + 2, c[0] + 5, c[1] + 5, 0xFF140F1A);
			g.fill(c[0] + 3, c[1] + 3, c[0] + 4, c[1] + 4, UiUtil.GOLD_LIGHT);
		}
	}

	private void drawStrip(GuiGraphicsExtractor g, int sx, int sy, int sw, int sh, int cx, double pos, double t) {
		g.fill(sx, sy, sx + sw, sy + sh, 0xFF0A0910);
		g.outline(sx, sy, sw, sh, 0xFF8A6A20);

		g.enableScissor(sx + 1, sy + 1, sx + sw - 1, sy + sh - 1);
		int half = sw / (2 * CELL) + 2;
		long first = (long) Math.floor(pos) - half;
		long last = (long) Math.floor(pos) + half + 1;
		int top = sy + 4;
		for (long k = first; k <= last; k++) {
			int f = cellFruit(k);
			float cellCx = (float) (cx + (k - pos) * CELL);
			int rc = rarityColor(rarities[f]);

			g.pose().pushMatrix();
			g.pose().translate(cellCx, 0);
			g.fill(-CELL / 2 + 2, top, CELL / 2 - 2, top + CELL, withAlpha(rc, 0.14));
			g.fill(-CELL / 2 + 2, top + CELL - 3, CELL / 2 - 2, top + CELL, rc);
			g.pose().pushMatrix();
			g.pose().translate(0, top + CELL / 2.0F - 1);
			g.pose().scale(2F, 2F);
			g.item(stacks[f], -8, -8);
			g.pose().popMatrix();
			g.pose().popMatrix();
		}

		// fondu sombre sur les bords
		for (int i = 0; i < 8; i++) {
			int a = (int) (0xB0 * (1 - i / 8.0)) << 24;
			g.fill(sx + 1 + i * 4, sy + 1, sx + 5 + i * 4, sy + sh - 1, a | 0x0A0910);
			g.fill(sx + sw - 5 - i * 4, sy + 1, sx + sw - 1 - i * 4, sy + sh - 1, a | 0x0A0910);
		}
		// case centrale mise en valeur
		g.fill(cx - CELL / 2, sy + 1, cx + CELL / 2, sy + sh - 1, 0x22F5D76E);
		g.fill(cx - CELL / 2, sy + 1, cx - CELL / 2 + 1, sy + sh - 1, 0xAAF5D76E);
		g.fill(cx + CELL / 2 - 1, sy + 1, cx + CELL / 2, sy + sh - 1, 0xAAF5D76E);
		g.fill(cx, sy + 1, cx + 1, sy + sh - 1, 0x66FFFFFF);
		g.disableScissor();

		// indicateur fixe (triangles ▼ au-dessus, ▲ au-dessous)
		for (int r = 0; r < 6; r++) {
			g.fill(cx - 6 + r, sy - 8 + r, cx + 6 - r, sy - 7 + r, UiUtil.GOLD_LIGHT);
			g.fill(cx - 6 + r, sy + sh + 7 - r, cx + 6 - r, sy + sh + 8 - r, UiUtil.GOLD_LIGHT);
		}

		// tic sonore à chaque case qui passe
		long cell = (long) Math.floor(pos + 0.5);
		if (cell != lastCell) {
			if (lastCell != Long.MIN_VALUE && !finished) {
				float pitch = 0.9F + 0.5F * (float) Math.min(1.0, t);
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT, pitch));
			}
			lastCell = cell;
		}
	}

	private void playFinishSound() {
		String r = rarities[winner];
		var sm = Minecraft.getInstance().getSoundManager();
		if (r.equals("COMMON")) {
			sm.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F));
		} else if ((r.equals("EPIC") || r.equals("LEGENDARY"))) {
			sm.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F));
		} else {
			sm.play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
		}
	}

	private void drawResult(GuiGraphicsExtractor g, int px, int pw, int y0, double r) {
		double a = Math.min(1.0, r / 0.5);
		String rar = rarities[winner];
		int rc = rarityColor(rar) & 0xFFFFFF;
		ItemStack stack = stacks[winner];
		int tx = px + pw / 2;
		int[] iconX = {px + pw / 5, px + pw - pw / 5};
		int iconY = y0 + 38;

		for (int ix : iconX) {
			// halo
			for (int i = 5; i >= 1; i--) {
				int rad = 20 + i * 5;
				g.fill(ix - rad, iconY - rad, ix + rad, iconY + rad, withAlpha(rc, 0.035 * a * (1 + Math.sin(r * 3) * 0.3)));
			}
			float bob = (float) Math.sin(r * 2.5) * 1.5F;
			g.pose().pushMatrix();
			g.pose().translate(ix, iconY + bob);
			g.pose().scale(3F, 3F);
			g.item(stack, -8, -8);
			g.pose().popMatrix();
			drawSparkles(g, ix, iconY, r, rc, a, sparkleCount(rar));
		}

		if (a * 255 >= 8) {
			g.pose().pushMatrix();
			g.pose().translate(tx, y0 + 2);
			g.pose().scale(1.5F, 1.5F);
			g.centeredText(this.font, "FÉLICITATIONS !", 0, 0, withAlpha(0xF5D76E, a));
			g.pose().popMatrix();
			g.centeredText(this.font, "Vous avez obtenu :", tx, y0 + 24, withAlpha(0xEAF0F7, a));
			g.pose().pushMatrix();
			g.pose().translate(tx, y0 + 37);
			g.pose().scale(1.25F, 1.25F);
			g.centeredText(this.font, stack.getHoverName().getString(), 0, 0, withAlpha(rc, a));
			g.pose().popMatrix();
			g.centeredText(this.font, "Rareté : " + rarityLabel(rar), tx, y0 + 54, withAlpha(rc, a));
		}
	}

	private void drawSparkles(GuiGraphicsExtractor g, int cx, int cy, double r, int rgb, double a, int count) {
		for (int i = 0; i < count; i++) {
			double ang = i * 2.399963 + r * (0.5 + (i % 5) * 0.12);
			double rad = 26 + 14 * Math.sin(r * 2 + i) + (i % 3) * 6;
			int x = cx + (int) (Math.cos(ang) * rad);
			int y = cy + (int) (Math.sin(ang) * rad * 0.8);
			double tw = 0.5 + 0.5 * Math.sin(r * 6 + i * 1.7);
			int col = withAlpha(rgb, a * (0.35 + 0.65 * tw));
			g.fill(x - 1, y, x + 2, y + 1, col);
			g.fill(x, y - 1, x + 1, y + 2, col);
		}
	}

	private void drawCloseButton(GuiGraphicsExtractor g, int mx, int my, int cx, int by) {
		int bx = cx - 45;
		boolean on = closeEnabled();
		boolean hover = on && UiUtil.inside(mx, my, bx, by, 90, 16);
		g.fill(bx, by, bx + 90, by + 16, on ? (hover ? 0xFF4A3A14 : 0xFF2A2210) : 0xFF16141A);
		g.outline(bx, by, 90, 16, on ? UiUtil.GOLD : 0xFF3A3640);
		g.centeredText(this.font, on ? "FERMER" : "Patientez…", cx, by + 4, on ? UiUtil.GOLD_LIGHT : 0xFF6A6672);
	}
}
