package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur -> client : tremblement de caméra (impacts du Zushi Zushi no Mi). Nom historique conservé. */
public record WadoShakePayload(int ticks, int intensity) implements CustomPacketPayload {
	public static final Type<WadoShakePayload> TYPE = new Type<>(MinePiece.id("wado_shake"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WadoShakePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WadoShakePayload::ticks,
			ByteBufCodecs.VAR_INT, WadoShakePayload::intensity,
			WadoShakePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
