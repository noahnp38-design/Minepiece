package minepiece.noah.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Élément de génération "minepiece:sky_island" : appelé une fois par chunk de l'Overworld (dernière étape de
 * décoration). Si une île volante couvre ce chunk, il pose UNIQUEMENT la colonne 16 x 16 de l'île qui correspond à ce
 * chunk ; les autres chunks posent leur propre morceau quand ils sont générés. L'île apparaît donc progressivement,
 * sans jamais écrire hors du chunk en cours ni bloquer le serveur.
 */
public record SkyIslandFeature() implements Feature {
	public static final MapCodec<SkyIslandFeature> CODEC = MapCodec.unit(new SkyIslandFeature());

	@Override
	public MapCodec<SkyIslandFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!SkyIslandConfig.get().enabled) {
			return false;
		}
		ServerLevel server = level.getLevel();
		if (server.dimension() != Level.OVERWORLD) {
			return false;
		}
		SkyIslandData data = SkyIslandData.get();
		if (data == null) {
			return false;
		}
		int x0 = (origin.getX() >> 4) << 4;
		int z0 = (origin.getZ() >> 4) << 4;
		SkyIslands.Island island = SkyIslands.islandAt(level.getSeed(), x0, z0, generator, level, server.getChunkSource().randomState());
		if (island == null) {
			return false;
		}
		int tx = (x0 - island.x()) >> 4;
		int tz = (z0 - island.z()) >> 4;
		byte[] col = data.tile(tx, tz);
		if (col == null) {
			return false;
		}
		int y0 = island.baseY() + data.yMin;
		int minY = level.getMinY(), maxY = level.getMaxY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int i = 0;
		for (int y = 0; y < data.height; y++) {
			int wy = y0 + y;
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++, i++) {
					byte b = col[i];
					if (b == 0 || wy < minY || wy > maxY) {
						continue;
					}
					BlockState state = data.state(b);
					if (state != null && !state.isAir()) {
						level.setBlock(pos.set(x0 + x, wy, z0 + z), state, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		return true;
	}
}
