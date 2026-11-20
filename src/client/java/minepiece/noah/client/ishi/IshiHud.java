package minepiece.noah.client.ishi;

import minepiece.noah.MinePiece;
import minepiece.noah.ability.ishi.IshiAnim;
import minepiece.noah.data.ModAttachments;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Ishi Ishi no Mi, interface du joueur concerné :
 * - voile de roche (sombre, brun-gris) quand le joueur passe sous le terrain : fin de l'enfoncement, début de la
 *   formation (la vue remonte avec le titan), et brièvement quand il ressort du sol ;
 * - jauge de structure du titan (au-dessus de la barre d'inventaire), qui rougit quand la roche est presque détruite.
 */
public final class IshiHud {
	private IshiHud() {}

	public static void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, MinePiece.id("ishi_hud"), IshiHud::extract);
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		IshiView v = IshiView.of(mc.player, delta.getGameTimeDeltaPartialTick(false), false);
		if (v == null || v.titanPhase() == 0) {
			return;
		}
		int w = g.guiWidth();
		int h = g.guiHeight();
		float t = v.titanTime();
		float veil = 0.0F;
		if (v.titanPhase() == IshiAnim.PHASE_FORM) {
			if (t > 22 && t < IshiAnim.SINK_END) {
				veil = (t - 22) / (IshiAnim.SINK_END - 22.0F);
			} else if (t >= IshiAnim.SINK_END && t < IshiAnim.SINK_END + 22) {
				veil = 1.0F - (t - IshiAnim.SINK_END) / 22.0F;
			}
		} else if (t >= IshiAnim.COLLAPSE_END - 4 && t < IshiAnim.COLLAPSE_END + 10) {
			veil = t < IshiAnim.COLLAPSE_END ? (t - IshiAnim.COLLAPSE_END + 4) / 4.0F : 1.0F - (t - IshiAnim.COLLAPSE_END) / 10.0F;
		}
		if (veil > 0.0F) {
			int a = (int) (Math.min(1.0F, veil) * 235);
			g.fill(0, 0, w, h, (a << 24) | 0x1C1814);
		}
		if (v.titanActive()) {
			Float integ = mc.player.getAttached(ModAttachments.ISHI_INTEGRITY);
			float k = integ == null ? 1.0F : Math.max(0.0F, Math.min(1.0F, integ));
			int bw = 120;
			int x = w / 2 - bw / 2;
			int y = h - 60;
			g.fill(x - 1, y - 1, x + bw + 1, y + 6, 0xFF101010);
			g.fill(x, y, x + bw, y + 5, 0xFF3A3632);
			int col = k > 0.5F ? 0xFF9C958A : k > 0.25F ? 0xFFB08850 : 0xFFC04030;
			g.fill(x, y, x + (int) (bw * k), y + 5, col);
			g.fill(x, y, x + (int) (bw * k), y + 1, 0x60FFFFFF);
			g.text(mc.font, "Titan de pierre", x, y - 10, 0xFFD8D0C0, true);
		}
	}
}
