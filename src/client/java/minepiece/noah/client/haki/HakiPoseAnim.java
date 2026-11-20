package minepiece.noah.client.haki;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import minepiece.noah.haki.HakiKo;
import minepiece.noah.haki.HakiPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Animations du Haki sur le modèle du joueur, calculées à partir de la pose synchronisée et du temps écoulé :
 * entrée rapide, maintien, retour fluide à la posture normale (interpolation lissée). Le haut du corps (tête, torse,
 * bras) pivote autour des hanches, comme un vrai buste qui se penche, pendant que les jambes s'adaptent pour garder
 * l'équilibre ; le déplacement du corps entier (pas de côté, se baisser) est appliqué dans setupRotations.
 */
public final class HakiPoseAnim {
	private HakiPoseAnim() {}

	private static float smooth(float x) {
		x = Math.max(0.0F, Math.min(1.0F, x));
		return x * x * (3.0F - 2.0F * x);
	}

	/** Enveloppe 0 -> 1 -> 0 : entrée (25 %), maintien, sortie (40 %). */
	public static float envelope(int pose, float t) {
		float dur = HakiPose.duration(pose);
		if (dur <= 0 || t < 0 || t >= dur) {
			return 0.0F;
		}
		if ((pose & 15) == HakiPose.KING_HOLD) {
			return smooth(t / 8.0F);   // maintenue tant que la touche est enfoncée
		}
		float in = dur * 0.25F;
		float out = dur * 0.4F;
		if (t < in) {
			return smooth(t / in);
		}
		if (t > dur - out) {
			return smooth((dur - t) / out);
		}
		return 1.0F;
	}

	/** Penche tête + torse + bras autour des hanches (ax > 0 : vers l'avant ; az > 0 : vers la gauche). */
	private static void lean(HumanoidModel<?> m, float ax, float az) {
		for (ModelPart p : new ModelPart[] {m.head, m.body, m.rightArm, m.leftArm}) {
			float y = p.y - 12.0F;
			float z = p.z;
			float cx = (float) Math.cos(ax), sx = (float) Math.sin(ax);
			float y1 = y * cx - z * sx;
			float z1 = y * sx + z * cx;
			float x = p.x;
			float cz = (float) Math.cos(az), sz = (float) Math.sin(az);
			float x2 = x * cz - y1 * sz;
			float y2 = x * sz + y1 * cz;
			p.x = x2;
			p.y = y2 + 12.0F;
			p.z = z1;
			p.xRot += ax;
			p.zRot += az;
		}
	}

	private static float mix(float from, float to, float k) {
		return from + (to - from) * k;
	}

