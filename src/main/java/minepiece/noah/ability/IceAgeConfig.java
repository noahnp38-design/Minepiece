package minepiece.noah.ability;

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
 * Réglages de l'Ice Age (Hie Hie no Mi).
 * Fichier : config/minepiece-ice-age.json (créé au premier lancement). Redémarre le jeu / le serveur après modification.
 * Les durées sont en ticks (20 ticks = 1 seconde).
 */
public final class IceAgeConfig {
	// ---- Zone
	/** Rayon de la zone gelée, en blocs. */
	public int radius = 34;
	/** Version du fichier : sert à appliquer les nouvelles valeurs par défaut aux anciens fichiers. */
	public int configVersion = 0;
	/** Hauteur traitée au-dessus et au-dessous du lanceur (collines, trous). */
	public int verticalRange = 8;
	/** Vitesse du front de glace, en blocs par tick (0.8 = 16 blocs par seconde). */
	public double speedBlocksPerTick = 1.0;
	/** Durée de la préparation avant le départ de l'onde. */
	public int prepTicks = 16;

	// ---- Créatures
	public double damage = 14.0;
	/** Durée pendant laquelle la créature est clouée sur place. */
	public int immobilizeTicks = 80;
	/** Durée totale du ralentissement (après l'immobilisation, il reste un ralentissement plus léger). */
	public int slowTicks = 240;
	/** Niveau du ralentissement léger (0 = Lenteur I). */
	public int slowLevel = 3;

	// ---- Recharge
	public int cooldownTicks = 600;

	// ---- Terrain
	/** false = seule l'eau gèle, le sol n'est pas transformé. */
	public boolean freezeGround = true;
	/** false = la pierre naturelle n'est pas transformée. */
	public boolean freezeStone = true;
	/** Part du sol gelé recouverte de neige (0 à 1). */
	public double snowChance = 0.40;
	/** Part du sol gelé qui reçoit un amas de cristaux (0 à 1). */
	public double crystalChance = 0.05;
	/** true = le terrain d'origine est remis en place après restoreAfterTicks. */
	public boolean restoreBlocks = true;
	/** Temps avant le dégel, compté à partir de la fin de l'onde. */
	public int restoreAfterTicks = 900;

	// ---- Effets
	/** Multiplicateur du nombre de particules (0 = aucune, 1 = normal, 2 = double). */
	public double particleMultiplier = 1.0;
	/** Durée de la brume et de la neige sur la zone après le passage de l'onde. */
	public int lingerTicks = 160;

	private static IceAgeConfig instance;

	public static IceAgeConfig get() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	public static void load() {
		IceAgeConfig cfg = new IceAgeConfig();
		Path path = FabricLoader.getInstance().getConfigDir().resolve("minepiece-ice-age.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					IceAgeConfig loaded = gson.fromJson(reader, IceAgeConfig.class);
					if (loaded != null) {
						cfg = loaded;
					}
				}
			}
			if (cfg.configVersion < 2) {
				// Ancien fichier : le rayon par défaut passe de 22 à 34 blocs.
				if (cfg.radius == 22) {
					cfg.radius = 34;
				}
				if (cfg.verticalRange == 6) {
					cfg.verticalRange = 8;
				}
				if (cfg.speedBlocksPerTick == 0.8) {
					cfg.speedBlocksPerTick = 1.0;
				}
				cfg.configVersion = 2;
			}
			cfg.sanitize();
			try (Writer writer = Files.newBufferedWriter(path)) {
				gson.toJson(cfg, writer);
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Config Ice Age illisible, valeurs par défaut utilisées.", e);
			cfg = new IceAgeConfig();
		}
		cfg.sanitize();
		instance = cfg;
	}

	private void sanitize() {
		radius = Math.max(3, Math.min(64, radius));
		verticalRange = Math.max(1, Math.min(16, verticalRange));
		speedBlocksPerTick = Math.max(0.1, Math.min(4.0, speedBlocksPerTick));
		prepTicks = Math.max(0, Math.min(100, prepTicks));
		damage = Math.max(0.0, damage);
		immobilizeTicks = Math.max(0, immobilizeTicks);
		slowTicks = Math.max(immobilizeTicks, slowTicks);
		slowLevel = Math.max(0, Math.min(9, slowLevel));
		cooldownTicks = Math.max(0, cooldownTicks);
		snowChance = Math.max(0.0, Math.min(1.0, snowChance));
		crystalChance = Math.max(0.0, Math.min(1.0 - snowChance, crystalChance));
		restoreAfterTicks = Math.max(20, restoreAfterTicks);
		particleMultiplier = Math.max(0.0, Math.min(4.0, particleMultiplier));
		lingerTicks = Math.max(0, Math.min(1200, lingerTicks));
	}
}
