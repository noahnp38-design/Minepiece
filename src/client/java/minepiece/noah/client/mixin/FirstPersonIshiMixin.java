package minepiece.noah.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import minepiece.noah.client.ishi.IshiLayer;
import minepiece.noah.client.ishi.IshiView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ishi Ishi no Mi, vue à la 1re personne : pendant le Poing colossal, le bras droit (même avec un objet en main) est
 * remplacé par le bras de pierre animé ; en Titan, par l'avant-bras géant du titan (la main gauche et l'objet tenu sont
 * masqués). Rendu personnalisé : voir IshiLayer.submitFirstPerson.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonIshiMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void minepiece$ishiArm(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState, float partialTicks,
			float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
			SubmitNodeCollector collector, int light, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		IshiView v = IshiView.of(mc.player, partialTicks, false);
		if (v == null) {
			return;
		}
		boolean titanForm = v.titanPhase() != 0;
		if (hand == InteractionHand.MAIN_HAND) {
			float walkPos = mc.player.walkAnimation.position(partialTicks);
			float walkSpeed = mc.player.walkAnimation.speed(partialTicks);
			if (IshiLayer.submitFirstPerson(poseStack, collector, light, v, walkPos, walkSpeed, mc.player.tickCount + partialTicks)) {
				ci.cancel();
			}
		} else if (titanForm) {
			ci.cancel();
		}
	}
}
