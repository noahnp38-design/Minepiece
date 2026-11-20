package minepiece.noah.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import minepiece.noah.client.haki.HakiPoseAnim;
import minepiece.noah.client.haki.HakiRenderData;
import minepiece.noah.data.ModAttachments;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Haki des Rois : toute entité vivante évanouie s'effondre (en arrière, vers l'avant, à genoux puis au sol, sur le côté),
 * reste au sol puis se relève. Rotation de tout le modèle autour des pieds : fonctionne avec tous les modèles.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("RETURN"))
	private void minepiece$hakiKoExtract(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		Long ko = entity.getAttached(ModAttachments.HAKI_KO);
		if (ko == null) {
			state.setData(HakiRenderData.KO, 0);
			return;
		}
		state.setData(HakiRenderData.KO, (int) (ko & 15L));
		state.setData(HakiRenderData.KO_TIME, (float) (entity.level().getGameTime() - (ko >> 4)) + partialTicks);
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
			at = @At("TAIL"))
	private void minepiece$hakiKoFall(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
		Integer ko = state.getData(HakiRenderData.KO);
		Float t = state.getData(HakiRenderData.KO_TIME);
		if (ko != null && ko != 0 && t != null) {
			HakiPoseAnim.koTransform(state, poseStack, ko, t);
		}
	}
}
