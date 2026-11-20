package minepiece.noah.ability.bomu;

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
 * Bombe 3D (sphère de voxels violet-noir aux fissures orange qui scintillent, avec une mèche dorée) : UNE entité
 * item_display vanilla par bombe (modèle fx/bomu_bomb), comme les VFX du Mera. Suit la bombe par setPos (aucune commande
 * après la création), supprimée à l'explosion / à l'arrêt du serveur (tag minepiece_fx). Au-delà de MAX bombes affichées
 * ou si une commande échoue, il ne reste que les particules.
 */
final class BomuVisual {
	private static final String TAG = "minepiece_fx";
	private static final int MAX = 40;
	private static final Set<BomuVisual> ALL = new HashSet<>();
	private static boolean disabled = false;

	private final ServerLevel level;
	private final UUID id = UUID.randomUUID();
	private boolean removed = false;
	private final boolean active;

	private BomuVisual(ServerLevel level, Vec3 pos, double scale) {
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
		double yaw = Math.random() * Math.PI * 2.0;
		String transformation = String.format(Locale.ROOT,
				"{left_rotation:[0f,%.4ff,0f,%.4ff],right_rotation:[0f,0f,0f,1f],scale:[%.3ff,%.3ff,%.3ff],translation:[0f,0.12f,0f]}",
				Math.sin(yaw / 2.0), Math.cos(yaw / 2.0), scale, scale, scale);
		run("summon minecraft:item_display " + String.format(Locale.ROOT, "%.3f %.3f %.3f", pos.x, pos.y, pos.z)
				+ " {item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"" + MinePiece.MOD_ID + ":fx/bomu_bomb\"}},"
				+ "item_display:\"none\",billboard:\"fixed\",transformation:" + transformation + ","
				+ "brightness:{sky:15,block:15},view_range:2f,shadow_radius:0f,shadow_strength:0f,"
				+ "teleport_duration:1,Tags:[\"" + TAG + "\"],UUID:[I;" + uuid + "]}", pos);
	}

	static BomuVisual create(ServerLevel level, Vec3 pos, double scale) {
		return new BomuVisual(level, pos, scale);
	}

	/** Suit la bombe. @return false si la bombe n'a pas de modèle 3D (le rendu se limite alors aux particules). */
	boolean move(Vec3 pos) {
		if (!active || removed || disabled) {
			return false;
		}
		Entity e = level.getEntity(id);
		if (e != null) {
			e.setPos(pos.x, pos.y, pos.z);
		}
		return true;
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
		for (BomuVisual v : new ArrayList<>(ALL)) {
			v.remove();
		}
	}

	private void run(String command, Vec3 at) {
		try {
			MinecraftServer server = level.getServer();
			CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPosition(at).withSuppressedOutput();
			server.getCommands().performPrefixedCommand(source, command);
		} catch (Exception e) {
			disabled = true;
			MinePiece.LOGGER.error("Rendu 3D du Bomu Bomu désactivé (commande refusée : {})", command, e);
		}
	}
}
