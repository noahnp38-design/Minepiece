package minepiece.noah.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import minepiece.noah.data.CombatStyle;
import minepiece.noah.data.Faction;
import minepiece.noah.data.Race;
import minepiece.noah.network.SelectionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import minepiece.noah.MinePiece;

/**
 * Ecran « Choisis ta destinée » : race, faction et style de combat.
 * Dessiné dans un repère virtuel de 480 x 300 mis à l'échelle de la fenêtre (pose), donc identique à toutes les tailles.
 */
public class SelectionScreen extends Screen {
	/** Une option cliquable. bonus / malus : seulement pour les races. */
	private record Option(String label, String description, int color, String bonus, String malus, Identifier icon) {}

	// ---- repère virtuel
	private static final int VW = 480;
	private static final int VH = 300;
	private static final int PW = 148;
	private static final int PGAP = 8;
	private static final int PX0 = 10;
	private static final int HEADER_Y = 46;
	private static final int PANEL_Y = 62;
	private static final int ROW0 = 70;
	private static final int ROW_H = 24;
	private static final int ROW_STEP = 26;
	private static final int PANEL_H = 6 * ROW_STEP + 14;
	private static final int DESC_X = 10;
	private static final int DESC_Y = 240;
	private static final int DESC_W = 322;
	private static final int DESC_H = 54;
	private static final int BTN_X = 344;
	private static final int BTN_Y = 250;
	private static final int BTN_W = 126;
	private static final int BTN_H = 32;

	// ---- palette
	private static final int FRAME_DARK = 0xFF5A3A14;
	private static final int FRAME_GOLD = 0xFFE2B33C;
	private static final int PANEL_BG = 0xE6120A14;
	private static final int ORANGE = 0xFFFFB02E;

	private static final String[] HEADERS = {"Race", "Faction", "Style de combat"};
	private static final String SEL = "textures/gui/select/";
	private static final Identifier LOGO = MinePiece.id(SEL + "logo.png");
	private static final int LOGO_TEX_W = 531;
	private static final int LOGO_TEX_H = 132;
	private static final Identifier[] HEADER_ICONS = {
			MinePiece.id(SEL + "header_race.png"), MinePiece.id(SEL + "header_faction.png"), MinePiece.id(SEL + "header_style.png")};

	private final List<List<Option>> columns = new ArrayList<>();
	/** Index choisi dans chaque colonne (-1 = rien). L'index correspond à l'ordinal de l'enum. */
	private final int[] selected = {-1, -1, -1};
	private int focusCol = -1;
	private int focusIdx = -1;

	private float scale = 1.0F;
	private float offX = 0.0F;
	private float offY = 0.0F;

	public SelectionScreen() {
		super(Component.literal("Choisis ta destinée"));

		List<Option> races = new ArrayList<>();
		for (Race r : Race.values()) {
			races.add(new Option(r.displayName(), r.description(), UiUtil.color(r), r.bonus(), r.malus(), icon(r)));
		}
		List<Option> factions = new ArrayList<>();
		for (Faction f : Faction.values()) {
			factions.add(new Option(f.displayName(), f.description(), UiUtil.color(f), null, null, icon(f)));
		}
		List<Option> styles = new ArrayList<>();
		for (CombatStyle s : CombatStyle.values()) {
			styles.add(new Option(s.displayName(), s.description(), UiUtil.color(s), null, null, icon(s)));
		}
		columns.add(races);
		columns.add(factions);
		columns.add(styles);
	}

	/** Les icones sont les images fournies (textures/gui/select/*.png), 64 x 64. */
	private static Identifier tex(String name) {
		return MinePiece.id(SEL + name + ".png");
	}

	private static Identifier icon(Race r) {
		return tex(switch (r) {
			case HUMAN -> "race_human";
			case SKYPIEAN -> "race_skypiean";
			case FISHMAN -> "race_fishman";
			case MINK -> "race_mink";
			case GIANT -> "race_giant";
			case LUNARIAN -> "race_lunarian";
		});
	}

	private static Identifier icon(Faction f) {
		return tex(switch (f) {
			case PIRATE -> "faction_pirate";
			case MARINE -> "faction_marine";
			case REVOLUTIONARY -> "faction_revolutionary";
			case BOUNTY_HUNTER -> "faction_bounty_hunter";
			case CIVILIAN -> "faction_civilian";
		});
	}

