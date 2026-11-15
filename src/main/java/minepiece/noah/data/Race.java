package minepiece.noah.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Les races jouables. Les textes bonus / malus ne servent qu'à l'affichage (écran de choix) :
 * les vraies valeurs sont dans RaceEffects (attributs) et RacePowers (vol, planeur, souffle...).
 */
public enum Race {
	HUMAN("Humain", "Race équilibrée, sans bonus ni malus. La référence.",
			"Aucun", "Aucun"),
	SKYPIEAN("Skypiéen", "Peuple du ciel : ses ailes lui permettent de voler et de planer.",
			"Vol libre (double saut) + planeur", "Vol à endurance limitée"),
	FISHMAN("Homme-Poisson", "Maître des océans, chez lui sous l'eau.",
			"Respire sous l'eau sans limite, nage très vite", "Aucun"),
	MINK("Mink", "Agilité et réflexes hors du commun.",
			"Vitesse +20 % sur terre", "Aucun"),
	GIANT("Géant", "Mesure 3 blocs de haut, portée accrue.",
			"Taille de 3 blocs, grande portée", "Aucun"),
	LUNARIAN("Lunarien", "Peau mate, ailes noires et queue enflammée. Résistant et capable de voler.",
			"Vol libre (ailes), dégâts reçus -30 %", "Vol à endurance limitée");

	/**
	 * Lecture / écriture de la race dans les données du joueur.
	 * "MAN" est une ancienne race supprimée : les joueurs qui l'avaient deviennent Humains au lieu de perdre leur profil.
	 */
	public static final Codec<Race> CODEC = Codec.STRING.comapFlatMap(name -> {
		if ("MAN".equals(name)) {
			return DataResult.success(HUMAN);
		}
		try {
			return DataResult.success(Race.valueOf(name));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Valeur inconnue : " + name);
		}
	}, Race::name);

	private final String displayName;
	private final String description;
	private final String bonus;
	private final String malus;

	Race(String displayName, String description, String bonus, String malus) {
		this.displayName = displayName;
		this.description = description;
		this.bonus = bonus;
		this.malus = malus;
	}

	public String displayName() { return displayName; }
	public String description() { return description; }
	/** Texte court des bonus (écran de choix). */
	public String bonus() { return bonus; }
	/** Texte court des malus (écran de choix). */
	public String malus() { return malus; }

	/** Vrai si la race a des ailes et peut voler (Skypiéen, Lunarien). Utilisé par le serveur ET le client. */
	public boolean canFly() {
		return this == SKYPIEAN || this == LUNARIAN;
	}

	/** Vrai si la race peut planer (maintenir Espace en tombant) : les races qui volent, quand leur endurance est vide. */
	public boolean canGlide() {
		return canFly();
	}

	public static Race byId(int id) {
		Race[] v = values();
		return id >= 0 && id < v.length ? v[id] : null;
	}
}
