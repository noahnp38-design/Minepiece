package minepiece.noah.world.village;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import minepiece.noah.MinePiece;
import minepiece.noah.data.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelData;

/**
 * Village de départ : à la création d'un NOUVEAU monde, on cherche (autour du point d'apparition vanilla) une côte
 * basse et assez plate au bord d'un vrai océan, on y construit un petit village de civils adapté au terrain
 * (voir {@link VillageBuilder}) et le point d'apparition du monde est déplacé sur sa place.
 * - Une seule fois par monde (marqueur persistant sur l'Overworld) ; un monde déjà joué (> 1 min) n'est jamais modifié.
 * - Au premier passage, chaque joueur est placé exactement sur la place (le point d'apparition vanilla est flou de
 *   quelques blocs et pourrait tomber sur un toit) ; idem à chaque réapparition sans lit / ancre.
 */
public final class SpawnVillage {
	/** Distance max (blocs) de recherche autour du point d'apparition vanilla, pas de la grille de recherche. */
	private static final int SEARCH_RADIUS = 1024;
	private static final int STEP = 16;

	private SpawnVillage() {}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(SpawnVillage::onStarted);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && newPlayer.getRespawnConfig() == null) {
				sendToPlaza(newPlayer);
			}
		});
	}

	// ------------------------------------------------------------------ création

	private static void onStarted(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getAttached(ModAttachments.VILLAGE_DONE) != null) {
			return;
		}
		level.setAttached(ModAttachments.VILLAGE_DONE, Boolean.TRUE);
		if (level.getGameTime() > 1200L) {
			return;   // monde existant : on n'y touche pas
		}
		long t0 = System.currentTimeMillis();
		try {
			Site site = find(level);
			BlockPos spawn = new VillageBuilder(level, site.x, site.z, site.dir, level.getRandom()).build();
			level.setAttached(ModAttachments.VILLAGE_SPAWN, spawn);
			server.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, spawn, site.dir == null ? 0.0F : site.dir.toYRot(), 0.0F));
			MinePiece.LOGGER.info("Village de départ construit en {} {} {} (océan : {}) en {} ms", spawn.getX(), spawn.getY(), spawn.getZ(),
					site.dir, System.currentTimeMillis() - t0);
		} catch (Exception e) {
			MinePiece.LOGGER.error("Impossible de construire le village de départ", e);
		}
	}

	private record Site(int x, int z, Direction dir) {}

	/** Recherche en spirale (carrés concentriques) du premier site valable ; à défaut, le point d'apparition vanilla. */
	private static Site find(ServerLevel level) {
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState rs = level.getChunkSource().randomState();
		int sea = level.getSeaLevel();
		BlockPos o = level.getRespawnData().pos();
		int ox = Math.floorDiv(o.getX(), 8) * 8;
		int oz = Math.floorDiv(o.getZ(), 8) * 8;
		Map<Long, Integer> cache = new HashMap<>();
		Heights h = (x, z) -> cache.computeIfAbsent(BlockPos.asLong(x, 0, z),
				k -> gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs) - 1);
		for (int r = 0; r * STEP <= SEARCH_RADIUS; r++) {
			for (int i = -r; i <= r; i++) {
				for (int j = -r; j <= r; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != r) {
						continue;
					}
					int cx = ox + i * STEP;
					int cz = oz + j * STEP;
					Direction d = check(level, h, sea, cx, cz);
					if (d != null) {
						return new Site(cx, cz, d);
					}
				}
			}
		}
		MinePiece.LOGGER.warn("Aucune côte trouvée près du point d'apparition : village construit sur place");
		return new Site(o.getX(), o.getZ(), null);
	}

	@FunctionalInterface
	private interface Heights {
		int at(int x, int z);
	}

	/**
	 * Site valable : terre ferme basse (niveau de la mer .. +10) et peu pentue sur ~33 x 33 blocs, et un VRAI océan
	 * (profond, biome océanique, qui continue au large) à 24..48 blocs dans une direction cardinale. Renvoie cette
	 * direction (la plus proche si plusieurs), ou null.
	 */
	private static Direction check(ServerLevel level, Heights h, int sea, int cx, int cz) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int dx = -16; dx <= 16; dx += 8) {
			for (int dz = -16; dz <= 16; dz += 8) {
				int g = h.at(cx + dx, cz + dz);
				if (g < sea || g > sea + 10) {
					return null;
				}
				min = Math.min(min, g);
				max = Math.max(max, g);
				if (max - min > 6) {
					return null;
				}
			}
		}
		for (int d = 24; d <= 48; d += 8) {
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int sx = dir.getStepX();
				int sz = dir.getStepZ();
				if (h.at(cx + sx * d, cz + sz * d) < sea - 3
						&& h.at(cx + sx * (d + 16), cz + sz * (d + 16)) < sea - 4
						&& h.at(cx + sx * (d + 40), cz + sz * (d + 40)) < sea - 5
						&& level.getUncachedNoiseBiome((cx + sx * (d + 16)) >> 2, sea >> 2, (cz + sz * (d + 16)) >> 2).is(BiomeTags.IS_OCEAN)) {
					return dir;
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ joueurs

	private static void onJoin(ServerPlayer p) {
		if (Boolean.TRUE.equals(p.getAttached(ModAttachments.VILLAGE_ARRIVED))) {
			return;
		}
		p.setAttached(ModAttachments.VILLAGE_ARRIVED, Boolean.TRUE);
		sendToPlaza(p);
	}

	private static void sendToPlaza(ServerPlayer p) {
		ServerLevel level = ((ServerLevel) p.level()).getServer().overworld();
		BlockPos spawn = level.getAttached(ModAttachments.VILLAGE_SPAWN);
		if (spawn == null) {
			return;
		}
		float yaw = level.getRespawnData().yaw();
		p.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), yaw, 0.0F, false);
	}
}
