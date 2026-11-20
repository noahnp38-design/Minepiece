package minepiece.noah.crew;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.data.Faction;

/** Un équipage (pirates), une unité (Marine) ou une cellule (Révolutionnaires). Données serveur. */
public final class Crew {
	public final UUID id;
	public String name;
	public final Faction faction;
	public UUID captain;
	/** Membres dans l'ordre d'arrivée : UUID -> dernier pseudo connu. Le capitaine en fait partie. */
	public final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
	public byte[] logo;

	public Crew(UUID id, String name, Faction faction, UUID captain, byte[] logo) {
		this.id = id;
		this.name = name;
		this.faction = faction;
		this.captain = captain;
		this.logo = logo;
	}

	public boolean isMember(UUID player) {
		return members.containsKey(player);
	}

	public String captainName() {
		String n = members.get(captain);
		return n == null ? "?" : n;
	}

	/** Prochain capitaine si le capitaine part : le membre le plus ancien. */
	public UUID nextCaptain() {
		for (Map.Entry<UUID, String> e : members.entrySet()) {
			if (!e.getKey().equals(captain)) {
				return e.getKey();
			}
		}
		return null;
	}

	/** Nom du groupe selon la faction. */
	public static String kind(Faction faction) {
		return switch (faction) {
			case MARINE -> "Unité de la Marine";
			case REVOLUTIONARY -> "Cellule révolutionnaire";
			default -> "Équipage pirate";
		};
	}
}
