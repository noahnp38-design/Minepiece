package minepiece.noah.world;

import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Génération du monde : île volante Skypiea (forêt géante) au-dessus des océans.
 * - type d'élément "minepiece:sky_island" (code) ;
 * - data/minepiece/worldgen/feature/sky_island.json + placed_feature/sky_island.json (données) ;
 * - ajouté à tous les biomes de l'Overworld, à la dernière étape de décoration (TOP_LAYER_MODIFICATION) ; c'est
 *   SkyIslands qui décide, chunk par chunk, si une île est là (et seulement au-dessus de l'eau).
 * Seuls les NOUVEAUX chunks reçoivent l'île : il faut explorer des océans jamais générés.
 */
public final class ModWorldgen {
	public static final ResourceKey<PlacedFeature> SKY_ISLAND = ResourceKey.create(Registries.PLACED_FEATURE, MinePiece.id("sky_island"));

	private ModWorldgen() {}

	public static void init() {
		SkyIslandConfig.load();
		Registry.register(BuiltInRegistries.FEATURE_TYPE, MinePiece.id("sky_island"), SkyIslandFeature.CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION, SKY_ISLAND);
		minepiece.noah.world.village.SpawnVillage.init();
	}
}
