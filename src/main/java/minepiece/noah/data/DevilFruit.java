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
			new String[] {"Pheasant Beak", "Ice Time", "Ice Age"},
			new int[] {60, 200, 600}, // temps de recharge en ticks (20 ticks = 1 s) ; Ice Age : maintenir pour charger
			true // Logia : invulnérable aux attaques physiques
	),
	MERA_MERA_NO_MI(
			"Mera Mera no Mi",
			"Fruit du Feu : ton corps est de flammes, immunisé au feu et aux attaques physiques.",
			new String[] {"Fire Gun", "Hiken", "Dai Enkai : Entei"},
			new int[] {30, 160, 900},
			true
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