	/** Pose de Haki appliquée aux parties du modèle (après l'animation vanilla). */
	public static void apply(HumanoidModel<?> m, int pose, float t) {
		float e = envelope(pose, t);
		if (e <= 0.0F) {
			return;
		}
		boolean perfect = (pose & HakiPose.PERFECT) != 0;
		float a = perfect ? e * 1.25F : e;
		switch (pose & 15) {
			case HakiPose.ARM_FOCUS -> {
				// concentration : poings serrés devant la poitrine, tête baissée vers eux
				m.rightArm.xRot = mix(m.rightArm.xRot, -1.25F, e);
				m.rightArm.yRot = mix(m.rightArm.yRot, -0.45F, e);
				m.leftArm.xRot = mix(m.leftArm.xRot, -1.25F, e);
				m.leftArm.yRot = mix(m.leftArm.yRot, 0.45F, e);
				m.head.xRot = mix(m.head.xRot, 0.35F, e);
				m.rightLeg.zRot += 0.08F * e;
				m.leftLeg.zRot -= 0.08F * e;
			}
			case HakiPose.OBS_FOCUS -> {
				// tête inclinée, bras relâchés légèrement écartés
				m.head.xRot = mix(m.head.xRot, 0.6F, e);
				m.head.zRot = mix(m.head.zRot, 0.12F, e);
				m.rightArm.zRot += 0.12F * e;
				m.leftArm.zRot -= 0.12F * e;
			}
			case HakiPose.KING_ROAR, HakiPose.KING_HOLD -> {
				// posture imposante : tête levée, torse bombé, bras en arrière et écartés, jambes ancrées
				lean(m, -0.18F * e, 0.0F);
				m.head.xRot = mix(m.head.xRot, -0.5F, e);
				m.rightArm.xRot = mix(m.rightArm.xRot, 0.35F, e);
				m.rightArm.zRot = mix(m.rightArm.zRot, 0.4F, e);
				m.leftArm.xRot = mix(m.leftArm.xRot, 0.35F, e);
				m.leftArm.zRot = mix(m.leftArm.zRot, -0.4F, e);
				m.rightLeg.zRot += 0.14F * e;
				m.leftLeg.zRot -= 0.14F * e;
			}
			case HakiPose.DODGE_LEFT, HakiPose.DODGE_RIGHT -> {
				float s = (pose & 15) == HakiPose.DODGE_LEFT ? 1.0F : -1.0F;
				// buste et tête vers le côté, épaules qui pivotent, bras qui équilibrent, jambes qui s'écartent
				lean(m, 0.08F * a, 0.5F * a * s);
				m.body.yRot += 0.3F * a * s;
				m.head.yRot += 0.2F * a * s;
				m.rightArm.zRot += (s > 0 ? 0.55F : 0.2F) * a;
				m.leftArm.zRot -= (s > 0 ? 0.2F : 0.55F) * a;
				m.rightLeg.zRot += (s > 0 ? 0.25F : 0.05F) * a;
				m.leftLeg.zRot -= (s > 0 ? 0.05F : 0.25F) * a;
			}
			case HakiPose.DODGE_BACK -> {
				// buste en arrière, tête qui recule, bras écartés, un pied recule
				lean(m, -0.6F * a, 0.0F);
				m.head.xRot -= 0.35F * a;
				m.rightArm.zRot += 0.6F * a;
				m.leftArm.zRot -= 0.6F * a;
				m.rightArm.xRot -= 0.25F * a;
				m.leftArm.xRot -= 0.25F * a;
				m.rightLeg.xRot = mix(m.rightLeg.xRot, 0.45F, a);
				m.leftLeg.xRot = mix(m.leftLeg.xRot, -0.25F, a);
			}
			case HakiPose.DODGE_DUCK -> {
				// jambes pliées (fente), torse qui descend en avant, tête baissée, bras en garde
				lean(m, 0.6F * a, 0.0F);
				m.head.xRot += 0.25F * a;
				m.rightLeg.xRot = mix(m.rightLeg.xRot, -1.15F, a);
				m.leftLeg.xRot = mix(m.leftLeg.xRot, 0.75F, a);
				m.rightArm.xRot = mix(m.rightArm.xRot, -0.9F, a);
				m.leftArm.xRot = mix(m.leftArm.xRot, -0.7F, a);
			}
			case HakiPose.DASH_LEFT, HakiPose.DASH_RIGHT -> {
				float s = (pose & 15) == HakiPose.DASH_LEFT ? 1.0F : -1.0F;
				// corps incliné dans la direction, grande enjambée latérale, bras en balancier
				lean(m, 0.12F * e, 0.32F * e * s);
				m.rightLeg.zRot += (s > 0 ? 0.1F : 0.45F) * e;
				m.leftLeg.zRot -= (s > 0 ? 0.45F : 0.1F) * e;
				m.rightArm.zRot += 0.45F * e;
				m.leftArm.zRot -= 0.45F * e;
				m.head.zRot -= 0.15F * e * s;   // la tête reste droite : reprise d'équilibre
			}
			case HakiPose.DASH_BACK -> {
				lean(m, -0.3F * e, 0.0F);
				m.rightLeg.xRot = mix(m.rightLeg.xRot, 0.55F, e);
				m.leftLeg.xRot = mix(m.leftLeg.xRot, -0.35F, e);
				m.rightArm.xRot = mix(m.rightArm.xRot, -0.5F, e);
				m.leftArm.xRot = mix(m.leftArm.xRot, -0.5F, e);
			}
			case HakiPose.DASH_FORWARD -> {
				lean(m, 0.45F * e, 0.0F);
				m.rightLeg.xRot = mix(m.rightLeg.xRot, -0.7F, e);
				m.leftLeg.xRot = mix(m.leftLeg.xRot, 0.6F, e);
				m.rightArm.xRot = mix(m.rightArm.xRot, 0.6F, e);
				m.leftArm.xRot = mix(m.leftArm.xRot, 0.6F, e);
			}
			case HakiPose.EMISSION -> {
				// coup de poing tendu dans l'axe du regard, épaules qui tournent
				m.body.yRot += 0.35F * e;
				m.rightArm.xRot = mix(m.rightArm.xRot, m.head.xRot - 1.57F, e);
				m.rightArm.yRot = mix(m.rightArm.yRot, m.head.yRot + 0.1F, e);
				m.leftArm.xRot = mix(m.leftArm.xRot, 0.5F, e);
				m.rightLeg.xRot = mix(m.rightLeg.xRot, 0.3F, e);
				m.leftLeg.xRot = mix(m.leftLeg.xRot, -0.35F, e);
			}
			case HakiPose.INFUSE -> {
				// bras chargés, tendus vers le bas et écartés, qui tremblent sous la puissance
				float tr = (float) Math.sin(t * 2.7F) * 0.04F * e;
				m.rightArm.xRot = mix(m.rightArm.xRot, -0.35F + tr, e);
				m.leftArm.xRot = mix(m.leftArm.xRot, -0.35F - tr, e);
				m.rightArm.zRot = mix(m.rightArm.zRot, 0.35F, e);
				m.leftArm.zRot = mix(m.leftArm.zRot, -0.35F, e);
				m.head.xRot = mix(m.head.xRot, 0.2F, e);
			}
			default -> {
			}
		}
	}

