package minepiece.noah.data;

import com.mojang.serialization.Codec;

public enum CombatStyle {
	SWORDSMAN("Épéiste", "Maniement du sabre, jusqu'au style à trois sabres."),
	BLACK_LEG("Jambes Noires", "Combat uniquement avec les jambes."),
	FISHMAN_KARATE("Karaté Homme-Poisson", "Manipule l'eau pour frapper."),
	SNIPER("Tireur d'élite", "Précision à longue distance.");

	public static final Codec<CombatStyle> CODEC = EnumCodecs.of(CombatStyle.class);

	private final String displayName;
	private final String description;

	CombatStyle(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public String displayName() { return displayName; }
	public String description() { return description; }

	public static CombatStyle byId(int id) {
		CombatStyle[] v = values();
		return id >= 0 && id < v.length ? v[id] : null;
	}
}
