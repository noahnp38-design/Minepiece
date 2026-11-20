package minepiece.noah.client.ishi;

import minepiece.noah.ability.ishi.IshiAnim;
import minepiece.noah.data.ModAttachments;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.entity.Entity;

/**
 * État visuel du Ishi Ishi no Mi d'un joueur, calculé depuis les attachements synchronisés (identique pour tous les
 * clients) : animation en cours (Éruption, Poing, versions Titan) et phase du Titan de pierre.
 */
public record IshiView(int action, float actionTime, int actionMat, int titanPhase, float titanTime, int titanMat,
		float finalScale, float visualHeight, float scale, boolean realInvisible) {

	public static final RenderStateDataKey<IshiView> KEY = RenderStateDataKey.create(() -> "minepiece ishi view");

	/** null si le joueur n'utilise aucun pouvoir du Ishi Ishi no Mi. */
	public static IshiView of(Entity e, float partial, boolean realInvisible) {
		Long a = e.getAttached(ModAttachments.ISHI_ACTION);
		Long t = e.getAttached(ModAttachments.ISHI_TITAN);
		if (a == null && t == null) {
			return null;
		}
		float now = e.level().getGameTime() + partial;
		int action = 0;
		float at = 0.0F;
		int am = 0;
		if (a != null) {
			action = IshiAnim.kind(a);
			at = now - IshiAnim.actionStart(a);
			am = IshiAnim.actionMat(a);
			int total = switch (action) {
				case IshiAnim.ERUPT -> IshiAnim.ERUPT_TOTAL;
				case IshiAnim.FIST -> IshiAnim.FIST_TOTAL;
				case IshiAnim.TITAN_ERUPT -> IshiAnim.TITAN_ERUPT_TOTAL;
				case IshiAnim.TITAN_PUNCH -> IshiAnim.TITAN_PUNCH_TOTAL;
				default -> 0;
			};
			if (at > total || at < 0) {
				action = 0;
			}
		}
		int phase = 0;
		float tt = 0.0F;
		int tm = 0;
		float fs = 16.0F;
		float vh = 42.0F;
		if (t != null) {
			phase = IshiAnim.phase(t);
			tt = Math.max(0.0F, now - IshiAnim.titanStart(t));
			tm = IshiAnim.titanMat(t);
			Integer size = e.getAttached(ModAttachments.ISHI_TITAN_SIZE);
			if (size != null) {
				fs = (size / 2048) / 10.0F;
				vh = (size % 2048) / 10.0F;
			}
		}
		float scale = e instanceof net.minecraft.world.entity.LivingEntity le ? le.getScale() : 1.0F;
		return new IshiView(action, at, am, phase, tt, tm, Math.max(1.0F, fs), Math.max(1.8F, vh), Math.max(0.05F, scale), realInvisible);
	}

	/** Le modèle du titan est dessiné (à la place du joueur). */
	public boolean titanVisible() {
		if (titanPhase == IshiAnim.PHASE_FORM) {
			return titanTime >= IshiAnim.SINK_END;
		}
		return titanPhase == IshiAnim.PHASE_COLLAPSE && titanTime < IshiAnim.COLLAPSE_END;
	}

	/** Enfoncement du joueur dans le sol (blocs) : fusion avant la formation, sortie après l'effondrement. */
	public float sinkDepth() {
		if (titanPhase == IshiAnim.PHASE_FORM && titanTime < IshiAnim.SINK_END) {
			return IshiAnim.sinkDepth(titanTime);
		}
		if (titanPhase == IshiAnim.PHASE_COLLAPSE && titanTime >= IshiAnim.COLLAPSE_END) {
			return IshiAnim.SINK_DEPTH * (1.0F - IshiAnim.smooth((titanTime - IshiAnim.COLLAPSE_END) / (IshiAnim.EMERGE_END - IshiAnim.COLLAPSE_END)));
		}
		return 0.0F;
	}

	public boolean titanActive() {
		return titanPhase == IshiAnim.PHASE_FORM && titanTime >= IshiAnim.FORM_END;
	}

	public boolean fist() {
		return action == IshiAnim.FIST;
	}
}
