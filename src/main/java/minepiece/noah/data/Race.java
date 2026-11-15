package minepiece.noah.data;

import com.mojang.serialization.Codec;

public enum Race {
	HUMAN("Humain", "Polyvalent, sans bonus ni malus."),
	SKYPIEAN("Skypiéen", "Peuple du ciel, avec des ailes blanches dans le dos."),
	FISHMAN("Homme-Poisson", "Nageoires sur le corps et peau bleutée."),
	MINK("Mink", "Agilité et réflexes hors du commun."),
	GIANT("Géant", "Mesure 3 blocs de haut, portée accrue."),
	LUNARIAN("Lunarien", "Peau mate, ailes noires et queue enflammée.");

	public static final Codec<Race> CODEC = EnumCodecs.of(Race.class);

	private final String displayName;
	private final String description;

	Race(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public String displayName() { return displayName; }
	public String description() { return description; }

	public static Race byId(int id) {
		Race[] v = values();
		return id >= 0 && id < v.length ? v[id] : null;
	}
}
