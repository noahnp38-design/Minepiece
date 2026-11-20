package minepiece.noah.client;

import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import minepiece.noah.MinePiece;
import minepiece.noah.client.crew.ClientCrew;
import minepiece.noah.client.crew.CrewEditScreen;
import minepiece.noah.client.crew.CrewInviteScreen;
import minepiece.noah.crew.Crew;
import minepiece.noah.crew.CrewLogo;
import minepiece.noah.crew.CrewManager;
import minepiece.noah.data.Faction;
import minepiece.noah.network.CrewActionPayload;
import minepiece.noah.network.CrewSyncPayload;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Interface du profil (touche O), en forme d'avis de recherche, avec 3 onglets :
 *  PROFIL (nom, faction, race, prime), NIVEAU (niveau et XP) et FRUIT (fruit du démon).
 * Toutes les données sont lues en direct chaque image : l'écran se met à jour tout seul.
 */
public class ProfileScreen extends Screen {
	private static final Identifier BERRY_ICON = MinePiece.id("textures/gui/berry.png");
	// Affiche « WANTED » : 960x1000 px de texture dessinés en 216x225 (la fenêtre). Le fond de l'onglet Profil porte le décor
	// (titre, cadres, icônes) ; le fond « vide » sert aux onglets Équipage et Fruit. Les onglets sont deux petites images.
	private static final Identifier BG_PROFILE = MinePiece.id("textures/gui/wanted_main.png");
	private static final Identifier BG_BLANK = MinePiece.id("textures/gui/wanted_blank.png");
	private static final Identifier TAB_ON = MinePiece.id("textures/gui/wanted_tab_on.png");
	private static final Identifier TAB_OFF = MinePiece.id("textures/gui/wanted_tab_off.png");
	private static final int TEX_W = 960;
	private static final int TEX_H = 1000;
	private static final int W = 216;
	private static final int H = 225;

	private static final int INK = 0xFF3B2A14;        // encre foncée
	private static final int INK_LIGHT = 0xFF7A5A2E;  // encre claire (libellés)
	private static final int LINE = 0xFF8A6A38;

	// Onglets : x et largeur de chaque emplacement dans la fenêtre.
	private static final int[] TAB_X = {14, 60, 114};
	private static final int[] TAB_WIDTH = {45, 52, 44};

	// Contenu des onglets Niveau / Fruit : l'intérieur du cadre.
	private static final int IX = 22;
	private static final int IW = 172;

	private enum Tab {
		PROFILE("Profil"), CREW("Équipage"), FRUIT("Fruit");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private Tab tab = Tab.PROFILE;

	/** Zones cliquables dessinées à l'image précédente (boutons de l'onglet Équipage). */
	private record Hot(int x, int y, int w, int h, Runnable action) {}

	private final List<Hot> hots = new java.util.ArrayList<>();
	private boolean crewRequested = false;
	/** Action dangereuse en attente de confirmation (deuxième clic dans les 3 s). */
	private String confirmKey;
	private long confirmUntil;

	public ProfileScreen() {
		super(Component.literal("Profil"));
	}

