package minepiece.noah.world;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import minepiece.noah.MinePiece;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Données de l'île Skypiea V4 (forêt géante, 512 x 448 blocs, 224 de haut), converties depuis le .schem fourni.
 * Format compact MinePiece (minepiece/skyisland/skypiea_v4.bin, ~1,8 Mo) : palette de blocs + une tranche compressée
 * par colonne de chunk (16 x 16 x hauteur). Pendant la génération, chaque chunk ne décompresse QUE sa colonne : aucun
 * fichier de structure géant en mémoire.
 */
public final class SkyIslandData {
	private static final String RESOURCE = "/minepiece/skyisland/skypiea_v4.bin";
	private static volatile SkyIslandData instance;

	public final int tilesX;
	public final int tilesZ;
	/** Décalage vertical du premier niveau stocké (dans le fichier d'origine). */
	public final int yMin;
	public final int height;
	private final BlockState[] palette;
	private final byte[][] tiles;

	private SkyIslandData(int tilesX, int tilesZ, int yMin, int height, BlockState[] palette, byte[][] tiles) {
		this.tilesX = tilesX;
		this.tilesZ = tilesZ;
		this.yMin = yMin;
		this.height = height;
		this.palette = palette;
		this.tiles = tiles;
	}

	/** Données chargées (une seule fois) ; null si le fichier est absent ou abîmé. */
	public static SkyIslandData get() {
		SkyIslandData d = instance;
		if (d == null) {
			synchronized (SkyIslandData.class) {
				d = instance;
				if (d == null) {
					d = load();
					instance = d;
				}
			}
		}
		return d.palette.length == 0 ? null : d;
	}

	public int width() {
		return tilesX * 16;
	}

	public int length() {
		return tilesZ * 16;
	}

	public boolean hasTile(int tx, int tz) {
		return tx >= 0 && tz >= 0 && tx < tilesX && tz < tilesZ && tiles[tz * tilesX + tx].length > 0;
	}

	/** Colonne de chunk décompressée : index = (y * 16 + z) * 16 + x ; null si vide. */
	public byte[] tile(int tx, int tz) {
		if (!hasTile(tx, tz)) {
			return null;
		}
		byte[] out = new byte[16 * 16 * height];
		try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(tiles[tz * tilesX + tx]))) {
			int off = 0;
			while (off < out.length) {
				int n = in.read(out, off, out.length - off);
				if (n < 0) {
					break;
				}
				off += n;
			}
		} catch (IOException e) {
			MinePiece.LOGGER.warn("Île volante : colonne {} {} illisible.", tx, tz, e);
			return null;
		}
		return out;
	}

	public BlockState state(byte index) {
		int i = index & 0xFF;
		return i < palette.length ? palette[i] : null;
	}

	private static SkyIslandData load() {
		try (InputStream raw = SkyIslandData.class.getResourceAsStream(RESOURCE)) {
			if (raw == null) {
				throw new IOException("ressource absente : " + RESOURCE);
			}
			DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw));
			byte[] magic = new byte[4];
			in.readFully(magic);
			if (magic[0] != 'M' || magic[1] != 'P' || magic[2] != 'S' || magic[3] != 'K') {
				throw new IOException("format inconnu");
			}
			int version = in.readInt();
			if (version != 1) {
				throw new IOException("version " + version + " non gérée");
			}
			int tx = in.readInt();
			int tz = in.readInt();
			int yMin = in.readInt();
			int height = in.readInt();
			int n = in.readInt();
			BlockState[] palette = new BlockState[n];
			for (int i = 0; i < n; i++) {
				palette[i] = parse(in.readUTF());
			}
			byte[][] tiles = new byte[tx * tz][];
			for (int i = 0; i < tiles.length; i++) {
				int len = in.readInt();
				tiles[i] = new byte[len];
				in.readFully(tiles[i]);
			}
			MinePiece.LOGGER.info("Île volante Skypiea chargée : {} x {} colonnes de chunk, {} blocs de haut.", tx, tz, height);
			return new SkyIslandData(tx, tz, yMin, height, palette, tiles);
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.error("Île volante Skypiea : impossible de charger les données, génération désactivée.", e);
			return new SkyIslandData(0, 0, 0, 0, new BlockState[0], new byte[0][]);
		}
	}

	/** "minecraft:jungle_leaves[distance=7,persistent=true]" -> état de bloc (air si le bloc n'existe pas). */
	private static BlockState parse(String s) {
		String name = s;
		String props = null;
		int b = s.indexOf('[');
		if (b >= 0 && s.endsWith("]")) {
			name = s.substring(0, b);
			props = s.substring(b + 1, s.length() - 1);
		}
		Identifier id = Identifier.tryParse(name);
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			MinePiece.LOGGER.warn("Île volante : bloc inconnu {}, remplacé par de l'air.", s);
			return Blocks.AIR.defaultBlockState();
		}
		BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
		if (props != null && !props.isEmpty()) {
			for (String kv : props.split(",")) {
				String[] p = kv.split("=", 2);
				if (p.length != 2) {
					continue;
				}
				Property<?> prop = state.getBlock().getStateDefinition().getProperty(p[0].trim());
				if (prop != null) {
					state = with(state, prop, p[1].trim());
				}
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> prop, String value) {
		return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
	}
}
