package minepiece.noah.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Niveau du joueur : le niveau, et l'XP accumulée DANS ce niveau (l'excédent est conservé au passage au niveau suivant).
 * L'XP nécessaire pour monter se calcule avec {@link minepiece.noah.level.LevelSystem#xpToNext(int)}.
 */
public record PlayerLevel(int level, long xp) {
	public static final PlayerLevel START = new PlayerLevel(1, 0L);

	public static final Codec<PlayerLevel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 1).forGetter(PlayerLevel::level),
			Codec.LONG.optionalFieldOf("xp", 0L).forGetter(PlayerLevel::xp)
	).apply(instance, PlayerLevel::new));

	/** Synchronisation serveur -> client (le client ne peut jamais modifier ses données). */
	public static final StreamCodec<ByteBuf, PlayerLevel> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PlayerLevel::level,
			ByteBufCodecs.VAR_LONG, PlayerLevel::xp,
			PlayerLevel::new
	);

	public PlayerLevel {
		level = Math.max(1, level);
		xp = Math.max(0L, xp);
	}
}
