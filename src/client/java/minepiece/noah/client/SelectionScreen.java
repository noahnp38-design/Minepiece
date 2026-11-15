package minepiece.noah.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

/** Fenêtre de choix de la race, de la faction et du style de combat. */
public class SelectionScreen extends Screen {
	/** Une option cliquable : nom, description et couleur d'accent. */
	private record Option(String label, String description, int color, String bonus, String malus) {}

	private static final String[] HEADERS = {"Race", "Faction", "Style de combat"};
	private static final int GAP = 8;
	private static final int ROW_H = 18;
	private static final int ROW_STEP = 20;
	private static final int ROWS_OFFSET = 47;   // haut des lignes, depuis "top"
	private static final int DESC_H = 38;
	private static final int BTN_W = 140;
	private static final int BTN_H = 18;

	private final List<List<Option>> columns = new ArrayList<>();
	private final int maxRows;
	/** Index choisi dans chaque colonne (-1 = rien). L'index correspond à l'ordinal de l'enum. */
	private final int[] selected = {-1, -1, -1};
	/** Dernière option survolée ou cliquée (sert à afficher sa description). */
	private int focusCol = -1;
	private int focusIdx = -1;

	private int colW;
	private int left;
	private int top;
	private int[] starX = new int[0];
	private int[] starY = new int[0];

	public SelectionScreen() {
		super(Component.literal("Choisis ta destinée"));

		List<Option> races = new ArrayList<>();
		for (Race r : Race.values()) {
			races.add(new Option(r.displayName(), r.description(), UiUtil.color(r), r.bonus(), r.malus()));
		}
		List<Option> factions = new ArrayList<>();
		for (Faction f : Faction.values()) {
			factions.add(new Option(f.displayName(), f.description(), UiUtil.color(f), null, null));
		}
		List<Option> styles = new ArrayList<>();
		for (CombatStyle s : CombatStyle.values()) {
			styles.add(new Option(s.displayName(), s.description(), UiUtil.color(s), null, null));
		}
		columns.add(races);
		columns.add(factions);
		columns.add(styles);
		this.maxRows = Math.max(races.size(), Math.max(factions.size(), styles.size()));
	}

	// ------------------------------------------------------------------ géométrie

	private int totalW() { return colW * 3 + GAP * 2; }
	private int colX(int col) { return left + col * (colW + GAP); }
	private int rowY(int row) { return top + ROWS_OFFSET + row * ROW_STEP; }
	private int descY() { return top + ROWS_OFFSET + maxRows * ROW_STEP + 2; }
	private int btnY() { return descY() + DESC_H + 5; }
	private int btnX() { return this.width / 2 - BTN_W / 2; }

	@Override
	protected void init() {
		colW = Math.min(150, (this.width - 28 - 2 * GAP) / 3);
		left = (this.width - totalW()) / 2;
		int contentH = ROWS_OFFSET + maxRows * ROW_STEP + 2 + DESC_H + 5 + BTN_H;
		top = Math.max(8, (this.height - contentH) / 2);

		Random rnd = new Random(42);
		starX = new int[70];
		starY = new int[70];
		for (int i = 0; i < starX.length; i++) {
			starX[i] = rnd.nextInt(Math.max(1, this.width));
			starY[i] = rnd.nextInt(Math.max(1, this.height));
		}
	}

