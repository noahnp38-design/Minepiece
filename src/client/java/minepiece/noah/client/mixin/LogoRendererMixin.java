package minepiece.noah.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cache le logo "MINECRAFT" vanilla sur le menu principal : notre image contient déjà le logo MINEPIECE.
 * (@Pseudo + require = 0 : aucun plantage possible si cette classe change dans une future version.)
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.components.LogoRenderer")
public abstract class LogoRendererMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
	private void minepiece$hideVanillaLogo(CallbackInfo ci) {
		if (Minecraft.getInstance().gui.screen() instanceof TitleScreen) {
			ci.cancel();
		}
	}
}
