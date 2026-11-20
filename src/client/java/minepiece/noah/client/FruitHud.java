package minepiece.noah.client;

import minepiece.noah.MinePiece;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.network.FruitCooldownPayload;
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
 * HUD des fruits du démon : 3 boutons avec icône, recharge visuelle et temps restant (Suke Suke : Z / X / C ;
 * Mera Mera et Hie Hie : R / G / V, ou les touches choisies dans les contrôles).
 * Affichée à gauche de la barre d'inventaire, selon le fruit du joueur.
 * Les recharges viennent du serveur (FruitCooldownPayload) : elles sont donc toujours exactes.
 */
public final class FruitHud {
	private static final Identifier[] SUKE_ICONS = {
			MinePiece.id("textures/gui/suke/vanish.png"),
			MinePiece.id("textures/gui/suke/strike.png"),
			MinePiece.id("textures/gui/suke/veil.png")};
	private static final Identifier[] MERA_ICONS = icons("mera");
	private static final Identifier[] HIE_ICONS = icons("hie");
	private static final Identifier[] BOMU_ICONS = icons("bomu");
	private static final Identifier[] SUPA_ICONS = icons("supa");
	private static final Identifier[] USHI_ICONS = icons("ushi");
	private static final Identifier[] ZUSHI_ICONS = icons("zushi");
	private static final Identifier[] GORO_ICONS = icons("goro");
	private static final Identifier[] ISHI_ICONS = icons("ishi");

	private static Identifier[] icons(String prefix) {
		Identifier[] out = new Identifier[3];
		for (int i = 0; i < 3; i++) {
			out[i] = MinePiece.id("textures/gui/fruit/" + prefix + "_" + i + ".png");
		}
		return out;
	}

	private static Identifier[] iconsOf(DevilFruit fruit) {
		return switch (fruit) {
			case SUKE_SUKE_NO_MI -> SUKE_ICONS;
			case MERA_MERA_NO_MI -> MERA_ICONS;
			case HIE_HIE_NO_MI -> HIE_ICONS;
			case BOMU_BOMU_NO_MI -> BOMU_ICONS;
			case SUPA_SUPA_NO_MI -> SUPA_ICONS;
			case USHI_USHI_NO_MI -> USHI_ICONS;
			case ZUSHI_ZUSHI_NO_MI -> ZUSHI_ICONS;
			case GORO_GORO_NO_MI -> GORO_ICONS;
			case ISHI_ISHI_NO_MI -> ISHI_ICONS;
		};
	}

	/** Couleur du cadre quand l'attaque est prête. */
	private static int accent(DevilFruit fruit) {
		return switch (fruit) {
			case SUKE_SUKE_NO_MI -> 0xFF40E0D0;
			case MERA_MERA_NO_MI -> 0xFFFF8A2A;
			case HIE_HIE_NO_MI -> 0xFF8AD8FF;
			case BOMU_BOMU_NO_MI -> 0xFFFFB02E;
			case SUPA_SUPA_NO_MI -> 0xFFCFE6FF;
			case USHI_USHI_NO_MI -> 0xFFE8C98A;
			case ZUSHI_ZUSHI_NO_MI -> 0xFFB45CFF;
			case GORO_GORO_NO_MI -> 0xFF4AB8FF;
			case ISHI_ISHI_NO_MI -> 0xFFB8A88A;
		};
	}
	private static final int SIZE = 18;
	private static final int GAP = 4;

	private static final long[] READY_AT_NS = new long[DevilFruit.ABILITY_COUNT];
	private static final long[] TOTAL_MS = {1, 1, 1};

	private FruitHud() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(FruitCooldownPayload.TYPE, (payload, context) -> {
			int i = payload.slot();
			if (i < 0 || i >= READY_AT_NS.length) {
				return;
			}
			READY_AT_NS[i] = System.nanoTime() + payload.remainingTicks() * 50_000_000L;
			TOTAL_MS[i] = Math.max(1L, payload.totalTicks() * 50L);
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, MinePiece.id("suke_hud"), FruitHud::extract);
	}

