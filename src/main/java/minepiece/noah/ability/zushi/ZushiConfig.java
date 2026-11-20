package minepiece.noah.ability.zushi;

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
 * Réglages du Zushi Zushi no Mi. Fichier : config/minepiece-zushi.json (créé au premier lancement, complété à chaque
 * mise à jour). Redémarre le jeu / le serveur après modification.
 */
public final class ZushiConfig {
	// ---- Environnement
	/** false = l'Écrasement et la Météorite ne modifient aucun bloc (effets visuels et dégâts conservés). */
	public boolean destroyTerrain = true;
	/** Les blocs plus durs que ça résistent (pierre 1.5, obsidienne 50). La bedrock n'est jamais touchée. */
	public float maxBlockHardness = 10.0F;
	/** Blocs modifiés au maximum par tick (le cratère se forme progressivement). */
	public int blocksPerTick = 600;
	/** Plafond de blocs pour un cratère de météorite (protège contre les lags). */
	public int maxCraterBlocks = 40000;

	// ---- Effets visuels
	/** Multiplicateur du nombre de particules envoyées par le serveur (0.3 = sobre, 1 = normal). */
	public double particleDensity = 1.0;
	/** Secousse de caméra des joueurs proches à l'impact de la météorite. */
	public boolean cameraShake = true;
	/** Nombre max de débris 3D en vol en même temps (modèles temporaires). */
	public int maxFlyingDebris = 40;
	/** Nombre max de vrais blocs du terrain projetés en même temps par la météorite (copies visuelles temporaires). */
	public int maxBlockDebris = 100;

	// ---- Dégâts
	public double damageMultiplier = 1.0;

	// ---- Attaque 1 : Plateforme gravitationnelle
	public int platformDurationTicks = 400;
	public double platformSpeed = 0.42;
	public double platformSprintSpeed = 0.68;
	public double platformVerticalSpeed = 0.32;
	/** Hauteur max au-dessus du sol. */
	public int platformMaxHeight = 40;

	// ---- Attaque 2 : Écrasement gravitationnel
	public double crushRadius = 18.0;
	/** Rayon autour du lanceur qui reste intact (le sol sous ses pieds ne bouge pas). */
	public double crushSafeRadius = 2.5;
	public int crushDurationTicks = 100;
	/** Profondeur max de l'affaissement (blocs). */
	public int crushDepth = 4;
	public double crushPulseDamage = 2.5;
	public double crushPulseDamageStep = 1.0;

	// ---- Attaque 3 : Météorite gravitationnelle (3 niveaux de charge)
	public int meteorLevel2Ticks = 30;
	public int meteorLevel3Ticks = 60;
	public int meteorMaxChargeTicks = 90;
	public double meteorRange = 80.0;
	/** Altitude (Y) d'où tombe la météorite : la couche de nuages (192 par défaut). Au moins 70 blocs au-dessus de la cible. */
	public double meteorCloudHeight = 200.0;
	public double[] meteorDamage = {28.0, 45.0, 70.0};
	public double[] meteorRadius = {10.0, 15.0, 22.0};
	public double[] meteorCraterRadius = {7.0, 10.0, 15.0};
	/** Échelle du modèle 3D (1 = environ 1,6 bloc de diamètre) : niveau 1 ~ 11 blocs, niveau 3 ~ 26 blocs. */
	public double[] meteorScale = {7.0, 11.0, 16.0};

	public int configVersion = 4;

	private static ZushiConfig instance = new ZushiConfig();

