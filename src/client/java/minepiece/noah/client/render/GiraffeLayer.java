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

/** Couche de rendu du Ushi Ushi no Mi : accessoires hybrides (copient le joueur) et girafe complète (remplace le corps). */
@SuppressWarnings({"rawtypes", "unchecked"})
public class GiraffeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = MinePiece.id("textures/entity/giraffe.png");

	private final GiraffeModel hybrid;
	private final GiraffeModel giraffe;

	public GiraffeLayer(AvatarRenderer renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.hybrid = new GiraffeModel(context.bakeLayer(GiraffeModels.HYBRID), true);
		this.giraffe = new GiraffeModel(context.bakeLayer(GiraffeModels.GIRAFFE), false);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light,
			AvatarRenderState state, float yRot, float xRot) {
		GiraffeRenderData.View view = state.getData(GiraffeRenderData.VIEW);
		if (view == null || !view.active() || view.realInvisible()) {
			return;
		}
		OrderedSubmitNodeCollector ordered = nodeCollector.order(1);

		// Girafe complète : visible dès que sa forme "pousse" ; remplace entièrement le corps du joueur.
		if (view.wGiraffe() > 0.15F) {
			GiraffeModel.State ms = new GiraffeModel.State(view.wGiraffe(), view.animTime(), view.anim(), view.transition(),
					view.scale(), state.ageInTicks, state.walkAnimationPos, state.walkAnimationSpeed, state.yRot, state.xRot);
			ordered.submitModel(this.giraffe, ms, poseStack, RenderTypes.armorCutoutNoCull(TEXTURE),
					light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, state.outlineColor);
		}

		// Hybride : accessoires qui suivent la pose du joueur (tête, corps, bras, jambes).
		if (view.wHybrid() > 0.001F && view.wGiraffe() <= 0.35F) {
			GiraffeModel.State ms = new GiraffeModel.State(view.wHybrid(), view.animTime(), view.anim(), view.transition(),
					view.scale(), state.ageInTicks, 0.0F, 0.0F, 0.0F, 0.0F);
			ArmorRenderer.submitTransformCopyingModel(
					this.getParentModel(), state,
					this.hybrid, ms,
					true, ordered, poseStack,
					RenderTypes.armorCutoutNoCull(TEXTURE), light, OverlayTexture.NO_OVERLAY,
					state.outlineColor);
		}
	}
}
