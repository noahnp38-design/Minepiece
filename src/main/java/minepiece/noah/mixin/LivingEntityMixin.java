package minepiece.noah.mixin;

import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import minepiece.noah.data.Race;
import minepiece.noah.data.RaceEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Réduit de 30 % les dégâts reçus par les Lunariens. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(
			method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float minepiece$lunarianResistance(float amount, ServerLevel level, DamageSource source) {
		// Les dégâts qui ignorent tout (vide, /kill) ne sont pas réduits.
		if (amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		if ((Object) this instanceof ServerPlayer player) {
			PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
			if (profile != null && profile.race() == Race.LUNARIAN) {
				level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
						4, 0.25, 0.4, 0.25, 0.02);
				return amount * RaceEffects.LUNARIAN_DAMAGE_FACTOR;
			}
		}
		return amount;
	}
}
