package minepiece.noah.network;

import java.util.List;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur -> client : le fruit gagnant est DÉJÀ tiré. Le client ne fait que dessiner une bande qui s'arrête dessus.
 * winner = indice dans DevilFruit.values(), seed = graine de la bande, weights = poids affichés (même ordre).
 */
public record FruitChestStartPayload(int winner, long seed, List<Integer> weights) implements CustomPacketPayload {
	public static final Type<FruitChestStartPayload> TYPE = new Type<>(MinePiece.id("fruit_chest_start"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FruitChestStartPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FruitChestStartPayload::winner,
			ByteBufCodecs.VAR_LONG, FruitChestStartPayload::seed,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), FruitChestStartPayload::weights,
			FruitChestStartPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
