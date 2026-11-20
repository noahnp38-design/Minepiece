package minepiece.noah.network;

import minepiece.noah.MinePiece;
import minepiece.noah.crew.CrewLogo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> serveur : une action sur les équipages. Le serveur revérifie tout. */
public record CrewActionPayload(int action, String text, byte[] logo) implements CustomPacketPayload {
	public static final int CREATE = 0;   // text = nom, logo
	public static final int EDIT = 1;     // text = nom, logo
	public static final int INVITE = 2;   // text = pseudo
	public static final int ACCEPT = 3;   // text = id de l'équipage
	public static final int DECLINE = 4;  // text = id de l'équipage
	public static final int LEAVE = 5;
	public static final int KICK = 6;     // text = pseudo
	public static final int DISBAND = 7;
	public static final int REFRESH = 8;

	public static final Type<CrewActionPayload> TYPE = new Type<>(MinePiece.id("crew_action"));
	public static final StreamCodec<FriendlyByteBuf, CrewActionPayload> CODEC =
			CustomPacketPayload.codec(CrewActionPayload::write, CrewActionPayload::read);

	public CrewActionPayload(int action, String text) {
		this(action, text, CrewLogo.blank());
	}

	public CrewActionPayload(int action) {
		this(action, "", CrewLogo.blank());
	}

	private static CrewActionPayload read(FriendlyByteBuf buf) {
		int action = buf.readVarInt();
		String text = buf.readUtf(64);
		byte[] logo = buf.readByteArray(CrewLogo.CELLS);
		return new CrewActionPayload(action, text, logo);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(action);
		buf.writeUtf(text == null ? "" : text, 64);
		buf.writeByteArray(logo == null ? CrewLogo.blank() : logo);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
