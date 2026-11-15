package minepiece.noah.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Supprime le panorama tournant de Minecraft, UNIQUEMENT sur le menu principal
 * (les autres écrans gardent leur fond normal). Sans cela, il recouvrirait notre image.
 */
@Mixin(Screen.class)
public abstract class ScreenPanoramaMixin {
	// require = 0 : si une mise à jour de Minecraft renomme cette méthode, le jeu démarre quand même.
	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true, require = 0)
	private void minepiece$noPanoramaOnTitleScreen(CallbackInfo ci) {
		if ((Object) this instanceof TitleScreen) {
			ci.cancel();
		}
	}
}