	private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		DevilFruit fruit = mc.player == null ? null : mc.player.getAttached(ModAttachments.FRUIT);
		if (fruit == null) {
			return;
		}
		int total = DevilFruit.ABILITY_COUNT * SIZE + (DevilFruit.ABILITY_COUNT - 1) * GAP;
		int x0 = g.guiWidth() / 2 - 91 - 34 - total;
		int y = g.guiHeight() - 2 - SIZE - 10;
		for (int i = 0; i < DevilFruit.ABILITY_COUNT; i++) {
			button(g, mc.font, fruit, x0 + i * (SIZE + GAP), y, i);
		}
		if (fruit == DevilFruit.USHI_USHI_NO_MI) {
			// Forme actuelle (la touche de transformation est modifiable dans les contrôles).
			var state = minepiece.noah.ability.giraffe.GiraffeAbilities.state(mc.player);
			String form = switch (state.form()) {
				case minepiece.noah.ability.giraffe.GiraffeAnim.HYBRID -> "Hybride";
				case minepiece.noah.ability.giraffe.GiraffeAnim.GIRAFFE -> "Girafe";
				default -> "Humain";
			};
			g.text(mc.font, "Forme : " + form + "  [" + AbilityKeys.formLabel() + "]", x0, y - 11, 0xFFE8C98A, true);
		}
	}

	private static void button(GuiGraphicsExtractor g, Font font, DevilFruit fruit, int x, int y, int slot) {
		long remainingMs = Math.max(0L, (READY_AT_NS[slot] - System.nanoTime()) / 1_000_000L);
		boolean ready = remainingMs <= 0;
		float fraction = ready ? 0.0F : Math.min(1.0F, remainingMs / (float) TOTAL_MS[slot]);

		if (fruit == DevilFruit.ZUSHI_ZUSHI_NO_MI || fruit == DevilFruit.GORO_GORO_NO_MI || fruit == DevilFruit.ISHI_ISHI_NO_MI) {
			// Zushi : les logos ont déjà leur propre cadre doré -> pas de bordure colorée, le logo prend toute la case.
			g.blit(RenderPipelines.GUI_TEXTURED, iconsOf(fruit)[slot], x, y, 0, 0, SIZE, SIZE, 16, 16, 16, 16);
		} else {
			// Fond + cadre (turquoise quand prête).
			g.fill(x, y, x + SIZE, y + SIZE, 0xCC0A1620);
			g.outline(x, y, SIZE, SIZE, ready ? accent(fruit) : 0xFF3A4A56);
			g.blit(RenderPipelines.GUI_TEXTURED, iconsOf(fruit)[slot], x + 1, y + 1, 0, 0, 16, 16, 16, 16, 16, 16);
		}

		// Recharge : voile sombre qui descend à mesure que le temps passe + secondes restantes.
		if (!ready) {
			int h = Math.round((SIZE - 2) * fraction);
			g.fill(x + 1, y + 1, x + SIZE - 1, y + 1 + h, 0xAA000000);
			g.fill(x + 1, y + 1 + h, x + SIZE - 1, y + SIZE - 1, 0x55000000);
			String t = remainingMs >= 10_000L ? String.valueOf(Math.round(remainingMs / 1000.0))
					: String.format(java.util.Locale.ROOT, "%.1f", remainingMs / 1000.0);
			g.text(font, t, x + (SIZE - font.width(t)) / 2, y + (SIZE - 8) / 2, 0xFFFFFFFF, true);
		}
		// Touche sous l'icône.
		String key = AbilityKeys.label(slot);
		g.text(font, key, x + (SIZE - font.width(key)) / 2, y + SIZE + 1, ready ? 0xFFB8FFF6 : 0xFF8A98A4, true);
	}
}
