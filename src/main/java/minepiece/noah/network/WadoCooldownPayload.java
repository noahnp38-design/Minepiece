package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Serveur -> client : recharge d'une competence de la Wado Ichimonji (skill 0 = Shishi Sonson, 1 = 90 Cleasher Slash). */
public record WadoCooldownPayload(int skill, int remainingTicks, int totalTicks) implements CustomPacketPayload {
	public static final Type<WadoCooldownPayload> TYPE = new Type<>(MinePiece.id("wado_cooldown"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WadoCooldownPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WadoCooldownPayload::skill,
			ByteBufCodecs.VAR_INT, WadoCooldownPayload::remainingTicks,
			ByteBufCodecs.VAR_INT, WadoCooldownPayload::totalTicks,
			WadoCooldownPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
