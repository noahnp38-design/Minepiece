package minepiece.noah.world;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import minepiece.noah.MinePiece;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Réglages des îles volantes (config/minepiece-skyisland.json).
 * Le monde est découpé en grandes cellules carrées ; chaque cellule peut contenir au plus une île, placée au hasard dans
 * la cellule (graine du monde), et seulement si TOUT le dessous de l'île est de l'eau (océan).
 */
public final class SkyIslandConfig {
	/** Active la génération des îles volantes dans les nouveaux chunks. */
	public boolean enabled = true;
	/** Côté d'une cellule (blocs, multiple de 16, au moins 768). Plus grand = îles plus rares. */
	public int cellSize = 2304;
	/** Probabilité qu'une cellule tente d'accueillir une île (0..1) ; elle n'apparaît que si le dessous est de l'eau. */
	public double chance = 0.7;
	/** Hauteur du bas du fichier : avec 64, les nuages porteurs commencent à Y=95 et la cime des arbres monte à Y=318. */
	public int baseY = 64;
	/** Profondeur d'eau minimale sous chaque point testé (sol de l'océan au moins N blocs sous le niveau de la mer). */
	public int minWaterDepth = 4;
	/** Distance minimale entre le centre de l'île et le point (0, 0) du monde. */
	public int minDistanceFromSpawn = 1200;
	/** Un point de contrôle de l'eau toutes les N colonnes de chunk occupées par l'île (1 = toutes, plus lent). */
	public int waterSampleStep = 2;

	private static SkyIslandConfig instance = new SkyIslandConfig();

	public static SkyIslandConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("minepiece-skyisland.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					SkyIslandConfig loaded = gson.fromJson(reader, SkyIslandConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Config des îles volantes illisible, valeurs par défaut utilisées.", e);
			instance = new SkyIslandConfig();
		}
		instance.sanitize();
		try (Writer writer = Files.newBufferedWriter(path)) {
			gson.toJson(instance, writer);
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Impossible d'écrire la config des îles volantes.", e);
		}
	}

	private void sanitize() {
		cellSize = Math.max(768, Math.min(100_000, cellSize)) / 16 * 16;
		chance = Math.max(0.0, Math.min(1.0, chance));
		minWaterDepth = Math.max(1, Math.min(64, minWaterDepth));
		minDistanceFromSpawn = Math.max(0, minDistanceFromSpawn);
		waterSampleStep = Math.max(1, Math.min(8, waterSampleStep));
	}
}
