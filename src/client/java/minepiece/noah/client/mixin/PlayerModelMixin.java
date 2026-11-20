package minepiece.noah.client.mixin;

import minepiece.noah.ability.giraffe.GiraffeAnim;
import minepiece.noah.client.render.GiraffeRenderData;
import minepiece.noah.client.render.RaceRenderData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dai Enkai : Entei - pendant la charge, le joueur lève la main droite au-dessus de sa tête (la boule est au-dessus). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {
	protected PlayerModelMixin(ModelPart root) {
		super(root);
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
	private void minepiece$raiseHand(AvatarRenderState state, CallbackInfo ci) {
		if (Boolean.TRUE.equals(state.getData(RaceRenderData.RAISE_HAND))) {
			this.rightArm.xRot = -3.0F;
			this.rightArm.yRot = 0.0F;
			this.rightArm.zRot = 0.0F;
		}
		minepiece$giraffePose(state);
		minepiece$iceArmPose(state);
		minepiece$goroPose(state);
		minepiece$ishiPose(state);
		// Haki : concentration, esquives, libération, imprégnation ; membres relâchés si évanoui
		Integer hakiPose = state.getData(minepiece.noah.client.haki.HakiRenderData.POSE);
		Float hakiTime = state.getData(minepiece.noah.client.haki.HakiRenderData.POSE_TIME);
		if (hakiPose != null && hakiPose != 0 && hakiTime != null) {
			minepiece.noah.client.haki.HakiPoseAnim.apply(this, hakiPose, hakiTime);
		}
		Integer ko = state.getData(minepiece.noah.client.haki.HakiRenderData.KO);
		Float koTime = state.getData(minepiece.noah.client.haki.HakiRenderData.KO_TIME);
		if (ko != null && ko != 0 && koTime != null) {
			minepiece.noah.client.haki.HakiPoseAnim.koLimbs(this, ko, koTime);
		}
	}

	/**
	 * Goro Goro no Mi : bras tendu (Sango), bras levé au ciel (El Thor), deux mains levées puis lancer (Raigo),
	 * membres relâchés quand le joueur est KO (Défibrillateur), poussée sur les bras pour se relever.
	 */
	@Unique
	private void minepiece$goroPose(AvatarRenderState state) {
		Integer pose = state.getData(RaceRenderData.GORO_POSE);
		Float time = state.getData(RaceRenderData.GORO_TIME);
		if (pose == null || pose == 0 || time == null) {
			return;
		}
		float t = time;
		switch (pose) {
			case minepiece.noah.ability.goro.GoroPose.ARM_FORWARD -> {
				float k = Math.min(1.0F, t / 4.0F);
				this.rightArm.xRot += (this.head.xRot - 1.57F - this.rightArm.xRot) * k;
				this.rightArm.yRot += (this.head.yRot - this.rightArm.yRot) * k;
				this.rightArm.zRot *= 1.0F - k;
			}
			case minepiece.noah.ability.goro.GoroPose.ARM_UP -> {
				float k = Math.min(1.0F, t / 6.0F);
				this.rightArm.xRot += (-3.05F - this.rightArm.xRot) * k;
				this.rightArm.yRot *= 1.0F - k;
				this.rightArm.zRot += (0.12F - this.rightArm.zRot) * k;
			}
			case minepiece.noah.ability.goro.GoroPose.BOTH_UP -> {
				float k = Math.min(1.0F, t / 8.0F);
				float tremble = (float) Math.sin(t * 2.3F) * 0.03F;
				this.rightArm.xRot += (-2.95F + tremble - this.rightArm.xRot) * k;
				this.leftArm.xRot += (-2.95F - tremble - this.leftArm.xRot) * k;
				this.rightArm.zRot += (0.22F - this.rightArm.zRot) * k;
				this.leftArm.zRot += (-0.22F - this.leftArm.zRot) * k;
				this.rightArm.yRot *= 1.0F - k;
				this.leftArm.yRot *= 1.0F - k;
			}
			case minepiece.noah.ability.goro.GoroPose.THROW -> {
				float k = Math.min(1.0F, t / minepiece.noah.ability.goro.GoroPose.THROW_TICKS);
				float e = 1.0F - (1.0F - k) * (1.0F - k);
				float x = -2.95F + 1.6F * e;
				this.rightArm.xRot = x;
				this.leftArm.xRot = x;
				this.rightArm.zRot = 0.22F * (1.0F - e);
				this.leftArm.zRot = -0.22F * (1.0F - e);
				this.rightArm.yRot = 0.0F;
				this.leftArm.yRot = 0.0F;
			}
			case minepiece.noah.ability.goro.GoroPose.KO -> {
				this.rightArm.xRot = 0.0F;
				this.leftArm.xRot = 0.0F;
				this.rightArm.yRot = 0.0F;
				this.leftArm.yRot = 0.0F;
				this.rightArm.zRot = 0.55F;
				this.leftArm.zRot = -0.55F;
				this.rightLeg.xRot = 0.0F;
				this.leftLeg.xRot = 0.0F;
				this.rightLeg.zRot = 0.12F;
				this.leftLeg.zRot = -0.12F;
				this.head.xRot = -0.2F;
				this.head.yRot = 0.0F;
			}
			case minepiece.noah.ability.goro.GoroPose.GET_UP -> {
				float k = Math.min(1.0F, t / minepiece.noah.ability.goro.GoroPose.GET_UP_TICKS);
				this.rightArm.xRot = 0.9F * (1.0F - k);
				this.leftArm.xRot = 0.9F * (1.0F - k);
				this.rightLeg.xRot = -0.9F * (float) Math.sin(k * Math.PI) * 0.6F;
			}
			default -> {
			}
		}
	}

	/**
	 * Ishi Ishi no Mi : bras levé puis main tendue vers le sol (Éruption rocheuse), bras qui se forme, s'arme, frappe et
	 * revient (Poing colossal), concentration puis bras levés pendant que le joueur s'enfonce dans la roche (Titan), et
	 * sortie du sol après l'effondrement.
	 */
	@Unique
	private void minepiece$ishiPose(AvatarRenderState state) {
		minepiece.noah.client.ishi.IshiView v = state.getData(minepiece.noah.client.ishi.IshiView.KEY);
		if (v == null) {
			return;
		}
		float t = v.actionTime();
		if (v.action() == minepiece.noah.ability.ishi.IshiAnim.ERUPT) {
			float up = sm(t / minepiece.noah.ability.ishi.IshiAnim.ERUPT_RAISE);
			float point = sm((t - minepiece.noah.ability.ishi.IshiAnim.ERUPT_RAISE) / 6.0F);
			float back = sm((t - minepiece.noah.ability.ishi.IshiAnim.ERUPT_AT - 8) / 12.0F);
			float tremble = t > minepiece.noah.ability.ishi.IshiAnim.ERUPT_RAISE && t < minepiece.noah.ability.ishi.IshiAnim.ERUPT_AT
					? (float) Math.sin(t * 2.6F) * 0.05F : 0.0F;
			float x = -2.5F * up * (1 - point) + (-1.1F + tremble) * point;
			mix(this.rightArm, x, -0.1F, 0.05F, 1.0F - back);
			mix(this.leftArm, -0.4F * up, 0.0F, -0.15F, 1.0F - back);
		} else if (v.action() == minepiece.noah.ability.ishi.IshiAnim.FIST) {
			float form = sm(t / 8.0F);
			float wind = sm((t - minepiece.noah.ability.ishi.IshiAnim.FIST_FORM) / 8.0F);
			float strike = sm((t - minepiece.noah.ability.ishi.IshiAnim.FIST_WIND) / 3.0F);
			float back = sm((t - minepiece.noah.ability.ishi.IshiAnim.FIST_HOLD) / 14.0F);
			float x = -0.9F * form * (1 - wind) + 0.75F * wind * (1 - strike) + (this.head.xRot - 1.57F) * strike;
			mix(this.rightArm, x, (-0.25F * wind * (1 - strike)) + this.head.yRot * strike, 0.1F * (1 - strike), 1.0F - back);
			mix(this.leftArm, -0.7F * wind * (1 - strike) + 0.5F * strike, 0.0F, -0.1F, (1.0F - back) * Math.max(wind, strike));
			this.body.yRot += (0.35F * wind * (1 - strike) - 0.3F * strike) * (1.0F - back);
			this.rightLeg.xRot += -0.35F * strike * (1.0F - back);
			this.leftLeg.xRot += 0.35F * strike * (1.0F - back);
		}
		float tt = v.titanTime();
		if (v.titanPhase() == minepiece.noah.ability.ishi.IshiAnim.PHASE_FORM && tt < minepiece.noah.ability.ishi.IshiAnim.SINK_END) {
			// concentration (poings serrés devant soi, tête baissée) puis bras levés en s'enfonçant
			float focus = sm(tt / 6.0F);
			float sink = sm((tt - minepiece.noah.ability.ishi.IshiAnim.SINK_START) / 20.0F);
			mix(this.rightArm, -1.0F * (1 - sink) - 2.8F * sink, -0.5F * (1 - sink), 0.25F * sink, focus);
			mix(this.leftArm, -1.0F * (1 - sink) - 2.8F * sink, 0.5F * (1 - sink), -0.25F * sink, focus);
			this.head.xRot += (0.45F * (1 - sink) - 0.3F * sink) * focus;
			this.rightLeg.xRot += 0.2F * focus * (1 - sink);
			this.leftLeg.xRot -= 0.2F * focus * (1 - sink);
		} else if (v.titanPhase() == minepiece.noah.ability.ishi.IshiAnim.PHASE_COLLAPSE && tt >= minepiece.noah.ability.ishi.IshiAnim.COLLAPSE_END) {
			// le joueur ressort de la roche, bras tendus vers le haut puis retombant
			float k = sm((tt - minepiece.noah.ability.ishi.IshiAnim.COLLAPSE_END) / 16.0F);
			mix(this.rightArm, -2.6F, 0.0F, 0.2F, 1.0F - k);
			mix(this.leftArm, -2.6F, 0.0F, -0.2F, 1.0F - k);
		}
	}

	@Unique
	private static float sm(float x) {
		x = Math.max(0.0F, Math.min(1.0F, x));
		return x * x * (3.0F - 2.0F * x);
	}

	@Unique
	private static void mix(ModelPart part, float x, float y, float z, float k) {
		if (k <= 0.0F) {
			return;
		}
		part.xRot += (x - part.xRot) * k;
		part.yRot += (y - part.yRot) * k;
		part.zRot += (z - part.zRot) * k;
	}

	/** Pheasant Beak : bras droit tendu vers l'avant, légèrement vers le haut, dans la direction du regard. */
	@Unique
	private void minepiece$iceArmPose(AvatarRenderState state) {
		Float ice = state.getData(RaceRenderData.ICE_ARM);
		if (ice == null || ice < 0.0F) {
			return;
		}
		float k = Math.min(1.0F, ice * 3.0F);
		float targetX = this.head.xRot - 1.75F;
		float targetY = this.head.yRot - 0.12F;
		this.rightArm.xRot += (targetX - this.rightArm.xRot) * k;
		this.rightArm.yRot += (targetY - this.rightArm.yRot) * k;
		this.rightArm.zRot *= 1.0F - k;
	}

	/**
	 * Ushi Ushi no Mi : la tête du joueur est remplacée par celle de la girafe (cachée ici, dessinée par GiraffeLayer) ;
	 * poses des attaques : coup de pied du Rankyaku, bras écartés du Kirin Roundabout.
	 */
	@Unique
	private void minepiece$giraffePose(AvatarRenderState state) {
		GiraffeRenderData.View view = state.getData(GiraffeRenderData.VIEW);
		// La visibilité de la tête n'est jamais remise à zéro par le jeu : on la rétablit toujours explicitement.
		this.head.visible = true;
		if (view == null || !view.active() || view.realInvisible()) {
			return;
		}
		if (view.wHybrid() > 0.4F) {
			this.head.visible = false;
			this.hat.visible = false;
		}
		float t = view.animTime();
		if (t < 0.0F) {
			return;
		}
		if (view.anim() == GiraffeAnim.ANIM_RANKYAKU) {
			// Jambe droite levée violemment vers l'avant, bras opposé en arrière.
			float k = (float) Math.sin(Math.min(1.0F, t / GiraffeAnim.RANKYAKU_TICKS) * Math.PI);
			this.rightLeg.xRot = -2.1F * k;
			this.leftArm.xRot = 0.9F * k;
			this.rightArm.xRot = -0.6F * k;
		} else if (view.anim() == GiraffeAnim.ANIM_KIRIN) {
			float k = Math.min(1.0F, t / GiraffeAnim.KIRIN_PREP);
			this.rightArm.zRot = 1.45F * k;
			this.leftArm.zRot = -1.45F * k;
			this.rightArm.xRot = 0.0F;
			this.leftArm.xRot = 0.0F;
			this.rightLeg.zRot = 0.35F * k;
			this.leftLeg.zRot = -0.35F * k;
		} else if (view.anim() == GiraffeAnim.ANIM_BIGAN) {
			this.rightArm.xRot = 0.5F;
			this.leftArm.xRot = 0.5F;
		}
	}
}
