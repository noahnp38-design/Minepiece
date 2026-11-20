package minepiece.noah.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Un joueur invisible (Suke Suke no Mi) cache aussi son équipement : ici une couche de rendu (ItemInHandLayer). */
@Mixin(ItemInHandLayer.class)
public abstract class HideHandItemMixin {
	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
			at = @At("HEAD"), cancellable = true)
	private void minepiece$hideWhenInvisible(PoseStack poseStack, SubmitNodeCollector collector, int light, ArmedEntityRenderState state,
			float a, float b, CallbackInfo ci) {
		if (state instanceof AvatarRenderState && state.isInvisible) {
			ci.cancel();
		}
	}
}
