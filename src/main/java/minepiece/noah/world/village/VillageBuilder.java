package minepiece.noah.world.village;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Construit le village de départ autour de (cx, cz), la mer étant dans la direction {@code sea} (null = pas de côte).
 * Repère local du village : u vers la mer, v à sa droite.
 * Adaptation au terrain :
 * - chaque bâtiment est posé à la hauteur MÉDIANE du sol sous son emprise (essais décalés si l'emplacement est dans
 *   l'eau ou trop pentu, abandon sinon), avec des fondations en pierre jusqu'au sol là où le terrain descend ;
 * - autour de chaque bâtiment et de la place, le sol est raccordé en pente douce au terrain naturel (sur 4 blocs), avec
 *   le même matériau de surface (herbe, sable, neige...) ;
 * - les arbres qui gênent sont abattus (troncs entiers), les chemins suivent le relief et deviennent des passerelles
 *   au-dessus des ruisseaux ; le ponton part du rivage réel ;
 * - bois de sapin dans les biomes de taïga / neige, de chêne ailleurs.
 */
final class VillageBuilder {
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	private enum Kind { HOUSE, TAVERN, FARM }

	private static final class Plot {
		final Kind kind;
		final int w;
		final int d;
		int ax;
		int az;
		Direction facing;
		Rotation rot;
		int y0;
		int minX;
		int maxX;
		int minZ;
		int maxZ;
		int index;

		Plot(Kind kind, int w, int d) {
			this.kind = kind;
			this.w = w;
			this.d = d;
		}
	}

	private final ServerLevel level;
	private final int cx;
	private final int cz;
	private final Direction u;
	private final Direction v;
	private final boolean coast;
	private final RandomSource random;
	private final int sea;
	private final boolean spruce;
	private final List<Plot> plots = new ArrayList<>();
	private final List<BlockPos> shapes = new ArrayList<>();
	private final Set<Long> reserved = new HashSet<>();
	private int plazaY;

	VillageBuilder(ServerLevel level, int cx, int cz, Direction sea, RandomSource random) {
		this.level = level;
		this.cx = cx;
		this.cz = cz;
		this.coast = sea != null;
		this.u = sea == null ? Direction.EAST : sea;
		this.v = this.u.getClockWise();
		this.random = random;
		this.sea = level.getSeaLevel();
		var biome = level.getBiome(new BlockPos(cx, this.sea, cz));
		this.spruce = biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.SPAWNS_SNOW_FOXES);
	}

	/** Construit tout et renvoie le point d'apparition (pieds du joueur, sur la place). */
	BlockPos build() {
		loadChunks();
		plaza();
		Object[][] slots = {
				{Kind.TAVERN, -16, 0},
				{Kind.HOUSE, -8, -14}, {Kind.HOUSE, -8, 14},
				{Kind.HOUSE, 5, -14}, {Kind.HOUSE, 5, 14},
				{Kind.HOUSE, -24, -14}, {Kind.HOUSE, -24, 14},
				{Kind.FARM, -5, -27},
		};
		int index = 0;
		for (Object[] s : slots) {
			Kind k = (Kind) s[0];
			Plot p = k == Kind.TAVERN ? new Plot(k, 11, 8) : k == Kind.FARM ? new Plot(k, 9, 7) : new Plot(k, 7, 6);
			if (site(p, (Integer) s[1], (Integer) s[2])) {
				p.index = index++;
				plots.add(p);
			}
		}
		for (Plot p : plots) {
			terraform(p);
		}
		for (Plot p : plots) {
			switch (p.kind) {
				case HOUSE -> house(p);
				case TAVERN -> tavern(p);
				case FARM -> farm(p);
			}
		}
		well();
		for (Plot p : plots) {
			int[] door = local(p, p.w / 2, -1);
			path(at(5, 0, door[0], door[1]), door[0], door[1], true);
		}
		if (coast) {
			pier();
		}
		decorate();
		for (BlockPos p : shapes) {
			BlockState s = level.getBlockState(p);
			level.setBlock(p, Block.updateFromNeighbourShapes(s, level, p), FLAGS);
		}
		villagers();
		int[] sp = uv(0, 3);
		return new BlockPos(sp[0], plazaY + 1, sp[1]);
	}

	// ------------------------------------------------------------------ repères

	private int[] uv(int du, int dv) {
		return new int[] {cx + u.getStepX() * du + v.getStepX() * dv, cz + u.getStepZ() * du + v.getStepZ() * dv};
	}

	/** Point du bord de la place (rayon r) en direction de (x, z). */
	private int[] at(double r, int unused, int x, int z) {
		double dx = x - cx;
		double dz = z - cz;
		double l = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
		return new int[] {(int) Math.round(cx + dx / l * r), (int) Math.round(cz + dz / l * r)};
	}

	/** Colonne monde (x, z) d'un point local (lx, lz) d'un bâtiment (façade : lz = 0 côté place). */
	private static int[] local(Plot p, int lx, int lz) {
		BlockPos o = new BlockPos(lx - p.w / 2, 0, lz - p.d / 2).rotate(p.rot);
		return new int[] {p.ax + o.getX(), p.az + o.getZ()};
	}

	private void put(Plot p, int lx, int dy, int lz, BlockState s) {
		int[] c = local(p, lx, lz);
		set(new BlockPos(c[0], p.y0 + dy, c[1]), s.rotate(p.rot));
	}

	private void set(BlockPos pos, BlockState s) {
		level.setBlock(pos, s, FLAGS);
		Block b = s.getBlock();
		if (b instanceof FenceBlock || b instanceof IronBarsBlock || b instanceof WallBlock || b instanceof StairBlock) {
			shapes.add(pos);
		}
	}

	private static long key(int x, int z) {
		return BlockPos.asLong(x, 0, z);
	}

	private void loadChunks() {
		int r = 80;
		for (int x = (cx - r) >> 4; x <= (cx + r) >> 4; x++) {
			for (int z = (cz - r) >> 4; z <= (cz + r) >> 4; z++) {
				level.getChunk(x, z);
			}
		}
	}

	// ------------------------------------------------------------------ terrain

	/** Hauteur du sol réel (sans arbres, plantes, neige) ; à la surface de l'eau pour les colonnes immergées. */
	private int ground(int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
		while (y > level.getMinY()) {
			m.setY(y);
			BlockState s = level.getBlockState(m);
			if (s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE_BY_TREES)
					|| (s.canBeReplaced() && s.getFluidState().isEmpty()) || s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS)
					|| s.is(Blocks.SUGAR_CANE) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.BROWN_MUSHROOM_BLOCK)
					|| s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM)) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	private boolean water(int x, int z, int g) {
		return !level.getFluidState(new BlockPos(x, g, z)).isEmpty();
	}

	/** Matériau de surface à reprendre quand on remodèle une colonne. */
	private BlockState topOf(int x, int g, int z) {
		BlockState s = level.getBlockState(new BlockPos(x, g, z));
		if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)
				|| s.is(Blocks.COARSE_DIRT) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.GRAVEL) || s.is(Blocks.DIRT)) {
			return s;
		}
		return Blocks.GRASS_BLOCK.defaultBlockState();
	}

	private static BlockState fillOf(BlockState top) {
		if (top.is(Blocks.SAND)) {
			return Blocks.SAND.defaultBlockState();
		}
		if (top.is(Blocks.RED_SAND)) {
			return Blocks.RED_SAND.defaultBlockState();
		}
		if (top.is(Blocks.GRAVEL)) {
			return Blocks.GRAVEL.defaultBlockState();
		}
		return Blocks.DIRT.defaultBlockState();
	}

	/** Abat un arbre dont le tronc part de (x, y, z) (les feuilles restantes se décomposent seules). */
	private void fellAbove(int x, int y, int z) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
		for (int i = 0; i < 40; i++) {
			m.setY(y + i);
			BlockState s = level.getBlockState(m);
			if (s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) {
				level.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
			} else if (!s.isAir() || i > 3) {
				if (!s.is(BlockTags.LEAVES)) {
					break;
				}
			}
		}
	}

	/** Vide (air) de y0 à y1 inclus, troncs compris. */
	private void clear(int x, int z, int y0, int y1) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y0, z);
		for (int y = y0; y <= y1; y++) {
			m.setY(y);
			BlockState s = level.getBlockState(m);
			if (!s.isAir()) {
				if (s.is(BlockTags.LOGS) && y == y1) {
					fellAbove(x, y, z);
				} else {
					level.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
				}
			}
		}
		fellAbove(x, y1 + 1, z);
	}

	/** Remodèle une colonne pour que son sol soit à {@code target} (comblée ou arasée), surface naturelle conservée. */
	private void shape(int x, int z, int target, BlockState top, int clearHeight) {
		int g = ground(x, z);
		if (water(x, z, g)) {
			return;
		}
		BlockState fill = fillOf(top);
		for (int y = g + 1; y < target; y++) {
			level.setBlock(new BlockPos(x, y, z), fill, FLAGS);
		}
		level.setBlock(new BlockPos(x, target, z), top, FLAGS);
		clear(x, z, target + 1, Math.max(g, target) + clearHeight);
	}

	/** Raccord en pente douce entre une zone plate (hauteur y) et le terrain naturel, sur la couronne r = 1..4. */
	private void blend(int x, int z, int y, int r) {
		if (reserved.contains(key(x, z))) {
			return;
		}
		int g = ground(x, z);
		if (water(x, z, g) || Math.abs(g - y) <= 0) {
			return;
		}
		int target = y + (int) Math.round((g - y) * r / 5.0);
		shape(x, z, target, topOf(x, g, z), r <= 1 ? 6 : 3);
	}

	// ------------------------------------------------------------------ place

	private void plaza() {
		int[] hs = new int[121];
		int n = 0;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				if (dx * dx + dz * dz <= 30) {
					hs[n++] = ground(cx + dx, cz + dz);
				}
			}
		}
		Arrays.sort(hs, 0, n);
		plazaY = Math.max(sea, hs[n / 2]);
		for (int dx = -9; dx <= 9; dx++) {
			for (int dz = -9; dz <= 9; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				int x = cx + dx;
				int z = cz + dz;
				if (r <= 5.6) {
					reserved.add(key(x, z));
					int g = ground(x, z);
					BlockState fill = Blocks.DIRT.defaultBlockState();
					for (int y = Math.min(g, plazaY - 1) + 1; y < plazaY; y++) {
						level.setBlock(new BlockPos(x, y, z), fill, FLAGS);
					}
					float f = random.nextFloat();
					BlockState top = f < 0.5F ? Blocks.DIRT_PATH.defaultBlockState() : f < 0.7F ? Blocks.GRAVEL.defaultBlockState()
							: f < 0.85F ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
					if (r > 4.6 && random.nextFloat() < 0.5F) {
						top = Blocks.GRASS_BLOCK.defaultBlockState();
					}
					level.setBlock(new BlockPos(x, plazaY, z), top, FLAGS);
					clear(x, z, plazaY + 1, Math.max(g, plazaY) + 10);
				}
			}
		}
		for (int dx = -9; dx <= 9; dx++) {
			for (int dz = -9; dz <= 9; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > 5.6 && r <= 9.6) {
					blend(cx + dx, cz + dz, plazaY, (int) Math.ceil(r - 5.6));
				}
			}
		}
	}

	private void well() {
		int y = plazaY;
		BlockState cob = Blocks.COBBLESTONE.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos b = new BlockPos(cx + dx, y, cz + dz);
				level.setBlock(b.below(2), cob, FLAGS);
				boolean center = dx == 0 && dz == 0;
				BlockState ring = random.nextFloat() < 0.3F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : cob;
				level.setBlock(b.below(), center ? Blocks.WATER.defaultBlockState() : cob, FLAGS);
				level.setBlock(b, center ? Blocks.WATER.defaultBlockState() : ring, FLAGS);
				level.setBlock(b.above(), center ? Blocks.AIR.defaultBlockState() : ring, FLAGS);
				if (dx != 0 && dz != 0) {
					set(b.above(2), fence());
					set(b.above(3), fence());
				}
				level.setBlock(b.above(4), slab(SlabType.BOTTOM), FLAGS);
			}
		}
		set(new BlockPos(cx, y + 3, cz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		// cloche du village (côté terre)
		int[] b = uv(-4, 0);
		level.setBlock(new BlockPos(b[0], y, b[1]), Blocks.STONE_BRICKS.defaultBlockState(), FLAGS);
		level.setBlock(new BlockPos(b[0], y + 1, b[1]), Blocks.BELL.defaultBlockState()
				.trySetValue(BellBlock.FACING, v).trySetValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR), FLAGS);
	}

	// ------------------------------------------------------------------ emplacements

	/** Cherche un emplacement acceptable près de (du, dv) : sec, peu pentu, sans chevauchement. */
	private boolean site(Plot p, int du, int dv) {
		int[][] tries = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}, {-5, 0}, {0, 5}, {0, -5}};
		for (int[] t : tries) {
			int[] c = uv(du + t[0], dv + t[1]);
			p.ax = c[0];
			p.az = c[1];
			int dx = cx - p.ax;
			int dz = cz - p.az;
			p.facing = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
			p.rot = switch (p.facing) {
				case EAST -> Rotation.CLOCKWISE_90;
				case SOUTH -> Rotation.CLOCKWISE_180;
				case WEST -> Rotation.COUNTERCLOCKWISE_90;
				default -> Rotation.NONE;
			};
			p.minX = p.minZ = Integer.MAX_VALUE;
			p.maxX = p.maxZ = Integer.MIN_VALUE;
			int[] hs = new int[p.w * p.d];
			int n = 0;
			boolean ok = true;
			for (int lx = 0; lx < p.w && ok; lx++) {
				for (int lz = 0; lz < p.d; lz++) {
					int[] w = local(p, lx, lz);
					p.minX = Math.min(p.minX, w[0]);
					p.maxX = Math.max(p.maxX, w[0]);
					p.minZ = Math.min(p.minZ, w[1]);
					p.maxZ = Math.max(p.maxZ, w[1]);
					int g = ground(w[0], w[1]);
					if (water(w[0], w[1], g) || g < sea - 1) {
						ok = false;
						break;
					}
					hs[n++] = g;
				}
			}
			if (!ok) {
				continue;
			}
			Arrays.sort(hs, 0, n);
			if (hs[n - 1] - hs[0] > 6) {
				continue;
			}
			if (overlaps(p)) {
				continue;
			}
			p.y0 = Math.max(sea, hs[n / 2]);
			for (int x = p.minX - 1; x <= p.maxX + 1; x++) {
				for (int z = p.minZ - 1; z <= p.maxZ + 1; z++) {
					reserved.add(key(x, z));
				}
			}
			return true;
		}
		return false;
	}

	private boolean overlaps(Plot p) {
		// la place (rayon 7 + marge)
		int nx = Math.max(p.minX, Math.min(cx, p.maxX)) - cx;
		int nz = Math.max(p.minZ, Math.min(cz, p.maxZ)) - cz;
		if (nx * nx + nz * nz < 8 * 8) {
			return true;
		}
		for (Plot o : plots) {
			if (p.minX - 3 <= o.maxX && p.maxX + 3 >= o.minX && p.minZ - 3 <= o.maxZ && p.maxZ + 3 >= o.minZ) {
				return true;
			}
		}
		return false;
	}

	/** Fondations sous l'emprise, dégagement au-dessus, raccord en pente douce autour (4 blocs). */
	private void terraform(Plot p) {
		for (int x = p.minX; x <= p.maxX; x++) {
			for (int z = p.minZ; z <= p.maxZ; z++) {
				int g = ground(x, z);
				BlockState top = topOf(x, g, z);
				for (int y = g + 1; y < p.y0; y++) {
					BlockState f = p.kind == Kind.FARM ? fillOf(top)
							: random.nextFloat() < 0.25F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
					level.setBlock(new BlockPos(x, y, z), f, FLAGS);
				}
				level.setBlock(new BlockPos(x, p.y0, z), p.kind == Kind.FARM ? Blocks.DIRT.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), FLAGS);
				clear(x, z, p.y0 + 1, Math.max(g, p.y0) + 14);
			}
		}
		// couronne immédiate (r = 1) : réservée mais remodelée à la hauteur du bâtiment pour pouvoir en faire le tour
		for (int r = 1; r <= 4; r++) {
			for (int x = p.minX - r; x <= p.maxX + r; x++) {
				for (int z = p.minZ - r; z <= p.maxZ + r; z++) {
					int cheb = Math.max(Math.max(p.minX - x, x - p.maxX), Math.max(p.minZ - z, z - p.maxZ));
					if (cheb != r) {
						continue;
					}
					if (r == 1) {
						int g = ground(x, z);
						if (!water(x, z, g)) {
							shape(x, z, p.y0, topOf(x, g, z), 8);
						}
					} else {
						blend(x, z, p.y0, r - 1);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ matériaux

	private BlockState planks(Plot p) {
		if (p.kind == Kind.TAVERN) {
			return Blocks.SPRUCE_PLANKS.defaultBlockState();
		}
		return spruce ? Blocks.SPRUCE_PLANKS.defaultBlockState() : (p.index % 3 == 2 ? Blocks.BIRCH_PLANKS : Blocks.OAK_PLANKS).defaultBlockState();
	}

	private BlockState log(Direction.Axis axis) {
		return (spruce ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG).defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private BlockState stripped(Direction.Axis axis) {
		return (spruce ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_OAK_LOG).defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
	}

	private Block roof(Plot p) {
		Block[] roofs = spruce ? new Block[] {Blocks.SPRUCE_STAIRS, Blocks.DARK_OAK_STAIRS} : new Block[] {Blocks.SPRUCE_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.OAK_STAIRS};
		return p.kind == Kind.TAVERN ? Blocks.DARK_OAK_STAIRS : roofs[p.index % roofs.length];
	}

	private BlockState fence() {
		return (spruce ? Blocks.SPRUCE_FENCE : Blocks.OAK_FENCE).defaultBlockState();
	}

	private BlockState slab(SlabType type) {
		return (spruce ? Blocks.SPRUCE_SLAB : Blocks.OAK_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, type);
	}

	private static BlockState stairs(Block b, Direction facing) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing);
	}

	private static BlockState facing(Block b, Direction f) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, f);
	}

	// ------------------------------------------------------------------ bâtiments (repère local : façade au nord, lz = 0)

	/** Murs, fenêtres, porte, poutre et toit à deux pans (faîtage parallèle à la façade). */
	private void shell(Plot p, int wallH) {
		int w = p.w;
		int d = p.d;
		BlockState planks = planks(p);
		for (int lx = 0; lx < w; lx++) {
			for (int lz = 0; lz < d; lz++) {
				boolean edgeX = lx == 0 || lx == w - 1;
				boolean edgeZ = lz == 0 || lz == d - 1;
				if (!edgeX && !edgeZ) {
					put(p, lx, 0, lz, planks);
					continue;
				}
				for (int y = 1; y <= wallH; y++) {
					BlockState s = edgeX && edgeZ ? log(Direction.Axis.Y) : y == wallH ? stripped(edgeX ? Direction.Axis.Z : Direction.Axis.X) : planks;
					put(p, lx, y, lz, s);
				}
			}
		}
		// fenêtres
		BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
		for (int lz = 2; lz <= d - 3; lz += 2) {
			for (int y = 2; y <= 3; y++) {
				put(p, 0, y, lz, pane);
				put(p, w - 1, y, lz, pane);
			}
		}
		for (int lx = 1; lx < w - 1; lx++) {
			if (Math.abs(lx - w / 2) >= 2 && lx % 2 == 1) {
				put(p, lx, 2, 0, pane);
				put(p, lx, 2, d - 1, pane);
			}
		}
		put(p, w / 2, 2, d - 1, pane);
		put(p, w / 2, 3, d - 1, pane);
		// porte (ouvre vers l'intérieur), torches de part et d'autre
		BlockState door = (spruce || p.kind == Kind.TAVERN ? Blocks.SPRUCE_DOOR : Blocks.OAK_DOOR).defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
		put(p, w / 2, 1, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		put(p, w / 2, 2, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		put(p, w / 2, 0, -1, Blocks.COBBLESTONE.defaultBlockState());
		// poutre et toit
		for (int lz = 1; lz < d - 1; lz++) {
			put(p, w / 2, wallH, lz, stripped(Direction.Axis.Z));
		}
		Block roof = roof(p);
		for (int k = 0; ; k++) {
			int front = -1 + k;
			int back = d - k;
			if (front > back) {
				break;
			}
			int y = wallH + k;
			for (int lx = -1; lx <= w; lx++) {
				if (front == back) {
					put(p, lx, y, front, slab(SlabType.BOTTOM));
				} else {
					put(p, lx, y, front, stairs(roof, Direction.SOUTH));
					put(p, lx, y, back, stairs(roof, Direction.NORTH));
				}
			}
			if (k >= 1) {
				for (int lz = front + 1; lz <= back - 1; lz++) {
					put(p, 0, y, lz, planks);
					put(p, w - 1, y, lz, planks);
				}
			}
		}
		BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH);
		put(p, w / 2 - 1, 3, -1, torch);
		put(p, w / 2 + 1, 3, -1, torch);
	}

	private void bed(Plot p, int lx, int lzFoot, Block bed) {
		BlockState s = facing(bed, Direction.SOUTH);
		put(p, lx, 1, lzFoot, s.trySetValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		put(p, lx, 1, lzFoot + 1, s.trySetValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	private void chest(Plot p, int lx, int lz, ItemStack... items) {
		put(p, lx, 1, lz, facing(Blocks.CHEST, Direction.NORTH));
		int[] c = local(p, lx, lz);
		if (level.getBlockEntity(new BlockPos(c[0], p.y0 + 1, c[1])) instanceof Container box) {
			for (int i = 0; i < items.length; i++) {
				box.setItem(i * 2 % box.getContainerSize(), items[i]);
			}
		}
	}

	private void house(Plot p) {
		shell(p, 4);
		Block[] beds = {Blocks.BED.red(), Blocks.BED.pick(net.minecraft.world.item.DyeColor.BLUE), Blocks.BED.pick(net.minecraft.world.item.DyeColor.YELLOW),
				Blocks.BED.pick(net.minecraft.world.item.DyeColor.GREEN), Blocks.BED.white(), Blocks.BED.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE)};
		bed(p, 1, p.d - 3, beds[p.index % beds.length]);
		// métier du villageois de la maison (pêcheur en tête : on est au bord de l'eau)
		BlockState[] jobs = {
				Blocks.BARREL.defaultBlockState().trySetValue(BlockStateProperties.FACING, Direction.UP),
				facing(Blocks.SMOKER, Direction.NORTH),
				Blocks.COMPOSTER.defaultBlockState(),
				facing(Blocks.LOOM, Direction.NORTH),
				Blocks.CARTOGRAPHY_TABLE.defaultBlockState(),
				Blocks.FLETCHING_TABLE.defaultBlockState(),
		};
		put(p, p.w - 3, 1, p.d - 2, jobs[p.index % jobs.length]);
		chest(p, p.w - 2, p.d - 2, new ItemStack(Items.BREAD, 2 + random.nextInt(4)), new ItemStack(Items.APPLE, 1 + random.nextInt(3)));
		put(p, p.w - 2, 1, p.d - 3, Blocks.CRAFTING_TABLE.defaultBlockState());
		put(p, 1, 1, 1, (p.index % 2 == 0 ? Blocks.POTTED_POPPY : Blocks.POTTED_DANDELION).defaultBlockState());
		put(p, p.w / 2, 1, 2, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.BROWN).defaultBlockState());
		put(p, p.w / 2, 3, p.d / 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
	}

	/** Taverne : comptoir, tables, deux lits, et le coffre du voyageur (équipement de départ). */
	private void tavern(Plot p) {
		shell(p, 5);
		int w = p.w;
		int d = p.d;
		BlockState counter = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
		for (int lx = 7; lx <= w - 2; lx++) {
			put(p, lx, 1, d - 4, counter);
		}
		put(p, 7, 1, d - 2, facing(Blocks.SMOKER, Direction.NORTH));
		chest(p, 8, d - 2,
				new ItemStack(Items.BREAD, 12), new ItemStack(Items.COOKED_COD, 8), new ItemStack(Items.APPLE, 6),
				new ItemStack(Items.STONE_SWORD), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_AXE),
				new ItemStack(Items.TORCH, 24), new ItemStack(Items.FISHING_ROD), new ItemStack(Items.OAK_BOAT));
		put(p, 9, 1, d - 2, Blocks.BARREL.defaultBlockState().trySetValue(BlockStateProperties.FACING, Direction.UP));
		put(p, 6, 1, d - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		put(p, 9, 2, d - 2, Blocks.LANTERN.defaultBlockState());
		// tables et chaises
		for (int lz : new int[] {2, 4}) {
			put(p, 3, 1, lz, fence());
			put(p, 3, 2, lz, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
			put(p, 2, 1, lz, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
			put(p, 4, 1, lz, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
		}
		bed(p, 1, d - 3, Blocks.BED.red());
		bed(p, 3, d - 3, Blocks.BED.red());
		for (int lz = 1; lz <= 3; lz++) {
			put(p, w / 2, 1, lz, Blocks.CARPET.red().defaultBlockState());
		}
		put(p, w / 2, 4, 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		put(p, w / 2, 4, d - 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		put(p, 1, 1, 1, facing(Blocks.LECTERN, Direction.EAST));
	}

	/** Champ : bordure de rondins, terre labourée hydratée par une rigole centrale, blé / carottes / patates. */
	private void farm(Plot p) {
		Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES};
		for (int lx = 0; lx < p.w; lx++) {
			for (int lz = 0; lz < p.d; lz++) {
				boolean edge = lx == 0 || lz == 0 || lx == p.w - 1 || lz == p.d - 1;
				if (edge) {
					put(p, lx, 0, lz, log(lx == 0 || lx == p.w - 1 ? Direction.Axis.Z : Direction.Axis.X));
				} else if (lz == p.d / 2) {
					put(p, lx, 0, lz, Blocks.WATER.defaultBlockState());
				} else {
					put(p, lx, 0, lz, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
					CropBlock crop = (CropBlock) crops[(lx - 1) * crops.length / (p.w - 2)];
					put(p, lx, 1, lz, crop.getStateForAge(random.nextInt(crop.getMaxAge() + 1)));
				}
			}
		}
		put(p, 0, 1, 0, Blocks.COMPOSTER.defaultBlockState());
		put(p, p.w - 1, 1, 0, Blocks.HAY_BLOCK.defaultBlockState());
	}

	// ------------------------------------------------------------------ chemins, ponton, décor

	/** Chemin de 2 de large qui suit le relief (passerelle au-dessus de l'eau), lampadaires réguliers. */
	private void path(int[] from, int tx, int tz, boolean lamps) {
		double dx = tx - from[0];
		double dz = tz - from[1];
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0) {
			return;
		}
		int px = Math.abs(dx) > Math.abs(dz) ? 0 : 1;
		int pz = 1 - px;
		int steps = (int) Math.ceil(len * 2);
		int lastLamp = -1;
		for (int i = 0; i <= steps; i++) {
			int x = (int) Math.round(from[0] + dx * i / steps);
			int z = (int) Math.round(from[1] + dz * i / steps);
			stamp(x, z);
			stamp(x + px, z + pz);
			double dist = len * i / steps;
			if (lamps && dist > 3 && dist < len - 3 && (int) (dist / 9) != lastLamp && (int) dist % 9 == 0) {
				lastLamp = (int) (dist / 9);
				lamp(x - px * 2, z - pz * 2);
			}
		}
	}

	private void stamp(int x, int z) {
		if (reserved.contains(key(x, z)) && !inPlotMargin(x, z)) {
			return;
		}
		int g = ground(x, z);
		BlockPos top = new BlockPos(x, g, z);
		if (water(x, z, g)) {
			level.setBlock(top, Blocks.SPRUCE_PLANKS.defaultBlockState(), FLAGS);
		} else {
			BlockState s = level.getBlockState(top);
			if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.MYCELIUM)
					|| s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.STONE)
					|| s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.MUD)) {
				level.setBlock(top, Blocks.DIRT_PATH.defaultBlockState(), FLAGS);
			}
		}
		clear(x, z, g + 1, g + 2);
	}

	/** Les chemins peuvent passer dans la couronne d'un bâtiment, jamais dans ses murs. */
	private boolean inPlotMargin(int x, int z) {
		for (Plot p : plots) {
			if (x >= p.minX && x <= p.maxX && z >= p.minZ && z <= p.maxZ) {
				return false;
			}
		}
		double r2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
		return r2 > 4.5 * 4.5;
	}

	private void lamp(int x, int z) {
		if (reserved.contains(key(x, z))) {
			return;
		}
		int g = ground(x, z);
		if (water(x, z, g)) {
			return;
		}
		clear(x, z, g + 1, g + 4);
		set(new BlockPos(x, g + 1, z), fence());
		set(new BlockPos(x, g + 2, z), fence());
		level.setBlock(new BlockPos(x, g + 3, z), Blocks.LANTERN.defaultBlockState(), FLAGS);
	}

	/** Ponton : part du rivage réel dans la direction de la mer, 3 de large, pieux jusqu'au fond, lanternes au bout. */
	private void pier() {
		int shore = -1;
		for (int k = 6; k <= 90; k++) {
			int[] c = uv(k, 0);
			int g = ground(c[0], c[1]);
			if (water(c[0], c[1], g)) {
				shore = k;
				break;
			}
		}
		if (shore < 0) {
			return;
		}
		int[] edge = uv(5, 0);
		int[] land = uv(shore - 1, 0);
		path(edge, land[0], land[1], true);
		int[] s = uv(shore, 0);
		int deck = ground(s[0], s[1]) + 1;
		int length = 14;
		for (int k = shore - 1; k <= shore + length; k++) {
			for (int dv = -1; dv <= 1; dv++) {
				int[] c = uv(k, dv);
				int g = ground(c[0], c[1]);
				if (!water(c[0], c[1], g) && g >= deck) {
					continue;
				}
				level.setBlock(new BlockPos(c[0], deck, c[1]), Blocks.SPRUCE_PLANKS.defaultBlockState(), FLAGS);
				clear(c[0], c[1], deck + 1, deck + 3);
				boolean post = dv != 0 && ((k - shore) % 4 == 0 || k == shore + length);
				if (post) {
					for (int y = deck - 1; y > level.getMinY(); y--) {
						BlockPos b = new BlockPos(c[0], y, c[1]);
						BlockState at = level.getBlockState(b);
						if (!at.isAir() && at.getFluidState().isEmpty()) {
							break;
						}
						level.setBlock(b, log(Direction.Axis.Y), FLAGS);
					}
					set(new BlockPos(c[0], deck + 1, c[1]), fence());
					if (k == shore + length) {
						level.setBlock(new BlockPos(c[0], deck + 2, c[1]), Blocks.LANTERN.defaultBlockState(), FLAGS);
					}
				}
			}
		}
		int[] end = uv(shore + length - 1, 0);
		level.setBlock(new BlockPos(end[0], deck + 1, end[1]), Blocks.BARREL.defaultBlockState().trySetValue(BlockStateProperties.FACING, Direction.UP), FLAGS);
	}

	/** Quelques fleurs et bottes de foin autour des bâtiments (à 2-3 blocs des murs, sur l'herbe). */
	private void decorate() {
		for (Plot p : plots) {
			for (int i = 0; i < 10; i++) {
				int off = 2 + random.nextInt(2);
				int x;
				int z;
				switch (random.nextInt(4)) {
					case 0 -> { x = p.minX - off; z = p.minZ + random.nextInt(p.maxZ - p.minZ + 1); }
					case 1 -> { x = p.maxX + off; z = p.minZ + random.nextInt(p.maxZ - p.minZ + 1); }
					case 2 -> { x = p.minX + random.nextInt(p.maxX - p.minX + 1); z = p.minZ - off; }
					default -> { x = p.minX + random.nextInt(p.maxX - p.minX + 1); z = p.maxZ + off; }
				}
				int g = ground(x, z);
				BlockPos top = new BlockPos(x, g, z);
				if (!level.getBlockState(top).is(Blocks.GRASS_BLOCK) || !level.getBlockState(top.above()).isAir()) {
					continue;
				}
				float f = random.nextFloat();
				BlockState s = f < 0.3F ? Blocks.POPPY.defaultBlockState() : f < 0.6F ? Blocks.DANDELION.defaultBlockState()
						: f < 0.85F ? Blocks.AZURE_BLUET.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState();
				level.setBlock(top.above(), s, FLAGS);
			}
		}
	}

	/** Quelques civils sur la place (ils trouveront seuls lits et métiers). */
	private void villagers() {
		for (int i = 0; i < 5; i++) {
			double a = i * Math.PI * 2 / 5 + 0.3;
			double x = cx + 0.5 + Math.cos(a) * 3.5;
			double z = cz + 0.5 + Math.sin(a) * 3.5;
			Villager vil = EntityTypes.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
			if (vil == null) {
				continue;
			}
			vil.snapTo(x, plazaY + 1, z, random.nextFloat() * 360.0F, 0.0F);
			vil.finalizeSpawn(level, level.getCurrentDifficultyAt(vil.blockPosition()), EntitySpawnReason.STRUCTURE, null);
			vil.setPersistenceRequired();
			level.addFreshEntity(vil);
		}
	}
}
