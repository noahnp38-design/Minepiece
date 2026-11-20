package minepiece.noah.client.ishi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import minepiece.noah.MinePiece;
import minepiece.noah.ability.ishi.IshiAnim;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Rendu du Ishi Ishi no Mi sur les joueurs (visible par tous) :
 * - Poing colossal : bras de pierre + poing qui suit la pose du bras droit ;
 * - Titan de pierre : le modèle du titan REMPLACE le joueur (caché comme pour la girafe du Ushi Ushi no Mi), à sa taille
 *   finale dès le début de la formation (les pièces sortent du sol), quelle que soit l'échelle actuelle du joueur.
 * Vue à la 1re personne : voir {@link #submitFirstPerson}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class IshiLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static TitanModel firstTitan;
	private static StoneArmModel firstArm;

	private final TitanModel titan;
	private final StoneArmModel arm;

	public IshiLayer(AvatarRenderer renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.titan = new TitanModel(context.bakeLayer(IshiModels.TITAN));
		this.arm = new StoneArmModel(context.bakeLayer(IshiModels.STONE_ARM));
		if (firstTitan == null) {
			firstTitan = new TitanModel(context.bakeLayer(IshiModels.TITAN));
			firstArm = new StoneArmModel(context.bakeLayer(IshiModels.STONE_ARM));
		}
	}

	public static Identifier titanTexture(int mat) {
		return MinePiece.id("textures/entity/ishi/ishi_titan_" + IshiAnim.bodyVariant(mat) + ".png");
	}

	public static Identifier armTexture(int mat) {
		return MinePiece.id("textures/entity/ishi/ishi_stone_arm_" + IshiAnim.bodyVariant(mat) + ".png");
	}

	static TitanModel.State titanState(IshiView v, float walkPos, float walkSpeed, float headYaw, float headPitch, float age) {
		TitanModel.State ms = new TitanModel.State();
		if (v.titanPhase() == IshiAnim.PHASE_FORM) {
			ms.form = IshiAnim.formProgress(v.titanTime());
		} else {
			ms.form = 1.0F;
			ms.collapse = v.titanTime();
		}
		ms.action = v.action();
		ms.actionTime = v.actionTime();
		ms.walkPos = walkPos;
		ms.walkSpeed = walkSpeed;
		ms.headYaw = headYaw;
		ms.headPitch = headPitch;
		ms.age = age;
		return ms;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		IshiView v = state.getData(IshiView.KEY);
		if (v == null || v.realInvisible()) {
			return;
		}
		if (v.titanVisible()) {
			TitanModel.State ms = titanState(v, state.walkAnimationPos, state.walkAnimationSpeed, state.yRot, state.xRot, state.ageInTicks);
			poseStack.pushPose();
			// pieds du joueur ; taille FINALE du titan quelle que soit l'échelle actuelle (il sort du sol, il ne grandit pas)
			poseStack.translate(0.0F, 1.501F, 0.0F);
			float k = (v.visualHeight() / v.scale()) / (IshiModels.TITAN_HEIGHT_UNITS / 16.0F) / 0.9375F;
			poseStack.scale(k, k, k);
			collector.order(0).submitModel(this.titan, ms, poseStack, RenderTypes.entityCutout(titanTexture(v.titanMat())), light,
					OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, state.outlineColor);
			poseStack.popPose();
			return;
		}
		if (v.fist() && !state.isInvisible) {
			StoneArmModel.State ms = new StoneArmModel.State();
			ms.arm = this.getParentModel().rightArm.storePose();
			ms.time = v.actionTime();
			collector.order(1).submitModel(this.arm, ms, poseStack, RenderTypes.entityCutout(armTexture(v.actionMat())), light,
					OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, state.outlineColor);
		}
	}

	// ------------------------------------------------------------------ 1re personne

	/**
	 * Vue à la 1re personne (remplace le bras droit vanilla, rendu personnalisé) :
	 * - Poing colossal : le bras se recouvre de pierre, recule, le poing grossit, part violemment vers l'avant puis revient ;
	 * - Titan : avant-bras et main gigantesques du titan, balancement de marche, frappe (X) et bras levé puis abattu (W).
	 * Le poing reste en bas à droite de l'écran (jamais plein écran) ; la secousse de caméra vient du serveur.
	 * @return true si le bras vanilla doit être masqué
	 */
	public static boolean submitFirstPerson(PoseStack ps, SubmitNodeCollector collector, int light, IshiView v, float walkPos, float walkSpeed,
			float age) {
		if (firstTitan == null || v == null) {
			return false;
		}
		if (v.titanVisible() || v.sinkDepth() > 0.0F || (v.titanPhase() == IshiAnim.PHASE_FORM && v.titanTime() >= IshiAnim.SINK_START)) {
			if (!v.titanVisible()) {
				return true;   // enfoncé dans la pierre : pas de bras
			}
			titanFirstPerson(ps, collector, light, v, walkPos, walkSpeed, age);
			return true;
		}
		if (!v.fist()) {
			return false;
		}
		float t = v.actionTime();
		ps.pushPose();
		// élévation pendant la formation, recul (armé), coup (frappe), retour puis le bras redescend hors de l'écran
		float rise = 1.0F - IshiAnim.smooth(t / 8.0F);
		float wind = IshiAnim.smooth((t - IshiAnim.FIST_FORM) / (IshiAnim.FIST_WIND - IshiAnim.FIST_FORM));
		float strike = IshiAnim.smooth((t - IshiAnim.FIST_WIND) / (IshiAnim.FIST_STRIKE - IshiAnim.FIST_WIND));
		float back = IshiAnim.smooth((t - IshiAnim.FIST_HOLD) / (IshiAnim.FIST_TOTAL - IshiAnim.FIST_HOLD));
		float z = -0.45F + 0.22F * wind * (1 - strike) - 0.55F * strike * (1 - back);
		float x = 0.42F + 0.06F * wind * (1 - strike) - 0.14F * strike * (1 - back);
		float y = -0.5F - 0.45F * rise - 0.08F * wind * (1 - strike) + 0.05F * strike * (1 - back) - 0.6F * back * back;
		ps.translate(x, y, z);
		ps.rotateDegrees(Axis.YP, 14.0F + 8.0F * wind * (1 - strike) - 10.0F * strike * (1 - back));
		ps.rotateDegrees(Axis.ZP, -6.0F * wind * (1 - strike));
		ps.rotateDegrees(Axis.XP, 100.0F - 10.0F * wind * (1 - strike));
		ps.scale(0.62F, -0.62F, 0.62F);
		StoneArmModel.State ms = new StoneArmModel.State();
		ms.time = t;
		collector.order(1).submitModel(firstArm, ms, ps, RenderTypes.entityCutout(armTexture(v.actionMat())), light,
				OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, 0);
		ps.popPose();
		return true;
	}

	private static void titanFirstPerson(PoseStack ps, SubmitNodeCollector collector, int light, IshiView v, float walkPos, float walkSpeed,
			float age) {
		TitanModel.State ms = titanState(v, walkPos, walkSpeed, 0.0F, 0.0F, age);
		ms.firstPerson = true;
		float x = 0.62F;
		float y = -0.78F;
		float z = -0.55F;
		float pitch = 0.0F;
		float yaw = 0.0F;
		// balancement de la marche
		float amp = Math.min(1.0F, walkSpeed * 1.4F);
		y += (float) Math.abs(Math.sin(walkPos * 0.42F)) * 0.05F * amp;
		x += (float) Math.cos(walkPos * 0.42F) * 0.03F * amp;
		if (v.titanPhase() == IshiAnim.PHASE_FORM && v.titanTime() < IshiAnim.FORM_END) {
			// le bras sort du sol en même temps que le corps
			y -= 0.9F * (1.0F - IshiAnim.smooth((ms.form - 0.6F) / 0.4F));
		}
		if (v.titanPhase() == IshiAnim.PHASE_COLLAPSE) {
			float k = IshiAnim.smooth((v.titanTime() - IshiAnim.COLLAPSE_FALL) / 20.0F);
			y -= 1.2F * k;
		}
		if (v.action() == IshiAnim.TITAN_PUNCH) {
			float t = v.actionTime();
			float wind = IshiAnim.smooth(t / IshiAnim.TITAN_WIND);
			float strike = IshiAnim.smooth((t - IshiAnim.TITAN_WIND) / (IshiAnim.TITAN_STRIKE - IshiAnim.TITAN_WIND));
			float back = IshiAnim.smooth((t - IshiAnim.TITAN_STRIKE - 4) / 14.0F);
			z += (0.3F * wind * (1 - strike) - 0.9F * strike) * (1 - back);
			x += (0.12F * wind * (1 - strike) - 0.3F * strike) * (1 - back);
			y += (-0.1F * wind + 0.15F * strike) * (1 - back);
			yaw += (12.0F * wind * (1 - strike) - 14.0F * strike) * (1 - back);
		} else if (v.action() == IshiAnim.TITAN_ERUPT) {
			float t = v.actionTime();
			float up = IshiAnim.smooth(t / (IshiAnim.TITAN_ERUPT_AT - 2.0F));
			float slam = IshiAnim.smooth((t - IshiAnim.TITAN_ERUPT_AT + 2) / 4.0F);
			float back = IshiAnim.smooth((t - IshiAnim.TITAN_ERUPT_AT - 6) / 14.0F);
			y += (0.9F * up * (1 - slam) - 0.2F * slam) * (1 - back);
			pitch += (-35.0F * up * (1 - slam) + 30.0F * slam) * (1 - back);
		}
		ps.pushPose();
		ps.translate(x, y, z);
		ps.rotateDegrees(Axis.YP, 16.0F + yaw);
		ps.rotateDegrees(Axis.XP, 100.0F + pitch);
		float s = 0.5F;
		ps.scale(s, -s, s);
		collector.order(1).submitModel(firstTitan, ms, ps, RenderTypes.entityCutout(titanTexture(v.titanMat())), light,
				OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, 0);
		ps.popPose();
	}
}