	private static Identifier icon(CombatStyle s) {
		return tex(switch (s) {
			case SWORDSMAN -> "style_swordsman";
			case BLACK_LEG -> "style_black_leg";
			case FISHMAN_KARATE -> "style_fishman_karate";
			case SNIPER -> "style_sniper";
		});
	}

	// ------------------------------------------------------------------ géométrie (repère virtuel)

	private static int colX(int col) { return PX0 + col * (PW + PGAP); }
	private static int rowY(int row) { return ROW0 + row * ROW_STEP; }

	@Override
	protected void init() {
		scale = Math.min(this.width / (float) VW, this.height / (float) VH);
		offX = (this.width - VW * scale) / 2.0F;
		offY = (this.height - VH * scale) / 2.0F;
	}

	private int vx(int mx) { return Math.round((mx - offX) / scale); }
	private int vy(int my) { return Math.round((my - offY) / scale); }

	/** @return {colonne, ligne} de l'option sous la souris (coordonnées virtuelles), ou null. */
	private int[] cellAt(int x, int y) {
		for (int c = 0; c < columns.size(); c++) {
			for (int i = 0; i < columns.get(c).size(); i++) {
				if (UiUtil.inside(x, y, colX(c) + 5, rowY(i), PW - 10, ROW_H)) {
					return new int[] {c, i};
				}
			}
		}
		return null;
	}

	private boolean ready() {
		return selected[0] >= 0 && selected[1] >= 0 && selected[2] >= 0;
	}

	// ------------------------------------------------------------------ actions

