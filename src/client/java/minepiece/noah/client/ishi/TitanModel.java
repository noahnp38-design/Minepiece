package minepiece.noah.client.ishi;

import minepiece.noah.ability.ishi.IshiAnim;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Titan de pierre (Ishi Ishi no Mi) : un seul modèle animé (pas des milliers de blocs). Chaque plaque est une pièce :
 * - formation : les pièces surgissent du sol dans l'ordre (jambes, bassin, torse, épaules et bras, tête, mains),
 *   remontent en tournoyant légèrement et s'emboîtent ; le titan, d'abord voûté, se redresse ;
 * - marche lourde (jambes, balancement du corps, bras), respiration, tête qui suit le regard ;
 * - coup de poing (épaule, torse et bras), bras levé puis abattu (Éruption Titan) ;
 * - effondrement : fissures (tremblement des plaques) puis les pièces se détachent, tombent et s'écrasent.
 * Unités du modèle : 1/16 de bloc avant mise à l'échelle (pieds en y = 0, y vers le bas).
 */
public class TitanModel extends Model<TitanModel.State> {
	/** État d'animation d'un titan (calculé une fois par image dans l'état de rendu du joueur). */
	public static final class State {
		/** Formation (0..1) ; 1 = formé. */
		public float form = 1.0F;
		/** Temps écoulé dans l'effondrement (ticks), -1 = aucun. */
		public float collapse = -1.0F;
		public int action;
		public float actionTime;
		public float walkPos;
		public float walkSpeed;
		public float headYaw;
		public float headPitch;
		public float age;
		/** Vue à la 1re personne : seul l'avant-bras droit et la main sont dessinés. */
		public boolean firstPerson;
	}

	private final ModelPart[] pieces;
	/** Pièces de l'avant-bras et de la main droite (seules visibles à la 1re personne). */
	private final boolean[] forearm;
	private final float[] reveal;
	private final float[] seed;
	private final ModelPart hips;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightForearm;
	private final ModelPart leftForearm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart rightShin;
	private final ModelPart leftShin;
	private final ModelPart[] rightFingers = new ModelPart[4];
	private final ModelPart[] leftFingers = new ModelPart[4];
	private final ModelPart rightThumb;
	private final ModelPart leftThumb;

	public TitanModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
		String[][] table = IshiModels.TITAN_PIECES;
		this.pieces = new ModelPart[table.length];
		this.reveal = new float[table.length];
		this.forearm = new boolean[table.length];
		this.seed = new float[table.length];
		for (int i = 0; i < table.length; i++) {
			ModelPart p = root;
			for (String s : table[i][0].split("/")) {
				p = p.getChild(s);
			}
			pieces[i] = p;
			forearm[i] = table[i][0].startsWith("body/right_arm/right_forearm/");
			reveal[i] = Float.parseFloat(table[i][1]);
			seed[i] = (float) ((Math.sin(i * 12.9898) * 43758.5453) % 1.0 + 1.0) % 1.0F;
		}
		this.hips = root.getChild("hips");
		this.body = root.getChild("body");
		this.head = body.getChild("head");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
		this.rightForearm = rightArm.getChild("right_forearm");
		this.leftForearm = leftArm.getChild("left_forearm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.rightShin = rightLeg.getChild("right_shin");
		this.leftShin = leftLeg.getChild("left_shin");
		ModelPart rh = rightForearm.getChild("right_hand");
		ModelPart lh = leftForearm.getChild("left_hand");
		for (int i = 0; i < 4; i++) {
			rightFingers[i] = rh.getChild("right_finger" + i);
			leftFingers[i] = lh.getChild("left_finger" + i);
		}
		this.rightThumb = rh.getChild("right_thumb");
		this.leftThumb = lh.getChild("left_thumb");
	}

