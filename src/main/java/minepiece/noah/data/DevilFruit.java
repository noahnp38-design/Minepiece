package minepiece.noah.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Les fruits du démon. Un joueur ne peut en manger qu'un seul. */
public enum DevilFruit {
	HIE_HIE_NO_MI(
			"Hie Hie no Mi",
			"Fruit de la Glace : la glace se forme sous tes pas sur l'eau.",
			new String[] {"Ice Time", "Pheasant Beak", "Ice Age"},
			new int[] {200, 160, 600}, // temps de recharge en ticks (20 ticks = 1 s)  ; Ice Age : réglable dans config/minepiece-ice-age.json
			true // Logia : invulnérable aux attaques physiques
	),
	MERA_MERA_NO_MI(
			"Mera Mera no Mi",
			"Fruit du Feu : ton corps est de flammes, immunisé au feu et aux attaques physiques.",
			new String[] {"Fire Gun", "Hiken", "Dai Enkai : Entei"},
			new int[] {100, 160, 900}, // Fire Gun : 100 ticks = 5 s de recharge, démarrée à la fin du tir
			true
	),
	SUKE_SUKE_NO_MI(
			"Suke Suke no Mi",
			"Fruit de l'Invisibilité (Paramécia) : furtivité et assassinat.",
			new String[] {"Disparition totale", "Frappe fantôme", "Voile invisible"},
			new int[] {500, 240, 900}, // 25 s, 12 s, 45 s
			false
	),
	BOMU_BOMU_NO_MI(
			"Bomu Bomu no Mi",
			"Fruit de l'Explosion (Paramécia) : ton corps est explosif, tu es immunisé à tes propres explosions et tes coups explosent.",
			new String[] {"Bomu Shot", "Bomu Bash", "Bomu Graviton"},
			new int[] {160, 300, 700}, // 8 s, 15 s, 35 s
			false
	),
	SUPA_SUPA_NO_MI(
			"Supa Supa no Mi",
			"Fruit Tailladant (Paramécia) : ton corps devient acier, tes membres des lames. Passif : -30 % de dégâts physiques, +2 en mêlée.",
			new String[] {"Sparkling Daisy", "Spiral Hollow", "Atomic Spada"},
			new int[] {160, 240, 400}, // 8 s, 12 s, 20 s
			false
	),
	USHI_USHI_NO_MI(
			"Ushi Ushi no Mi, modèle Girafe",
			"Fruit Zoan (Kaku) : transforme-toi en hybride puis en girafe complète (V : forme suivante, B : forme humaine).",
			new String[] {"Rankyaku", "Bigan", "Kirin Roundabout"},
			new int[] {120, 200, 440}, // 6 s, 10 s, 22 s
			false
	),
	ZUSHI_ZUSHI_NO_MI(
			"Zushi Zushi no Mi",
			"Fruit de la Gravité (Paramécia, Fujitora) : soulève le terrain, écrase tout autour de toi et fais tomber des météorites.",
			new String[] {"Plateforme gravitationnelle", "Écrasement gravitationnel", "Météorite gravitationnelle"},
			new int[] {300, 400, 900}, // 15 s (après l'atterrissage), 20 s, 45 s (après le lancement) ; réglages : config/minepiece-zushi.json
			false
	),
	GORO_GORO_NO_MI(
			"Goro Goro no Mi",
			"Fruit de la Foudre (Logia, Ener) : deviens la foudre, invoque des orages et réanime-toi une fois par vie (Défibrillateur).",
			new String[] {"Sango", "El Thor", "Raigo"},
			new int[] {120, 500, 1500}, // 6 s (après le lancer), 25 s, 75 s (après le lancement) ; réglages : config/minepiece-goro.json
			true
	),
	ISHI_ISHI_NO_MI(
			"Ishi Ishi no Mi",
			"Fruit de la Pierre (Paramécia, Pica) : fais surgir des pics du sol, change ton bras en poing de pierre et fusionne avec la roche pour devenir un titan.",
			new String[] {"Éruption rocheuse", "Poing colossal", "Titan de pierre"},
			new int[] {140, 260, 1200}, // 7 s, 13 s, 60 s (après l'effondrement du titan) ; réglages : config/minepiece-ishi.json
			false
	);

	public static final int ABILITY_COUNT = 3;
	public static final Codec<DevilFruit> CODEC = EnumCodecs.of(DevilFruit.class);
	public static final StreamCodec<ByteBuf, DevilFruit> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	private final String displayName;
	private final String description;
	private final String[] abilityNames;
	private final int[] cooldowns;
	private final boolean logia;

	DevilFruit(String displayName, String description, String[] abilityNames, int[] cooldowns, boolean logia) {
		this.displayName = displayName;
		this.description = description;
		this.abilityNames = abilityNames;
		this.cooldowns = cooldowns;
		this.logia = logia;
	}

	public String displayName() { return displayName; }
	public String description() { return description; }
	public String abilityName(int slot) { return abilityNames[slot]; }
	public int cooldownTicks(int slot) { return cooldowns[slot]; }
	public boolean isLogia() { return logia; }
}
