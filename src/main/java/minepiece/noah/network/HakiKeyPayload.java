package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> serveur : touche de Haki. action : 0 Armement (J), 1 Observation (K), 2 Rois (L), 3 esquive (R).
 * dir (esquive) : 0 gauche, 1 droite, 2 arrière, 3 avant. Maj (accroupi) = technique avancée : le serveur le lit lui-même.
 */
public record HakiKeyPayload(int action, int dir) implements CustomPacketPayload {
	public static final Type<HakiKeyPayload> TYPE = new Type<>(MinePiece.id("haki_key"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HakiKeyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HakiKeyPayload::action,
			ByteBufCodecs.VAR_INT, HakiKeyPayload::dir,
			HakiKeyPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
