package minepiece.noah.block;

import java.util.function.Function;

import minepiece.noah.MinePiece;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Blocs du mod. Pour l'instant : le terrain gelé par l'Ice Age (Hie Hie no Mi).
 * Ces blocs ne donnent rien quand on les casse (ils remplacent temporairement le terrain, voir IceAge).
 */
public final class ModBlocks {
	private ModBlocks() {}

	public static final Block FROZEN_GRASS_BLOCK = register("frozen_grass_block", Block::new, frozen(MapColor.SNOW, SoundType.SNOW));
	public static final Block FROZEN_DIRT = register("frozen_dirt", Block::new, frozen(MapColor.ICE, SoundType.GLASS));
	public static final Block FROZEN_SAND = register("frozen_sand", Block::new, frozen(MapColor.SNOW, SoundType.SNOW));
	public static final Block FROZEN_STONE = register("frozen_stone", Block::new, frozen(MapColor.ICE, SoundType.GLASS).strength(1.5F));

	public static final Block ICE_CRYSTAL = register("ice_crystal", IceCrystalBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.ICE).sound(SoundType.GLASS)
					.noCollision().noOcclusion().instabreak().noLootTable()
					.pushReaction(PushReaction.POPPED).lightLevel(state -> 4));

	/** Sol gelé : un peu glissant (0.6 = normal, 0.98 = glace), se casse à la main, ne donne rien. */
	private static BlockBehaviour.Properties frozen(MapColor color, SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(0.6F).friction(0.8F).noLootTable();
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, MinePiece.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
	}

	/** Appelé au démarrage : charge la classe, donc enregistre les blocs. */
	public static void init() {}
}
