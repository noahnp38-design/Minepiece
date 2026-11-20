package minepiece.noah.item;

import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Onglet créatif "MinePiece" : contient tous les objets du mod (fruits du démon, Wado Ichimonji, Shisui et tout ce qui sera
 * enregistré plus tard avec ModItems.register()). Le nom de l'onglet est dans les fichiers de langue.
 */
public final class ModCreativeTabs {
	public static final ResourceKey<CreativeModeTab> MINEPIECE_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, MinePiece.id("minepiece"));

	public static final CreativeModeTab MINEPIECE = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(ModItems.MERA_MERA_NO_MI))
			.title(Component.translatable("creativeTab.minepiece"))
			.displayItems((params, output) -> ModItems.all().forEach(output::accept))
			.build();

	private ModCreativeTabs() {}

	/** Appelé au démarrage, après ModItems.init() : enregistre l'onglet. */
	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MINEPIECE_KEY, MINEPIECE);
	}
}
