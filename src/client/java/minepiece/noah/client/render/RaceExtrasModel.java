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
	/** Etat passe au modele : le temps (flamme, respiration) et la pose des ailes (battement, ouverture). */
	public record State(float ageInTicks, WingPose wing) {}

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
		WingPose wing = state.wing();

		// Battement : angle de chaque aile = sinus de la phase. Le décalage (stagger) fait battre l'aile droite
		// légèrement après la gauche, ce qui donne un mouvement vivant plutôt qu'un balancier.
		float flapLeft = (float) Math.sin(wing.phase()) * wing.amp();
		float flapRight = (float) Math.sin(wing.phase() + wing.stagger()) * wing.amp();
		// Les ailes balaient aussi un peu vers l'avant / l'arrière (décalé d'un quart de cycle) : plus naturel.
		float sweepLeft = (float) Math.cos(wing.phase()) * wing.amp() * 0.25F;
		float sweepRight = (float) Math.cos(wing.phase() + wing.stagger()) * wing.amp() * 0.25F;
		// Au repos : léger mouvement lent, pour que les ailes ne soient jamais figées.
		float idle = (float) Math.sin(state.ageInTicks() * 0.07F) * 0.04F * (1.0F - wing.open());
		// Ouvertes : ailes plus relevées et moins rabattues vers l'arrière.
		float raise = wing.open() * 0.20F;
		float sweep = RaceModels.WING_SWEEP * (1.0F - 0.5F * wing.open());

		if (wingLeft != null) {
			wingLeft.zRot = RaceModels.WING_TILT_LEFT - raise - flapLeft + idle;
			wingLeft.yRot = -sweep - sweepLeft;
		}
		if (wingRight != null) {
			wingRight.zRot = RaceModels.WING_TILT_RIGHT + raise + flapRight - idle;
			wingRight.yRot = sweep + sweepRight;
		}
		if (flame != null) {
			flame.zRot = (float) Math.sin(state.ageInTicks() * 0.45F) * 0.14F;
		}
	}
}
