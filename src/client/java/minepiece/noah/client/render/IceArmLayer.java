package minepiece.noah.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Pheasant Beak : dessine la glace sur le bras droit du joueur (de tous les joueurs, la progression est synchronisée). */
@SuppressWarnings({"rawtypes", "unchecked"})
public class IceArmLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = MinePiece.id("textures/entity/ice_arm.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	/** Durées (ticks) : identiques à celles du serveur (PheasantBeak). */
	private static final float FREEZE_TICKS = 14.0F;
	private static final float THAW_TICKS = 24.0F;

	/** Modèle partagé pour la vue à la première personne (créé avec la première couche). */
	private static IceArmModel firstPerson;

	private final IceArmModel model;

	public IceArmLayer(AvatarRenderer renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.model = new IceArmModel(context.bakeLayer(IceArmModel.LAYER));
		if (firstPerson == null) {
			firstPerson = new IceArmModel(context.bakeLayer(IceArmModel.LAYER));
		}
	}

	/**
	 * Progression du gel du bras (0 à 1), -1 si le bras est normal. ICE_ARM > 0 : début du gel ;
	 * ICE_ARM < 0 : -(début du dégel), la glace disparaît alors progressivement (1 -> 0).
	 */
	public static float progress(net.minecraft.world.entity.Entity entity, float partialTicks) {
		Long v = entity.getAttached(minepiece.noah.data.ModAttachments.ICE_ARM);
		if (v == null) {
			return -1.0F;
		}
		float now = entity.level().getGameTime() + partialTicks;
		if (v >= 0) {
			return Math.max(0.0F, Math.min(1.0F, (now - v) / FREEZE_TICKS));
		}
		float p = 1.0F - (now + v) / THAW_TICKS;
		return p <= 0.0F ? -1.0F : Math.min(1.0F, p);
	}

	/** Vue à la première personne : la glace est dessinée sur le bras affiché à l'écran (même pose que lui). */
	public static void submitFirstPerson(PoseStack poseStack, SubmitNodeCollector collector,
			net.minecraft.client.model.geom.ModelPart arm, float progress) {
		if (firstPerson == null) {
			return;
		}
		firstPerson.setupAnim(progress);
		net.minecraft.client.model.geom.ModelPart ice = firstPerson.rightArm();
		ice.loadPose(arm.storePose());
		collector.order(1).submitModelPart(ice, poseStack, RenderTypes.entityTranslucent(TEXTURE), FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, null);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light,
			AvatarRenderState state, float yRot, float xRot) {
		Float ice = state.getData(RaceRenderData.ICE_ARM);
		if (ice == null || ice < 0.0F || state.isInvisible) {
			return;
		}
		OrderedSubmitNodeCollector ordered = nodeCollector.order(2);
		ArmorRenderer.submitTransformCopyingModel(
				this.getParentModel(), state,
				this.model, ice,
				true, ordered, poseStack,
				RenderTypes.entityTranslucent(TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				state.outlineColor);
	}
}
