package minepiece.noah.client;

import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerLevel;
import minepiece.noah.data.PlayerProfile;
import minepiece.noah.item.ModItems;
import minepiece.noah.level.LevelSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Interface du profil (touche K), en forme d'avis de recherche, avec 3 onglets :
 *  PROFIL (nom, faction, race, prime), NIVEAU (niveau et XP) et FRUIT (fruit du démon).
 * Toutes les données sont lues en direct chaque image : l'écran se met à jour tout seul.
 */
public class ProfileScreen extends Screen {
	private static final int W = 170;
	private static final int H = 232;

	private static final int INK = 0xFF3B2A14;        // encre foncée
	private static final int INK_LIGHT = 0xFF7A5A2E;  // encre claire (libellés)
	private static final int LINE = 0xFF8A6A38;

	private static final int TAB_W = 54;
	private static final int TAB_H = 16;
	private static final int TAB_GAP = 2;

	private enum Tab {
		PROFILE("Profil"), LEVEL("Niveau"), FRUIT("Fruit");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private Tab tab = Tab.PROFILE;

	public ProfileScreen() {
		super(Component.literal("Profil"));
	}

	/** Comme l'inventaire : le jeu continue de tourner en solo. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (ProfileKey.matches(event)) {
			this.onClose();
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_RIGHT) {
			selectTab(1);
			return true;
		}
		if (key == InputConstants.KEY_LEFT) {
			selectTab(-1);
			return true;
		}
		return super.keyPressed(event);
	}

	private void selectTab(int direction) {
		Tab[] tabs = Tab.values();
		this.tab = tabs[(this.tab.ordinal() + direction + tabs.length) % tabs.length];
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int mx = (int) event.x();
			int my = (int) event.y();
			int px = (this.width - W) / 2;
			int py = paperY();
			Tab[] tabs = Tab.values();
			for (int i = 0; i < tabs.length; i++) {
				if (UiUtil.inside(mx, my, px + i * (TAB_W + TAB_GAP), py - TAB_H, TAB_W, TAB_H)) {
					this.tab = tabs[i];
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Haut du papier : on garde la place des onglets au-dessus. */
	private int paperY() {
		return Math.max(4 + TAB_H, (this.height - H) / 2);
	}

