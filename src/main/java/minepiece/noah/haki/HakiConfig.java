package minepiece.noah.haki;

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
 * Réglages du Haki (config/minepiece-haki.json). Les tableaux [3] sont indexés par la maîtrise (niveau 1, 2, 3).
 * Énergie : 0..energyMax, coûts et consommations en points ; durées en ticks (20 = 1 s).
 */
public final class HakiConfig {
	/** Créatif : toutes les maîtrises au maximum (y compris le Haki des Rois) pour tout tester. */
	public boolean creativeAllMastery = true;

	// ---- énergie
	public double energyMax = 100.0;
	public double energyRegenPerSecond = 7.0;
	/** Délai avant que l'énergie remonte après la dernière dépense. */
	public int energyRegenDelay = 30;

	// ---- progression (XP nécessaire pour la maîtrise 2 puis 3 ; Armement / Observation commencent au niveau 1)
	/** XP pour passer aux maîtrises 2, 3, 4 et 5 de l'Armement. */
	public int[] armXp = {250, 600, 1100, 1800};
	public int[] obsXp = {200, 900};
	public int[] kingXp = {150, 600};
	/** Chance (unique, au premier passage) qu'un joueur naisse avec le Haki des Rois. Sinon : /haki eveil (opérateurs). */
	public double kingAwakenChance = 0.03;

	// ---- Armement
	public double armDrainPerSecond = 1.6;
	/** Bonus par maîtrise 1..5 (plus le corps est recouvert, plus il est résistant). */
	public double[] armDamageBonus = {0.20, 0.30, 0.40, 0.50, 0.60};
	public double[] armArmor = {2.0, 3.0, 4.5, 6.0, 8.0};
	public double[] armToughness = {1.0, 1.5, 2.0, 3.0, 4.0};

	// ---- Observation
	public double obsDrainPerSecond = 1.2;
	public double[] obsRadius = {16.0, 28.0, 40.0};
	public double[] dodgeChance = {0.30, 0.50, 0.70};
	public double futureDodgeChance = 0.90;
	public double futureDrainPerSecond = 3.5;
	public double autoDodgeCost = 12.0;
	public int autoDodgeCooldown = 30;
	public int futureDodgeCooldown = 14;
	public double dashCost = 10.0;
	public int dashCooldown = 16;
	public double dashStrength = 1.05;

	// ---- Rois
	/** Haki des Rois (1 niveau) : touche L MAINTENUE -> éclairs noirs et rouges qui assomment les ennemis. */
	public double kingStartCost = 15.0;
	public double kingHoldDrainPerSecond = 10.0;
	/** Intervalle (ticks) entre deux salves d'éclairs, et nombre de cibles par salve. */
	public int kingBoltInterval = 6;
	public int kingBoltTargets = 3;
	public double kingHoldRadius = 18.0;
	/** PV max au-dessous desquels un ennemi frappé par un éclair du Roi s'évanouit (au-dessus : il résiste). */
	public double kingHoldKoHealth = 100.0;
	/** Recharge après avoir relâché la touche. */
	public int kingCooldown = 200;
	public double kingCost = 45.0;
	public double[] kingRadius = {16.0, 26.0, 38.0};
	/** PV max au-dessous desquels un ennemi s'effondre (ennemis plus forts : ils résistent, simple pression). */
	public double[] kingKoHealth = {20.0, 60.0, 120.0};
	public int kingKoTicks = 140;
	/** Les joueurs sans aucune maîtrise avancée (Armement + Observation <= 2) et avec peu de PV peuvent s'évanouir. */
	public boolean kingKoPlayers = true;
	public double infuseDrainPerSecond = 4.0;
	public double infuseDamageBonus = 0.25;
	public int clashWindow = 30;

	public int configVersion = 2;

	private static HakiConfig instance = new HakiConfig();

	public static HakiConfig get() {
		return instance;
	}

	static double at(double[] a, int mastery) {
		return a[Math.max(0, Math.min(a.length - 1, mastery - 1))];
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("minepiece-haki.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					HakiConfig loaded = gson.fromJson(reader, HakiConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Config du Haki illisible, valeurs par défaut utilisées.", e);
			instance = new HakiConfig();
		}
		instance.sanitize();
		try (Writer writer = Files.newBufferedWriter(path)) {
			gson.toJson(instance, writer);
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Impossible d'écrire la config du Haki.", e);
		}
	}

	private static double[] fix(double[] a, double[] def) {
		return a == null || a.length < 3 ? def : a;
	}

	private void sanitize() {
		HakiConfig d = new HakiConfig();
		energyMax = Math.max(10.0, energyMax);
		armXp = armXp == null || armXp.length < 2 ? d.armXp : armXp;
		obsXp = obsXp == null || obsXp.length < 2 ? d.obsXp : obsXp;
		kingXp = kingXp == null || kingXp.length < 2 ? d.kingXp : kingXp;
		armXp = armXp.length < 4 ? d.armXp : armXp;
		armDamageBonus = armDamageBonus == null || armDamageBonus.length < 5 ? d.armDamageBonus : armDamageBonus;
		armArmor = armArmor == null || armArmor.length < 5 ? d.armArmor : armArmor;
		armToughness = armToughness == null || armToughness.length < 5 ? d.armToughness : armToughness;
		kingBoltInterval = Math.max(2, kingBoltInterval);
		kingBoltTargets = Math.max(1, Math.min(10, kingBoltTargets));
		obsRadius = fix(obsRadius, d.obsRadius);
		dodgeChance = fix(dodgeChance, d.dodgeChance);
		kingRadius = fix(kingRadius, d.kingRadius);
		kingKoHealth = fix(kingKoHealth, d.kingKoHealth);
		kingAwakenChance = Math.max(0.0, Math.min(1.0, kingAwakenChance));
		futureDodgeChance = Math.max(0.0, Math.min(0.98, futureDodgeChance));
		kingKoTicks = Math.max(20, kingKoTicks);
	}
}
