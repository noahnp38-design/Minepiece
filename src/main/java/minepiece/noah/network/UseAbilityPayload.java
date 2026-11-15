package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> serveur : touche de l'attaque n° slot pressée (pressed = true) ou relâchée (false). */
public record UseAbilityPayload(int slot, boolean pressed) implements CustomPacketPayload {
	public static final Type<UseAbilityPayload> TYPE = new Type<>(MinePiece.id("use_ability"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UseAbilityPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, UseAbilityPayload::slot,
			ByteBufCodecs.BOOL, UseAbilityPayload::pressed,
			UseAbilityPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
