package minepiece.noah.entity;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import minepiece.noah.MinePiece;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/** Enregistrement des créatures du mod (type, attributs, règles et biomes d'apparition). */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> MARINE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, MinePiece.id("marine"));

	/** Marine de base : 0,6 x 1,95 blocs (taille d'un joueur), catégorie CREATURE (apparition rare, pas de règle de lumière nocturne). */
	public static final EntityType<MarineEntity> MARINE = Registry.register(BuiltInRegistries.ENTITY_TYPE, MARINE_KEY,
			EntityType.Builder.of(MarineEntity::new, MobCategory.CREATURE)
					.sized(0.6F, 1.95F)
					.clientTrackingRange(8)
					.build(MARINE_KEY));

	private ModEntities() {}

	public static void init() {
		FabricDefaultAttributeRegistry.register(MARINE, MarineEntity.createAttributes());

		SpawnPlacements.register(MARINE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				MarineEntity::checkSpawnRules);

		// Apparition naturelle : plaines, prairies, forêts, plages et rivages (là où une patrouille de la Marine est crédible).
		// Poids faible (une vache = 8) mais groupes de 4 à 5 : des patrouilles rares, qui ne surchargent pas le monde.
		BiomeModifications.addSpawn(
				BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST,
						Biomes.BIRCH_FOREST, Biomes.BEACH, Biomes.STONY_SHORE, Biomes.RIVER),
				MobCategory.CREATURE, MARINE, 3, 4, 5);
	}
}
