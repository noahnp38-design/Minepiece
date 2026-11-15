package minepiece.noah.client.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Modele "accessoires" (ailes, nageoires, queue, flamme).
 * Ses parties portent les memes noms que celles du joueur (head, body, right_arm...)
 * pour que leurs positions soient copiees depuis le modele du joueur.
 */
public class RaceExtrasModel extends Model<RaceExtrasModel.State> {
	/** Etat passe au modele : juste le temps, pour animer. */
	public record State(float ageInTicks) {}

	private final ModelPart wingLeft;
	private final ModelPart wingRight;
	private final ModelPart flame;

	public RaceExtrasModel(ModelPart root) {
		super(root, id -> RenderTypes.armorCutoutNoCull(id));
		ModelPart body = root.getChild("body");
		this.wingLeft = body.hasChild("wing_l") ? body.getChild("wing_l") : null;
		this.wingRight = body.hasChild("wing_r") ? body.getChild("wing_r") : null;
		this.flame = findFlame(body);
	}

	private static ModelPart findFlame(ModelPart body) {
		if (!body.hasChild("tail1")) return null;
		ModelPart t1 = body.getChild("tail1");
		if (!t1.hasChild("tail2")) return null;
		ModelPart t2 = t1.getChild("tail2");
		if (!t2.hasChild("tail3")) return null;
		ModelPart t3 = t2.getChild("tail3");
		return t3.hasChild("flame") ? t3.getChild("flame") : null;
	}

	@Override
	public void setupAnim(State state) {
		float flap = (float) Math.sin(state.ageInTicks() * 0.12F) * 0.08F;
		if (wingLeft != null) {
			wingLeft.zRot = RaceModels.WING_TILT_LEFT + flap;
		}
		if (wingRight != null) {
			wingRight.zRot = RaceModels.WING_TILT_RIGHT - flap;
		}
		if (flame != null) {
			flame.zRot = (float) Math.sin(state.ageInTicks() * 0.45F) * 0.14F;
		}
	}
}
