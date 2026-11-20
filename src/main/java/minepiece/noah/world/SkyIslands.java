package minepiece.noah.world;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Où apparaissent les îles volantes. Calcul 100 % déterministe à partir de la graine du monde, identique quel que soit le
 * chunk qui le demande (la génération est multi-thread) :
 * 1. le monde est découpé en cellules de cellSize blocs ; chaque cellule tente une île avec la probabilité chance ;
 * 2. l'île est placée au hasard dans la cellule, alignée sur les chunks, sans jamais déborder sur une autre cellule ;
 * 3. elle n'est gardée QUE si tout son dessous est de l'eau : sous chaque colonne de chunk occupée (échantillonnée), le
 *    sol prévu par le générateur doit être au moins minWaterDepth blocs sous le niveau de la mer (océan). Ce test se
 *    fait avec le bruit du générateur, avant que les chunks existent, donc il donne toujours la même réponse.
 */
public final class SkyIslands {
	/** Île retenue : coin nord-ouest (x, z) du fichier et hauteur du bas du fichier. */
	public record Island(int x, int z, int baseY) {
		public BlockPos center(SkyIslandData data) {
			return new BlockPos(x + data.width() / 2, baseY + data.yMin + data.height / 2, z + data.length() / 2);
		}
	}

	private record CellKey(long seed, int x, int z) {}

	private static final Map<CellKey, Optional<Island>> CACHE = new ConcurrentHashMap<>();

	private SkyIslands() {}

	/** Île de la cellule (cellX, cellZ), ou null. */
	public static Island island(long seed, int cellX, int cellZ, ChunkGenerator gen, LevelHeightAccessor heights, RandomState noise) {
		if (CACHE.size() > 4096) {
			CACHE.clear();
		}
		return CACHE.computeIfAbsent(new CellKey(seed, cellX, cellZ),
				k -> Optional.ofNullable(compute(seed, cellX, cellZ, gen, heights, noise))).orElse(null);
	}

	/** Île qui couvre la colonne de blocs (x, z), ou null. */
	public static Island islandAt(long seed, int x, int z, ChunkGenerator gen, LevelHeightAccessor heights, RandomState noise) {
		int cell = SkyIslandConfig.get().cellSize;
		return island(seed, Math.floorDiv(x, cell), Math.floorDiv(z, cell), gen, heights, noise);
	}

	private static Island compute(long seed, int cellX, int cellZ, ChunkGenerator gen, LevelHeightAccessor heights, RandomState noise) {
		SkyIslandConfig cfg = SkyIslandConfig.get();
		SkyIslandData data = SkyIslandData.get();
		if (data == null) {
			return null;
		}
		RandomSource r = RandomSource.create(seed ^ (cellX * 341873128712L + cellZ * 132897987541L) ^ 0x5EB1EA_15L);
		if (r.nextDouble() >= cfg.chance) {
			return null;
		}
		int margin = 64;
		int freeX = cfg.cellSize - data.width() - 2 * margin;
		int freeZ = cfg.cellSize - data.length() - 2 * margin;
		if (freeX < 0 || freeZ < 0) {
			return null;
		}
		int x = cellX * cfg.cellSize + margin + r.nextInt(freeX / 16 + 1) * 16;
		int z = cellZ * cfg.cellSize + margin + r.nextInt(freeZ / 16 + 1) * 16;
		double cx = x + data.width() / 2.0, cz = z + data.length() / 2.0;
		if (cx * cx + cz * cz < (double) cfg.minDistanceFromSpawn * cfg.minDistanceFromSpawn) {
			return null;
		}
		// l'île doit tenir dans la hauteur du monde
		int baseY = Math.min(cfg.baseY, heights.getMaxY() - data.yMin - data.height + 1);
		// au-dessus de l'eau uniquement
		int sea = gen.getSeaLevel();
		int step = cfg.waterSampleStep;
		for (int tz = 0; tz < data.tilesZ; tz++) {
			for (int tx = 0; tx < data.tilesX; tx++) {
				boolean edge = tx == 0 || tz == 0 || tx == data.tilesX - 1 || tz == data.tilesZ - 1;
				if (!data.hasTile(tx, tz) || (!edge && (tx % step != 0 || tz % step != 0))) {
					continue;
				}
				int floor = gen.getBaseHeight(x + tx * 16 + 8, z + tz * 16 + 8, Heightmap.Types.OCEAN_FLOOR_WG, heights, noise);
				if (floor > sea - cfg.minWaterDepth) {
					return null;   // terre (ou eau trop peu profonde) sous l'île : pas d'île dans cette cellule
				}
			}
		}
		return new Island(x, z, baseY);
	}

	/** Île la plus proche (cellules en anneaux autour de la position), ou null. Pour la commande /skypiea. */
	public static Island locate(ServerLevel level, BlockPos from, int radiusCells) {
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState noise = level.getChunkSource().randomState();
		long seed = level.getSeed();
		int cell = SkyIslandConfig.get().cellSize;
		int cx = Math.floorDiv(from.getX(), cell), cz = Math.floorDiv(from.getZ(), cell);
		SkyIslandData data = SkyIslandData.get();
		if (data == null) {
			return null;
		}
		Island best = null;
		double bestD = Double.MAX_VALUE;
		for (int ring = 0; ring <= radiusCells; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					Island i = island(seed, cx + dx, cz + dz, gen, level, noise);
					if (i != null) {
						double d = i.center(data).distSqr(from);
						if (d < bestD) {
							bestD = d;
							best = i;
						}
					}
				}
			}
			if (best != null) {
				return best;   // l'anneau suivant est forcément plus loin (à une cellule près)
			}
		}
		return best;
	}
}
