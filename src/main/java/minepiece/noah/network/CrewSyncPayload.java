package minepiece.noah.network;

import java.util.ArrayList;
import java.util.List;

import minepiece.noah.MinePiece;
import minepiece.noah.crew.CrewLogo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur -> client : tout ce que l'onglet Équipage affiche.
 * allowed = la faction du joueur peut former un équipage ; info = son équipage (null s'il n'en a pas) ;
 * invites = invitations reçues ; candidates = joueurs que le capitaine peut inviter.
 */
public record CrewSyncPayload(boolean allowed, Info info, List<Invite> invites, List<String> candidates)
		implements CustomPacketPayload {

	public record Info(String id, String name, int faction, String captain, boolean isCaptain,
			List<String> members, List<Boolean> online, byte[] logo) {}

	public record Invite(String id, String name, String captain, byte[] logo) {}

	public static final Type<CrewSyncPayload> TYPE = new Type<>(MinePiece.id("crew_sync"));
	public static final StreamCodec<FriendlyByteBuf, CrewSyncPayload> CODEC =
			CustomPacketPayload.codec(CrewSyncPayload::write, CrewSyncPayload::read);

	private static CrewSyncPayload read(FriendlyByteBuf buf) {
		boolean allowed = buf.readBoolean();
		Info info = null;
		if (buf.readBoolean()) {
			String id = buf.readUtf(64);
			String name = buf.readUtf(64);
			int faction = buf.readVarInt();
			String captain = buf.readUtf(64);
			boolean isCaptain = buf.readBoolean();
			int n = Math.min(64, buf.readVarInt());
			List<String> members = new ArrayList<>(n);
			List<Boolean> online = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				members.add(buf.readUtf(64));
				online.add(buf.readBoolean());
			}
			byte[] logo = buf.readByteArray(CrewLogo.CELLS);
			info = new Info(id, name, faction, captain, isCaptain, members, online, logo);
		}
		int ni = Math.min(64, buf.readVarInt());
		List<Invite> invites = new ArrayList<>(ni);
		for (int i = 0; i < ni; i++) {
			invites.add(new Invite(buf.readUtf(64), buf.readUtf(64), buf.readUtf(64), buf.readByteArray(CrewLogo.CELLS)));
		}
		int nc = Math.min(256, buf.readVarInt());
		List<String> candidates = new ArrayList<>(nc);
		for (int i = 0; i < nc; i++) {
			candidates.add(buf.readUtf(64));
		}
		return new CrewSyncPayload(allowed, info, invites, candidates);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBoolean(allowed);
		buf.writeBoolean(info != null);
		if (info != null) {
			buf.writeUtf(info.id(), 64);
			buf.writeUtf(info.name(), 64);
			buf.writeVarInt(info.faction());
			buf.writeUtf(info.captain(), 64);
			buf.writeBoolean(info.isCaptain());
			buf.writeVarInt(info.members().size());
			for (int i = 0; i < info.members().size(); i++) {
				buf.writeUtf(info.members().get(i), 64);
				buf.writeBoolean(info.online().get(i));
			}
			buf.writeByteArray(info.logo());
		}
		buf.writeVarInt(invites.size());
		for (Invite inv : invites) {
			buf.writeUtf(inv.id(), 64);
			buf.writeUtf(inv.name(), 64);
			buf.writeUtf(inv.captain(), 64);
			buf.writeByteArray(inv.logo());
		}
		buf.writeVarInt(candidates.size());
		for (String c : candidates) {
			buf.writeUtf(c, 64);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
