package minepiece.noah.client.mixin;

import minepiece.noah.client.ishi.IshiView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ishi Ishi no Mi : la caméra suit le joueur qui s'enfonce dans la roche (fusion avec le terrain) puis qui en ressort,
 * en douceur (même courbe que le rendu du corps). Pendant le titan, la hauteur des yeux suit déjà l'échelle du joueur.
 */
@Mixin(Camera.class)
public abstract class CameraIshiMixin {
	@Shadow
	private Entity entity;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void minepiece$ishiSink(float partialTicks, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (this.entity == null || this.entity != mc.player) {
			return;
		}
		IshiView v = IshiView.of(this.entity, partialTicks, false);
		if (v == null) {
			return;
		}
		float d = v.sinkDepth();
		if (d > 0.0F) {
			this.setPosition(this.position().add(0.0, -d * 0.78, 0.0));
		}
	}
}
