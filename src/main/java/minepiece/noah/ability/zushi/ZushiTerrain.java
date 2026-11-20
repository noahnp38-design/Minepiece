package minepiece.noah.ability.zushi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Déformation RÉELLE du terrain (côté serveur, donc synchronisée avec tous les joueurs) :
 * - affaissement par colonnes (Écrasement gravitationnel) : toute la colonne descend d'un bloc, herbe et plantes
 *   comprises, comme un sol enfoncé par la pression ;
 * - cratère en cuvette (Météorite), creusé progressivement depuis le centre, fond tapissé de roche brisée et
 *   bourrelet de débris autour.
 * Jamais : bedrock / blocs trop durs (maxBlockHardness), coffres et autres blocs à inventaire, liquides.
 * Rien n'est lâché au sol (pas d'objets).
 */
public final class ZushiTerrain {
	/** Sans mise à jour des voisins (aucun bloc ne casse en chaîne) et sans objets lâchés. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
	private static final int SCAN_UP = 12;
	private static final int SCAN_DOWN = 10;

	private static final List<Crater> CRATERS = new ArrayList<>();

	private ZushiTerrain() {}

	public static boolean movable(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness <= ZushiConfig.get().maxBlockHardness;
	}

	/**
	 * Protections du monde : bordure du monde, protection du spawn, mode aventure (le lanceur ne peut pas construire)
	 * et Level.mayInteract (utilisé aussi par les mods de protection qui le surchargent).
	 */
	public static boolean allowed(ServerLevel level, BlockPos pos, ServerPlayer owner) {
		if (!level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		if (owner == null) {
			return true;
		}
		return owner.mayBuild() && level.mayInteract(owner, pos)
				&& !level.getServer().isUnderSpawnProtection(level, pos, owner);
	}

	private static boolean terrain(BlockState state) {
		return state.isSolid() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
	}

	/**
	 * Enfonce la colonne (x, z) d'un bloc. yRef = hauteur du sol au centre de l'attaque.
	 * @return la position du nouveau sol (pour les effets), ou null si la colonne n'a pas bougé
	 */
	static BlockPos sinkColumn(ServerLevel level, int x, int z, int yRef, ServerPlayer owner) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int minY = Math.max(level.getMinY() + 1, yRef - SCAN_DOWN);
		int top = Integer.MIN_VALUE;
		int ground = Integer.MIN_VALUE;
		for (int y = Math.min(level.getMaxY(), yRef + SCAN_UP); y >= minY; y--) {
			m.set(x, y, z);
			if (!level.isLoaded(m)) {
				return null;
			}
			BlockState s = level.getBlockState(m);
			if (s.isAir()) {
				continue;
			}
			if (top == Integer.MIN_VALUE) {
				top = y;
			}
			if (terrain(s)) {
				ground = y;
				break;
			}
		}
		if (ground == Integer.MIN_VALUE || ground - 1 < minY || !allowed(level, new BlockPos(x, ground, z), owner)) {
			return null;
		}
		// Tout ce qui va bouger (de ground - 1 à top) doit pouvoir l'être.
		for (int y = ground - 1; y <= top; y++) {
			m.set(x, y, z);
			if (!movable(level, m, level.getBlockState(m))) {
				return null;
			}
		}
		for (int y = ground; y <= top; y++) {
			m.set(x, y, z);
			BlockState s = level.getBlockState(m);
			BlockPos below = new BlockPos(x, y - 1, z);
			if (level.getBlockState(below) != s) {
				level.setBlock(below, s, FLAGS);
			}
		}
		level.setBlock(new BlockPos(x, top, z), Blocks.AIR.defaultBlockState(), FLAGS);
		return new BlockPos(x, ground - 1, z);
	}

	// ------------------------------------------------------------------ cratère de météorite

	private static final class Crater {
		final ServerLevel level;
		final List<BlockPos> clear;
		final List<BlockPos> floor;
		final List<BlockPos> rim;
		final Vec3 impact;
		final double radius;
		int index;
		int stage;
		boolean plain;

		Crater(ServerLevel level, Vec3 impact, double radius, List<BlockPos> clear, List<BlockPos> floor, List<BlockPos> rim) {
			this.level = level;
			this.impact = impact;
			this.radius = radius;
			this.clear = clear;
			this.floor = floor;
			this.rim = rim;
		}
	}

