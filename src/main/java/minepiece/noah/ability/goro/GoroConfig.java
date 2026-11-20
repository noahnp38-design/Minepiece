package minepiece.noah.ability.goro;

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
 * Réglages du Goro Goro no Mi : config/minepiece-goro.json (créé au premier lancement, complété à chaque mise à jour).
 * Les réglages visuels communs (densité de particules, nombre de débris, secousse de caméra, dureté max des blocs, blocs
 * modifiés par tick) sont partagés avec le Zushi Zushi no Mi : config/minepiece-zushi.json.
 */
public final class GoroConfig {
	/** false = aucune attaque du Goro ne détruit de bloc (effets visuels et dégâts conservés). */
	public boolean destroyTerrain = true;
	public double damageMultiplier = 1.0;

	// ---- Sango (touche maintenue, 3 niveaux)
	public int sangoLevel2Ticks = 20;
	public int sangoLevel3Ticks = 50;
	public int sangoMaxChargeTicks = 90;
	public double sangoRange = 40.0;
	public double sangoBoltDamage = 9.0;
	public double sangoDogDamage = 18.0;
	public double sangoDogRadius = 3.5;
	public double sangoDogCrater = 1.8;
	public double sangoDragonDamage = 34.0;
	public double sangoDragonRadius = 8.0;
	public double sangoDragonCrater = 5.0;

	// ---- El Thor
	public double elThorRange = 60.0;
	public int elThorPrepareTicks = 45;
	public double elThorDamage = 42.0;
	public double elThorRadius = 9.0;
	public double elThorCrater = 6.5;
	/** Rayon de la colonne de foudre (blocs) : sert aussi de zone de dégâts continus. */
	public double elThorBeamRadius = 4.5;
	/** Durée pendant laquelle la colonne reste visible et frappe le sol (ticks). */
	public int elThorBeamTicks = 40;
	/** Dégâts infligés toutes les 5 ticks aux entités dans la colonne. */
	public double elThorBeamDamage = 5.0;

	// ---- Raigo (touche maintenue : la sphère grossit)
	public int raigoMaxChargeTicks = 160;
	public double raigoRange = 90.0;
	public double raigoMinDamage = 35.0;
	public double raigoMaxDamage = 95.0;
	public double raigoMinRadius = 9.0;
	public double raigoMaxRadius = 24.0;
	public double raigoMinCrater = 6.0;
	public double raigoMaxCrater = 16.0;
	/** Rayon visuel de la sphère (blocs) au début et à la fin de la charge. */
	public double raigoMinSphere = 1.5;
	public double raigoMaxSphere = 9.0;

	// ---- Défibrillateur
	public boolean defibrillatorEnabled = true;
	/** Fraction des PV max rendue à la réanimation. */
	public double defibrillatorHealth = 0.20;

	public int configVersion = 2;

	private static GoroConfig instance = new GoroConfig();

	public static GoroConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("minepiece-goro.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					GoroConfig loaded = gson.fromJson(reader, GoroConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Config Goro Goro illisible, valeurs par défaut utilisées.", e);
			instance = new GoroConfig();
		}
		instance.sanitize();
		try (Writer writer = Files.newBufferedWriter(path)) {
			gson.toJson(instance, writer);
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Impossible d'écrire la config Goro Goro.", e);
		}
	}

	private void sanitize() {
		damageMultiplier = Math.max(0.0, damageMultiplier);
		sangoLevel2Ticks = Math.max(1, sangoLevel2Ticks);
		sangoLevel3Ticks = Math.max(sangoLevel2Ticks + 1, sangoLevel3Ticks);
		sangoMaxChargeTicks = Math.max(sangoLevel3Ticks, sangoMaxChargeTicks);
		sangoRange = Math.max(4.0, Math.min(120.0, sangoRange));
		elThorRange = Math.max(4.0, Math.min(160.0, elThorRange));
		elThorPrepareTicks = Math.max(10, Math.min(200, elThorPrepareTicks));
		elThorRadius = Math.max(1.0, Math.min(32.0, elThorRadius));
		elThorBeamRadius = Math.max(1.0, Math.min(16.0, elThorBeamRadius));
		elThorBeamTicks = Math.max(10, Math.min(200, elThorBeamTicks));
		elThorBeamDamage = Math.max(0.0, elThorBeamDamage);
		raigoMaxChargeTicks = Math.max(20, Math.min(600, raigoMaxChargeTicks));
		raigoRange = Math.max(8.0, Math.min(200.0, raigoRange));
		raigoMaxRadius = Math.max(raigoMinRadius, Math.min(40.0, raigoMaxRadius));
		raigoMaxCrater = Math.max(raigoMinCrater, Math.min(24.0, raigoMaxCrater));
		raigoMinSphere = Math.max(0.5, raigoMinSphere);
		raigoMaxSphere = Math.max(raigoMinSphere, Math.min(16.0, raigoMaxSphere));
		defibrillatorHealth = Math.max(0.05, Math.min(1.0, defibrillatorHealth));
		sangoDogCrater = Math.min(sangoDogCrater, 6.0);
		sangoDragonCrater = Math.min(sangoDragonCrater, 16.0);
		elThorCrater = Math.min(elThorCrater, 20.0);
	}
}
