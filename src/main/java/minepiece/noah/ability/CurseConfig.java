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
 * Réglages de la faiblesse à l'eau des fruits du démon (Malédiction du Démon).
 * Fichier : config/minepiece-demon-curse.json (créé au premier lancement). Redémarre le jeu / le serveur après modification.
 * Les durées sont en ticks (20 ticks = 1 seconde).
 */
public final class CurseConfig {
	/** false = la mécanique est désactivée. */
	public boolean enabled = true;

	// ---- Détection de l'immersion
	/** Le joueur doit être totalement immergé pendant ce nombre de ticks avant que la mécanique démarre (anti-clignotement). */
	public int enterTicks = 5;
	/** Profondeur minimale des yeux sous la surface pour démarrer (évite de déclencher à la limite de la surface). */
	public double minEyeDepth = 0.3;

	// ---- Attraction vers le fond (gravité ajoutée ; la gravité normale du joueur est 0,08, le maximum du jeu est 1,0)
	public double pullStart = 0.10;
	public double pullMax = 0.80;
	/** Gravité ajoutée par seconde passée sous l'eau. */
	public double pullRampPerSecond = 0.20;

	// ---- Malédiction du Démon
	/** Temps sous l'eau avant l'apparition de la malédiction (30 ticks = 1,5 s). */
	public int curseDelayTicks = 30;
	/** Niveau maximal de l'effet (0 = niveau I). */
	public int maxCurseLevel = 4;

	// ---- Dégâts
	public double damageAmount = 1.0;
	public int damageIntervalTicks = 20;
	/** Toutes les X ticks sous malédiction, le niveau et les dégâts augmentent. */
	public int escalationIntervalTicks = 100;
	public double escalationDamage = 0.5;
	public double damageMax = 4.0;

	// ---- Particules (sombres / violettes)
	public boolean particles = true;
	public int particleCount = 6;
	public int particleIntervalTicks = 4;

	private static CurseConfig instance = new CurseConfig();

	public static CurseConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("minepiece-demon-curse.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					CurseConfig loaded = gson.fromJson(reader, CurseConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			try (Writer writer = Files.newBufferedWriter(path)) {
				gson.toJson(instance, writer);
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Config Malédiction du Démon illisible, valeurs par défaut utilisées.", e);
			instance = new CurseConfig();
		}
		instance.sanitize();
	}

	private void sanitize() {
		enterTicks = Math.max(1, enterTicks);
		minEyeDepth = Math.max(0.0, minEyeDepth);
		pullStart = Math.max(0.0, pullStart);
		pullMax = Math.max(pullStart, Math.min(0.92, pullMax));
		pullRampPerSecond = Math.max(0.0, pullRampPerSecond);
		curseDelayTicks = Math.max(0, curseDelayTicks);
		maxCurseLevel = Math.max(0, Math.min(10, maxCurseLevel));
		damageAmount = Math.max(0.0, damageAmount);
		damageIntervalTicks = Math.max(1, damageIntervalTicks);
		escalationIntervalTicks = Math.max(1, escalationIntervalTicks);
		escalationDamage = Math.max(0.0, escalationDamage);
		damageMax = Math.max(damageAmount, damageMax);
		particleCount = Math.max(0, particleCount);
		particleIntervalTicks = Math.max(1, particleIntervalTicks);
	}
}