	/** Prépare un cratère (creusé ensuite sur plusieurs ticks, du centre vers le bord). */
	static void crater(ServerLevel level, Vec3 impact, double radius, ServerPlayer owner) {
		crater(level, impact, radius, owner, ZushiConfig.get().destroyTerrain);
	}

	/**
	 * Cratère avec un réglage de destruction propre à l'appelant (le Goro Goro no Mi a le sien). Les protections du monde,
	 * la dureté max, le budget de blocs par tick et le plafond de blocs restent ceux de la config Zushi (réglages communs).
	 */
	public static void crater(ServerLevel level, Vec3 impact, double radius, ServerPlayer owner, boolean destroy) {
		crater(level, impact, radius, owner, destroy, false);
	}

	/**
	 * plain = cratère de roche "naturelle" (Ishi Ishi no Mi) : fond et bourrelet en pierre, gravier, andésite, tuf, sans
	 * l'obsidienne pleureuse violette du Zushi.
	 */
	public static void crater(ServerLevel level, Vec3 impact, double radius, ServerPlayer owner, boolean destroy, boolean plain) {
		ZushiConfig cfg = ZushiConfig.get();
		if (!destroy || radius < 1.0) {
			return;
		}
		RandomSource rnd = level.getRandom();
		BlockPos c = BlockPos.containing(impact);
		int r = (int) Math.ceil(radius) + 1;
		double depth = radius * 0.6;
		List<BlockPos> clear = new ArrayList<>();
		List<BlockPos> floor = new ArrayList<>();
		List<BlockPos> rim = new ArrayList<>();
		int limit = cfg.maxCraterBlocks;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double h2 = (dx * dx + dz * dz) / (radius * radius);
				double edge = 1.0 + (rnd.nextDouble() - 0.5) * 0.18; // bord irrégulier
				if (h2 <= edge && allowed(level, c.offset(dx, 0, dz), owner)) {
					// cuvette : profondeur maximale au centre
					int bottom = (int) Math.floor(-depth * Math.sqrt(Math.max(0.0, 1.0 - h2 / edge)));
					int topClear = (int) Math.ceil(radius * 0.55 * Math.sqrt(Math.max(0.0, 1.0 - h2)));
					for (int dy = topClear; dy >= bottom; dy--) {
						BlockPos p = c.offset(dx, dy, dz);
						BlockState s = level.isLoaded(p) ? level.getBlockState(p) : null;
						if (s != null && !s.isAir() && movable(level, p, s) && clear.size() < limit) {
							clear.add(p);
						}
					}
					BlockPos f = c.offset(dx, bottom - 1, dz);
					if (level.isLoaded(f)) {
						floor.add(f);
					}
				} else if (h2 > edge && h2 <= 1.45 && rnd.nextFloat() < 0.45 && allowed(level, c.offset(dx, 0, dz), owner)) {
					rim.add(c.offset(dx, 0, dz));
				}
			}
		}
		if (clear.isEmpty() && floor.isEmpty()) {
			return;
		}
		clear.sort(Comparator.comparingDouble(p -> p.distSqr(c)));
		Crater added = new Crater(level, impact, radius, clear, floor, rim);
		added.plain = plain;
		CRATERS.add(added);
	}

	private static BlockState floorBlock(RandomSource rnd, boolean center) {
		if (center && rnd.nextFloat() < 0.18) {
			return Blocks.CRYING_OBSIDIAN.defaultBlockState(); // éclats violets lumineux au coeur de l'impact
		}
		return switch (rnd.nextInt(6)) {
			case 0 -> Blocks.COARSE_DIRT.defaultBlockState();
			case 1 -> Blocks.GRAVEL.defaultBlockState();
			case 2 -> Blocks.TUFF.defaultBlockState();
			case 3 -> Blocks.BLACKSTONE.defaultBlockState();
			case 4 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
			default -> Blocks.SMOOTH_BASALT.defaultBlockState();
		};
	}

	/** Roche "naturelle" (fond et bourrelet des cratères du Ishi Ishi no Mi). */
	private static BlockState stoneBlock(RandomSource rnd) {
		return switch (rnd.nextInt(6)) {
			case 0 -> Blocks.GRAVEL.defaultBlockState();
			case 1 -> Blocks.ANDESITE.defaultBlockState();
			case 2 -> Blocks.TUFF.defaultBlockState();
			case 3 -> Blocks.COBBLESTONE.defaultBlockState();
			case 4 -> Blocks.COARSE_DIRT.defaultBlockState();
			default -> Blocks.STONE.defaultBlockState();
		};
	}

	static void tick() {
		int budget = ZushiConfig.get().blocksPerTick;
		for (Iterator<Crater> it = CRATERS.iterator(); it.hasNext() && budget > 0;) {
			Crater cr = it.next();
			RandomSource rnd = cr.level.getRandom();
			if (cr.stage == 0) {
				while (cr.index < cr.clear.size() && budget-- > 0) {
					BlockPos p = cr.clear.get(cr.index++);
					BlockState s = cr.level.getBlockState(p);
					if (!s.isAir() && movable(cr.level, p, s)) {
						cr.level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
						if (rnd.nextFloat() < 0.04f) {
							ZushiFx.crumble(cr.level, p, s, 4);
						}
						// pendant le creusement, des blocs arrachés continuent de jaillir du cratère
						if (s.isSolid() && rnd.nextFloat() < 0.03f) {
							Vec3 out = new Vec3(p.getX() + 0.5 - cr.impact.x, 0, p.getZ() + 0.5 - cr.impact.z);
							out = out.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : out.normalize();
							double sp = 0.35 + rnd.nextDouble() * 0.5;
							ZushiFx.throwBlock(cr.level, s, new Vec3(p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5),
									new Vec3(out.x * sp, 0.7 + rnd.nextDouble() * 0.6, out.z * sp), 0.6 + rnd.nextDouble() * 0.35);
						}
					}
				}
				if (cr.index >= cr.clear.size()) {
					cr.stage = 1;
					cr.index = 0;
				}
			} else if (cr.stage == 1) {
				for (BlockPos p : cr.floor) {
					BlockState s = cr.level.getBlockState(p);
					if (s.isSolid() && movable(cr.level, p, s) && rnd.nextFloat() < 0.65F) {
						cr.level.setBlock(p, cr.plain ? stoneBlock(rnd) : floorBlock(rnd, false), FLAGS);
					}
				}
				cr.stage = 2;
			} else {
				for (BlockPos p : cr.rim) {
					// bourrelet : un bloc de débris posé sur le sol autour du cratère
					BlockPos.MutableBlockPos m = p.mutable().move(0, 4, 0);
					for (int i = 0; i < 9; i++, m.move(0, -1, 0)) {
						BlockState s = cr.level.getBlockState(m);
						if (!s.isAir() && s.isSolid()) {
							BlockPos above = m.above();
							if (cr.level.getBlockState(above).isAir()) {
								cr.level.setBlock(above, cr.plain ? stoneBlock(rnd) : floorBlock(rnd, false), FLAGS);
							}
							break;
						}
					}
				}
				if (!cr.plain) {
					markCore(cr.level, cr.impact, cr.radius);
				}
				it.remove();
			}
		}
	}

	/** Coeur du cratère (une fois creusé) : quelques blocs d'obsidienne pleureuse violette au fond. */
	private static void markCore(ServerLevel level, Vec3 impact, double radius) {
		RandomSource rnd = level.getRandom();
		BlockPos c = BlockPos.containing(impact);
		int r = Math.max(1, (int) (radius * 0.35));
		for (int i = 0; i < 4 + r * 2; i++) {
			int dx = rnd.nextInt(r * 2 + 1) - r;
			int dz = rnd.nextInt(r * 2 + 1) - r;
			BlockPos.MutableBlockPos m = c.mutable().move(dx, 2, dz);
			for (int k = 0; k < (int) (radius + 4); k++, m.move(0, -1, 0)) {
				BlockState s = level.getBlockState(m);
				if (!s.isAir()) {
					if (s.isSolid() && movable(level, m, s)) {
						level.setBlock(m.immutable(), floorBlock(rnd, true), FLAGS);
					}
					break;
				}
			}
		}
	}

	static boolean busy() {
		return !CRATERS.isEmpty();
	}

	static void cleanupAll() {
		CRATERS.clear();
	}
}
