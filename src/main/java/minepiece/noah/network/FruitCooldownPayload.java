package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur -> client : recharge de l'attaque n° slot (0..2) du fruit du démon (temps restant et durée totale, en ticks). */
public record FruitCooldownPayload(int slot, int remainingTicks, int totalTicks) implements CustomPacketPayload {
	public static final Type<FruitCooldownPayload> TYPE = new Type<>(MinePiece.id("fruit_cooldown"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FruitCooldownPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FruitCooldownPayload::slot,
			ByteBufCodecs.VAR_INT, FruitCooldownPayload::remainingTicks,
			ByteBufCodecs.VAR_INT, FruitCooldownPayload::totalTicks,
			FruitCooldownPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
