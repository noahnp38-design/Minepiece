package minepiece.noah.client.mixin;

import minepiece.noah.client.render.RaceRenderData;
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
		state.setData(RaceRenderData.RACE, profile == null ? Race.HUMAN : profile.race());
	}
}
