package minepiece.noah.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PlayerProfile(Race race, Faction faction, CombatStyle combatStyle) {
	public static final Codec<PlayerProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Race.CODEC.fieldOf("race").forGetter(PlayerProfile::race),
			Faction.CODEC.fieldOf("faction").forGetter(PlayerProfile::faction),
			CombatStyle.CODEC.fieldOf("combat_style").forGetter(PlayerProfile::combatStyle)
	).apply(instance, PlayerProfile::new));

	/** Sert à synchroniser le profil vers les clients (pour voir la race des autres joueurs). */
	public static final StreamCodec<ByteBuf, PlayerProfile> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
