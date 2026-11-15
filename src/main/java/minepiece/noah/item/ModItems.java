package minepiece.noah.item;

import java.util.function.Function;

import minepiece.noah.MinePiece;
import minepiece.noah.data.DevilFruit;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item HIE_HIE_NO_MI = register("hie_hie_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.HIE_HIE_NO_MI),
			fruitProperties());

	public static final Item MERA_MERA_NO_MI = register("mera_mera_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.MERA_MERA_NO_MI),
			fruitProperties());

	private ModItems() {}

	/** Propriétés d'un fruit : on le mange vraiment (animation de repas, 3 secondes à maintenir). */
	private static Item.Properties fruitProperties() {
		return new Item.Properties()
				.stacksTo(1)
				.rarity(Rarity.EPIC)
				.component(DataComponents.CONSUMABLE, Consumable.builder()
						.consumeSeconds(3.0F)
						.animation(ItemUseAnimation.EAT)
						.sound(SoundEvents.GENERIC_EAT)
						.hasConsumeParticles(true)
						.build());
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MinePiece.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	/** L'objet (fruit) qui correspond à un fruit du démon : sert à l'icône dans l'interface. */
	public static Item of(DevilFruit fruit) {
		return switch (fruit) {
			case HIE_HIE_NO_MI -> HIE_HIE_NO_MI;
			case MERA_MERA_NO_MI -> MERA_MERA_NO_MI;
		};
	}

	/** Appelé au démarrage : charge la classe (donc enregistre les items) et remplit l'onglet créatif. */
	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS)
				.register(entries -> {
					entries.accept(HIE_HIE_NO_MI);
					entries.accept(MERA_MERA_NO_MI);
				});
	}
}
