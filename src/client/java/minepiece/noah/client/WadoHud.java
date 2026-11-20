package minepiece.noah.client;

import minepiece.noah.MinePiece;
import minepiece.noah.item.ModItems;
import minepiece.noah.network.WadoCooldownPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * HUD de la Wado Ichimonji : icone + barre de recharge de chaque competence (images fournies, non redessinees).
 * Visible uniquement quand le sabre est en main. La barre se vide a l'utilisation, se remplit pendant la recharge,
 * et retrouve toute sa luminosite quand la competence est prete. Le temps vient du serveur (WadoCooldownPayload).
 */
public final class WadoHud {
	private static final String DIR = "textures/gui/wado/";
	private static final Identifier ICON_SHISHI = MinePiece.id(DIR + "skill_shishi_sonson.png");
	private static final Identifier ICON_CLEASHER = MinePiece.id(DIR + "skill_cleasher_slash.png");
	private static final Identifier BAR = MinePiece.id(DIR + "cooldown_bar.png");
	private static final Identifier FILL = MinePiece.id(DIR + "cooldown_fill.png");

	// Tailles des textures (pixels).
	private static final int ICON_TEX = 256;
	private static final int BAR_TEX_W = 505, BAR_TEX_H = 92;
	private static final int FILL_TEX_W = 339, FILL_TEX_H = 15;
	/** Position de la zone de remplissage dans la texture de la barre. */
	private static final int FILL_X = 102, FILL_Y = 45;

	// Taille a l'ecran.
	private static final int ICON_SIZE = 16;
	private static final int BAR_W = 70, BAR_H = 13;
	private static final int ROW_H = 17;
	private static final int KEY_W = 8;

	/** Fin de recharge (System.nanoTime) et duree totale (ms) de chaque competence. */
	private static final long[] READY_AT_NS = new long[2];
	private static final long[] TOTAL_MS = {1, 1};

	private WadoHud() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(WadoCooldownPayload.TYPE, (payload, context) -> {
			int i = payload.skill();
			if (i < 0 || i > 1) {
				return;
			}
			READY_AT_NS[i] = System.nanoTime() + payload.remainingTicks() * 50_000_000L;
			TOTAL_MS[i] = Math.max(1L, payload.totalTicks() * 50L);
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, MinePiece.id("wado_hud"), WadoHud::extract);
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.getMainHandItem().getItem() != ModItems.WADO_ICHIMONJI) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		// A droite de la barre d'inventaire (hotbar = 182 px centree), alignee sur son bas.
		int x = w / 2 + 91 + 30;
		int y0 = h - 2 - 2 * ROW_H;
		row(g, mc.font, x, y0, 0, ICON_SHISHI);
		row(g, mc.font, x, y0 + ROW_H, 1, ICON_CLEASHER);
	}

	private static void row(GuiGraphicsExtractor g, Font font, int x, int y, int skill, Identifier icon) {
		long remainingMs = Math.max(0L, (READY_AT_NS[skill] - System.nanoTime()) / 1_000_000L);
		boolean ready = remainingMs <= 0;
		float progress = ready ? 1.0F : Math.max(0.0F, Math.min(1.0F, 1.0F - remainingMs / (float) TOTAL_MS[skill]));

		// Icone.
		int ix = x + KEY_W + 1;
		g.text(font, AbilityKeys.label(skill == 0 ? 0 : 2), x, y + (ICON_SIZE - 8) / 2, 0xFFFFFFFF, true);
		g.blit(RenderPipelines.GUI_TEXTURED, icon, ix, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_TEX, ICON_TEX, ICON_TEX, ICON_TEX);
		if (!ready) {
			g.fill(ix, y, ix + ICON_SIZE, y + ICON_SIZE, 0x88000000);
		}

		// Barre (cadre vide, puis remplissage proportionnel).
		int bx = ix + ICON_SIZE + 2;
		int by = y + (ICON_SIZE - BAR_H) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, BAR, bx, by, 0, 0, BAR_W, BAR_H, BAR_TEX_W, BAR_TEX_H, BAR_TEX_W, BAR_TEX_H);
		float k = BAR_W / (float) BAR_TEX_W;
		int fx = bx + Math.round(FILL_X * k);
		int fy = by + Math.round(FILL_Y * k);
		int fullW = Math.round(FILL_TEX_W * k);
		int fh = Math.max(1, Math.round(FILL_TEX_H * k));
		int fw = Math.round(fullW * progress);
		if (fw > 0) {
			int srcW = Math.max(1, Math.round(FILL_TEX_W * progress));
			g.blit(RenderPipelines.GUI_TEXTURED, FILL, fx, fy, 0, 0, fw, fh, srcW, FILL_TEX_H, FILL_TEX_W, FILL_TEX_H);
		}
		if (!ready) {
			// Pendant la recharge la barre est assombrie ; prete, elle retrouve toute sa luminosite.
			g.fill(fx, fy, fx + fullW, fy + fh, 0x55000000);
			String t = String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(remainingMs / 1000.0));
			g.text(font, t, ix - font.width(t) - 1 + ICON_SIZE, y + ICON_SIZE - 8, 0xFFE8FFE8, true);
		}
	}
}
