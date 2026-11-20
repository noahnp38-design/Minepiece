package minepiece.noah.client.ishi;

import minepiece.noah.ability.ishi.IshiAnim;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Poing colossal : bras de pierre qui se forme sur le bras droit du joueur (3e personne : suit la pose du bras du
 * modèle du joueur ; 1re personne : posé par IshiFirstPerson). Animation :
 * - formation (0-20) : les plaques apparaissent du poignet vers l'épaule, chacune arrive de l'extérieur et s'emboîte ;
 *   le poing se forme (paume, doigts un par un, pouce, plaques des phalanges) ;
 * - armé (20-30) : le poing GROSSIT ; frappe (30-34) ; maintien ; retour (38-54) : le poing reprend sa taille et la
 *   pierre se détache dans l'ordre inverse.
 */
public class StoneArmModel extends Model<StoneArmModel.State> {
	public static final class State {
		/** Pose du bras droit du joueur (3e personne), null à la 1re personne (bras posé par l'appelant). */
		public PartPose arm;
		/** Temps de l'animation du Poing colossal (ticks). */
		public float time;
	}

	private final ModelPart arm;
	private final ModelPart fist;
	private final ModelPart[] pieces;
	private final float[] reveal;
	private final float[] seed;

	public StoneArmModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
		this.arm = root.getChild("arm");
		this.fist = arm.getChild("fist");
		String[][] table = IshiModels.ARM_PIECES;
		this.pieces = new ModelPart[table.length];
		this.reveal = new float[table.length];
		this.seed = new float[table.length];
		for (int i = 0; i < table.length; i++) {
			ModelPart p = root;
			for (String s : table[i][0].split("/")) {
				p = p.getChild(s);
			}
			pieces[i] = p;
			reveal[i] = Float.parseFloat(table[i][1]);
			seed[i] = (float) ((Math.sin(i * 78.233) * 12543.2) % 1.0 + 1.0) % 1.0F;
		}
	}

	/** Recouvrement (0..1) : monte pendant la formation, redescend pendant le retour. */
	public static float cover(float t) {
		if (t < IshiAnim.FIST_HOLD) {
			return Math.min(1.0F, t / IshiAnim.FIST_FORM);
		}
		return Math.max(0.0F, 1.0F - (t - IshiAnim.FIST_HOLD) / (IshiAnim.FIST_TOTAL - IshiAnim.FIST_HOLD));
	}

	/** Taille du poing (facteur) : se forme, grossit quand le bras s'arme, énorme à la frappe, puis rétrécit. */
	public static float fistScale(float t) {
		if (t < IshiAnim.FIST_FORM) {
			return 0.7F + 0.6F * IshiAnim.smooth(t / IshiAnim.FIST_FORM);
		}
		if (t < IshiAnim.FIST_WIND) {
			return 1.3F + 0.7F * IshiAnim.smooth((t - IshiAnim.FIST_FORM) / (IshiAnim.FIST_WIND - IshiAnim.FIST_FORM));
		}
		if (t < IshiAnim.FIST_HOLD) {
			return 2.0F + 0.35F * IshiAnim.smooth((t - IshiAnim.FIST_WIND) / 3.0F);
		}
		return 2.35F - 1.35F * IshiAnim.smooth((t - IshiAnim.FIST_HOLD) / (IshiAnim.FIST_TOTAL - IshiAnim.FIST_HOLD));
	}

	@Override
	public void setupAnim(State s) {
		for (ModelPart p : this.allParts()) {
			p.resetPose();
			p.visible = true;
		}
		if (s.arm != null) {
			arm.loadPose(s.arm);
		}
		float t = s.time;
		float c = cover(t);
		float g = fistScale(t);
		fist.xScale = g;
		fist.yScale = g;
		fist.zScale = g;
		// le poing serré tremble légèrement de puissance quand il est armé
		if (t >= IshiAnim.FIST_FORM && t < IshiAnim.FIST_WIND) {
			fist.x += (float) Math.sin(t * 5.3F) * 0.08F;
			fist.z += (float) Math.cos(t * 4.7F) * 0.08F;
		}
		for (int i = 0; i < pieces.length; i++) {
			ModelPart p = pieces[i];
			float e = Math.max(0.0F, Math.min(1.0F, (c - reveal[i] * 0.85F) / 0.15F));
			if (e <= 0.0F) {
				p.visible = false;
				continue;
			}
			float k = 1.0F - e;
			float dir = seed[i] - 0.5F;
			p.x += dir * 6.0F * k;
			p.y += 3.0F * k;
			p.z += (seed[(i + 5) % seed.length] - 0.5F) * 6.0F * k;
			p.xRot += dir * 2.0F * k;
			p.zRot += (seed[(i + 3) % seed.length] - 0.5F) * 2.0F * k;
			float sc = 0.3F + 0.7F * e;
			p.xScale = sc;
			p.yScale = sc;
			p.zScale = sc;
		}
	}

	public ModelPart arm() {
		return arm;
	}
}