	@Override
	public void setupAnim(State s) {
		for (ModelPart p : this.allParts()) {
			p.resetPose();
			p.visible = true;
		}
		float rad = (float) (Math.PI / 180.0);
		// ---- marche lourde : grandes foulées lentes, corps qui se balance
		float amp = Math.min(1.0F, s.walkSpeed * 1.4F);
		float ph = s.walkPos * 0.42F;
		float c = (float) Math.cos(ph);
		float sn = (float) Math.sin(ph);
		rightLeg.xRot += c * 0.55F * amp;
		leftLeg.xRot -= c * 0.55F * amp;
		rightShin.xRot += Math.max(0.0F, -sn) * 0.7F * amp;
		leftShin.xRot += Math.max(0.0F, sn) * 0.7F * amp;
		rightArm.xRot -= c * 0.32F * amp;
		leftArm.xRot += c * 0.32F * amp;
		body.zRot += sn * 0.05F * amp;
		body.yRot += c * 0.06F * amp;
		hips.zRot += sn * 0.04F * amp;
		body.y += Math.abs(sn) * 1.2F * amp;
		hips.y += Math.abs(sn) * 1.2F * amp;
		// respiration
		float br = (float) Math.sin(s.age * 0.06F);
		body.xRot += br * 0.012F;
		rightArm.zRot += br * 0.015F;
		leftArm.zRot -= br * 0.015F;
		// tête qui suit le regard (amortie : le cou d'un géant de pierre)
		head.yRot += Math.max(-1.0F, Math.min(1.0F, s.headYaw * rad * 0.85F));
		head.xRot += Math.max(-0.6F, Math.min(0.7F, s.headPitch * rad * 0.7F));
		// doigts : légèrement ouverts au repos
		float open = 0.35F;

		// ---- attaques
		if (s.action == IshiAnim.TITAN_PUNCH) {
			float t = s.actionTime;
			float wind = IshiAnim.smooth(t / IshiAnim.TITAN_WIND);
			float strike = IshiAnim.smooth((t - IshiAnim.TITAN_WIND) / (IshiAnim.TITAN_STRIKE - IshiAnim.TITAN_WIND));
			float back = 1.0F - IshiAnim.smooth((t - IshiAnim.TITAN_STRIKE - 4) / 14.0F);
			float aim = s.headPitch * rad;
			float armX = (0.7F * wind) * (1 - strike) + (-1.55F + aim) * strike;
			rightArm.xRot = rightArm.xRot + (armX - rightArm.xRot) * back;
			rightArm.yRot += (-0.25F * wind * (1 - strike) + 0.15F * strike) * back;
			rightForearm.xRot += (-1.3F * wind * (1 - strike) - 0.05F * strike) * back;
			body.yRot += (0.5F * wind * (1 - strike) - 0.45F * strike) * back;
			body.xRot += 0.18F * strike * back;
			leftArm.xRot += (-0.5F * wind + 0.6F * strike) * back;
			rightLeg.xRot += 0.3F * strike * back;
			leftLeg.xRot -= 0.35F * strike * back;
			open = 0.0F;
		} else if (s.action == IshiAnim.TITAN_ERUPT) {
			float t = s.actionTime;
			float up = IshiAnim.smooth(t / (IshiAnim.TITAN_ERUPT_AT - 2.0F));
			float slam = IshiAnim.smooth((t - IshiAnim.TITAN_ERUPT_AT + 2) / 4.0F);
			float back = 1.0F - IshiAnim.smooth((t - IshiAnim.TITAN_ERUPT_AT - 6) / 14.0F);
			float x = -2.7F * up * (1 - slam) + (-0.8F) * slam;
			rightArm.xRot = rightArm.xRot + (x - rightArm.xRot) * back;
			rightArm.zRot += 0.2F * up * back;
			body.xRot += (-0.12F * up * (1 - slam) + 0.3F * slam) * back;
			open = 0.9F * back;
		}

		for (int i = 0; i < 4; i++) {
			rightFingers[i].xRot += open * (0.8F + i * 0.1F);
			leftFingers[i].xRot += 0.35F * (0.8F + i * 0.1F);
		}
		rightThumb.zRot += open * 0.4F;

		// ---- formation : les plaques surgissent du sol et s'assemblent ; le titan se redresse
		if (s.form < 1.0F) {
			float f = s.form;
			float hunch = 1.0F - IshiAnim.smooth((f - 0.55F) / 0.45F);
			body.xRot += 0.55F * hunch;
			head.xRot += 0.3F * hunch;
			rightArm.xRot -= 0.4F * hunch;
			leftArm.xRot -= 0.4F * hunch;
			rightLeg.xRot -= 0.35F * hunch;
			leftLeg.xRot -= 0.35F * hunch;
			rightShin.xRot += 0.7F * hunch;
			leftShin.xRot += 0.7F * hunch;
			body.y += 10.0F * hunch;
			hips.y += 10.0F * hunch;
			for (int i = 0; i < 4; i++) {
				rightFingers[i].xRot += 1.1F * hunch;
				leftFingers[i].xRot += 1.1F * hunch;
			}
			for (int i = 0; i < pieces.length; i++) {
				ModelPart p = pieces[i];
				float e = Math.max(0.0F, Math.min(1.0F, (f - reveal[i]) / 0.14F));
				if (e <= 0.0F) {
					p.visible = false;
					continue;
				}
				float k = 1.0F - e;
				p.y += k * k * 46.0F;
				p.x += (seed[i] - 0.5F) * 14.0F * k;
				p.z += (seed[(i + 7) % seed.length] - 0.5F) * 14.0F * k;
				p.xRot += (seed[i] - 0.5F) * 2.5F * k;
				p.zRot += (seed[(i + 3) % seed.length] - 0.5F) * 2.5F * k;
				float sc = 0.45F + 0.55F * e;
				p.xScale = sc;
				p.yScale = sc;
				p.zScale = sc;
			}
		}

		// ---- effondrement : fissures puis chute des plaques
		if (s.collapse >= 0.0F) {
			float t = s.collapse;
			float crack = Math.min(1.0F, t / IshiAnim.COLLAPSE_FALL);
			float fall = Math.max(0.0F, (t - IshiAnim.COLLAPSE_FALL) / (IshiAnim.COLLAPSE_END - IshiAnim.COLLAPSE_FALL));
			body.xRot += 0.35F * fall;
			rightArm.zRot += 0.5F * fall;
			leftArm.zRot -= 0.5F * fall;
			rightLeg.xRot -= 0.3F * fall;
			leftLeg.xRot -= 0.3F * fall;
			for (int i = 0; i < pieces.length; i++) {
				ModelPart p = pieces[i];
				float jit = crack * (1.0F - fall) * 0.6F;
				p.x += (float) Math.sin(s.age * 3.1F + i) * jit;
				p.y += (float) Math.cos(s.age * 2.7F + i * 1.3F) * jit;
				// les pièces du haut se détachent en premier
				float delay = (1.0F - reveal[i]) * 0.45F + seed[i] * 0.15F;
				float k = Math.max(0.0F, Math.min(1.0F, (fall - delay) / 0.4F));
				if (k <= 0.0F) {
					continue;
				}
				if (k >= 1.0F) {
					p.visible = false;
					continue;
				}
				p.y += k * k * 95.0F;
				p.x += (seed[i] - 0.5F) * 30.0F * k;
				p.z += (seed[(i + 5) % seed.length] - 0.5F) * 30.0F * k;
				p.xRot += (seed[i] - 0.5F) * 4.0F * k;
				p.yRot += (seed[(i + 2) % seed.length] - 0.5F) * 3.0F * k;
				p.zRot += (seed[(i + 9) % seed.length] - 0.5F) * 4.0F * k;
				float sc = 1.0F - 0.45F * k;
				p.xScale = sc;
				p.yScale = sc;
				p.zScale = sc;
			}
		}

		// ---- vue à la 1re personne : bras droit seul
		if (s.firstPerson) {
			// l'avant-bras droit est ramené à l'origine du modèle (la vue le place à l'écran)
			for (ModelPart q : new ModelPart[] {body, rightArm, rightForearm}) {
				q.x = 0.0F;
				q.y = 0.0F;
				q.z = 0.0F;
				q.xRot = 0.0F;
				q.yRot = 0.0F;
				q.zRot = 0.0F;
			}
			hips.visible = false;
			rightLeg.visible = false;
			leftLeg.visible = false;
			head.visible = false;
			leftArm.visible = false;
			for (int i = 0; i < pieces.length; i++) {
				pieces[i].visible = pieces[i].visible && forearm[i];
			}
		}
	}

	public ModelPart rightArm() {
		return rightArm;
	}

	public ModelPart body() {
		return body;
	}
}
