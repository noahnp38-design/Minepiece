package minepiece.noah.effect;

import minepiece.noah.MinePiece;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Effets de potion personnalisés du mod. */
public final class ModEffects {
	/** Malédiction du Démon : appliquée (et retirée) uniquement par DemonCurse, jamais par une potion. */
	public static final Holder<MobEffect> DEMON_CURSE = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT, MinePiece.id("demon_curse"), new DemonCurseEffect());

	private ModEffects() {}

	/** Appelé au démarrage pour forcer l'enregistrement. */
	public static void init() {}

	private static final class DemonCurseEffect extends MobEffect {
		DemonCurseEffect() {
			super(MobEffectCategory.HARMFUL, 0x4B1A7A);
			// Chaque niveau de malédiction alourdit le corps : nage plus lente (le niveau monte avec le temps).
			addAttributeModifier(Attributes.MOVEMENT_SPEED, MinePiece.id("demon_curse_slow"), -0.12,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}
}
