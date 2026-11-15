package minepiece.noah.client;

import minepiece.noah.data.CombatStyle;
import minepiece.noah.data.Faction;
import minepiece.noah.data.Race;
import minepiece.noah.network.SelectionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Fenêtre de choix de la race, de la faction et du style de combat. */
public class SelectionScreen extends Screen {
	private static final int COL_WIDTH = 140;
	private static final int GAP = 12;
	private static final int BTN_H = 20;
	private static final int ROW = 24;

	private Race race = null;
	private Faction faction = null;
	private CombatStyle style = null;

	public SelectionScreen() {
		super(Component.literal("Choisis ta destinée"));
	}

	@Override
	protected void init() {
		int total = COL_WIDTH * 3 + GAP * 2;
		int left = (this.width - total) / 2;
		int top = this.height / 2 - 95;

		// Colonne 1 : races
		int y = top;
		for (Race r : Race.values()) {
			Button b = Button.builder(Component.literal(r.displayName()), btn -> {
				this.race = r;
				this.rebuildWidgets();
			}).bounds(left, y, COL_WIDTH, BTN_H).build();
			b.active = this.race != r; // l'option choisie est grisée
			this.addRenderableWidget(b);
			y += ROW;
		}

		// Colonne 2 : factions
		y = top;
		for (Faction f : Faction.values()) {
			Button b = Button.builder(Component.literal(f.displayName()), btn -> {
				this.faction = f;
				this.rebuildWidgets();
			}).bounds(left + COL_WIDTH + GAP, y, COL_WIDTH, BTN_H).build();
			b.active = this.faction != f;
			this.addRenderableWidget(b);
			y += ROW;
		}

		// Colonne 3 : styles de combat
		y = top;
		for (CombatStyle s : CombatStyle.values()) {
			Button b = Button.builder(Component.literal(s.displayName()), btn -> {
				this.style = s;
				this.rebuildWidgets();
			}).bounds(left + (COL_WIDTH + GAP) * 2, y, COL_WIDTH, BTN_H).build();
			b.active = this.style != s;
			this.addRenderableWidget(b);
			y += ROW;
		}

		// Bouton de confirmation (actif seulement quand les 3 choix sont faits)
		Button confirm = Button.builder(Component.literal("Confirmer"), btn -> confirm())
				.bounds(this.width / 2 - 60, top + ROW * 6 + 22, 120, BTN_H).build();
		confirm.active = race != null && faction != null && style != null;
		this.addRenderableWidget(confirm);
	}

	private void confirm() {
		if (race == null || faction == null || style == null) {
			return;
		}
		ClientPlayNetworking.send(new SelectionPayload(race.ordinal(), faction.ordinal(), style.ordinal()));
		this.minecraft.gui.setScreen(null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int total = COL_WIDTH * 3 + GAP * 2;
		int left = (this.width - total) / 2;
		int top = this.height / 2 - 95;

		drawCentered(graphics, this.title, this.width / 2, top - 38, 0xFFFFD54A);
		drawCentered(graphics, Component.literal("Race"), left + COL_WIDTH / 2, top - 14, 0xFFFFFFFF);
		drawCentered(graphics, Component.literal("Faction"), left + COL_WIDTH + GAP + COL_WIDTH / 2, top - 14, 0xFFFFFFFF);
		drawCentered(graphics, Component.literal("Style de combat"), left + (COL_WIDTH + GAP) * 2 + COL_WIDTH / 2, top - 14, 0xFFFFFFFF);

		// Description de l'option survolée / choisie, sous les colonnes
		String desc = null;
		if (race != null) desc = race.description();
		if (faction != null) desc = (desc == null ? "" : desc + "  |  ") + faction.description();
		if (style != null) desc = (desc == null ? "" : desc + "  |  ") + style.description();
		if (desc != null) {
			drawCentered(graphics, Component.literal(desc), this.width / 2, top + ROW * 6 + 4, 0xFFAAAAAA);
		}
	}

	private void drawCentered(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color) {
		graphics.text(this.font, text, centerX - this.font.width(text) / 2, y, color, true);
	}

	/** Le joueur doit faire un choix : Échap ne ferme pas la fenêtre. */
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}
}