	public static ZushiConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("minepiece-zushi.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					ZushiConfig loaded = gson.fromJson(reader, ZushiConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Config Zushi Zushi illisible, valeurs par défaut utilisées.", e);
			instance = new ZushiConfig();
		}
		instance.migrate();
		instance.sanitize();
		try (Writer writer = Files.newBufferedWriter(path)) {
			gson.toJson(instance, writer);
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Impossible d'écrire la config Zushi Zushi.", e);
		}
	}

	/** Anciens fichiers : nouvelles valeurs par défaut, sauf si le joueur les avait modifiées. */
	private void migrate() {
		if (configVersion < 2) {
			if (java.util.Arrays.equals(meteorScale, new double[] {1.6, 2.8, 4.5})) {
				meteorScale = new double[] {5.0, 8.0, 12.0};
			}
			if (java.util.Arrays.equals(meteorRadius, new double[] {5.0, 8.0, 12.0})) {
				meteorRadius = new double[] {8.0, 12.0, 18.0};
			}
			if (java.util.Arrays.equals(meteorCraterRadius, new double[] {3.5, 5.5, 8.5})) {
				meteorCraterRadius = new double[] {6.0, 9.0, 13.0};
			}
			if (java.util.Arrays.equals(meteorDamage, new double[] {16.0, 28.0, 45.0})) {
				meteorDamage = new double[] {20.0, 34.0, 55.0};
			}
			if (maxCraterBlocks == 9000) {
				maxCraterBlocks = 40000;
			}
			configVersion = 2;
		}
		if (configVersion < 3) {
			// v3 : météorite et écrasement plus puissants (les valeurs modifiées à la main sont conservées)
			meteorScale = upgrade(meteorScale, new double[] {5.0, 8.0, 12.0}, new double[] {7.0, 11.0, 16.0});
			meteorRadius = upgrade(meteorRadius, new double[] {8.0, 12.0, 18.0}, new double[] {10.0, 15.0, 22.0});
			meteorCraterRadius = upgrade(meteorCraterRadius, new double[] {6.0, 9.0, 13.0}, new double[] {7.0, 10.0, 15.0});
			meteorDamage = upgrade(meteorDamage, new double[] {20.0, 34.0, 55.0}, new double[] {28.0, 45.0, 70.0});
			if (crushRadius == 10.0) crushRadius = 18.0;
			if (crushDurationTicks == 80) crushDurationTicks = 100;
			if (crushDepth == 3) crushDepth = 4;
			if (crushPulseDamage == 1.5) crushPulseDamage = 2.5;
			if (crushPulseDamageStep == 0.75) crushPulseDamageStep = 1.0;
			configVersion = 3;
		}
		if (configVersion < 4) {
			// v4 : plus de blocs projetés par la météorite, et place pour les morceaux qui se détachent en s'effritant
			if (maxBlockDebris == 40) maxBlockDebris = 100;
			if (maxFlyingDebris == 24) maxFlyingDebris = 40;
			configVersion = 4;
		}
	}

	private static double[] upgrade(double[] current, double[] oldDefault, double[] newDefault) {
		return current == null || java.util.Arrays.equals(current, oldDefault) ? newDefault : current;
	}

	private static double[] fix3(double[] values, double[] defaults, double min, double max) {
		double[] out = defaults.clone();
		if (values != null) {
			for (int i = 0; i < Math.min(3, values.length); i++) {
				out[i] = Math.max(min, Math.min(max, values[i]));
			}
		}
		return out;
	}

	private void sanitize() {
		blocksPerTick = Math.max(20, blocksPerTick);
		maxCraterBlocks = Math.max(0, maxCraterBlocks);
		particleDensity = Math.max(0.0, Math.min(3.0, particleDensity));
		maxFlyingDebris = Math.max(0, Math.min(80, maxFlyingDebris));
		maxBlockDebris = Math.max(0, Math.min(160, maxBlockDebris));
		damageMultiplier = Math.max(0.0, damageMultiplier);
		platformDurationTicks = Math.max(40, platformDurationTicks);
		platformSpeed = Math.max(0.05, Math.min(1.5, platformSpeed));
		platformSprintSpeed = Math.max(platformSpeed, Math.min(2.0, platformSprintSpeed));
		platformVerticalSpeed = Math.max(0.05, Math.min(1.0, platformVerticalSpeed));
		platformMaxHeight = Math.max(4, Math.min(200, platformMaxHeight));
		crushRadius = Math.max(4.0, Math.min(32.0, crushRadius));
		crushSafeRadius = Math.max(1.0, Math.min(crushRadius - 2.0, crushSafeRadius));
		crushDurationTicks = Math.max(40, Math.min(400, crushDurationTicks));
		crushDepth = Math.max(0, Math.min(8, crushDepth));
		crushPulseDamage = Math.max(0.0, crushPulseDamage);
		crushPulseDamageStep = Math.max(0.0, crushPulseDamageStep);
		meteorLevel2Ticks = Math.max(1, meteorLevel2Ticks);
		meteorLevel3Ticks = Math.max(meteorLevel2Ticks + 1, meteorLevel3Ticks);
		meteorMaxChargeTicks = Math.max(meteorLevel3Ticks, meteorMaxChargeTicks);
		meteorRange = Math.max(8.0, Math.min(160.0, meteorRange));
		meteorCloudHeight = Math.max(-64.0, Math.min(400.0, meteorCloudHeight));
		meteorDamage = fix3(meteorDamage, new double[] {28.0, 45.0, 70.0}, 0.0, 1000.0);
		meteorRadius = fix3(meteorRadius, new double[] {10.0, 15.0, 22.0}, 1.0, 40.0);
		meteorCraterRadius = fix3(meteorCraterRadius, new double[] {7.0, 10.0, 15.0}, 0.0, 24.0);
		meteorScale = fix3(meteorScale, new double[] {7.0, 11.0, 16.0}, 0.3, 24.0);
	}
}
