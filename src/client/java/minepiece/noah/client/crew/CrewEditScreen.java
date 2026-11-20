package minepiece.noah.client.crew;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import minepiece.noah.client.ProfileScreen;
import minepiece.noah.client.UiUtil;
import minepiece.noah.crew.CrewLogo;
import minepiece.noah.crew.CrewManager;
import minepiece.noah.data.Faction;
import minepiece.noah.network.CrewActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Création / modification d'un équipage : nom + logo 12 x 12 à dessiner pixel par pixel.
 * Outils : pinceau (clic gauche maintenu), pot de peinture (remplit une zone), pipette (reprend une couleur) ;
 * clic droit : effacer. Ctrl+Z : annuler. Le serveur valide ; sa réponse s'affiche dans l'écran.
 */
public class CrewEditScreen extends Screen {
	private static final int W = 300;
	private static final int H = 214;
	private static final int CELL = 11;
	private static final int TOOL_BRUSH = 0;
	private static final int TOOL_FILL = 1;
	private static final int TOOL_PICK = 2;
	private static final String[] TOOL_NAMES = {"Pinceau", "Pot", "Pipette"};

	/** Un bouton : zone, texte, état "enfoncé", action. kind : 0 bouton, 1 interrupteur, 2 case de couleur. */
	private record Btn(int x, int y, int w, int h, String label, boolean on, int kind, int colorIndex, Runnable action) {}

	private final boolean create;
	private final Faction faction;
	private final byte[] logo;
	private final ArrayDeque<byte[]> undo = new ArrayDeque<>();
	private String name;
	private int color = 1;
	private int tool = TOOL_BRUSH;
	private int preset = 0;
	/** Bouton de souris en train de peindre (0 = gauche, 1 = droit, -1 = aucun). */
	private int painting = -1;
	private String error;
	private boolean waiting = false;
	private int waitMessageId;
	private long waitSince;
	private EditBox nameBox;

	public CrewEditScreen(boolean create, String name, byte[] logo, Faction faction) {
		super(Component.literal(create ? "Créer un équipage" : "Modifier l'équipage"));
		this.create = create;
		this.name = name == null ? "" : name;
		this.logo = logo == null || logo.length != CrewLogo.CELLS ? CrewLogo.preset(faction, 0) : logo.clone();
		this.faction = faction;
	}

	private int px() {
		return (this.width - W) / 2;
	}

	private int py() {
		return Math.max(2, (this.height - H) / 2);
	}

	private int gridX() {
		return px() + 12;
	}

	private int gridY() {
		return py() + 52;
	}

