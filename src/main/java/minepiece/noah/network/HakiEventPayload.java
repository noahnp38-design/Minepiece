package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur -> client : effet d'écran de Haki. kind : 0 esquive parfaite (Vision du futur : léger "ralenti" visuel),
 * 1 pression du Haki des Rois subie, 2 clash de Haki des Rois tout proche.
 */
public record HakiEventPayload(int kind, int strength) implements CustomPacketPayload {
	public static final int PERFECT_DODGE = 0;
	public static final int PRESSURE = 1;
	public static final int CLASH = 2;

	public static final Type<HakiEventPayload> TYPE = new Type<>(MinePiece.id("haki_event"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HakiEventPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HakiEventPayload::kind,
			ByteBufCodecs.VAR_INT, HakiEventPayload::strength,
			HakiEventPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