	/** @return {colonne, ligne} de l'option sous la souris, ou null. */
	private int[] cellAt(int mx, int my) {
		for (int c = 0; c < columns.size(); c++) {
			for (int i = 0; i < columns.get(c).size(); i++) {
				if (UiUtil.inside(mx, my, colX(c), rowY(i), colW, ROW_H)) {
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
			int mx = (int) event.x();
			int my = (int) event.y();

			int[] cell = cellAt(mx, my);
			if (cell != null) {
				selected[cell[0]] = cell[1];
				focusCol = cell[0];
				focusIdx = cell[1];
				click();
				return true;
			}
			if (ready() && UiUtil.inside(mx, my, btnX(), btnY(), BTN_W, BTN_H)) {
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
		UiUtil.drawOcean(g, this.width, this.height, starX, starY);
		super.extractRenderState(g, mx, my, delta);

		int total = totalW();
		int panelX = left - 12;
		int panelY = top - 10;
		int panelW = total + 24;
		int panelH = btnY() + BTN_H + 10 - panelY;

		// Panneau principal
		g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC08111C);
		g.outline(panelX, panelY, panelW, panelH, UiUtil.GOLD);
		g.outline(panelX + 2, panelY + 2, panelW - 4, panelH - 4, 0x44E2B33C);

		// Titre
		int cx = this.width / 2;
		UiUtil.centeredScaled(g, this.font, this.title.getString(), cx, top, 1.6F, UiUtil.GOLD_LIGHT, true);
		UiUtil.centered(g, this.font, "Ton aventure sur Grand Line commence ici", cx, top + 18, 0xFF8FB3D9, false);
		g.fill(cx - 70, top + 29, cx + 70, top + 30, 0x88E2B33C);
		g.fill(cx - 2, top + 28, cx + 2, top + 32, UiUtil.GOLD);

		// Survol
		int[] hovered = cellAt(mx, my);
		if (hovered != null) {
			focusCol = hovered[0];
			focusIdx = hovered[1];
		}

		// Colonnes
		for (int c = 0; c < columns.size(); c++) {
			int x = colX(c);
			UiUtil.centered(g, this.font, HEADERS[c].toUpperCase(), x + colW / 2, top + 34, UiUtil.GOLD, false);
			g.fill(x, top + 44, x + colW, top + 45, 0x66E2B33C);

			List<Option> options = columns.get(c);
			for (int i = 0; i < options.size(); i++) {
				drawCell(g, options.get(i), x, rowY(i), c, i, hovered != null && hovered[0] == c && hovered[1] == i);
			}
		}

		drawDescription(g, left, descY(), total);
		drawConfirm(g, mx, my);
	}

	private void drawCell(GuiGraphicsExtractor g, Option o, int x, int y, int col, int idx, boolean hover) {
		boolean isSelected = selected[col] == idx;

		int bg = isSelected ? 0xDD2B2410 : hover ? 0xDD1F3249 : 0xAA101B2A;
		int border = isSelected ? UiUtil.GOLD : hover ? 0xFF5A7CA3 : 0x33FFFFFF;
		g.fill(x, y, x + colW, y + ROW_H, bg);
		g.outline(x, y, colW, ROW_H, border);
		g.fill(x, y, x + 3, y + ROW_H, o.color());

		// Pastille avec l'initiale
		int bx = x + 8;
		int by = y + 2;
		g.fill(bx, by, bx + 13, by + 13, o.color());
		g.outline(bx, by, 13, 13, 0x66000000);
		String initial = o.label().substring(0, 1).toUpperCase();
		g.text(this.font, initial, bx + (13 - this.font.width(initial)) / 2, by + 3, 0xFF111111, false);

		// Nom
		int maxW = colW - 26 - (isSelected ? 14 : 6);
		UiUtil.fit(g, this.font, o.label(), x + 26, y + 5, maxW, isSelected ? UiUtil.GOLD_LIGHT : UiUtil.TEXT, true);

		// Repère « choisi »
		if (isSelected) {
			g.fill(x + colW - 11, y + 6, x + colW - 5, y + 12, UiUtil.GOLD);
		}
	}

	private void drawDescription(GuiGraphicsExtractor g, int x, int y, int w) {
		g.fill(x, y, x + w, y + DESC_H, 0xB0101B2A);
		g.outline(x, y, w, DESC_H, 0x55E2B33C);

		if (focusCol < 0) {
			UiUtil.centered(g, this.font, "Survole une option pour en savoir plus.", x + w / 2, y + 15, UiUtil.TEXT_DIM, false);
			return;
		}
		Option o = columns.get(focusCol).get(focusIdx);
		g.fill(x + 6, y + 4, x + 9, y + 13, o.color());
		g.text(this.font, HEADERS[focusCol] + "  ·  " + o.label(), x + 13, y + 4, UiUtil.TEXT, true);
		UiUtil.fit(g, this.font, o.description(), x + 8, y + 14, w - 16, 0xFFB8C4D4, false);

		if (o.bonus() != null) {
			int half = (w - 16) / 2;
			UiUtil.fit(g, this.font, "+ " + o.bonus(), x + 8, y + 26, half - 6, 0xFF6FD08C, false);
			UiUtil.fit(g, this.font, "- " + o.malus(), x + 8 + half, y + 26, half, 0xFFE87070, false);
		}
	}

	private void drawConfirm(GuiGraphicsExtractor g, int mx, int my) {
		int x = btnX();
		int y = btnY();
		boolean ready = ready();
		boolean hover = ready && UiUtil.inside(mx, my, x, y, BTN_W, BTN_H);

		String label;
		if (ready) {
			label = "Confirmer";
			g.fillGradient(x, y, x + BTN_W, y + BTN_H, hover ? 0xFFF5D76E : 0xFFE9BC45, hover ? 0xFFD6A12A : 0xFFB8860B);
			g.outline(x, y, BTN_W, BTN_H, 0xFF7A5A12);
			UiUtil.centered(g, this.font, label, x + BTN_W / 2, y + 5, 0xFF2A1B00, false);
		} else {
			int count = 0;
			for (int s : selected) {
				if (s >= 0) count++;
			}
			label = "Choix : " + count + " / 3";
			g.fill(x, y, x + BTN_W, y + BTN_H, 0xAA1A2430);
			g.outline(x, y, BTN_W, BTN_H, 0x44FFFFFF);
			UiUtil.centered(g, this.font, label, x + BTN_W / 2, y + 5, 0xFF7C8796, false);
		}
	}
}
