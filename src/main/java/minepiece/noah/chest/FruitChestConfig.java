package minepiece.noah.chest;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import minepiece.noah.MinePiece;
import minepiece.noah.data.DevilFruit;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Probabilités du Coffre à Fruits.
 *
 * Le mod n'a pas de poids de drop par fruit (la rareté des objets est purement visuelle), donc par défaut
 * tous les fruits ont le même poids. Pour changer les chances : édite
 * <code>config/minepiece_fruit_chest.json</code> (poids relatifs, 0 = le fruit ne sort jamais).
 * Un nouveau fruit ajouté à l'enum DevilFruit est pris en compte automatiquement (poids par défaut).
 */
public final class FruitChestConfig {
	public static final int DEFAULT_WEIGHT = 100;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Poids dans l'ordre de DevilFruit.values(). */
	private static volatile int[] weights = defaults();

	private FruitChestConfig() {}

	private static int[] defaults() {
		int[] w = new int[DevilFruit.values().length];
		Arrays.fill(w, DEFAULT_WEIGHT);
		return w;
	}

	public static String fruitId(DevilFruit fruit) {
		return fruit.name().toLowerCase(Locale.ROOT);
	}

	/** Poids actuels (ne pas modifier le tableau). */
	public static int[] weights() {
		return weights;
	}

	public static void load() {
		DevilFruit[] all = DevilFruit.values();
		int[] w = defaults();
		Path file = FabricLoader.getInstance().getConfigDir().resolve("minepiece_fruit_chest.json");
		try {
			if (Files.exists(file)) {
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
					JsonObject map = root.has("weights") ? root.getAsJsonObject("weights") : new JsonObject();
					for (int i = 0; i < all.length; i++) {
						JsonElement e = map.get(fruitId(all[i]));
						if (e != null && e.isJsonPrimitive()) {
							w[i] = Math.max(0, e.getAsInt());
						}
					}
				}
			} else {
				JsonObject root = new JsonObject();
				root.addProperty("_comment", "Poids relatifs de chaque fruit dans le Coffre a Fruits (0 = jamais).");
				JsonObject map = new JsonObject();
				for (DevilFruit f : all) {
					map.addProperty(fruitId(f), DEFAULT_WEIGHT);
				}
				root.add("weights", map);
				Files.createDirectories(file.getParent());
				try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
					GSON.toJson(root, writer);
				}
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.warn("Config du Coffre à Fruits illisible, poids par défaut utilisés", e);
			w = defaults();
		}
		weights = w;
	}
}