	@Override
	protected void init() {
		this.nameBox = new EditBox(this.font, px() + 12, py() + 28, 200, 16, Component.literal("Nom"));
		this.nameBox.setMaxLength(CrewManager.NAME_MAX);
		this.nameBox.setValue(this.name);
		this.nameBox.setHint(Component.literal("Nom de l'équipage (3 à 24 caractères)"));
		this.nameBox.setResponder(v -> {
			this.name = v;
			this.error = null;
		});
		addRenderableWidget(this.nameBox);
		setInitialFocus(this.nameBox);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(new ProfileScreen(true));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		tickPainting(mx, my);
		tickWaiting();

		int px = px();
		int py = py();
		g.fillGradient(0, 0, this.width, this.height, 0xC0101A28, 0xE0050A12);
		g.fill(px, py, px + W, py + H, 0xF0161220);
		g.outline(px, py, W, H, UiUtil.GOLD);
		g.outline(px + 3, py + 3, W - 6, H - 6, 0xFF6E5418);
		UiUtil.centered(g, this.font, create ? "CRÉER : " + kindUpper() : "MODIFIER : " + kindUpper(),
				px + W / 2, py + 10, UiUtil.GOLD_LIGHT, true);

		super.extractRenderState(g, mx, my, delta);

		// ---- grille du logo
		int gx = gridX();
		int gy = gridY();
		int size = CELL * CrewLogo.SIZE;
		for (int y = 0; y < CrewLogo.SIZE; y++) {
			for (int x = 0; x < CrewLogo.SIZE; x++) {
				int bg = ((x + y) & 1) == 0 ? 0xFF2E2A38 : 0xFF25222E;
				g.fill(gx + x * CELL, gy + y * CELL, gx + x * CELL + CELL, gy + y * CELL + CELL, bg);
			}
		}
		ClientCrew.drawLogo(g, this.logo, gx, gy, CELL);
		g.outline(gx - 1, gy - 1, size + 2, size + 2, UiUtil.GOLD);
		if (UiUtil.inside(mx, my, gx, gy, size, size)) {
			int cx = (mx - gx) / CELL;
			int cy = (my - gy) / CELL;
			// aperçu de la couleur sous le curseur
			if (this.tool == TOOL_BRUSH && this.color > 0) {
				g.fill(gx + cx * CELL + 2, gy + cy * CELL + 2, gx + cx * CELL + CELL - 2, gy + cy * CELL + CELL - 2,
						(CrewLogo.PALETTE[this.color] & 0x00FFFFFF) | 0x99000000);
			}
			g.outline(gx + cx * CELL, gy + cy * CELL, CELL, CELL, 0xFFFFFFFF);
		}
		UiUtil.text(g, this.font, "Gauche : " + TOOL_NAMES[this.tool].toLowerCase() + "   Droit : gomme", gx, gy + size + 4,
				UiUtil.TEXT_DIM, false);

		// ---- aperçu
		int rx = px + 194;
		UiUtil.text(g, this.font, "Aperçu", rx, gy - 2, UiUtil.TEXT_DIM, false);
		g.fill(rx, gy + 8, rx + 52, gy + 60, 0x40000000);
		ClientCrew.drawLogo(g, this.logo, rx + 2, gy + 10, 4);
		g.outline(rx, gy + 8, 52, 52, 0xFF6E5418);
		ClientCrew.drawLogo(g, this.logo, rx + 58, gy + 10, 2);
		ClientCrew.drawLogo(g, this.logo, rx + 58, gy + 38, 1);

		// ---- palette, outils, boutons (mêmes zones que pour les clics)
		for (Btn b : buttons()) {
			drawButton(g, b, mx, my);
		}

		if (this.error != null) {
			List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.literal(this.error), rx - px - 20);
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				g.text(this.font, lines.get(i), px + 12, py + H - 26 + i * 10, 0xFFFF6B5A, false);
			}
		}
	}

	private String kindUpper() {
		return switch (this.faction) {
			case MARINE -> "UNITÉ DE LA MARINE";
			case REVOLUTIONARY -> "CELLULE RÉVOLUTIONNAIRE";
			default -> "ÉQUIPAGE PIRATE";
		};
	}

	// ------------------------------------------------------------------ dessin

	/** Peinture continue tant qu'un bouton reste enfoncé (ne dépend pas de l'événement "glisser"). */
	private void tickPainting(int mx, int my) {
		if (this.painting < 0) {
			return;
		}
		boolean held = this.painting == 0 ? this.minecraft.mouseHandler.isLeftPressed() : this.minecraft.mouseHandler.isRightPressed();
		if (!held) {
			this.painting = -1;
			return;
		}
		if (this.painting == 1 || this.tool == TOOL_BRUSH) {
			applyAt(mx, my, false);
		}
	}

	/** Coordonnées de grille sous la souris, ou -1. */
	private int cellAt(double mx, double my) {
		int x = (int) Math.floor((mx - gridX()) / CELL);
		int y = (int) Math.floor((my - gridY()) / CELL);
		if (x < 0 || y < 0 || x >= CrewLogo.SIZE || y >= CrewLogo.SIZE) {
			return -1;
		}
		return y * CrewLogo.SIZE + x;
	}

	/** Applique l'outil à la case sous la souris. first = premier clic (pot / pipette ne s'appliquent qu'au clic). */
	private boolean applyAt(double mx, double my, boolean first) {
		int i = cellAt(mx, my);
		if (i < 0) {
			return false;
		}
		this.error = null;
		if (this.painting == 1) {
			this.logo[i] = 0;
			return true;
		}
		switch (this.tool) {
			case TOOL_FILL -> {
				if (first) {
					floodFill(i, (byte) this.color);
				}
			}
			case TOOL_PICK -> {
				if (first) {
					this.color = this.logo[i];
					this.tool = TOOL_BRUSH;
				}
			}
			default -> this.logo[i] = (byte) this.color;
		}
		return true;
	}

	private void floodFill(int start, byte to) {
		byte from = this.logo[start];
		if (from == to) {
			return;
		}
		ArrayDeque<Integer> q = new ArrayDeque<>();
		q.add(start);
		while (!q.isEmpty()) {
			int i = q.poll();
			if (this.logo[i] != from) {
				continue;
			}
			this.logo[i] = to;
			int x = i % CrewLogo.SIZE;
			int y = i / CrewLogo.SIZE;
			if (x > 0) q.add(i - 1);
			if (x < CrewLogo.SIZE - 1) q.add(i + 1);
			if (y > 0) q.add(i - CrewLogo.SIZE);
			if (y < CrewLogo.SIZE - 1) q.add(i + CrewLogo.SIZE);
		}
	}

	private void pushUndo() {
		this.undo.push(this.logo.clone());
		while (this.undo.size() > 40) {
			this.undo.removeLast();
		}
	}

	private void popUndo() {
		byte[] prev = this.undo.poll();
		if (prev != null) {
			System.arraycopy(prev, 0, this.logo, 0, CrewLogo.CELLS);
		}
	}

	// ------------------------------------------------------------------ validation

	private void submit() {
		if (this.waiting) {
			return;
		}
		String clean = this.name.trim().replaceAll("\\s+", " ");
		if (clean.length() < CrewManager.NAME_MIN || clean.length() > CrewManager.NAME_MAX) {
			this.error = "Le nom doit faire entre " + CrewManager.NAME_MIN + " et " + CrewManager.NAME_MAX + " caractères.";
			return;
		}
		for (int i = 0; i < clean.length(); i++) {
			char c = clean.charAt(i);
			if (!(Character.isLetterOrDigit(c) || " '-_.!".indexOf(c) >= 0)) {
				this.error = "Caractère interdit dans le nom : « " + c + " »";
				return;
			}
		}
		boolean empty = true;
		for (byte b : this.logo) {
			if (b != 0) {
				empty = false;
				break;
			}
		}
		if (empty) {
			this.error = "Dessine au moins un pixel sur ton logo.";
			return;
		}
		this.waiting = true;
		this.waitMessageId = ClientCrew.messageId();
		this.waitSince = System.currentTimeMillis();
		ClientCrew.send(new CrewActionPayload(this.create ? CrewActionPayload.CREATE : CrewActionPayload.EDIT, clean, this.logo.clone()));
	}

	/** Attend la réponse du serveur : succès -> retour à l'onglet Équipage ; refus -> message affiché ici. */
	private void tickWaiting() {
		if (!this.waiting) {
			return;
		}
		if (ClientCrew.messageId() != this.waitMessageId) {
			this.waiting = false;
			if (ClientCrew.messageOk()) {
				onClose();
			} else {
				this.error = ClientCrew.message();
			}
		} else if (System.currentTimeMillis() - this.waitSince > 4000L) {
			this.waiting = false;
			this.error = "Pas de réponse du serveur. Réessaie.";
		}
	}

	// ------------------------------------------------------------------ boutons

	/** Tous les boutons de l'écran, calculés à partir de la taille de la fenêtre (utilisés pour le dessin ET les clics). */
	private List<Btn> buttons() {
		List<Btn> list = new ArrayList<>();
		int px = px();
		int py = py();
		int gy = gridY();

		// palette : 2 colonnes x 8
		int sx = px + 152;
		for (int i = 0; i < CrewLogo.PALETTE.length; i++) {
			final int idx = i;
			list.add(new Btn(sx + (i / 8) * 17, gy + (i % 8) * 16, 14, 14, "", i == this.color, 2, i, () -> {
				this.color = idx;
				if (this.tool == TOOL_PICK) {
					this.tool = TOOL_BRUSH;
				}
			}));
		}

		int rx = px + 194;
		int rw = W - 12 - (rx - px);
		int ty = gy + 66;
		int tw = (rw - 4) / 3;
		for (int t = 0; t < 3; t++) {
			final int tt = t;
			list.add(new Btn(rx + t * (tw + 2), ty, tw, 14, TOOL_NAMES[t], this.tool == t, 1, 0, () -> this.tool = tt));
		}
		list.add(new Btn(rx, ty + 18, rw, 14, "Modèle suivant", false, 0, 0, () -> {
			pushUndo();
			this.preset++;
			System.arraycopy(CrewLogo.preset(this.faction, this.preset), 0, this.logo, 0, CrewLogo.CELLS);
		}));
		int half = (rw - 2) / 2;
		list.add(new Btn(rx, ty + 34, half, 14, "Effacer", false, 0, 0, () -> {
			pushUndo();
			Arrays.fill(this.logo, (byte) 0);
		}));
		list.add(new Btn(rx + half + 2, ty + 34, half, 14, "Annuler", false, 0, 0, this::popUndo));

		int by = py + H - 26;
		list.add(new Btn(rx, by, half, 18, this.waiting ? "..." : (create ? "Créer" : "Valider"), false, 0, 0, this::submit));
		list.add(new Btn(rx + half + 2, by, half, 18, "Retour", false, 0, 0, this::onClose));
		return list;
	}

	private void drawButton(GuiGraphicsExtractor g, Btn b, int mx, int my) {
		boolean hover = UiUtil.inside(mx, my, b.x(), b.y(), b.w(), b.h());
		if (b.kind() == 2) {
			int x = b.x();
			int y = b.y();
			if (b.colorIndex() == 0) {
				g.fill(x, y, x + 14, y + 14, 0xFF2E2A38);
				g.fill(x + 1, y + 6, x + 13, y + 8, 0xFFD62828);
			} else {
				g.fill(x, y, x + 14, y + 14, CrewLogo.PALETTE[b.colorIndex()]);
			}
			g.outline(x - 1, y - 1, 16, 16, b.on() ? UiUtil.GOLD_LIGHT : hover ? 0xFFB0A890 : 0xFF3A3640);
			if (b.on()) {
				g.outline(x - 2, y - 2, 18, 18, UiUtil.GOLD);
			}
			return;
		}
		boolean on = b.kind() == 1 && b.on();
		g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), on ? 0xFF6E5418 : hover ? 0xFF4A3A14 : 0xFF2A2210);
		g.outline(b.x(), b.y(), b.w(), b.h(), on || hover ? UiUtil.GOLD_LIGHT : UiUtil.GOLD);
		float s = Math.min(1.0F, (b.w() - 3) / (float) this.font.width(b.label()));
		UiUtil.centeredScaled(g, this.font, b.label(), b.x() + b.w() / 2, b.y() + (b.h() - Math.round(8 * s)) / 2, s,
				on ? 0xFFFFFFFF : UiUtil.GOLD_LIGHT, false);
	}

	// ------------------------------------------------------------------ souris / clavier

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int mx = (int) event.x();
		int my = (int) event.y();
		if (event.button() == 0) {
			// la palette est un peu plus large que les cases pour être facile à viser
			for (Btn b : buttons()) {
				int m = b.kind() == 2 ? 1 : 0;
				if (UiUtil.inside(mx, my, b.x() - m, b.y() - m, b.w() + 2 * m, b.h() + 2 * m)) {
					b.action().run();
					return true;
				}
			}
		}
		if ((event.button() == 0 || event.button() == 1) && cellAt(event.x(), event.y()) >= 0) {
			pushUndo();
			this.painting = event.button();
			applyAt(event.x(), event.y(), true);
			this.nameBox.setFocused(false);
			setFocused(null);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.painting >= 0) {
			if (this.painting == 1 || this.tool == TOOL_BRUSH) {
				applyAt(event.x(), event.y(), false);
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.painting = -1;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Ctrl+Z : annuler le dernier coup de pinceau (sauf pendant la saisie du nom)
		if (!this.nameBox.isFocused() && event.key() == 90 && event.hasControlDown()) {
			popUndo();
			return true;
		}
		return super.keyPressed(event);
	}
}
