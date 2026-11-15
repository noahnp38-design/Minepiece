package minepiece.noah.ability;

import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Invulnérabilité des fruits Logia : les attaques physiques (coups, flèches...) ne font aucun dégât. */
public final class LogiaProtection {
	private LogiaProtection() {}

	public static void init() {
		// Renvoie false pour annuler les dégâts.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !blocks(entity, source));
	}

	/** Vrai si ce joueur Logia ignore ces dégâts. Point d'entrée pour le futur Haki (qui pourra les contourner). */
	public static boolean blocks(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player)) {
			return false;
		}
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
		if (fruit == null || !fruit.isLogia()) {
			return false;
		}
		// Le vide, /kill, etc. passent toujours.
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof LivingEntity;
	}
}