	private void click() {
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private void confirm() {
		if (!ready()) {
			return;
		}
		ClientPlayNetworking.send(new SelectionPayload(selected[0], selected[1], selected[2]));
		this.minecraft.gui.setScreen(null);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int x = vx((int) event.x());
			int y = vy((int) event.y());

			int[] cell = cellAt(x, y);
			if (cell != null) {
				selected[cell[0]] = cell[1];
				focusCol = cell[0];
				focusIdx = cell[1];
				click();
				return true;
			}
			if (ready() && UiUtil.inside(x, y, BTN_X, BTN_Y, BTN_W, BTN_H)) {
				click();
				confirm();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Le joueur doit faire un choix : Échap ne ferme pas la fenêtre. */
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	// ------------------------------------------------------------------ rendu

	/** Le fond est dessiné dans extractRenderState : on neutralise celui de Minecraft. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		drawSky(g, this.width, this.height);
		super.extractRenderState(g, mx, my, delta);

		int x = vx(mx);
		int y = vy(my);

		g.pose().pushMatrix();
		g.pose().translate(offX, offY);
		g.pose().scale(scale, scale);

		drawTitle(g);

		int[] hovered = cellAt(x, y);
		if (hovered != null) {
			focusCol = hovered[0];
			focusIdx = hovered[1];
		}
		for (int c = 0; c < columns.size(); c++) {
			drawPanel(g, c);
			List<Option> options = columns.get(c);
			for (int i = 0; i < options.size(); i++) {
				drawRow(g, options.get(i), c, i, hovered != null && hovered[0] == c && hovered[1] == i);
			}
		}
		drawDescription(g);
		drawButton(g, x, y);

		g.pose().popMatrix();
	}

	/** Ciel bleu, nuages qui défilent, mer animée. Dessiné en coordonnées d'écran. */
	private static void drawSky(GuiGraphicsExtractor g, int w, int h) {
		int horizon = (int) (h * 0.62F);
		g.fillGradient(0, 0, w, horizon, 0xFF173F86, 0xFF58A6E6);
		g.fillGradient(0, horizon, w, h, 0xFF1C69A8, 0xFF061A38);

		long t = System.currentTimeMillis();
		for (int i = 0; i < 7; i++) {
			int cw = 60 + (i * 37) % 70;
			int cx = (int) (((i * 211L + t / 90) % (w + 2 * cw)) - cw);
			int cy = 8 + (i * 53) % Math.max(20, horizon - 40);
			g.fill(cx, cy + 6, cx + cw, cy + 14, 0x66FFFFFF);
			g.fill(cx + 10, cy, cx + cw - 14, cy + 8, 0x77FFFFFF);
			g.fill(cx + 22, cy - 5, cx + cw - 30, cy + 2, 0x88FFFFFF);
		}
		for (int layer = 0; layer < 3; layer++) {
			int base = horizon + 6 + layer * 14;
			int color = layer == 0 ? 0x66A8D8FF : layer == 1 ? 0x553A86C8 : 0x44205A90;
			double speed = 28.0 + layer * 14.0;
			double length = 12.0 + layer * 7.0;
			for (int xx = 0; xx < w; xx += 2) {
				int yy = base + (int) Math.round(Math.sin((xx + t / speed) / length + layer) * 2.0);
				g.fill(xx, yy, xx + 2, yy + 1 + layer, color);
			}
		}
	}

	private void outlinedText(GuiGraphicsExtractor g, String text, float x, float y, float s, int color, int edge) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx != 0 || dy != 0) {
					UiUtil.scaled(g, this.font, text, x + dx * s * 0.5F, y + dy * s * 0.5F, s, edge, false);
				}
			}
		}
		UiUtil.scaled(g, this.font, text, x, y, s, color, false);
	}

	private void drawTitle(GuiGraphicsExtractor g) {
		// logo MINE PIECE (image fournie)
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, 6, 2, 0, 0, 176, 44, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

		// titre
		float tw = this.font.width(this.title.getString()) * 2.6F;
		outlinedText(g, this.title.getString(), 330 - tw / 2.0F, 6, 2.6F, 0xFFFFD866, 0xFF7A2A00);
		String sub = "Ton aventure sur Grand Line commence ici...";
		UiUtil.centeredScaled(g, this.font, sub, 330, 30, 0.95F, 0xFFFFFFFF, true);
	}

	private void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL_BG);
		g.outline(x, y, w, h, FRAME_DARK);
		g.outline(x + 1, y + 1, w - 2, h - 2, FRAME_GOLD);
		g.outline(x + 3, y + 3, w - 6, h - 6, 0x44E2B33C);
		// rivets aux coins
		for (int cx : new int[] {x - 1, x + w - 4}) {
			for (int cy : new int[] {y - 1, y + h - 4}) {
				g.fill(cx, cy, cx + 5, cy + 5, FRAME_GOLD);
				g.fill(cx + 1, cy + 1, cx + 4, cy + 4, 0xFF8A5A1E);
			}
		}
	}

	private void drawPanel(GuiGraphicsExtractor g, int col) {
		int x = colX(col);
		drawFrame(g, x, PANEL_Y, PW, PANEL_H);

		// bandeau du titre
		int hy = HEADER_Y;
		g.fillGradient(x + 4, hy, x + PW - 4, hy + 22, 0xFF4A1A3A, 0xFF1C0A1A);
		g.outline(x + 4, hy, PW - 8, 22, FRAME_GOLD);
		g.outline(x + 5, hy + 1, PW - 10, 20, 0x66000000);
		g.blit(RenderPipelines.GUI_TEXTURED, HEADER_ICONS[col], x + 8, hy - 2, 0, 0, 26, 26, 64, 64, 64, 64);
		float tw = this.font.width(HEADERS[col]) * 1.3F;
		UiUtil.scaled(g, this.font, HEADERS[col], x + 30 + (PW - 38 - tw) / 2.0F, hy + 6, 1.3F, UiUtil.GOLD_LIGHT, true);
	}

	private void drawRow(GuiGraphicsExtractor g, Option o, int col, int idx, boolean hover) {
		int x = colX(col) + 5;
		int y = rowY(idx);
		int w = PW - 10;
		boolean sel = selected[col] == idx;

		if (sel) {
			g.outline(x - 2, y - 2, w + 4, ROW_H + 4, 0x55FFB02E);
			g.fillGradient(x, y, x + w, y + ROW_H, 0xFF8A4408, 0xFF3C1C06);
			g.outline(x, y, w, ROW_H, ORANGE);
			g.outline(x + 1, y + 1, w - 2, ROW_H - 2, 0xAAFFD27A);
		} else if (hover) {
			g.fillGradient(x, y, x + w, y + ROW_H, 0xFF3A2A3A, 0xFF1C121C);
			g.outline(x, y, w, ROW_H, 0xFFB08A4A);
		} else {
			g.fill(x, y, x + w, y + ROW_H, 0xAA0C0810);
			g.outline(x, y, w, ROW_H, 0x66806040);
		}

		// icône dans une case sombre
		g.fill(x + 3, y + 2, x + 23, y + 22, 0xFF150E14);
		g.outline(x + 3, y + 2, 20, 20, o.color() & 0x99FFFFFF);
		g.blit(RenderPipelines.GUI_TEXTURED, o.icon(), x + 4, y + 3, 0, 0, 18, 18, 64, 64, 64, 64);

		// nom + accroche (première phrase, raccourcie)
		int tx = x + 28;
		int maxW = w - 32;
		UiUtil.fit(g, this.font, o.label(), tx, y + 4, maxW, sel ? UiUtil.GOLD_LIGHT : 0xFFFFFFFF, true);
		String tag = tagline(o.description());
		UiUtil.scaled(g, this.font, ellipsize(tag, maxW, 0.72F), tx, y + 14, 0.72F, sel ? 0xFFFFD9A0 : 0xFFA6A0AA, false);
	}

	private static String tagline(String description) {
		int dot = description.indexOf('.');
		return dot > 0 ? description.substring(0, dot) : description;
	}

	private String ellipsize(String text, int maxW, float s) {
		if (this.font.width(text) * s <= maxW) {
			return text;
		}
		String t = text;
		while (t.length() > 1 && this.font.width(t + "…") * s > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}

	private void drawDescription(GuiGraphicsExtractor g) {
		drawFrame(g, DESC_X, DESC_Y, DESC_W, DESC_H);
		if (focusCol < 0) {
			UiUtil.centered(g, this.font, "Survole une option pour en savoir plus.", DESC_X + DESC_W / 2,
					DESC_Y + DESC_H / 2 - 4, UiUtil.TEXT_DIM, false);
			return;
		}
		Option o = columns.get(focusCol).get(focusIdx);
		g.fill(DESC_X + 8, DESC_Y + 8, DESC_X + 11, DESC_Y + 20, o.color());
		outlinedText(g, o.label(), DESC_X + 16, DESC_Y + 7, 1.4F, 0xFFFFC73A, 0xFF5A2A00);
		UiUtil.scaled(g, this.font, HEADERS[focusCol], DESC_X + DESC_W - 10 - this.font.width(HEADERS[focusCol]) * 0.8F,
				DESC_Y + 9, 0.8F, UiUtil.TEXT_DIM, false);

		List<String> lines = wrap(o.description(), (DESC_W - 24) / 0.9F);
		int ly = DESC_Y + 21;
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			UiUtil.scaled(g, this.font, lines.get(i), DESC_X + 12, ly + i * 9, 0.9F, 0xFFE6E6EE, false);
		}
		if (o.bonus() != null) {
			int by = DESC_Y + DESC_H - 14;
			int half = (DESC_W - 24) / 2;
			UiUtil.fit(g, this.font, "Avantage : " + o.bonus(), DESC_X + 12, by, half - 4, 0xFF7BE59A, false);
			UiUtil.fit(g, this.font, "Malus : " + o.malus(), DESC_X + 12 + half, by, half, 0xFFEE8080, false);
		}
	}

	/** Coupe un texte en lignes de largeur maximale maxW (en pixels de police, avant mise à l'échelle). */
	private List<String> wrap(String text, float maxW) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = line.length() == 0 ? word : line + " " + word;
			if (this.font.width(test) > maxW && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		return out;
	}

	private void drawButton(GuiGraphicsExtractor g, int mx, int my) {
		boolean ready = ready();
		boolean hover = ready && UiUtil.inside(mx, my, BTN_X, BTN_Y, BTN_W, BTN_H);
		if (ready) {
			g.outline(BTN_X - 2, BTN_Y - 2, BTN_W + 4, BTN_H + 4, hover ? 0xAAFFB02E : 0x66FFB02E);
			g.fillGradient(BTN_X, BTN_Y, BTN_X + BTN_W, BTN_Y + BTN_H, hover ? 0xFFF03A26 : 0xFFD42A1C, hover ? 0xFFA01408 : 0xFF7A0E06);
			g.outline(BTN_X, BTN_Y, BTN_W, BTN_H, FRAME_GOLD);
			g.outline(BTN_X + 1, BTN_Y + 1, BTN_W - 2, BTN_H - 2, 0x88FFE08A);
			UiUtil.centeredScaled(g, this.font, "Commencer l'aventure", BTN_X + BTN_W / 2, BTN_Y + 11, 1.0F, 0xFFFFFFFF, true);
		} else {
			int count = 0;
			for (int s : selected) {
				if (s >= 0) count++;
			}
			g.fill(BTN_X, BTN_Y, BTN_X + BTN_W, BTN_Y + BTN_H, 0xCC1A1620);
			g.outline(BTN_X, BTN_Y, BTN_W, BTN_H, 0xFF5A4A3A);
			UiUtil.centeredScaled(g, this.font, "Choix : " + count + " / 3", BTN_X + BTN_W / 2, BTN_Y + 11, 1.0F, 0xFF8A8490, false);
		}
	}
}
