package minepiece.noah.client.mixin;

import minepiece.noah.ability.giraffe.GiraffeAnim;
import minepiece.noah.ability.giraffe.GiraffeState;
import minepiece.noah.client.render.GiraffeRenderData;
import minepiece.noah.client.render.RaceRenderData;
import minepiece.noah.client.render.WingAnimator;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import minepiece.noah.data.Race;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copie la race du joueur (synchronisée par le serveur) dans son état de rendu. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin<AvatarlikeEntity extends Avatar & ClientAvatarEntity> {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
	private void minepiece$extractRace(AvatarlikeEntity entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		PlayerProfile profile = entity.getAttached(ModAttachments.PROFILE);
		Race race = profile == null ? Race.HUMAN : profile.race();
		state.setData(RaceRenderData.RACE, race);
		state.setData(RaceRenderData.RAISE_HAND, Boolean.TRUE.equals(entity.getAttached(ModAttachments.RAISE_HAND)));
		boolean ishiRealInvisible = state.isInvisible;

		state.setData(RaceRenderData.ICE_ARM, minepiece.noah.client.render.IceArmLayer.progress(entity, partialTicks));

		// Zushi Zushi no Mi : sur la Plateforme gravitationnelle, le joueur est techniquement "monté" sur la roche (mouvement
		// parfaitement synchronisé) mais il est dessiné DEBOUT, pas assis.
		if (Boolean.TRUE.equals(entity.getAttached(ModAttachments.GRAVITY_RIDE))) {
			state.isPassenger = false;
		}

		extractGiraffe(entity, state, partialTicks);

		// Haki : recouvrement du Koka, arcs des Rois, yeux de l'Observation, pose animée
		state.setData(minepiece.noah.client.haki.HakiRenderData.KOKA, minepiece.noah.client.haki.HakiLayer.covers(entity, partialTicks));
		Integer hakiFlags = entity.getAttached(ModAttachments.HAKI_FLAGS);
		int hf = hakiFlags == null ? 0 : hakiFlags;
		state.setData(minepiece.noah.client.haki.HakiRenderData.KING_FRAME, minepiece.noah.client.haki.HakiLayer.kingFrame(entity));
		Long hakiPose = entity.getAttached(ModAttachments.HAKI_POSE);
		int pose = 0;
		float poseTime = 0.0F;
		if (hakiPose != null) {
			pose = (int) (hakiPose & 31L);
			poseTime = (float) (entity.level().getGameTime() - (hakiPose >> 5)) + partialTicks;
		}
		state.setData(minepiece.noah.client.haki.HakiRenderData.POSE, pose);
		state.setData(minepiece.noah.client.haki.HakiRenderData.POSE_TIME, poseTime);
		boolean obsFocus = (pose & 15) == minepiece.noah.haki.HakiPose.OBS_FOCUS && poseTime > 6.0F && poseTime < 36.0F;
		state.setData(minepiece.noah.client.haki.HakiRenderData.EYES, (hf & 4) != 0 || obsFocus);

		// Goro Goro no Mi : pose animée (synchronisée par le serveur, avancement calculé depuis le temps de jeu)
		Long goro = entity.getAttached(ModAttachments.GORO_POSE);
		if (goro == null) {
			state.setData(RaceRenderData.GORO_POSE, 0);
		} else {
			state.setData(RaceRenderData.GORO_POSE, (int) (goro & 7L));
			state.setData(RaceRenderData.GORO_TIME, (float) (entity.level().getGameTime() - (goro >> 3)) + partialTicks);
		}

		// Ishi Ishi no Mi : bras de pierre, Titan de pierre (le titan remplace le corps du joueur, comme la girafe)
		minepiece.noah.client.ishi.IshiView ishi = minepiece.noah.client.ishi.IshiView.of(entity, partialTicks, ishiRealInvisible);
		state.setData(minepiece.noah.client.ishi.IshiView.KEY, ishi);
		if (ishi != null && ishi.titanVisible() && !ishiRealInvisible) {
			state.isInvisible = true;
			state.isInvisibleToPlayer = false;
		}

		// Races à ailes : on calcule le battement (le drapeau "vole" est synchronisé par le serveur,
		// donc les ailes des autres joueurs battent aussi).
		if (race.canFly()) {
			boolean flying = Boolean.TRUE.equals(entity.getAttached(ModAttachments.FLYING));
			boolean airborne = !entity.onGround() && !entity.isInWater();
			double dx = entity.getX() - entity.xo;
			double dy = entity.getY() - entity.yo;
			double dz = entity.getZ() - entity.zo;
			float speed = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			state.setData(RaceRenderData.WING,
					WingAnimator.update(entity, race, state.ageInTicks, flying, airborne, speed));
		}
	}

	/**
	 * Ushi Ushi no Mi : poids visuels des formes, avancement de la transformation et de l'attaque en cours, calculés depuis le
	 * temps de jeu (donc identiques pour tous les joueurs). La girafe complète remplace le corps : on marque alors le joueur
	 * "invisible" pour le rendu vanille (corps, armure, objets, cape disparaissent) ; la vraie invisibilité est conservée à part.
	 */
	/** Pheasant Beak : glace sur le bras droit en vue à la première personne (gel puis dégel). */
	@Inject(method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
			at = @At("TAIL"))
	private void minepiece$iceFirstPerson(com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.SubmitNodeCollector collector, int light, net.minecraft.resources.Identifier skin,
			boolean sleeve, CallbackInfo ci) {
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		float progress = minepiece.noah.client.render.IceArmLayer.progress(player,
				net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		if (progress < 0.0F) {
			return;
		}
		net.minecraft.client.model.player.PlayerModel model =
				(net.minecraft.client.model.player.PlayerModel) ((AvatarRenderer<?>) (Object) this).getModel();
		minepiece.noah.client.render.IceArmLayer.submitFirstPerson(poseStack, collector, model.rightArm, progress);
	}

	/** Haki : Koka et arcs des Rois sur les bras vus à la 1re personne. */
	@Inject(method = "renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
			at = @At("TAIL"))
	private void minepiece$hakiRightHand(com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.SubmitNodeCollector collector, int light, net.minecraft.resources.Identifier skin,
			boolean sleeve, CallbackInfo ci) {
		minepiece$hakiHand(poseStack, collector, light, true);
	}

	@Inject(method = "renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
			at = @At("TAIL"))
	private void minepiece$hakiLeftHand(com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.SubmitNodeCollector collector, int light, net.minecraft.resources.Identifier skin,
			boolean sleeve, CallbackInfo ci) {
		minepiece$hakiHand(poseStack, collector, light, false);
	}

	@org.spongepowered.asm.mixin.Unique
	private void minepiece$hakiHand(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
			int light, boolean right) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		net.minecraft.client.model.player.PlayerModel model =
				(net.minecraft.client.model.player.PlayerModel) ((AvatarRenderer<?>) (Object) this).getModel();
		boolean slim = mc.player.getSkin().model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
		minepiece.noah.client.haki.HakiLayer.submitFirstPerson(poseStack, collector, light, right ? model.rightArm : model.leftArm, right,
				mc.player, slim, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	/** Haki : le corps entier se décale pendant les esquives (pas de côté, recul, se baisser). */
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void minepiece$hakiDodge(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, float bodyRot, float scale,
			CallbackInfo ci) {
		Integer pose = state.getData(minepiece.noah.client.haki.HakiRenderData.POSE);
		Float time = state.getData(minepiece.noah.client.haki.HakiRenderData.POSE_TIME);
		if (pose != null && pose != 0 && time != null) {
			minepiece.noah.client.haki.HakiPoseAnim.bodyOffset(poseStack, pose, time);
		}
	}

	/** Ishi Ishi no Mi : le joueur s'enfonce dans la roche (fusion avec le terrain) puis en ressort après l'effondrement. */
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void minepiece$ishiSink(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, float bodyRot, float scale,
			CallbackInfo ci) {
		minepiece.noah.client.ishi.IshiView v = state.getData(minepiece.noah.client.ishi.IshiView.KEY);
		if (v != null) {
			float d = v.sinkDepth();
			if (d > 0.0F) {
				poseStack.translate(0.0F, -d / Math.max(0.05F, scale), 0.0F);
			}
		}
	}

	/**
	 * Défibrillateur (Goro Goro no Mi) : le joueur KO tombe en arrière et reste allongé sur le dos, puis se relève.
	 * Rotation de tout le corps autour des pieds (après l'orientation du corps), visible par tous les joueurs.
	 */
	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void minepiece$goroKo(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, float bodyRot, float scale,
			CallbackInfo ci) {
		Integer pose = state.getData(RaceRenderData.GORO_POSE);
		Float time = state.getData(RaceRenderData.GORO_TIME);
		if (pose == null || time == null) {
			return;
		}
		float lie;
		float jolt = 0.0F;
		if (pose == minepiece.noah.ability.goro.GoroPose.KO) {
			float k = Math.min(1.0F, time / minepiece.noah.ability.goro.GoroPose.KO_FALL_TICKS);
			lie = k * k * (3 - 2 * k);
			// le corps réagit aux impulsions électriques (petits soubresauts de la poitrine)
			for (int p : new int[] {24, 32, 40, 52, 62}) {
				float d = time - p;
				if (d >= 0 && d < 4) {
					jolt = Math.max(jolt, (p >= 52 ? 0.22F : 0.1F) * (float) Math.sin(d / 4.0F * Math.PI));
				}
			}
		} else if (pose == minepiece.noah.ability.goro.GoroPose.GET_UP) {
			float k = Math.min(1.0F, time / minepiece.noah.ability.goro.GoroPose.GET_UP_TICKS);
			lie = 1.0F - k * k * (3 - 2 * k);
		} else {
			return;
		}
		poseStack.translate(0.0F, (0.14F + jolt) * lie, 0.0F);
		poseStack.rotateDegrees(com.mojang.math.Axis.XP, 90.0F * lie);
	}

	@org.spongepowered.asm.mixin.Unique
	private void extractGiraffe(AvatarlikeEntity entity, AvatarRenderState state, float partialTicks) {
		GiraffeState gs = entity.getAttached(ModAttachments.GIRAFFE);
		if (gs == null || (gs.form() == GiraffeAnim.HUMAN && gs.prev() == GiraffeAnim.HUMAN && gs.anim() == GiraffeAnim.ANIM_NONE)) {
			state.setData(GiraffeRenderData.VIEW, GiraffeRenderData.View.NONE);
			return;
		}
		float now = entity.level().getGameTime() + partialTicks;
		float wH = GiraffeAnim.weight(gs, GiraffeAnim.HYBRID, now);
		float wG = GiraffeAnim.weight(gs, GiraffeAnim.GIRAFFE, now);
		float animTime = GiraffeAnim.animTime(gs, now);
		int anim = animTime < 0.0F ? GiraffeAnim.ANIM_NONE : gs.anim();
		float scale = entity.getScale();
		boolean realInvisible = state.isInvisible;
		state.setData(GiraffeRenderData.VIEW, new GiraffeRenderData.View(wH, wG, animTime, anim, scale, realInvisible,
				GiraffeAnim.transition(gs, now)));
		if (wG > 0.35F && !realInvisible) {
			state.isInvisible = true;
			state.isInvisibleToPlayer = false;   // sinon le joueur local verrait son corps en fantôme translucide
		}
		// Kirin Roundabout : tout le corps tourne sur lui-même (les couches, y compris la girafe, suivent la rotation du corps).
		if (anim == GiraffeAnim.ANIM_KIRIN) {
			state.bodyRot += GiraffeAnim.kirinAngle(animTime);
		}
	}
}
