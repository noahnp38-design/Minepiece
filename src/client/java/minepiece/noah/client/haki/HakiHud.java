package minepiece.noah.client.haki;

import minepiece.noah.MinePiece;
import minepiece.noah.data.ModAttachments;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * HUD du Haki, à droite de la barre d'inventaire : jauge d'énergie, 3 icônes (Armement / Observation / Rois) avec
 * leur maîtrise (points) et leur touche ; contour lumineux quand le Haki est actif (rouge pour la Vision du futur et
 * l'imprégnation). Effets plein écran : vignette rouge de la Vision du futur, voile de l'esquive parfaite, pression des
 * Rois subie, éclair rouge d'un clash proche.
 */
public final class HakiHud {
	private static final Identifier[] ICONS = {
			MinePiece.id("textures/gui/haki/buso.png"),
			MinePiece.id("textures/gui/haki/kenbun.png"),
			MinePiece.id("textures/gui/haki/haoshoku.png")};
	private static final Identifier VIGNETTE = MinePiece.id("textures/gui/haki/future_vignette.png");
	private static final int SIZE = 16;
	private static final float ENERGY_MAX = 100.0F;
	private static final int[] MAX = minepiece.noah.haki.HakiData.MAX;

	private HakiHud() {}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, MinePiece.id("haki_hud"), HakiHud::extract);
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		Integer fl = mc.player.getAttached(ModAttachments.HAKI_FLAGS);
		int flags = fl == null ? 0 : fl;
		// ---- effets plein écran
		if ((flags & 4) != 0) {
			g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, w, h, 256, 256, 256, 256);
		}
		if (HakiClientFx.perfectTicks > 0) {
			int a = (int) (HakiClientFx.perfectTicks / 12.0F * 70);
			g.fill(0, 0, w, h, (a << 24) | 0xD8DCE8);
		}
		if (HakiClientFx.pressureTicks > 0) {
			int a = Math.min(120, HakiClientFx.pressureTicks * 5);
			g.fill(0, 0, w, h, (a << 24) | 0x0A0010);
		}
		if (HakiClientFx.clashTicks > 0) {
			int a = HakiClientFx.clashTicks * 7;
			g.fill(0, 0, w, h, (a << 24) | 0x500008);
		}
		// ---- icônes + jauge
		Integer packed = mc.player.getAttached(ModAttachments.HAKI_MASTERY);
		int[] mastery = new int[3];
		for (int t = 0; t < 3; t++) {
			mastery[t] = packed == null ? (t == 2 ? 0 : 1) : Math.min(MAX[t], (packed >> (t * 4)) & 15);
			if (mc.player.isCreative()) {
				mastery[t] = MAX[t];   // aperçu : le serveur applique le réglage creativeAllMastery
			}
		}
		int count = mastery[2] > 0 ? 3 : 2;
		int x0 = w / 2 + 91 + 8;
		int y = h - SIZE - 4;
		for (int t = 0; t < count; t++) {
			int x = x0 + t * (SIZE + 4);
			boolean on = switch (t) {
				case 0 -> (flags & 1) != 0;
				case 1 -> (flags & 2) != 0;
				default -> (flags & (8 | 16)) != 0;
			};
			boolean red = (t == 1 && (flags & 4) != 0) || (t == 2 && on);
			g.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, on ? (red ? 0xFFD02030 : 0xFFB8B8D0) : 0xFF20202A);
			g.blit(RenderPipelines.GUI_TEXTURED, ICONS[t], x, y, 0, 0, SIZE, SIZE, 32, 32, 32, 32);
			if (!on) {
				g.fill(x, y, x + SIZE, y + SIZE, 0x66000000);
			}
			// points de maîtrise (Armement 5, Observation 3, Rois 1)
			int dots = MAX[t];
			int step = SIZE / dots;
			for (int d = 0; d < dots; d++) {
				int dx = x + d * step + (step - 2) / 2;
				g.fill(dx, y - 4, dx + 2, y - 2, d < mastery[t] ? 0xFFE8C060 : 0xFF404048);
			}
			String key = HakiKeys.label(t);
			g.text(mc.font, key, x + (SIZE - mc.font.width(key)) / 2, y + SIZE - 7, 0xFFFFFFFF, true);
		}
		Float e = mc.player.getAttached(ModAttachments.HAKI_ENERGY);
		float energy = e == null ? ENERGY_MAX : e;
		int bw = count * (SIZE + 4) - 4;
		int by = y - 8;
		g.fill(x0 - 1, by - 1, x0 + bw + 1, by + 3, 0xFF101014);
		int fill = Math.round(bw * Math.max(0.0F, Math.min(1.0F, energy / ENERGY_MAX)));
		int col = energy < 20 ? 0xFFB02020 : (flags & (8 | 16)) != 0 ? 0xFF8A1020 : 0xFF6A6A9A;
		g.fill(x0, by, x0 + fill, by + 2, col);
	}
}
