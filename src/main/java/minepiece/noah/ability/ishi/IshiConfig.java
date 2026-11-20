package minepiece.noah.ability.ishi;

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
 * Réglages du Ishi Ishi no Mi : config/minepiece-ishi.json (créé au premier lancement, complété à chaque mise à jour).
 * Les réglages communs aux fruits destructeurs (dureté max des blocs, blocs modifiés par tick, densité des particules,
 * secousse de caméra, débris 3D max) sont ceux de config/minepiece-zushi.json.
 */
public final class IshiConfig {
	/** false = aucune attaque ne modifie le terrain (animations, dégâts et particules conservés). */
	public boolean destroyTerrain = true;
	/** Les pics laissent un relief de pierre permanent (sinon ils s'effondrent et disparaissent). */
	public boolean permanentSpikes = false;
	public double damageMultiplier = 1.0;
	/**
	 * Pierre exigée : une attaque ne fonctionne qu'au-dessus d'un sol rocheux (pierre, andésite, deepslate, tuf, grès...)
	 * présent à quelques blocs de profondeur. false = la pierre n'est jamais exigée.
	 */
	public boolean requireStone = true;
	/** Profondeur (blocs) où l'on cherche la pierre sous la cible. */
	public int stoneSearchDepth = 6;

	// ---- W : Éruption rocheuse
	public double eruptionRange = 26.0;
	public int eruptionSpikes = 7;
	public double eruptionRadius = 3.6;
	public double eruptionDamage = 16.0;
	public double eruptionMinHeight = 3.5;
	public double eruptionMaxHeight = 6.5;
	/** Durée de vie des pics dressés (ticks) avant effondrement. */
	public int eruptionHoldTicks = 50;
	public double eruptionCrater = 1.6;

	// ---- X : Poing colossal
	public double fistReach = 5.5;
	public double fistDamage = 24.0;
	public double fistRadius = 4.5;
	public double fistCrater = 3.4;
	public double fistKnockback = 2.4;

	// ---- V : Titan de pierre
	/** Échelle du joueur (attribut minecraft:scale, 16 au maximum) : boîte de collision = 1,8 x échelle blocs de haut. */
	public double titanScale = 16.0;
	/** Hauteur VISUELLE du titan (blocs) : peut dépasser la boîte de collision (plafond vanilla de l'échelle). */
	public double titanVisualHeight = 42.0;
	/** Points de structure du titan : les dégâts reçus usent la roche, pas le joueur ; à zéro le titan s'effondre. */
	public double titanIntegrity = 600.0;
	/** Nombre minimal de blocs rocheux dans le sol (rayon 4, 6 de profondeur) pour se former. */
	public int titanMinStone = 70;
	public double titanSpeedBonus = 0.7;
	public double titanMeleeBonus = 22.0;
	/** Le titan écrase arbres et feuillages sur son passage (protections respectées). */
	public boolean titanCrushTrees = true;
	public int titanMaxSeconds = 120;

	// ---- W titan : forêt de pics
	public double titanEruptionRange = 70.0;
	public int titanEruptionSpikes = 34;
	public double titanEruptionRadius = 22.0;
	public double titanEruptionDamage = 34.0;
	public double titanEruptionMinHeight = 10.0;
	public double titanEruptionMaxHeight = 24.0;
	public double titanEruptionCrater = 3.0;

	// ---- X titan : poing du titan
	public double titanFistDamage = 60.0;
	public double titanFistRadius = 13.0;
	public double titanFistCrater = 8.5;
	public double titanFistKnockback = 4.0;

	public int configVersion = 1;

	private static IshiConfig instance = new IshiConfig();

	public static IshiConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("minepiece-ishi.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					IshiConfig loaded = gson.fromJson(reader, IshiConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Config Ishi Ishi illisible, valeurs par défaut utilisées.", e);
			instance = new IshiConfig();
		}
		instance.sanitize();
		try (Writer writer = Files.newBufferedWriter(path)) {
			gson.toJson(instance, writer);
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Impossible d'écrire la config Ishi Ishi.", e);
		}
	}

	private void sanitize() {
		damageMultiplier = Math.max(0.0, damageMultiplier);
		stoneSearchDepth = Math.max(1, Math.min(16, stoneSearchDepth));
		eruptionRange = clamp(eruptionRange, 4, 64);
		eruptionSpikes = (int) clamp(eruptionSpikes, 1, 24);
		eruptionRadius = clamp(eruptionRadius, 0.5, 12);
		eruptionMinHeight = clamp(eruptionMinHeight, 1, 20);
		eruptionMaxHeight = clamp(eruptionMaxHeight, eruptionMinHeight, 30);
		eruptionHoldTicks = (int) clamp(eruptionHoldTicks, 5, 600);
		eruptionCrater = clamp(eruptionCrater, 0, 6);
		fistReach = clamp(fistReach, 2, 12);
		fistRadius = clamp(fistRadius, 1, 16);
		fistCrater = clamp(fistCrater, 0, 10);
		titanScale = clamp(titanScale, 2, 16);
		titanVisualHeight = clamp(titanVisualHeight, 1.8 * titanScale, 120);
		titanIntegrity = Math.max(1.0, titanIntegrity);
		titanMinStone = (int) clamp(titanMinStone, 0, 400);
		titanMaxSeconds = (int) clamp(titanMaxSeconds, 10, 3600);
		titanEruptionRange = clamp(titanEruptionRange, 8, 160);
		titanEruptionSpikes = (int) clamp(titanEruptionSpikes, 1, 80);
		titanEruptionRadius = clamp(titanEruptionRadius, 2, 48);
		titanEruptionMinHeight = clamp(titanEruptionMinHeight, 2, 60);
		titanEruptionMaxHeight = clamp(titanEruptionMaxHeight, titanEruptionMinHeight, 90);
		titanEruptionCrater = clamp(titanEruptionCrater, 0, 10);
		titanFistRadius = clamp(titanFistRadius, 2, 40);
		titanFistCrater = clamp(titanFistCrater, 0, 20);
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
