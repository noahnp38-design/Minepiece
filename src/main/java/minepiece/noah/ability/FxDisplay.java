package minepiece.noah.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.MinePiece;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Modèle 3D d'effet (item_display vanilla) pour les attaques du Hie Hie no Mi, sur le même principe que SupaVisual :
 * synchronisé automatiquement en multijoueur, tag minepiece_fx, supprimé en fin d'attaque / à l'arrêt du serveur.
 * L'axe +Z du modèle regarde dans la direction (yaw, pitch), comme un joueur.
 */
final class FxDisplay {
	private static final String TAG = "minepiece_fx";
	private static final int MAX = 32;
	private static final Set<FxDisplay> ALL = new HashSet<>();
	private static boolean disabled = false;

	private final ServerLevel level;
	private final UUID id = UUID.randomUUID();
	private boolean removed = false;
	private final boolean active;

	private FxDisplay(ServerLevel level, String model, Vec3 pos, float yaw, float pitch,
			double sx, double sy, double sz, double ty, int teleportTicks) {
		this.level = level;
		if (disabled || ALL.size() >= MAX) {
			active = false;
			removed = true;
			return;
		}
		active = true;
		ALL.add(this);
		long msb = id.getMostSignificantBits();
		long lsb = id.getLeastSignificantBits();
		String uuid = String.format(Locale.ROOT, "%d,%d,%d,%d", (int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb);
		run("summon minecraft:item_display " + String.format(Locale.ROOT, "%.3f %.3f %.3f", pos.x, pos.y, pos.z)
				+ " {item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"" + MinePiece.MOD_ID + ":fx/" + model + "\"}},"
				+ "item_display:\"none\",billboard:\"fixed\",transformation:" + transform(sx, sy, sz, ty) + ","
				+ "Rotation:" + String.format(Locale.ROOT, "[%.2ff,%.2ff]", yaw, pitch) + ","
				+ "brightness:{sky:15,block:15},view_range:3f,shadow_radius:0f,shadow_strength:0f,"
				+ "teleport_duration:" + teleportTicks + ",interpolation_duration:0,"
				+ "Tags:[\"" + TAG + "\"],UUID:[I;" + uuid + "]}", pos);
	}

	/**
	 * @param model          nom du modèle (sans "fx/")
	 * @param sx,sy,sz       échelle du modèle (1 = 1 bloc pour 16 unités)
	 * @param ty             décalage vertical après mise à l'échelle (place le bas du modèle au sol)
	 * @param teleportTicks  durée de l'interpolation de position / rotation entre deux mises à jour (1 = chaque tick)
	 */
	static FxDisplay create(ServerLevel level, String model, Vec3 pos, float yaw, float pitch,
			double sx, double sy, double sz, double ty, int teleportTicks) {
		return new FxDisplay(level, model, pos, yaw, pitch, sx, sy, sz, ty, teleportTicks);
	}

	private static String transform(double sx, double sy, double sz, double ty) {
		return String.format(Locale.ROOT,
				"{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[%.4ff,%.4ff,%.4ff],translation:[0f,%.4ff,0f]}",
				sx, sy, sz, ty);
	}

	boolean isActive() {
		return active && !removed && !disabled;
	}

	/** Déplace / oriente la lame (interpolé côté client). */
	void move(Vec3 pos, float yaw, float pitch) {
		if (!isActive()) {
			return;
		}
		Entity e = level.getEntity(id);
		if (e != null) {
			e.setPos(pos.x, pos.y, pos.z);
			e.setYRot(yaw);
			e.setXRot(pitch);
		}
	}

	/** Anime l'échelle (apparition, disparition) : interpolation côté client sur "ticks" ticks. */
	void scaleTo(double sx, double sy, double sz, double ty, int ticks) {
		if (!isActive()) {
			return;
		}
		run("data merge entity " + id + " {transformation:" + transform(sx, sy, sz, ty)
				+ ",start_interpolation:0,interpolation_duration:" + ticks + "}", null);
	}

	/** Remplace le modèle affiché (ex. deux poses d'ailes en alternance). */
	void setModel(String model) {
		if (!isActive()) {
			return;
		}
		run("data merge entity " + id + " {item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\""
				+ MinePiece.MOD_ID + ":fx/" + model + "\"}}}", null);
	}

	void remove() {
		if (removed) {
			return;
		}
		removed = true;
		ALL.remove(this);
		Entity e = level.getEntity(id);
		if (e != null) {
			e.discard();
		}
	}

	static void cleanupAll() {
		for (FxDisplay v : new ArrayList<>(ALL)) {
			v.remove();
		}
	}

	private void run(String command, Vec3 at) {
		try {
			MinecraftServer server = level.getServer();
			CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
			if (at != null) {
				source = source.withPosition(at);
			}
			server.getCommands().performPrefixedCommand(source, command);
		} catch (Exception e) {
			disabled = true;
			MinePiece.LOGGER.error("Rendu 3D (FxDisplay) désactivé (commande refusée : {})", command, e);
		}
	}
}
