package minepiece.noah.network;

import minepiece.noah.MinePiece;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur -> clients proches : une zone d'Écrasement gravitationnel (Zushi Zushi no Mi) commence (active = true) ou se
 * termine (active = false). Les petites particules de gravité qui tombent lentement dans la zone sont créées CÔTÉ CLIENT
 * (aucun paquet de particules par tick) et disparaissent dès la réception du "fin".
 */
public record GravityZonePayload(int id, double x, double y, double z, float radius, boolean active) implements CustomPacketPayload {
	public static final Type<GravityZonePayload> TYPE = new Type<>(MinePiece.id("gravity_zone"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GravityZonePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GravityZonePayload::id,
			ByteBufCodecs.DOUBLE, GravityZonePayload::x,
			ByteBufCodecs.DOUBLE, GravityZonePayload::y,
			ByteBufCodecs.DOUBLE, GravityZonePayload::z,
			ByteBufCodecs.FLOAT, GravityZonePayload::radius,
			ByteBufCodecs.BOOL, GravityZonePayload::active,
			GravityZonePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
