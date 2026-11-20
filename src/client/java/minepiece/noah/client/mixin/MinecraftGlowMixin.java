package minepiece.noah.client.mixin;

import minepiece.noah.data.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Haki de l'Observation : les présences vivantes dans le rayon (16 / 28 / 40 blocs selon la maîtrise) apparaissent en
 * contour discret, même derrière les obstacles. Uniquement pour le joueur qui utilise le Haki (rien n'est envoyé aux
 * autres, aucune entité n'est réellement modifiée).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
	private static final double[] RADIUS = {0.0, 16.0, 28.0, 40.0};

	@Inject(method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"), cancellable = true)
	private void minepiece$kenbunshoku(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player == null || entity == mc.player || !(entity instanceof LivingEntity) || entity instanceof ArmorStand || entity.isSpectator()) {
			return;
		}
		Integer f = mc.player.getAttached(ModAttachments.HAKI_FLAGS);
		if (f == null || (f & 2) == 0) {
			return;
		}
		double r = RADIUS[(f >> 8) & 3];
		if (mc.player.distanceToSqr(entity) <= r * r) {
			cir.setReturnValue(true);
		}
	}
}