	/** Le fond est dessiné dans extractRenderState : on neutralise celui de Minecraft. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		g.fillGradient(0, 0, this.width, this.height, 0xC0101A28, 0xE0050A12);
		super.extractRenderState(g, mx, my, delta);

		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}

		int px = (this.width - W) / 2;
		int py = paperY();
		int cx = this.width / 2;

		// ---- Papier (commun à tous les onglets)
		g.fill(px + 4, py + 4, px + W + 4, py + H + 4, 0x66000000); // ombre
		g.fillGradient(px, py, px + W, py + H, 0xFFEBD9A8, 0xFFD3B77B);
		g.outline(px, py, W, H, 0xFF4A3216);
		g.outline(px + 1, py + 1, W - 2, H - 2, 0xFF4A3216);
		g.outline(px + 5, py + 5, W - 10, H - 10, LINE);
		nail(g, px + 9, py + 9);
		nail(g, px + W - 12, py + 9);
		nail(g, px + 9, py + H - 12);
		nail(g, px + W - 12, py + H - 12);

		drawTabs(g, px, py, mx, my);

		switch (this.tab) {
			case PROFILE -> drawProfile(g, player, px, py, cx, mx, my);
			case LEVEL -> drawLevel(g, player, px, py, cx);
			case FRUIT -> drawFruit(g, player, px, py, cx);
		}

		// ---- Aide
		if (py + H + 14 <= this.height) {
			UiUtil.centered(g, this.font, "[" + ProfileKey.keyName() + "] ou Échap pour fermer  -  flèches : changer d'onglet", cx, py + H + 6, UiUtil.TEXT_DIM, false);
		}
	}

	// ------------------------------------------------------------------ onglets

	private void drawTabs(GuiGraphicsExtractor g, int px, int py, int mx, int my) {
		Tab[] tabs = Tab.values();
		for (int i = 0; i < tabs.length; i++) {
			int x = px + i * (TAB_W + TAB_GAP);
			boolean selected = tabs[i] == this.tab;
			boolean hover = UiUtil.inside(mx, my, x, py - TAB_H, TAB_W, TAB_H);
			int top = selected ? py - TAB_H : py - TAB_H + 3; // l'onglet actif est plus haut

			g.fillGradient(x, top, x + TAB_W, py + 1, selected ? 0xFFEBD9A8 : (hover ? 0xFFCDB173 : 0xFFB99C5E),
					selected ? 0xFFE3CE98 : 0xFFA88A4E);
			g.outline(x, top, TAB_W, py + 1 - top, 0xFF4A3216);
			if (selected) {
				g.fill(x + 1, py, x + TAB_W - 1, py + 2, 0xFFE3CE98); // fusionne l'onglet avec le papier
			}
			UiUtil.centered(g, this.font, tabs[i].label, x + TAB_W / 2, top + (py - top - 8) / 2 + 1, selected ? INK : INK_LIGHT, false);
		}
	}

	// ------------------------------------------------------------------ onglet PROFIL (inchangé)

	private void drawProfile(GuiGraphicsExtractor g, LocalPlayer player, int px, int py, int cx, int mx, int my) {
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
		long bounty = ModAttachments.bounty(player);

		// ---- Titre
		UiUtil.centeredScaled(g, this.font, "WANTED", cx, py + 10, 2.6F, INK, false);

		// ---- Portrait (le joueur, avec ses ailes / nageoires de race)
		int fx = cx - 45;
		int fy = py + 40;
		g.fillGradient(fx, fy, fx + 90, fy + 92, 0xFFDCCB9B, 0xFFC4AE74);
		g.outline(fx, fy, 90, 92, INK);
		g.outline(fx + 1, fy + 1, 88, 90, 0x558A6A38);
		InventoryScreen.extractEntityInInventoryFollowsMouse(g, fx + 3, fy + 3, fx + 87, fy + 89, 38, 0.0625F,
				(float) mx, (float) my, player);

		// ---- Nom
		String name = player.getName().getString().toUpperCase(Locale.ROOT);
		float nameScale = Math.min(1.5F, (W - 24) / (float) this.font.width(name));
		UiUtil.centeredScaled(g, this.font, name, cx, py + 138 + (int) ((13 - 9 * nameScale) / 2), nameScale, INK, false);
		g.fill(px + 14, py + 157, px + W - 14, py + 158, LINE);

		// ---- Faction et race
		if (profile != null) {
			infoRow(g, px, py + 163, "FACTION", profile.faction().displayName(), UiUtil.color(profile.faction()));
			infoRow(g, px, py + 177, "RACE", profile.race().displayName(), UiUtil.color(profile.race()));
		} else {
			infoRow(g, px, py + 163, "FACTION", "—", 0xFF9A8A6A);
			infoRow(g, px, py + 177, "RACE", "—", 0xFF9A8A6A);
		}
		g.fill(px + 14, py + 191, px + W - 14, py + 192, LINE);

		// ---- Prime
		UiUtil.centered(g, this.font, "PRIME", cx, py + 197, INK_LIGHT, false);
		String amount = UiUtil.BERRY + " " + UiUtil.formatBerries(bounty);
		float amountScale = Math.min(2.0F, (W - 24) / (float) this.font.width(amount));
		UiUtil.centeredScaled(g, this.font, amount, cx, py + 208 + (int) ((18 - 9 * amountScale) / 2), amountScale, 0xFF8E1B1B, false);
	}

	// ------------------------------------------------------------------ onglet NIVEAU

	private void drawLevel(GuiGraphicsExtractor g, LocalPlayer player, int px, int py, int cx) {
		PlayerLevel data = ModAttachments.level(player);
		boolean max = LevelSystem.isMaxLevel(data.level());
		long needed = LevelSystem.xpToNext(data.level());
		double progress = LevelSystem.progress(data);
		int percent = (int) Math.floor(progress * 100.0);

		UiUtil.centeredScaled(g, this.font, "NIVEAU", cx, py + 10, 2.6F, INK, false);
		g.fill(px + 14, py + 36, px + W - 14, py + 37, LINE);

		// ---- Gros chiffre du niveau
		UiUtil.centered(g, this.font, max ? "NIVEAU MAXIMUM" : "NIVEAU ACTUEL", cx, py + 46, INK_LIGHT, false);
		String level = Integer.toString(data.level());
		float levelScale = Math.min(4.5F, (W - 40) / (float) this.font.width(level));
		UiUtil.centeredScaled(g, this.font, level, cx, py + 62 + (int) ((36 - 9 * levelScale) / 2), levelScale, 0xFF8E1B1B, false);
		g.fill(px + 14, py + 108, px + W - 14, py + 109, LINE);

		// ---- Détails
		valueRow(g, px, py + 118, "XP", max ? "—" : data.xp() + " / " + needed);
		valueRow(g, px, py + 134, "XP NÉCESSAIRE", max ? "—" : Long.toString(needed));
		valueRow(g, px, py + 150, "PROGRESSION", percent + " %");

		// ---- Barre de progression
		int bx = px + 16;
		int by = py + 168;
		int bw = W - 32;
		int bh = 14;
		g.fill(bx, by, bx + bw, by + bh, 0xFF5A4122);
		int filled = (int) Math.round((bw - 2) * progress);
		if (filled > 0) {
			g.fillGradient(bx + 1, by + 1, bx + 1 + filled, by + bh - 1, 0xFFF5D76E, 0xFFD98E1F);
			g.fill(bx + 1, by + 1, bx + 1 + filled, by + 2, 0x66FFFFFF); // reflet
		}
		g.outline(bx, by, bw, bh, INK);
		UiUtil.centered(g, this.font, percent + " %", cx, by + 3, 0xFFFFFFFF, true);
	}

	// ------------------------------------------------------------------ onglet FRUIT

	private void drawFruit(GuiGraphicsExtractor g, LocalPlayer player, int px, int py, int cx) {
		// Lu en direct : change tout seul quand le joueur mange un fruit ou perd son pouvoir.
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);

		UiUtil.centeredScaled(g, this.font, "FRUIT", cx, py + 10, 2.6F, INK, false);
		g.fill(px + 14, py + 36, px + W - 14, py + 37, LINE);

		int fx = cx - 45;
		int fy = py + 42;
		g.fillGradient(fx, fy, fx + 90, fy + 70, 0xFFDCCB9B, 0xFFC4AE74);
		g.outline(fx, fy, 90, 70, INK);
		g.outline(fx + 1, fy + 1, 88, 68, 0x558A6A38);

		if (fruit == null) {
			// Aucun faux fruit : juste la mention claire.
			UiUtil.centeredScaled(g, this.font, "Sans fruit", cx, fy + 29, 1.6F, INK_LIGHT, false);
			UiUtil.centered(g, this.font, "Trouve et mange un fruit", cx, py + 124, INK_LIGHT, false);
			UiUtil.centered(g, this.font, "du démon pour l'obtenir.", cx, py + 136, INK_LIGHT, false);
			return;
		}

		// ---- Icône du fruit (agrandie x4)
		ItemStack icon = new ItemStack(ModItems.of(fruit));
		g.pose().pushMatrix();
		g.pose().translate(cx - 32, fy + 3);
		g.pose().scale(4.0F, 4.0F);
		g.item(icon, 0, 0);
		g.pose().popMatrix();

		// ---- Nom exact
		String name = fruit.displayName();
		float nameScale = Math.min(1.5F, (W - 24) / (float) this.font.width(name));
		UiUtil.centeredScaled(g, this.font, name, cx, py + 118 + (int) ((13 - 9 * nameScale) / 2), nameScale, INK, false);

		// ---- Type
		if (fruit.isLogia()) {
			infoRow(g, px, py + 134, "TYPE", "Logia", 0xFFD94545);
		}
		g.fill(px + 14, py + 147, px + W - 14, py + 148, LINE);

		// ---- Description
		List<FormattedCharSequence> lines = this.font.split(Component.literal(fruit.description()), W - 32);
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			g.text(this.font, lines.get(i), px + 16, py + 152 + i * 10, INK, false);
		}

		// ---- Attaques
		// Prévu pour plus tard : rang d'éveil, maîtrise, informations supplémentaires du fruit (à ajouter ici).
		int ay = py + 186;
		g.fill(px + 14, ay - 4, px + W - 14, ay - 3, LINE);
		for (int slot = 0; slot < DevilFruit.ABILITY_COUNT; slot++) {
			String cooldown = seconds(fruit.cooldownTicks(slot));
			g.text(this.font, (slot + 1) + ".", px + 16, ay + slot * 11, INK_LIGHT, false);
			UiUtil.fit(g, this.font, fruit.abilityName(slot), px + 27, ay + slot * 11, 82, INK, false);
			g.text(this.font, cooldown, px + W - 16 - this.font.width(cooldown), ay + slot * 11, INK_LIGHT, false);
		}
	}

	/** 30 -> "1,5 s", 160 -> "8 s". */
	private static String seconds(int ticks) {
		return ticks % 20 == 0 ? (ticks / 20) + " s" : String.format(Locale.FRENCH, "%.1f s", ticks / 20.0);
	}

	/** Une ligne « LIBELLÉ ........ valeur » sans pastille. */
	private void valueRow(GuiGraphicsExtractor g, int px, int y, String label, String value) {
		g.text(this.font, label, px + 16, y, INK_LIGHT, false);
		g.text(this.font, value, px + W - 16 - this.font.width(value), y, INK, false);
	}

	/** Une ligne « LIBELLÉ ........ valeur » avec une pastille de couleur. */
	private void infoRow(GuiGraphicsExtractor g, int px, int y, String label, String value, int color) {
		g.text(this.font, label, px + 16, y, INK_LIGHT, false);

		int valueX = px + W - 16 - this.font.width(value);
		g.text(this.font, value, valueX, y, INK, false);

		int sx = valueX - 11;
		g.fill(sx, y, sx + 7, y + 8, color);
		g.outline(sx, y, 7, 8, INK);
	}

	/** Petit clou aux coins de l'affiche. */
	private void nail(GuiGraphicsExtractor g, int x, int y) {
		g.fill(x, y, x + 3, y + 3, 0xFF4A3216);
		g.fill(x, y, x + 1, y + 1, 0xFFB08A50);
	}
}
