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
	private final ModelPart[] flameLayers = new ModelPart[3];
	private final ModelPart[] embers = new ModelPart[RaceModels.EMBERS];
	/** Taille des ailes à plumes (Skypiéen et Lunarien) : 0.75 = 25 % plus petites que le modèle de base. */
	private static final float SKYPIEAN_SCALE = 0.75F;
	/** true : les ailes battent d'avant en arrière (Skypiéen) au lieu de haut en bas. */
	private final boolean sweepFlap;

	public RaceExtrasModel(ModelPart root) {
		this(root, false);
	}

	public RaceExtrasModel(ModelPart root, boolean sweepFlap) {
		super(root, id -> RenderTypes.armorCutoutNoCull(id));
		this.sweepFlap = sweepFlap;
		ModelPart body = root.getChild("body");
		this.wingLeft = body.hasChild("wing_l") ? body.getChild("wing_l") : null;
		this.wingRight = body.hasChild("wing_r") ? body.getChild("wing_r") : null;
		this.flame = findFlame(body);
		if (this.flame != null) {
			String[] names = {"outer", "mid", "core"};
			for (int i = 0; i < names.length; i++) {
				this.flameLayers[i] = this.flame.hasChild(names[i]) ? this.flame.getChild(names[i]) : null;
			}
			for (int i = 0; i < this.embers.length; i++) {
				this.embers[i] = this.flame.hasChild("ember" + i) ? this.flame.getChild("ember" + i) : null;
			}
		}
	}

	private static ModelPart findFlame(ModelPart body) {
		if (!body.hasChild("tail1")) return null;
		ModelPart cur = body.getChild("tail1");
		for (int i = 2; i <= RaceModels.TAIL_SEGMENTS; i++) {
			String name = "tail" + i;
			if (!cur.hasChild(name)) return null;
			cur = cur.getChild(name);
		}
		return cur.hasChild("flame") ? cur.getChild("flame") : null;
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

		if (sweepFlap) {
			// Battement avant / arrière : les ailes balaient vers l'avant puis repartent vers l'arrière,
			// avec seulement un léger mouvement vertical pour rester naturel.
			float swingLeft = (float) Math.sin(wing.phase()) * wing.amp() * 0.85F;
			float swingRight = (float) Math.sin(wing.phase() + wing.stagger()) * wing.amp() * 0.85F;
			float liftLeft = (float) Math.cos(wing.phase()) * wing.amp() * 0.15F;
			float liftRight = (float) Math.cos(wing.phase() + wing.stagger()) * wing.amp() * 0.15F;
			float idleSweep = (float) Math.sin(state.ageInTicks() * 0.07F) * 0.05F * (1.0F - wing.open());
			if (wingLeft != null) {
				wingLeft.xScale = wingLeft.yScale = wingLeft.zScale = SKYPIEAN_SCALE;
				wingLeft.zRot = RaceModels.WING_TILT_LEFT - raise - liftLeft;
				wingLeft.yRot = -sweep + swingLeft + idleSweep;
			}
			if (wingRight != null) {
				wingRight.xScale = wingRight.yScale = wingRight.zScale = SKYPIEAN_SCALE;
				wingRight.zRot = RaceModels.WING_TILT_RIGHT + raise + liftRight;
				wingRight.yRot = sweep - swingRight - idleSweep;
			}
		} else {
			if (wingLeft != null) {
				wingLeft.zRot = RaceModels.WING_TILT_LEFT - raise - flapLeft + idle;
				wingLeft.yRot = -sweep - sweepLeft;
			}
			if (wingRight != null) {
				wingRight.zRot = RaceModels.WING_TILT_RIGHT + raise + flapRight - idle;
				wingRight.yRot = sweep + sweepRight;
			}
		}
		if (flame != null) {
			animateFlame(state.ageInTicks());
		}
	}

	/**
	 * Flamme vivante : elle ondule, chaque couche grandit / rétrécit à son propre rythme (vacillement), les plans croisés
	 * tournent lentement sur eux-mêmes, et des braises montent en tournoyant puis s'éteignent.
	 */
	private void animateFlame(float t) {
		flame.zRot = (float) Math.sin(t * 0.45F) * 0.12F;
		flame.xScale = flame.yScale = flame.zScale = 1.0F;
		for (int i = 0; i < flameLayers.length; i++) {
			ModelPart layer = flameLayers[i];
			if (layer == null) {
				continue;
			}
			float flick = 1.0F + 0.13F * (float) Math.sin(t * (0.9F + 0.35F * i) + i * 1.7F)
					+ 0.06F * (float) Math.sin(t * (2.3F + 0.5F * i) + i);
			float breathe = 1.0F - 0.08F * (float) Math.sin(t * 1.3F + i * 2.1F);
			layer.yScale = flick;
			layer.xScale = breathe;
			layer.zScale = breathe;
			layer.yRot = t * (0.05F + 0.03F * i) * (i % 2 == 0 ? 1.0F : -1.0F);
			layer.zRot = (float) Math.sin(t * (0.7F + 0.2F * i) + i) * 0.06F;
		}
		for (int k = 0; k < embers.length; k++) {
			ModelPart e = embers[k];
			if (e == null) {
				continue;
			}
			float p = (t * 0.045F + k / (float) embers.length) % 1.0F;   // 0 = naissance, 1 = extinction
			float a = t * 0.12F + k * 1.9F;
			float r = 1.5F + 1.5F * p;
			e.x = (float) Math.cos(a) * r;
			e.z = (float) Math.sin(a) * r;
			e.y = -5.0F - p * 13.0F;
			float size = 1.0F - p;
			e.xScale = e.yScale = e.zScale = Math.max(0.05F, size);
		}
	}
}
