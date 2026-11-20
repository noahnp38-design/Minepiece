package minepiece.noah.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import minepiece.noah.MinePiece;
import minepiece.noah.data.DevilFruit;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	/**
	 * Tous les objets du mod, dans l'ordre d'enregistrement. Rempli automatiquement par register() : un nouvel objet
	 * apparaît tout seul dans l'onglet créatif MinePiece (voir ModCreativeTabs), sans rien ajouter d'autre.
	 * Doit rester déclaré avant le premier objet.
	 */
	private static final List<Item> ALL = new ArrayList<>();

	public static final Item HIE_HIE_NO_MI = register("hie_hie_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.HIE_HIE_NO_MI),
			tiered("hie_hie_no_mi", Rarity.EPIC, 0xFFAA00));

	public static final Item MERA_MERA_NO_MI = register("mera_mera_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.MERA_MERA_NO_MI),
			tiered("mera_mera_no_mi", Rarity.EPIC, 0xFFAA00));

	/** Suke Suke no Mi : fruit commun (Paramécia). Touches Z / X / C. */
	public static final Item SUKE_SUKE_NO_MI = register("suke_suke_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.SUKE_SUKE_NO_MI),
			tiered("suke_suke_no_mi", Rarity.RARE, 0x5B8CFF));

	/** Bomu Bomu no Mi : fruit Paramécia de l'Explosion. Touches R / G / V. */
	public static final Item BOMU_BOMU_NO_MI = register("bomu_bomu_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.BOMU_BOMU_NO_MI),
			tiered("bomu_bomu_no_mi", Rarity.RARE, 0x5B8CFF));

	/** Supa Supa no Mi : fruit Paramécia Tailladant (Mr. 1). Touches Z / X / C. */
	public static final Item SUPA_SUPA_NO_MI = register("supa_supa_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.SUPA_SUPA_NO_MI),
			fruitProperties());

	/** Ushi Ushi no Mi, modèle Girafe : fruit Zoan de Kaku. Touches Z / X / C pour les attaques, V / B pour les formes. */
	public static final Item USHI_USHI_NO_MI = register("ushi_ushi_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.USHI_USHI_NO_MI),
			tiered("ushi_ushi_no_mi", Rarity.RARE, 0x5B8CFF));

	/** Zushi Zushi no Mi : fruit Paramécia de la Gravité (Fujitora), légendaire. Touches Z / X / C. */
	public static final Item ZUSHI_ZUSHI_NO_MI = register("zushi_zushi_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.ZUSHI_ZUSHI_NO_MI),
			tiered("zushi_zushi_no_mi", Rarity.EPIC, 0xFFAA00));

	/** Goro Goro no Mi : fruit Logia de la Foudre (Ener), légendaire. */
	public static final Item GORO_GORO_NO_MI = register("goro_goro_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.GORO_GORO_NO_MI),
			tiered("goro_goro_no_mi", Rarity.EPIC, 0xFFAA00));

	/** Ishi Ishi no Mi : fruit Paramécia de la Pierre (Pica), légendaire. */
	public static final Item ISHI_ISHI_NO_MI = register("ishi_ishi_no_mi",
			props -> new DevilFruitItem(props, DevilFruit.ISHI_ISHI_NO_MI),
			tiered("ishi_ishi_no_mi", Rarity.EPIC, 0xFFAA00));

	/** Coffre à Fruits (objet à tenir en main) et sa clé : voir minepiece.noah.chest. */
	public static final Item FRUIT_CHEST = register("fruit_chest",
			minepiece.noah.chest.FruitChestItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

	public static final Item FRUIT_KEY = register("fruit_key", Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

	/** Blocs gelés de l'Ice Age (disponibles en créatif pour la décoration). */
	public static final Item FROZEN_GRASS_BLOCK = blockItem("frozen_grass_block", minepiece.noah.block.ModBlocks.FROZEN_GRASS_BLOCK);
	public static final Item FROZEN_DIRT = blockItem("frozen_dirt", minepiece.noah.block.ModBlocks.FROZEN_DIRT);
	public static final Item FROZEN_SAND = blockItem("frozen_sand", minepiece.noah.block.ModBlocks.FROZEN_SAND);
	public static final Item FROZEN_STONE = blockItem("frozen_stone", minepiece.noah.block.ModBlocks.FROZEN_STONE);
	public static final Item ICE_CRYSTAL = blockItem("ice_crystal", minepiece.noah.block.ModBlocks.ICE_CRYSTAL);

	/** Log Pose : boussole de Grand Line (aiguille mobile, voir LogPoseItem). */
	public static final Item LOG_POSE = register("log_pose", LogPoseItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));

	/** Oeuf d'apparition du Marine de base. */
	public static final Item MARINE_SPAWN_EGG = register("marine_spawn_egg",
			net.minecraft.world.item.SpawnEggItem::new,
			new Item.Properties().spawnEgg(minepiece.noah.entity.ModEntities.MARINE));

	/** Sabre de la Marine : sabre d'abordage (dégâts et vitesse de l'épée en fer). Équipe les Marines. */
	public static final Item MARINE_SABER = register("marine_saber", Item::new,
			new Item.Properties().sword(ToolMaterial.IRON, 3.0F, -2.4F));

	/** Matériau de la Wado Ichimonji : 2500 de durabilité, enchantable comme le diamant (15). */
	private static final ToolMaterial WADO_MATERIAL = new ToolMaterial(
			BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 2500, 8.0F, 0.0F, 15, ItemTags.DIAMOND_TOOL_MATERIALS);

	/**
	 * Wado Ichimonji, katana légendaire de Zoro (rareté épique) : 12 de dégâts (1 de base + 11), vitesse d'attaque 1,8
	 * (4 - 2,2). Modèle 3D : models/item/wado_ichimonji.json (Blockbench, voir tools/make_wado.py). Pas d'attaque spéciale.
	 */
	public static final Item WADO_ICHIMONJI = register("wado_ichimonji", Item::new,
			new Item.Properties()
					.sword(WADO_MATERIAL, 11.0F, -2.2F)
					.rarity(Rarity.EPIC)
					.component(DataComponents.LORE, new ItemLore(List.of(
							Component.literal("§7Katana légendaire de Roronoa Zoro"),
							Component.literal("§7Lame blanche, garde dorée")))));

	/** Matériau de Shisui : 2500 de durabilité, enchantable comme le diamant (15). */
	private static final ToolMaterial SHISUI_MATERIAL = new ToolMaterial(
			BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 2500, 8.0F, 0.0F, 15, ItemTags.DIAMOND_TOOL_MATERIALS);

	/**
	 * Shisui, le katana noir de Roronoa Zoro : 10 de dégâts (1 de base + 9) et vitesse d'attaque 1,6 (4 - 2,4).
	 * Épée normale (pas encore de capacité spéciale). Enchantable comme une épée : elle est dans le tag minecraft:swords
	 * (data/minecraft/tags/item/swords.json). Modèle 3D (fait sous Blockbench) : models/item/shisui.json.
	 */
	public static final Item SHISUI = register("shisui", Item::new,
			new Item.Properties()
					.sword(SHISUI_MATERIAL, 9.0F, -2.4F)
					.rarity(Rarity.EPIC)
					.component(DataComponents.LORE, new ItemLore(List.of(
							Component.literal("§7Katana noir de Roronoa Zoro"),
							Component.literal("§8L'un des 21 Ô Wazamono")))));

	/** Matériau du Sandai Kitetsu : 2500 de durabilité, enchantable comme le diamant (15). */
	private static final ToolMaterial KITETSU_MATERIAL = new ToolMaterial(
			BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 2500, 8.0F, 0.0F, 15, ItemTags.DIAMOND_TOOL_MATERIALS);

	/**
	 * Sandai Kitetsu, le katana maudit de Roronoa Zoro (rareté épique) : 11 de dégâts (1 de base + 10), vitesse
	 * d'attaque 1,8 (4 - 2,2). Modèle 3D converti depuis le Blockbench fourni (maillages -> tools/make_kitetsu.py),
	 * même prise en main que la Wado Ichimonji ; recouvrement du Haki de l'Armement et des Rois comme les autres sabres.
	 */
	public static final Item SANDAI_KITETSU = register("sandai_kitetsu", Item::new,
			new Item.Properties()
					.sword(KITETSU_MATERIAL, 10.0F, -2.2F)
					.rarity(Rarity.EPIC)
					.component(DataComponents.LORE, new ItemLore(List.of(
							Component.literal("§7Katana maudit de Roronoa Zoro"),
							Component.literal("§8L'un des 50 Ryô Wazamono")))));

	private ModItems() {}

	/** Propriétés d'un fruit : on le mange vraiment (animation de repas, 3 secondes à maintenir). */
	/** Fruit "rare" (nom bleu) ou "légendaire" (nom doré) : couleur du nom imposée, visible dans l'inventaire. */
	private static Item.Properties tiered(String name, Rarity rarity, int rgb) {
		return fruitProperties().rarity(rarity).component(DataComponents.ITEM_NAME,
				net.minecraft.network.chat.Component.translatable("item." + MinePiece.MOD_ID + "." + name)
						.withStyle(st -> st.withColor(rgb)));
	}

	/** Palier d'affichage : "legendary" (Mera, Hie, Zushi), "rare" (Suke, Bomu, Ushi), sinon null (rareté normale). */
	public static String tierOf(DevilFruit fruit) {
		return switch (fruit) {
			case MERA_MERA_NO_MI, HIE_HIE_NO_MI, ZUSHI_ZUSHI_NO_MI, GORO_GORO_NO_MI, ISHI_ISHI_NO_MI -> "LEGENDARY";
			case SUKE_SUKE_NO_MI, BOMU_BOMU_NO_MI, USHI_USHI_NO_MI -> "RARE";
			default -> null;
		};
	}

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

	private static Item blockItem(String name, net.minecraft.world.level.block.Block block) {
		return register(name, props -> new net.minecraft.world.item.BlockItem(block, props),
				new Item.Properties().useBlockDescriptionPrefix());
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MinePiece.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ALL.add(item);
		return item;
	}

	/** L'objet (fruit) qui correspond à un fruit du démon : sert à l'icône dans l'interface. */
	public static Item of(DevilFruit fruit) {
		return switch (fruit) {
			case HIE_HIE_NO_MI -> HIE_HIE_NO_MI;
			case MERA_MERA_NO_MI -> MERA_MERA_NO_MI;
			case SUKE_SUKE_NO_MI -> SUKE_SUKE_NO_MI;
			case BOMU_BOMU_NO_MI -> BOMU_BOMU_NO_MI;
			case SUPA_SUPA_NO_MI -> SUPA_SUPA_NO_MI;
			case USHI_USHI_NO_MI -> USHI_USHI_NO_MI;
			case ZUSHI_ZUSHI_NO_MI -> ZUSHI_ZUSHI_NO_MI;
			case GORO_GORO_NO_MI -> GORO_GORO_NO_MI;
			case ISHI_ISHI_NO_MI -> ISHI_ISHI_NO_MI;
		};
	}

	/** Tous les objets du mod (pour l'onglet créatif MinePiece). */
	public static List<Item> all() {
		return Collections.unmodifiableList(ALL);
	}

	/** Appelé au démarrage : charge la classe, donc enregistre les items. L'onglet créatif est dans ModCreativeTabs. */
	public static void init() {}
}
