package minepiece.noah.data;

import com.mojang.serialization.Codec;

public enum Faction {
	PIRATE("Pirate", "Liberté, aventure et prime sur la tête."),
	MARINE("Marine", "Faire respecter la Justice."),
	REVOLUTIONARY("Armée Révolutionnaire", "Renverser le Gouvernement Mondial."),
	BOUNTY_HUNTER("Chasseur de primes", "Traquer les hors-la-loi contre de l'argent."),
	CIVILIAN("Civil", "Vivre en paix, loin des conflits.");

	public static final Codec<Faction> CODEC = EnumCodecs.of(Faction.class);

	private final String displayName;
	private final String description;

	Faction(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public String displayName() { return displayName; }
	public String description() { return description; }

	public static Faction byId(int id) {
		Faction[] v = values();
		return id >= 0 && id < v.length ? v[id] : null;
	}
}