	/** Membres relâchés d'un joueur évanoui. */
	public static void koLimbs(HumanoidModel<?> m, int ko, float t) {
		if (ko == 0) {
			return;
		}
		float k = ko >= 8 ? 1.0F - smooth(t / HakiKo.WAKE_TICKS) : smooth(t / 10.0F);
		m.rightArm.xRot = mix(m.rightArm.xRot, 0.0F, k);
		m.leftArm.xRot = mix(m.leftArm.xRot, 0.0F, k);
		m.rightArm.zRot = mix(m.rightArm.zRot, 0.5F, k);
		m.leftArm.zRot = mix(m.leftArm.zRot, -0.5F, k);
		m.rightLeg.xRot = mix(m.rightLeg.xRot, 0.0F, k);
		m.leftLeg.xRot = mix(m.leftLeg.xRot, 0.0F, k);
		m.rightLeg.zRot = mix(m.rightLeg.zRot, 0.1F, k);
		m.leftLeg.zRot = mix(m.leftLeg.zRot, -0.1F, k);
		m.head.xRot = mix(m.head.xRot, (ko & 7) == HakiKo.FORWARD ? 0.0F : -0.15F, k);
		m.head.yRot = mix(m.head.yRot, 0.35F, k);
	}

	/** Déplacement du corps entier pendant les esquives (repère local : +X droite, +Z arrière). */
	public static void bodyOffset(PoseStack ps, int pose, float t) {
		float e = envelope(pose, t);
		if (e <= 0.0F) {
			return;
		}
		float a = (pose & HakiPose.PERFECT) != 0 ? e * 1.25F : e;
		switch (pose & 15) {
			case HakiPose.DODGE_LEFT -> ps.translate(-0.22F * a, 0.0F, 0.0F);
			case HakiPose.DODGE_RIGHT -> ps.translate(0.22F * a, 0.0F, 0.0F);
			case HakiPose.DODGE_BACK -> ps.translate(0.0F, 0.0F, 0.2F * a);
			case HakiPose.DODGE_DUCK -> ps.translate(0.0F, -0.42F * a, 0.0F);
			case HakiPose.DASH_LEFT, HakiPose.DASH_RIGHT, HakiPose.DASH_BACK -> ps.translate(0.0F, -0.08F * e, 0.0F);
			default -> {
			}
		}
	}

	/** Chute KO de n'importe quelle entité vivante (rotation du corps autour des pieds). */
	public static void koTransform(LivingEntityRenderState s, PoseStack ps, int ko, float t) {
		if (ko == 0 || s.deathTime > 0.0F) {
			return;
		}
		boolean waking = ko >= 8;
		int variant = ko & 7;
		float fall = variant == HakiKo.KNEES ? 20.0F : 10.0F;
		float k = waking ? 1.0F - Math.max(0.0F, Math.min(1.0F, t / HakiKo.WAKE_TICKS)) : Math.max(0.0F, Math.min(1.0F, t / fall));
		float e = smooth(k);
		float lift = 0.12F * Math.max(0.3F, s.boundingBoxWidth);
		switch (variant) {
			case HakiKo.BACK -> {
				ps.translate(0.0F, lift * e, 0.0F);
				ps.rotateDegrees(Axis.XP, 90.0F * e);
			}
			case HakiKo.FORWARD -> {
				ps.translate(0.0F, lift * e, 0.0F);
				ps.rotateDegrees(Axis.XP, -90.0F * e);
			}
			case HakiKo.KNEES -> {
				float k1 = smooth(Math.min(1.0F, k * 2.0F));
				float k2 = smooth(Math.max(0.0F, k * 2.0F - 1.0F));
				ps.translate(0.0F, -0.28F * s.boundingBoxHeight * k1 * (1.0F - k2) + lift * k2, 0.0F);
				ps.rotateDegrees(Axis.XP, -(18.0F * k1 + 72.0F * k2));
			}
			default -> {
				ps.translate(0.0F, lift * e, 0.0F);
				ps.rotateDegrees(Axis.ZP, 90.0F * e);
			}
		}
	}
}
