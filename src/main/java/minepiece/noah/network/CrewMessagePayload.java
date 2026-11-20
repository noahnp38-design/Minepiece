package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur -> client : réponse à une action d'équipage (ok = réussie), affichée dans l'éditeur et l'onglet Équipage. */
public record CrewMessagePayload(String text, boolean ok) implements CustomPacketPayload {
	public static final Type<CrewMessagePayload> TYPE = new Type<>(MinePiece.id("crew_message"));
	public static final StreamCodec<FriendlyByteBuf, CrewMessagePayload> CODEC =
			CustomPacketPayload.codec(CrewMessagePayload::write, CrewMessagePayload::read);

	private static CrewMessagePayload read(FriendlyByteBuf buf) {
		return new CrewMessagePayload(buf.readUtf(256), buf.readBoolean());
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(text, 256);
		buf.writeBoolean(ok);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
