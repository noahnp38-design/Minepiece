package minepiece.noah.crew;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import minepiece.noah.MinePiece;
import minepiece.noah.data.Faction;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import minepiece.noah.network.CrewActionPayload;
import minepiece.noah.network.CrewMessagePayload;
import minepiece.noah.network.CrewSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Équipages : création, nom, logo, invitations, membres. Tout est décidé ici (le client ne fait qu'afficher et demander).
 * Sauvegardé dans le dossier du monde : minepiece_crews.json. Les invitations en attente ne sont pas sauvegardées.
 */
public final class CrewManager {
	public static final int MAX_MEMBERS = 16;
	public static final int NAME_MIN = 3;
	public static final int NAME_MAX = 24;

	private static final Map<UUID, Crew> CREWS = new HashMap<>();
	private static final Map<UUID, UUID> BY_PLAYER = new HashMap<>();
	/** Joueur invité -> équipages qui l'ont invité. */
	private static final Map<UUID, LinkedHashSet<UUID>> INVITES = new HashMap<>();
	private static MinecraftServer server;

	private CrewManager() {}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			load();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			save();
			CREWS.clear();
			BY_PLAYER.clear();
			INVITES.clear();
			server = null;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			ServerPlayer player = handler.getPlayer();
			Crew crew = crewOf(player.getUUID());
			if (crew != null) {
				String name = player.getName().getString();
				if (!name.equals(crew.members.get(player.getUUID()))) {
					crew.members.put(player.getUUID(), name);
					save();
				}
			}
			sync(player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> INVITES.remove(handler.getPlayer().getUUID()));
	}

	/** Seuls les pirates, la Marine et les révolutionnaires forment des groupes. */
	public static boolean allowed(Faction faction) {
		return faction == Faction.PIRATE || faction == Faction.MARINE || faction == Faction.REVOLUTIONARY;
	}

	public static Crew crewOf(UUID player) {
		UUID id = BY_PLAYER.get(player);
		return id == null ? null : CREWS.get(id);
	}

	private static Faction factionOf(ServerPlayer player) {
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
		return profile == null ? null : profile.faction();
	}

	// ------------------------------------------------------------------ actions du client

	public static void handle(ServerPlayer player, CrewActionPayload a) {
		if (server == null) {
			return;
		}
		switch (a.action()) {
			case CrewActionPayload.CREATE -> create(player, a.text(), a.logo());
			case CrewActionPayload.EDIT -> edit(player, a.text(), a.logo());
			case CrewActionPayload.INVITE -> invite(player, a.text());
			case CrewActionPayload.ACCEPT -> accept(player, a.text());
			case CrewActionPayload.DECLINE -> decline(player, a.text());
			case CrewActionPayload.LEAVE -> leave(player);
			case CrewActionPayload.KICK -> kick(player, a.text());
			case CrewActionPayload.DISBAND -> disband(player);
			case CrewActionPayload.REFRESH -> sync(player);
			default -> { }
		}
	}

	private static void create(ServerPlayer player, String rawName, byte[] logo) {
		Faction faction = factionOf(player);
		if (faction == null || !allowed(faction)) {
			fail(player, "Seuls les pirates, la Marine et les révolutionnaires peuvent former un équipage.");
			return;
		}
		if (crewOf(player.getUUID()) != null) {
			fail(player, "Tu fais déjà partie d'un équipage.");
			return;
		}
		String name = cleanName(rawName);
		String error = checkName(name, null);
		if (error != null) {
			fail(player, error);
			return;
		}
		if (!CrewLogo.valid(logo)) {
			fail(player, "Logo invalide.");
			return;
		}
		Crew crew = new Crew(UUID.randomUUID(), name, faction, player.getUUID(), logo.clone());
		crew.members.put(player.getUUID(), player.getName().getString());
		CREWS.put(crew.id, crew);
		BY_PLAYER.put(player.getUUID(), crew.id);
		INVITES.remove(player.getUUID());
		save();
		player.sendSystemMessage(Component.literal("§6" + Crew.kind(faction) + " §e" + name + " §6créé ! Tu en es le capitaine."));
		player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
		sync(player);
		ok(player, Crew.kind(faction) + " " + name + " créé !");
	}

	private static void edit(ServerPlayer player, String rawName, byte[] logo) {
		Crew crew = crewOf(player.getUUID());
		if (crew == null || !crew.captain.equals(player.getUUID())) {
			fail(player, "Seul le capitaine peut modifier l'équipage.");
			return;
		}
		String name = cleanName(rawName);
		String error = checkName(name, crew);
		if (error != null) {
			fail(player, error);
			return;
		}
		if (!CrewLogo.valid(logo)) {
			fail(player, "Logo invalide.");
			return;
		}
		boolean renamed = !name.equals(crew.name);
		crew.name = name;
		crew.logo = logo.clone();
		save();
		if (renamed) {
			broadcast(crew, "§6Ton équipage s'appelle désormais §e" + name + "§6.");
		} else {
			player.sendSystemMessage(Component.literal("§6Logo de l'équipage mis à jour."), true);
		}
		syncCrew(crew);
		ok(player, "Équipage mis à jour.");
	}

	private static void invite(ServerPlayer player, String targetName) {
		Crew crew = crewOf(player.getUUID());
		if (crew == null || !crew.captain.equals(player.getUUID())) {
			fail(player, "Seul le capitaine peut inviter.");
			return;
		}
		if (crew.members.size() >= MAX_MEMBERS) {
			fail(player, "L'équipage est complet (" + MAX_MEMBERS + " membres).");
			return;
		}
		ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
		if (target == null || target == player) {
			fail(player, "Joueur introuvable.");
			return;
		}
		if (factionOf(target) != crew.faction) {
			fail(player, target.getName().getString() + " n'est pas de ta faction.");
			return;
		}
		if (crewOf(target.getUUID()) != null) {
			fail(player, target.getName().getString() + " a déjà un équipage.");
			return;
		}
		INVITES.computeIfAbsent(target.getUUID(), id -> new LinkedHashSet<>()).add(crew.id);
		player.sendSystemMessage(Component.literal("§6Invitation envoyée à §e" + target.getName().getString() + "§6."), true);
		target.sendSystemMessage(Component.literal("§6" + player.getName().getString() + " t'invite à rejoindre §e" + crew.name
				+ "§6. Ouvre ton profil, onglet Équipage, pour accepter ou refuser."));
		target.level().playSound(null, target.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
		sync(target);
		sync(player);
	}

	private static void accept(ServerPlayer player, String crewId) {
		UUID id = parse(crewId);
		Set<UUID> mine = INVITES.get(player.getUUID());
		if (id == null || mine == null || !mine.contains(id)) {
			fail(player, "Cette invitation n'existe plus.");
			sync(player);
			return;
		}
		Crew crew = CREWS.get(id);
		if (crew == null) {
			mine.remove(id);
			fail(player, "Cet équipage n'existe plus.");
			sync(player);
			return;
		}
		if (crewOf(player.getUUID()) != null) {
			fail(player, "Tu fais déjà partie d'un équipage.");
			return;
		}
		if (factionOf(player) != crew.faction) {
			fail(player, "Tu n'es pas de la même faction que cet équipage.");
			return;
		}
		if (crew.members.size() >= MAX_MEMBERS) {
			fail(player, "L'équipage est complet.");
			return;
		}
		INVITES.remove(player.getUUID());
		crew.members.put(player.getUUID(), player.getName().getString());
		BY_PLAYER.put(player.getUUID(), crew.id);
		save();
		broadcast(crew, "§e" + player.getName().getString() + " §6a rejoint l'équipage !");
		syncCrew(crew);
	}

	private static void decline(ServerPlayer player, String crewId) {
		UUID id = parse(crewId);
		Set<UUID> mine = INVITES.get(player.getUUID());
		if (id != null && mine != null) {
			mine.remove(id);
			Crew crew = CREWS.get(id);
			if (crew != null) {
				ServerPlayer captain = server.getPlayerList().getPlayer(crew.captain);
				if (captain != null) {
					captain.sendSystemMessage(Component.literal("§7" + player.getName().getString() + " a refusé l'invitation."), true);
				}
			}
		}
		sync(player);
	}

	private static void leave(ServerPlayer player) {
		Crew crew = crewOf(player.getUUID());
		if (crew == null) {
			return;
		}
		removeMember(crew, player.getUUID());
		player.sendSystemMessage(Component.literal("§7Tu as quitté " + crew.name + "."));
		sync(player);
	}

	private static void kick(ServerPlayer player, String targetName) {
		Crew crew = crewOf(player.getUUID());
		if (crew == null || !crew.captain.equals(player.getUUID())) {
			fail(player, "Seul le capitaine peut exclure un membre.");
			return;
		}
		UUID target = null;
		for (Map.Entry<UUID, String> e : crew.members.entrySet()) {
			if (e.getValue().equalsIgnoreCase(targetName) && !e.getKey().equals(crew.captain)) {
				target = e.getKey();
			}
		}
		if (target == null) {
			fail(player, "Membre introuvable.");
			return;
		}
		String name = crew.members.get(target);
		removeMember(crew, target);
		ServerPlayer kicked = server.getPlayerList().getPlayer(target);
		if (kicked != null) {
			kicked.sendSystemMessage(Component.literal("§cTu as été exclu de " + crew.name + "."));
			sync(kicked);
		}
		broadcast(crew, "§7" + name + " a été exclu de l'équipage.");
	}

	private static void disband(ServerPlayer player) {
		Crew crew = crewOf(player.getUUID());
		if (crew == null || !crew.captain.equals(player.getUUID())) {
			fail(player, "Seul le capitaine peut dissoudre l'équipage.");
			return;
		}
		broadcast(crew, "§c" + crew.name + " a été dissous par son capitaine.");
		List<UUID> members = new ArrayList<>(crew.members.keySet());
		deleteCrew(crew);
		for (UUID m : members) {
			ServerPlayer p = server.getPlayerList().getPlayer(m);
			if (p != null) {
				sync(p);
			}
		}
	}

	/** Retire un membre ; si c'était le capitaine, le plus ancien prend la relève ; équipage vide = supprimé. */
	private static void removeMember(Crew crew, UUID member) {
		crew.members.remove(member);
		BY_PLAYER.remove(member);
		if (crew.members.isEmpty()) {
			deleteCrew(crew);
			return;
		}
		if (crew.captain.equals(member)) {
			crew.captain = crew.members.keySet().iterator().next();
			broadcast(crew, "§e" + crew.captainName() + " §6est le nouveau capitaine.");
		}
		save();
		syncCrew(crew);
	}

	private static void deleteCrew(Crew crew) {
		CREWS.remove(crew.id);
		for (UUID m : crew.members.keySet()) {
			BY_PLAYER.remove(m);
		}
		for (Set<UUID> s : INVITES.values()) {
			s.remove(crew.id);
		}
		save();
	}

	// ------------------------------------------------------------------ nom

	private static String cleanName(String raw) {
		return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
	}

	/** @return le message d'erreur, ou null si le nom est valide. */
	private static String checkName(String name, Crew self) {
		if (name.length() < NAME_MIN || name.length() > NAME_MAX) {
			return "Le nom doit faire entre " + NAME_MIN + " et " + NAME_MAX + " caractères.";
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!(Character.isLetterOrDigit(c) || " '-_.!".indexOf(c) >= 0)) {
				return "Caractère interdit dans le nom : " + c;
			}
		}
		String lower = name.toLowerCase(Locale.ROOT);
		for (Crew c : CREWS.values()) {
			if (c != self && c.name.toLowerCase(Locale.ROOT).equals(lower)) {
				return "Ce nom est déjà pris.";
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ synchronisation

	private static void syncCrew(Crew crew) {
		for (UUID m : crew.members.keySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(m);
			if (p != null) {
				sync(p);
			}
		}
	}

	/** Envoie au joueur tout ce dont son onglet Équipage a besoin. */
	public static void sync(ServerPlayer player) {
		Faction faction = factionOf(player);
		boolean allowed = faction != null && allowed(faction);
		Crew crew = crewOf(player.getUUID());
		CrewSyncPayload.Info info = null;
		List<String> candidates = new ArrayList<>();
		if (crew != null) {
			List<String> members = new ArrayList<>();
			List<Boolean> online = new ArrayList<>();
			for (Map.Entry<UUID, String> e : crew.members.entrySet()) {
				members.add(e.getValue());
				online.add(server != null && server.getPlayerList().getPlayer(e.getKey()) != null);
			}
			boolean captain = crew.captain.equals(player.getUUID());
			info = new CrewSyncPayload.Info(crew.id.toString(), crew.name, crew.faction.ordinal(), crew.captainName(),
					captain, members, online, crew.logo);
			if (captain && server != null) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					if (p != player && factionOf(p) == crew.faction && crewOf(p.getUUID()) == null) {
						candidates.add(p.getName().getString());
					}
				}
			}
		}
		List<CrewSyncPayload.Invite> invites = new ArrayList<>();
		Set<UUID> mine = INVITES.get(player.getUUID());
		if (mine != null) {
			for (UUID id : mine) {
				Crew c = CREWS.get(id);
				if (c != null) {
					invites.add(new CrewSyncPayload.Invite(c.id.toString(), c.name, c.captainName(), c.logo));
				}
			}
		}
		ServerPlayNetworking.send(player, new CrewSyncPayload(allowed, info, invites, candidates));
	}

	private static void broadcast(Crew crew, String text) {
		if (server == null) {
			return;
		}
		for (UUID m : crew.members.keySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(m);
			if (p != null) {
				p.sendSystemMessage(Component.literal(text));
			}
		}
	}

	private static void fail(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal("§c" + text), true);
		ServerPlayNetworking.send(player, new CrewMessagePayload(text, false));
	}

	private static void ok(ServerPlayer player, String text) {
		ServerPlayNetworking.send(player, new CrewMessagePayload(text, true));
	}

	private static UUID parse(String s) {
		try {
			return UUID.fromString(s);
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ sauvegarde

	private static Path file() {
		return server == null ? null : server.getWorldPath(LevelResource.ROOT).resolve("minepiece_crews.json");
	}

	private static void load() {
		CREWS.clear();
		BY_PLAYER.clear();
		INVITES.clear();
		Path path = file();
		if (path == null || !Files.exists(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonArray arr = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("crews");
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				Faction faction = Faction.valueOf(o.get("faction").getAsString());
				Crew crew = new Crew(UUID.fromString(o.get("id").getAsString()), o.get("name").getAsString(), faction,
						UUID.fromString(o.get("captain").getAsString()), CrewLogo.fromHex(o.get("logo").getAsString()));
				Set<UUID> seen = new HashSet<>();
				for (JsonElement m : o.getAsJsonArray("members")) {
					JsonObject mo = m.getAsJsonObject();
					UUID uuid = UUID.fromString(mo.get("uuid").getAsString());
					if (BY_PLAYER.containsKey(uuid) || !seen.add(uuid)) {
						continue; // un joueur ne peut être que dans un seul équipage
					}
					crew.members.put(uuid, mo.get("name").getAsString());
				}
				if (crew.members.isEmpty()) {
					continue;
				}
				if (!crew.members.containsKey(crew.captain)) {
					crew.captain = crew.members.keySet().iterator().next();
				}
				CREWS.put(crew.id, crew);
				for (UUID m : crew.members.keySet()) {
					BY_PLAYER.put(m, crew.id);
				}
			}
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.error("Impossible de lire minepiece_crews.json : les équipages ne sont pas chargés.", e);
		}
	}

	private static void save() {
		Path path = file();
		if (path == null) {
			return;
		}
		JsonArray arr = new JsonArray();
		for (Crew c : CREWS.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("id", c.id.toString());
			o.addProperty("name", c.name);
			o.addProperty("faction", c.faction.name());
			o.addProperty("captain", c.captain.toString());
			o.addProperty("logo", CrewLogo.toHex(c.logo));
			JsonArray members = new JsonArray();
			for (Map.Entry<UUID, String> e : c.members.entrySet()) {
				JsonObject m = new JsonObject();
				m.addProperty("uuid", e.getKey().toString());
				m.addProperty("name", e.getValue());
				members.add(m);
			}
			o.add("members", members);
			arr.add(o);
		}
		JsonObject root = new JsonObject();
		root.add("crews", arr);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			Path tmp = path.resolveSibling("minepiece_crews.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				gson.toJson(root, writer);
			}
			Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			MinePiece.LOGGER.error("Impossible d'enregistrer les équipages.", e);
		}
	}
}
