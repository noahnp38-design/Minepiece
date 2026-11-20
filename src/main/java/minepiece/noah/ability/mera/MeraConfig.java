package minepiece.noah.ability.mera;

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
 * Réglages du Mera Mera no Mi. Fichier : config/minepiece-mera.json (créé au premier lancement).
 * Redémarre le jeu / le serveur après modification.
 */
public final class MeraConfig {
	// ---- Environnement
	/** false = les explosions ne cassent aucun bloc. */
	public boolean destroyBlocks = true;
	/** true = des flammes restent au sol après une explosion. */
	public boolean setFires = true;
	public double fireChance = 0.30;
	/** Flammes posées autour des explosions du Hiken et du Dai Enkai (nombre d'essais par bloc² de rayon). */
	public double fireDensity = 1.5;
	/** Durée pendant laquelle les créatures touchées brûlent (secondes). */
	public int burnSeconds = 12;
	/** Durée du brasier qui reste sur la zone après l'explosion (ticks) : il enflamme tout ce qui y entre. */
	public int blazeTicks = 100;
	/** Version du fichier : sert à appliquer les nouvelles valeurs par défaut aux anciens fichiers. */
	public int configVersion = 0;
	/** Les blocs plus durs que ça résistent (pierre 1.5, obsidienne 50). */
	public float maxBlockHardness = 10.0F;
	/** Coffres, fourneaux... sont épargnés tant que c'est false. */
	public boolean destroyBlockEntities = false;
	/** Plafond de blocs détruits par explosion (protège contre les lags). */
	public int maxBlocksPerExplosion = 20000;
	/** Blocs détruits par tick : la destruction s'étale sur plusieurs ticks. */
	public int blocksPerTick = 1000;
	/** Nombre max d'effets actifs en même temps (projectiles, ondes...). */
	public int maxActiveEffects = 400;

	// ---- Dégâts
	/** Multiplicateur global de tous les dégâts du fruit. */
	public double damageMultiplier = 1.0;

	// ---- Fire Gun
	public double fireGunDamage = 4.0;
	public double fireGunSpeed = 1.6;
	public int fireGunIntervalTicks = 2;
	/** Durée du Fire Gun une fois lancé : 64 ticks = 3,2 s, quoi qu'il arrive (relâcher la touche ne l'arrête pas). */
	public int fireGunDurationTicks = 64;
	public int fireGunLifeTicks = 25;
	/** Délai minimal (en ticks) entre deux lectures du son du Fire Gun pour un même joueur : la durée du son (3,8 s = 76 ticks). */
	public int fireGunSoundIntervalTicks = 76;

	// ---- Hiken
	public int hikenWindupTicks = 15;
	public double hikenSpeed = 1.1;
	public double hikenRadius = 1.6;
	public double hikenBlastRadius = 8.0;
	public double hikenDamage = 34.0;

	// ---- Dai Enkai : Entei
	public int enteiMinChargeTicks = 10;
	public int enteiMaxChargeTicks = 120;
	public double enteiSpeed = 0.9;
	public double enteiMinRadius = 1.2;
	public double enteiMaxRadius = 6.5;
	public double enteiMinBlastRadius = 6.0;
	public double enteiMaxBlastRadius = 16.0;
	public double enteiMinDamage = 30.0;
	public double enteiMaxDamage = 120.0;

	private static MeraConfig instance = new MeraConfig();

	public static MeraConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("minepiece-mera.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					MeraConfig loaded = gson.fromJson(reader, MeraConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			// On réécrit le fichier pour y ajouter les nouvelles options ajoutées par les mises à jour.
			try (Writer writer = Files.newBufferedWriter(path)) {
				gson.toJson(instance, writer);
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Config Mera Mera illisible, valeurs par défaut utilisées (fichier non modifié).", e);
			instance = new MeraConfig();
		}
		instance.migrate();
		instance.sanitize();
		try (Writer writer = Files.newBufferedWriter(path)) {
			gson.toJson(instance, writer);
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Impossible d'écrire la config Mera Mera.", e);
		}
	}

	/** Anciens fichiers : on applique les nouvelles valeurs par défaut sauf si le joueur les avait changées. */
	private void migrate() {
		if (configVersion < 2) {
			if (fireChance == 0.04) {
				fireChance = 0.30;
			}
			if (hikenBlastRadius == 5.0) {
				hikenBlastRadius = 8.0;
			}
			if (hikenDamage == 24.0) {
				hikenDamage = 34.0;
			}
			configVersion = 2;
		}
	}

	private void sanitize() {
		blocksPerTick = Math.max(1, blocksPerTick);
		maxBlocksPerExplosion = Math.max(0, maxBlocksPerExplosion);
		maxActiveEffects = Math.max(20, maxActiveEffects);
		fireGunIntervalTicks = Math.max(1, fireGunIntervalTicks);
		fireGunLifeTicks = Math.max(1, fireGunLifeTicks);
		fireGunDurationTicks = Math.max(1, fireGunDurationTicks);
		fireGunSoundIntervalTicks = Math.max(20, fireGunSoundIntervalTicks);
		hikenWindupTicks = Math.max(1, hikenWindupTicks);
		enteiMaxChargeTicks = Math.max(enteiMinChargeTicks + 1, enteiMaxChargeTicks);
		damageMultiplier = Math.max(0.0, damageMultiplier);
		fireChance = Math.max(0.0, Math.min(1.0, fireChance));
		fireDensity = Math.max(0.0, Math.min(10.0, fireDensity));
		burnSeconds = Math.max(0, burnSeconds);
		blazeTicks = Math.max(0, Math.min(600, blazeTicks));
	}
}
