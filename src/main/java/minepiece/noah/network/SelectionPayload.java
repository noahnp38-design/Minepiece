package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> serveur : les choix du joueur (index des enums). */
public record SelectionPayload(int race, int faction, int combatStyle) implements CustomPacketPayload {
	public static final Type<SelectionPayload> TYPE = new Type<>(MinePiece.id("selection"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SelectionPayload::race,
			ByteBufCodecs.VAR_INT, SelectionPayload::faction,
			ByteBufCodecs.VAR_INT, SelectionPayload::combatStyle,
			SelectionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