	/** crewTab = true : ouvre directement l'onglet Équipage (retour de l'éditeur de logo / des invitations). */
	public ProfileScreen(boolean crewTab) {
		this();
		if (crewTab) {
			this.tab = Tab.CREW;
		}
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
			for (Hot h : new java.util.ArrayList<>(this.hots)) {
				if (UiUtil.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
					h.action().run();
					return true;
				}
			}
			int px = (this.width - W) / 2;
			int py = paperY();
			Tab[] tabs = Tab.values();
			for (int i = 0; i < tabs.length; i++) {
				if (UiUtil.inside(mx, my, px + TAB_X[i], py + 2, TAB_WIDTH[i], 21)) {
					this.tab = tabs[i];
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Haut de la fenêtre (les onglets sont dans l'image). */
	private int paperY() {
		return Math.max(2, (this.height - H) / 2);
	}

	/** Le fond est dessiné dans extractRenderState : on neutralise celui de Minecraft. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		this.hots.clear();
		g.fillGradient(0, 0, this.width, this.height, 0xC0101A28, 0xE0050A12);
		super.extractRenderState(g, mx, my, delta);

		LocalPlayer player = this.minecraft.player;
		if (player == null) {
			return;
		}

		int px = (this.width - W) / 2;
		int py = paperY();
		int cx = px + W / 2;

		// ---- Affiche (commune à tous les onglets) puis onglets par-dessus
		Identifier bg = this.tab == Tab.PROFILE ? BG_PROFILE : BG_BLANK;
		g.blit(RenderPipelines.GUI_TEXTURED, bg, px, py, 0, 0, W, H, TEX_W, TEX_H, TEX_W, TEX_H);
		drawTabs(g, px, py, mx, my);

		switch (this.tab) {
			case PROFILE -> drawProfile(g, player, px, py, mx, my);
			case CREW -> drawCrew(g, player, px, py, cx, mx, my);
			case FRUIT -> drawFruit(g, player, px, py, cx);
		}

		// ---- Aide
		if (py + H + 12 <= this.height) {
			UiUtil.centered(g, this.font, "[" + ProfileKey.keyName() + "] ou Échap pour fermer  -  flèches : changer d'onglet", cx, py + H + 4, UiUtil.TEXT_DIM, false);
		}
	}

	// ------------------------------------------------------------------ onglets

	private void drawTabs(GuiGraphicsExtractor g, int px, int py, int mx, int my) {
		Tab[] tabs = Tab.values();
		// Les onglets inactifs d'abord, l'actif en dernier (il est plus haut et se fond dans l'affiche).
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < tabs.length; i++) {
				boolean selected = tabs[i] == this.tab;
				if (selected != (pass == 1)) {
					continue;
				}
				int x = px + TAB_X[i] - 1;
				int w = TAB_WIDTH[i] + 2;
				if (selected) {
					g.blit(RenderPipelines.GUI_TEXTURED, TAB_ON, x, py + 1, 0, 0, w, 24, 261, 130, 261, 130);
				} else {
					g.blit(RenderPipelines.GUI_TEXTURED, TAB_OFF, x, py + 2, 0, 0, w, 20, 243, 109, 243, 109);
					if (UiUtil.inside(mx, my, px + TAB_X[i], py + 2, TAB_WIDTH[i], 21)) {
						g.fill(x + 2, py + 4, x + w - 2, py + 20, 0x22FFFFFF);
					}
				}
				UiUtil.centered(g, this.font, tabs[i].label, px + TAB_X[i] + TAB_WIDTH[i] / 2, py + 9, selected ? INK : 0xFF2A1A0A, false);
			}
		}
	}

	// ------------------------------------------------------------------ onglet PROFIL

	private void drawProfile(GuiGraphicsExtractor g, LocalPlayer player, int px, int py, int mx, int my) {
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
		PlayerLevel level = ModAttachments.level(player);
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
		long bounty = ModAttachments.bounty(player);

		// ---- Portrait : le joueur (avec ses ailes / nageoires de race) dans le cadre de l'affiche
		InventoryScreen.extractEntityInInventoryFollowsMouse(g, px + 25, py + 58, px + 96, py + 147, 37, 0.0625F,
				(float) mx, (float) my, player);

		// ---- Nom
		String name = player.getName().getString().toUpperCase(Locale.ROOT);
		fitScaled(g, name, px + 128, py + 60, 66, 1.25F, INK);

		// ---- Niveau, barre d'XP
		boolean max = LevelSystem.isMaxLevel(level.level());
		g.text(this.font, "Niveau : " + level.level(), px + 128, py + 83, INK, false);
		String xp = max ? "MAX" : level.xp() + " / " + LevelSystem.xpToNext(level.level());
		int bx = px + 115;
		int by = py + 96;
		int bw = 78;
		int filled = (int) Math.round(bw * LevelSystem.progress(level));
		if (filled > 0) {
			g.fillGradient(bx, by, bx + filled, by + 4, 0xFFF5D76E, 0xFFD98E1F);
		}
		// XP dans la barre (petit texte blanc ombré)
		float xpScale = Math.min(0.6F, bw / (float) this.font.width(xp));
		UiUtil.scaled(g, this.font, xp, bx + (bw - this.font.width(xp) * xpScale) / 2f, by - 0.5F, xpScale, 0xFFFFFFFF, true);

		// ---- Fruit
		g.text(this.font, "Fruit :", px + 128, py + 108, INK_LIGHT, false);
		if (fruit != null) {
			g.pose().pushMatrix();
			g.pose().translate(px + 113, py + 111);
			g.pose().scale(0.75F, 0.75F);
			g.item(new ItemStack(ModItems.of(fruit)), 0, 0);
			g.pose().popMatrix();
			UiUtil.fit(g, this.font, fruit.displayName(), px + 128, py + 117, 66, INK, false);
		} else {
			g.text(this.font, "Aucun", px + 128, py + 117, INK, false);
		}

		// ---- Faction et race
		g.text(this.font, "Faction :", px + 128, py + 130, INK_LIGHT, false);
		if (profile != null) {
			coloredValue(g, profile.faction().displayName(), px + 128, py + 139, 66, UiUtil.color(profile.faction()));
			String raceLabel = "Race : ";
			int rw = this.font.width(raceLabel);
			g.text(this.font, raceLabel, px + 128, py + 156, INK_LIGHT, false);
			coloredValue(g, profile.race().displayName(), px + 128 + rw, py + 156, 66 - rw, UiUtil.color(profile.race()));
		} else {
			g.text(this.font, "—", px + 128, py + 139, INK, false);
			g.text(this.font, "Race : —", px + 128, py + 156, INK_LIGHT, false);
		}

		// ---- Argent (berries) : le portefeuille pour les futurs shops
		int wx = px + 24;
		int wy = py + 157;
		g.fill(wx, wy, wx + 74, wy + 14, 0x40603D18);
		g.outline(wx, wy, 74, 14, LINE);
		g.blit(RenderPipelines.GUI_TEXTURED, BERRY_ICON, wx + 3, wy + 1, 0, 0, 12, 12, 64, 64, 64, 64);
		String wallet = UiUtil.formatBerries(ModAttachments.berries(player));
		int ww = this.font.width(wallet);
		UiUtil.fit(g, this.font, wallet, wx + 71 - Math.min(ww, 52), wy + 3, 52, 0xFF6B4A0A, false);

		// ---- Prime
		String amount = UiUtil.BERRY + " " + UiUtil.formatBerries(bounty);
		float amountScale = Math.min(1.3F, 126F / this.font.width(amount));
		UiUtil.centeredScaled(g, this.font, amount, px + 111, py + 194 + (int) ((12 - 9 * amountScale) / 2), amountScale, 0xFF8E1B1B, false);
	}

	/** Texte écrit à (x, y) dans une largeur maximale, rétréci si besoin (échelle de départ : scale). */
	private void fitScaled(GuiGraphicsExtractor g, String text, int x, int y, int maxW, float scale, int color) {
		float s = Math.min(scale, maxW / (float) this.font.width(text));
		UiUtil.scaled(g, this.font, text, x, y + (9 * scale - 9 * s) / 2f, s, color, false);
	}

	/** Valeur en encre foncée soulignée de la couleur de la faction / de la race. */
	private void coloredValue(GuiGraphicsExtractor g, String text, int x, int y, int maxW, int color) {
		int w = this.font.width(text);
		float s = Math.min(1.0F, maxW / (float) w);
		UiUtil.fit(g, this.font, text, x, y, maxW, INK, false);
		g.fill(x, y + 9, x + Math.round(w * s), y + 10, color);
	}

	// ------------------------------------------------------------------ onglet ÉQUIPAGE

	private void drawCrew(GuiGraphicsExtractor g, LocalPlayer player, int px, int py, int cx, int mx, int my) {
		if (!this.crewRequested) {
			ClientCrew.send(new CrewActionPayload(CrewActionPayload.REFRESH));
			this.crewRequested = true;
		}
		CrewSyncPayload data = ClientCrew.get();
		CrewSyncPayload.Info info = data == null ? null : data.info();
		int x0 = px + IX;
		int x1 = px + IX + IW;

		if (info == null) {
			UiUtil.centeredScaled(g, this.font, "ÉQUIPAGE", cx, py + 33, 1.8F, INK, false);
		} else {
			float s = Math.min(1.6F, IW / (float) this.font.width(info.name()));
			UiUtil.centeredScaled(g, this.font, info.name(), cx, py + 33 + (int) ((14 - 9 * s) / 2), s, INK, false);
		}
		g.fill(x0, py + 51, x1, py + 52, LINE);

		if (data == null) {
			UiUtil.centered(g, this.font, "Chargement...", cx, py + 80, INK_LIGHT, false);
			return;
		}
		if (info != null) {
			drawCrewInfo(g, info, px, py, cx, mx, my);
		} else if (!data.allowed()) {
			UiUtil.centered(g, this.font, "Seuls les Pirates, la Marine", cx, py + 70, INK, false);
			UiUtil.centered(g, this.font, "et l'Armée Révolutionnaire", cx, py + 81, INK, false);
			UiUtil.centered(g, this.font, "peuvent former un équipage.", cx, py + 92, INK, false);
			PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
			if (profile != null) {
				UiUtil.centered(g, this.font, "Ta faction : " + profile.faction().displayName(), cx, py + 112, INK_LIGHT, false);
			}
		} else {
			drawNoCrew(g, data, player, py, cx, mx, my);
		}

		if (this.confirmKey != null && System.currentTimeMillis() < this.confirmUntil) {
			UiUtil.centered(g, this.font, "Clique encore pour confirmer", cx, py + 201, 0xFF8E1B1B, false);
		} else {
			String msg = ClientCrew.recentMessage(5000L);
			if (msg != null) {
				float s = Math.min(1.0F, IW / (float) this.font.width(msg));
				UiUtil.centeredScaled(g, this.font, msg, cx, py + 201, s, ClientCrew.messageOk() ? 0xFF2E6B1E : 0xFF8E1B1B, false);
			}
		}
	}

	private void drawNoCrew(GuiGraphicsExtractor g, CrewSyncPayload data, LocalPlayer player, int py, int cx, int mx, int my) {
		int x0 = cx - IW / 2;
		int x1 = cx + IW / 2;
		UiUtil.centered(g, this.font, "Tu n'as pas encore d'équipage.", cx, py + 58, INK_LIGHT, false);
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
		Faction faction = profile == null ? Faction.PIRATE : profile.faction();
		crewButton(g, cx - 60, py + 70, 120, 16, "Créer un équipage", mx, my,
				() -> this.minecraft.gui.setScreen(new CrewEditScreen(true, "", CrewLogo.preset(faction, 0), faction)));

		g.text(this.font, "INVITATIONS", x0, py + 95, INK_LIGHT, false);
		g.fill(x0, py + 105, x1, py + 106, LINE);
		List<CrewSyncPayload.Invite> invites = data.invites();
		if (invites.isEmpty()) {
			UiUtil.centered(g, this.font, "Aucune invitation pour l'instant.", cx, py + 116, INK_LIGHT, false);
			return;
		}
		for (int i = 0; i < Math.min(4, invites.size()); i++) {
			CrewSyncPayload.Invite inv = invites.get(i);
			int y = py + 110 + i * 18;
			g.fill(x0, y, x0 + 14, y + 14, 0x30603D18);
			ClientCrew.drawLogo(g, inv.logo(), x0 + 1, y + 1, 1);
			UiUtil.fit(g, this.font, inv.name(), x0 + 18, y, 92, INK, false);
			UiUtil.scaled(g, this.font, "cap. " + inv.captain(), x0 + 18, y + 9, 0.7F, INK_LIGHT, false);
			crewButton(g, x1 - 52, y + 1, 24, 12, "Oui", mx, my,
					() -> ClientCrew.send(new CrewActionPayload(CrewActionPayload.ACCEPT, inv.id())));
			crewButton(g, x1 - 26, y + 1, 24, 12, "Non", mx, my,
					() -> ClientCrew.send(new CrewActionPayload(CrewActionPayload.DECLINE, inv.id())));
		}
	}

	private void drawCrewInfo(GuiGraphicsExtractor g, CrewSyncPayload.Info info, int px, int py, int cx, int mx, int my) {
		int x0 = px + IX;
		int x1 = px + IX + IW;
		Faction faction = Faction.byId(info.faction());

		// ---- Logo
		int lx = x0;
		int ly = py + 56;
		g.fill(lx, ly, lx + 52, ly + 52, 0x30603D18);
		ClientCrew.drawLogo(g, info.logo(), lx + 2, ly + 2, 4);
		g.outline(lx, ly, 52, 52, INK);

		// ---- Infos
		int rx = x0 + 58;
		int rw = x1 - rx;
		if (faction != null) {
			coloredValue(g, Crew.kind(faction), rx, py + 57, rw, UiUtil.color(faction));
		}
		g.text(this.font, "Capitaine :", rx, py + 72, INK_LIGHT, false);
		UiUtil.fit(g, this.font, info.captain(), rx, py + 81, rw, 0xFF8E1B1B, false);
		g.text(this.font, "Membres : " + info.members().size() + " / " + CrewManager.MAX_MEMBERS, rx, py + 96, INK, false);

		// ---- Liste des membres (2 colonnes, 5 lignes)
		g.text(this.font, "MEMBRES", x0, py + 113, INK_LIGHT, false);
		g.fill(x0, py + 122, x1, py + 123, LINE);
		int colW = IW / 2;
		int shown = Math.min(info.members().size(), 10);
		for (int i = 0; i < shown; i++) {
			String name = info.members().get(i);
			boolean online = i < info.online().size() && info.online().get(i);
			boolean isCaptain = name.equals(info.captain());
			int x = x0 + (i / 5) * colW;
			int y = py + 126 + (i % 5) * 10;
			g.fill(x, y + 3, x + 3, y + 6, online ? 0xFF3FAE4A : 0xFF8A7F70);
			int nameW = colW - (info.isCaptain() && !isCaptain ? 16 : 6);
			UiUtil.fit(g, this.font, name, x + 5, y, nameW, isCaptain ? 0xFF8E1B1B : INK, false);
			if (info.isCaptain() && !isCaptain) {
				String key = "kick:" + name;
				boolean pending = key.equals(this.confirmKey) && System.currentTimeMillis() < this.confirmUntil;
				crewButton(g, x + colW - 11, y, 9, 9, pending ? "!" : "x", mx, my,
						confirm(key, () -> ClientCrew.send(new CrewActionPayload(CrewActionPayload.KICK, name))));
			}
		}
		if (info.members().size() > 10) {
			UiUtil.scaled(g, this.font, "+" + (info.members().size() - 10) + " autres", x1 - 34, py + 166, 0.7F, INK_LIGHT, false);
		}

		// ---- Actions (bandeau du bas)
		int by = py + 184;
		if (info.isCaptain()) {
			crewButton(g, x0, by, 40, 14, "Modifier", mx, my, () -> this.minecraft.gui.setScreen(
					new CrewEditScreen(false, info.name(), info.logo(), faction == null ? Faction.PIRATE : faction)));
			crewButton(g, x0 + 44, by, 40, 14, "Inviter", mx, my, () -> this.minecraft.gui.setScreen(new CrewInviteScreen()));
			crewButton(g, x0 + 88, by, 40, 14, label("leave", "Quitter"), mx, my,
					confirm("leave", () -> ClientCrew.send(new CrewActionPayload(CrewActionPayload.LEAVE))));
			crewButton(g, x0 + 132, by, 40, 14, label("disband", "Dissoudre"), mx, my,
					confirm("disband", () -> ClientCrew.send(new CrewActionPayload(CrewActionPayload.DISBAND))));
		} else {
			crewButton(g, cx - 40, by, 80, 14, label("leave", "Quitter l'équipage"), mx, my,
					confirm("leave", () -> ClientCrew.send(new CrewActionPayload(CrewActionPayload.LEAVE))));
		}
	}

	private String label(String key, String normal) {
		return key.equals(this.confirmKey) && System.currentTimeMillis() < this.confirmUntil ? "Sûr ?" : normal;
	}

	/** Premier clic : demande confirmation ; deuxième clic dans les 3 secondes : exécute. */
	private Runnable confirm(String key, Runnable action) {
		return () -> {
			long now = System.currentTimeMillis();
			if (key.equals(this.confirmKey) && now < this.confirmUntil) {
				this.confirmKey = null;
				action.run();
			} else {
				this.confirmKey = key;
				this.confirmUntil = now + 3000L;
			}
		};
	}

	private void crewButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my, Runnable action) {
		boolean hover = UiUtil.inside(mx, my, x, y, w, h);
		g.fill(x, y, x + w, y + h, hover ? 0x60603D18 : 0x30603D18);
		g.outline(x, y, w, h, hover ? INK : LINE);
		float s = Math.min(1.0F, (w - 3) / (float) this.font.width(label));
		UiUtil.centeredScaled(g, this.font, label, x + w / 2 + (w % 2 == 0 ? 0 : 1), y + (h - Math.round(8 * s)) / 2, s, INK, false);
		this.hots.add(new Hot(x, y, w, h, action));
	}

	// ------------------------------------------------------------------ onglet FRUIT

	private void drawFruit(GuiGraphicsExtractor g, LocalPlayer player, int px, int py, int cx) {
		// Lu en direct : change tout seul quand le joueur mange un fruit ou perd son pouvoir.
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
		int x0 = px + IX;
		int x1 = px + IX + IW;

		UiUtil.centeredScaled(g, this.font, "FRUIT", cx, py + 33, 1.8F, INK, false);
		g.fill(x0, py + 51, x1, py + 52, LINE);

		int fx = cx - 33;
		int fy = py + 56;
		g.fillGradient(fx, fy, fx + 66, fy + 44, 0x55DCCB9B, 0x55C4AE74);
		g.outline(fx, fy, 66, 44, INK);
		g.outline(fx + 1, fy + 1, 64, 42, 0x558A6A38);

		if (fruit == null) {
			// Aucun faux fruit : juste la mention claire.
			UiUtil.centeredScaled(g, this.font, "Sans fruit", cx, fy + 18, 1.4F, INK_LIGHT, false);
			UiUtil.centered(g, this.font, "Trouve et mange un fruit", cx, py + 112, INK_LIGHT, false);
			UiUtil.centered(g, this.font, "du démon pour l'obtenir.", cx, py + 124, INK_LIGHT, false);
			return;
		}

		// ---- Icône du fruit (agrandie x2,5)
		ItemStack icon = new ItemStack(ModItems.of(fruit));
		g.pose().pushMatrix();
		g.pose().translate(cx - 20, fy + 2);
		g.pose().scale(2.5F, 2.5F);
		g.item(icon, 0, 0);
		g.pose().popMatrix();

		// ---- Nom exact et type
		String name = fruit.displayName();
		float nameScale = Math.min(1.3F, IW / (float) this.font.width(name));
		UiUtil.centeredScaled(g, this.font, name, cx, py + 103, nameScale, INK, false);
		if (fruit.isLogia()) {
			valueRow(g, x0, x1, py + 115, "TYPE", "Logia");
		}

		// ---- Description
		g.fill(x0, py + 124, x1, py + 125, LINE);
		List<FormattedCharSequence> lines = this.font.split(Component.literal(fruit.description()), IW);
		for (int i = 0; i < Math.min(4, lines.size()); i++) {
			g.text(this.font, lines.get(i), x0, py + 128 + i * 9, INK, false);
		}

		// ---- Attaques (dans le bandeau du bas de l'affiche)
		// Prévu pour plus tard : rang d'éveil, maîtrise, informations supplémentaires du fruit (à ajouter ici).
		int ay = py + 180;
		g.fill(x0 - 2, ay - 4, x1 + 2, ay + 31, 0x30603D18);
		g.outline(x0 - 2, ay - 4, IW + 4, 35, LINE);
		for (int slot = 0; slot < DevilFruit.ABILITY_COUNT; slot++) {
			String cooldown = seconds(fruit.cooldownTicks(slot));
			g.text(this.font, (slot + 1) + ".", x0 + 2, ay + slot * 10, INK_LIGHT, false);
			UiUtil.fit(g, this.font, fruit.abilityName(slot), x0 + 13, ay + slot * 10, 100, INK, false);
			g.text(this.font, cooldown, x1 - 2 - this.font.width(cooldown), ay + slot * 10, INK_LIGHT, false);
		}
	}

	/** 30 -> "1,5 s", 160 -> "8 s". */
	private static String seconds(int ticks) {
		return ticks % 20 == 0 ? (ticks / 20) + " s" : String.format(Locale.FRENCH, "%.1f s", ticks / 20.0);
	}

	/** Une ligne « LIBELLÉ ........ valeur ». */
	private void valueRow(GuiGraphicsExtractor g, int x0, int x1, int y, String label, String value) {
		g.text(this.font, label, x0, y, INK_LIGHT, false);
		g.text(this.font, value, x1 - this.font.width(value), y, INK, false);
	}
}
