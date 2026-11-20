package minepiece.noah.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import minepiece.noah.MinePiece;
import minepiece.noah.data.Race;
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

/** Couche de rendu ajoutee a tous les joueurs : teinte de peau + ailes / nageoires / queue. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class RaceLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier EXTRAS_TEXTURE = MinePiece.id("textures/entity/race_extras.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	// Teintes (ARGB) multipliees avec la peau du joueur.
	private static final int TINT_FISHMAN = 0xFF70B8FF;   // bleute
	private static final int TINT_LUNARIAN = 0xFFD9B08A;  // peau mate / halee

	private final AvatarRenderer renderer;
	private final RaceExtrasModel skypian;
	private final RaceExtrasModel fishman;
	private final RaceExtrasModel lunarian;
	private final RaceExtrasModel lunarianFlame;

	public RaceLayer(AvatarRenderer renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.renderer = renderer;
		this.skypian = new RaceExtrasModel(context.bakeLayer(RaceModels.SKYPIEAN), true);
		this.fishman = new RaceExtrasModel(context.bakeLayer(RaceModels.FISHMAN));
		this.lunarian = new RaceExtrasModel(context.bakeLayer(RaceModels.LUNARIAN), true);
		this.lunarianFlame = new RaceExtrasModel(context.bakeLayer(RaceModels.LUNARIAN_FLAME));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light,
			AvatarRenderState state, float yRot, float xRot) {
		Race race = state.getData(RaceRenderData.RACE);
		if (race == null || state.isInvisible) {
			return;
		}

		switch (race) {
			case SKYPIEAN -> extras(poseStack, nodeCollector, light, state, skypian, false);
			case FISHMAN -> {
				tint(poseStack, nodeCollector, light, state, TINT_FISHMAN);
				extras(poseStack, nodeCollector, light, state, fishman, false);
			}
			case LUNARIAN -> {
				tint(poseStack, nodeCollector, light, state, TINT_LUNARIAN);
				extras(poseStack, nodeCollector, light, state, lunarian, false);
				extras(poseStack, nodeCollector, FULL_BRIGHT, state, lunarianFlame, true);
			}
			default -> { }
		}
	}

	private static WingPose wingPose(AvatarRenderState state) {
		WingPose pose = state.getData(RaceRenderData.WING);
		return pose == null ? WingPose.REST : pose;
	}

	/** Redessine le corps du joueur avec sa propre peau, multipliee par une couleur. */
	private void tint(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light,
			AvatarRenderState state, int argb) {
		Identifier skin = this.renderer.getTextureLocation(state);
		OrderedSubmitNodeCollector ordered = nodeCollector.order(0);
		ordered.submitModel(this.getParentModel(), state, poseStack,
				RenderTypes.armorCutoutNoCull(skin), light, OverlayTexture.NO_OVERLAY,
				argb, null, state.outlineColor);
	}

	private void extras(PoseStack poseStack, SubmitNodeCollector nodeCollector, int light,
			AvatarRenderState state, RaceExtrasModel model, boolean last) {
		OrderedSubmitNodeCollector ordered = nodeCollector.order(last ? 2 : 1);
		ArmorRenderer.submitTransformCopyingModel(
				this.getParentModel(), state,
				model, new RaceExtrasModel.State(state.ageInTicks, wingPose(state)),
				true, ordered, poseStack,
				RenderTypes.armorCutoutNoCull(EXTRAS_TEXTURE), light, OverlayTexture.NO_OVERLAY,
				state.outlineColor);
	}
}
