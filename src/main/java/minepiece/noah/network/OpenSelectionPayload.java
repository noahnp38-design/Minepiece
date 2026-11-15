package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur -> client : "ouvre la fenêtre de choix". */
public record OpenSelectionPayload() implements CustomPacketPayload {
	public static final Type<OpenSelectionPayload> TYPE = new Type<>(MinePiece.id("open_selection"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenSelectionPayload> CODEC =
			StreamCodec.unit(new OpenSelectionPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
