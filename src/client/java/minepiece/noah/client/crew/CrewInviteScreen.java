package minepiece.noah.client.crew;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import minepiece.noah.client.ProfileScreen;
import minepiece.noah.client.UiUtil;
import minepiece.noah.network.CrewActionPayload;
import minepiece.noah.network.CrewSyncPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Liste des joueurs connectés qu'on peut inviter (même faction, sans équipage) : liste fournie par le serveur. */
public class CrewInviteScreen extends Screen {
	private static final int W = 200;
	private static final int H = 180;
	private static final int ROWS = 8;
	private static final int ROW_H = 14;

	private record Hot(int x, int y, int w, int h, Runnable action) {}

	private final List<Hot> hots = new ArrayList<>();
	private final Set<String> invited = new HashSet<>();
	private int scroll = 0;

	public CrewInviteScreen() {
		super(Component.literal("Inviter"));
	}

	@Override
	protected void init() {
		ClientCrew.send(new CrewActionPayload(CrewActionPayload.REFRESH));
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

	private List<String> candidates() {
		CrewSyncPayload data = ClientCrew.get();
		return data == null ? List.of() : data.candidates();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		this.hots.clear();
		int px = (this.width - W) / 2;
		int py = Math.max(2, (this.height - H) / 2);
		g.fillGradient(0, 0, this.width, this.height, 0xC0101A28, 0xE0050A12);
		g.fill(px, py, px + W, py + H, 0xF0161220);
		g.outline(px, py, W, H, UiUtil.GOLD);
		g.outline(px + 3, py + 3, W - 6, H - 6, 0xFF6E5418);
		super.extractRenderState(g, mx, my, delta);
		UiUtil.centered(g, this.font, "INVITER UN JOUEUR", px + W / 2, py + 10, UiUtil.GOLD_LIGHT, true);

		List<String> list = candidates();
		int ly = py + 26;
		if (list.isEmpty()) {
			UiUtil.centered(g, this.font, "Aucun joueur disponible.", px + W / 2, ly + 30, UiUtil.TEXT, false);
			UiUtil.centered(g, this.font, "Il faut un joueur connecté,", px + W / 2, ly + 48, UiUtil.TEXT_DIM, false);
			UiUtil.centered(g, this.font, "de ta faction et sans équipage.", px + W / 2, ly + 58, UiUtil.TEXT_DIM, false);
		} else {
			this.scroll = Math.max(0, Math.min(this.scroll, list.size() - ROWS));
			for (int i = 0; i < ROWS && this.scroll + i < list.size(); i++) {
				String name = list.get(this.scroll + i);
				int y = ly + i * (ROW_H + 2);
				g.fill(px + 10, y, px + W - 10, y + ROW_H, 0x30FFFFFF);
				UiUtil.fit(g, this.font, name, px + 14, y + 3, W - 80, UiUtil.TEXT, false);
				boolean done = this.invited.contains(name);
				int bx = px + W - 60;
				boolean hover = !done && UiUtil.inside(mx, my, bx, y + 1, 46, ROW_H - 2);
				g.fill(bx, y + 1, bx + 46, y + ROW_H - 1, done ? 0xFF1E3A1E : hover ? 0xFF4A3A14 : 0xFF2A2210);
				g.outline(bx, y + 1, 46, ROW_H - 2, done ? 0xFF3FAE4A : UiUtil.GOLD);
				UiUtil.centered(g, this.font, done ? "Invité" : "Inviter", bx + 23, y + 3, done ? 0xFF8FE39A : UiUtil.GOLD_LIGHT, false);
				if (!done) {
					this.hots.add(new Hot(bx, y + 1, 46, ROW_H - 2, () -> {
						ClientCrew.send(new CrewActionPayload(CrewActionPayload.INVITE, name));
						this.invited.add(name);
					}));
				}
			}
			if (list.size() > ROWS) {
				UiUtil.centered(g, this.font, (this.scroll + 1) + "-" + Math.min(list.size(), this.scroll + ROWS) + " / " + list.size()
						+ "  (molette)", px + W / 2, ly + ROWS * (ROW_H + 2) + 2, UiUtil.TEXT_DIM, false);
			}
		}

		int bx = px + W / 2 - 30;
		int by = py + H - 22;
		boolean hover = UiUtil.inside(mx, my, bx, by, 60, 16);
		g.fill(bx, by, bx + 60, by + 16, hover ? 0xFF4A3A14 : 0xFF2A2210);
		g.outline(bx, by, 60, 16, hover ? UiUtil.GOLD_LIGHT : UiUtil.GOLD);
		UiUtil.centered(g, this.font, "Retour", bx + 30, by + 4, UiUtil.GOLD_LIGHT, false);
		this.hots.add(new Hot(bx, by, 60, 16, this::onClose));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int mx = (int) event.x();
			int my = (int) event.y();
			int px = (this.width - W) / 2;
			int py = Math.max(2, (this.height - H) / 2);
			// "Retour"
			if (UiUtil.inside(mx, my, px + W / 2 - 30, py + H - 22, 60, 16)) {
				onClose();
				return true;
			}
			// "Inviter" sur chaque ligne (mêmes positions que le dessin)
			List<String> list = candidates();
			int ly = py + 26;
			int scroll = Math.max(0, Math.min(this.scroll, list.size() - ROWS));
			for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
				String name = list.get(scroll + i);
				int y = ly + i * (ROW_H + 2);
				if (!this.invited.contains(name) && UiUtil.inside(mx, my, px + W - 60, y + 1, 46, ROW_H - 2)) {
					ClientCrew.send(new CrewActionPayload(CrewActionPayload.INVITE, name));
					this.invited.add(name);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		this.scroll -= (int) Math.signum(sy);
		return true;
	}
}
