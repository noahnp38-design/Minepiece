package minepiece.noah.ability.zushi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.MinePiece;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Modèle 3D temporaire du Zushi Zushi no Mi (plateforme, météorite, cercles, débris).
 *
 * C'est une entité vanilla minecraft:item_display qui affiche un modèle du mod (assets/minepiece/models/item/fx/zushi_*) :
 * - synchronisée toute seule avec tous les joueurs proches (multijoueur), mouvements interpolés côté client
 *   (teleport_duration) et animations d'échelle / de rotation interpolées (interpolation_duration) : fluide sans paquet
 *   supplémentaire ;
 * - aucune physique, aucune collision, aucune IA : quasiment gratuite côté serveur ;
 * - tag "minepiece_zushi" : supprimée à la fin de chaque capacité, à l'arrêt du serveur, et au chargement d'un chunk si
 *   une ancienne copie y était restée (crash) ; plafond de MAX modèles en même temps.
 */
public final class ZushiDisplay {
	public static final String TAG = "minepiece_zushi";
	private static final int MAX = 280;
	private static final Map<UUID, ZushiDisplay> ALL = new HashMap<>();
	private static boolean disabled = false;

	private final ServerLevel level;
	private final UUID id = UUID.randomUUID();
	private boolean removed;
	private double scale;
	private double ty;

	private ZushiDisplay(ServerLevel level, String model, Vec3 pos, float yaw, float pitch, double scale, double ty,
			int teleportTicks, float viewRange, boolean glow) {
		this(level, model, pos, yaw, pitch, scale, ty, teleportTicks, viewRange, glow, null);
	}

	private ZushiDisplay(ServerLevel level, String model, Vec3 pos, float yaw, float pitch, double scale, double ty,
			int teleportTicks, float viewRange, boolean glow, String customTransform) {
		this.level = level;
		this.scale = scale;
		this.ty = ty;
		if (disabled || ALL.size() >= MAX) {
			removed = true;
			return;
		}
		ALL.put(id, this);
		String head = model.startsWith("{")
				// bloc réel du terrain (block_display) : "model" contient déjà son état de bloc SNBT
				? "summon minecraft:block_display " + String.format(Locale.ROOT, "%.3f %.3f %.3f", pos.x, pos.y, pos.z)
						+ " {block_state:" + model + ",transformation:" + blockTransform(scale) + ","
				: "summon minecraft:item_display " + String.format(Locale.ROOT, "%.3f %.3f %.3f", pos.x, pos.y, pos.z)
						+ " {item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"" + MinePiece.MOD_ID + ":fx/" + model + "\"}},"
						+ "item_display:\"none\",transformation:" + (customTransform != null ? customTransform : transform(0, 0, 1, 0, scale, ty)) + ",";
		run(head + "billboard:\"fixed\",Rotation:" + String.format(Locale.ROOT, "[%.2ff,%.2ff]", yaw, pitch) + ","
				+ (glow ? "brightness:{sky:15,block:15}," : "")
				+ String.format(Locale.ROOT, "view_range:%.2ff,", viewRange)
				+ "shadow_radius:0f,shadow_strength:0f,"
				+ "teleport_duration:" + Math.max(0, Math.min(59, teleportTicks)) + ",interpolation_duration:0,"
				+ "Tags:[\"" + TAG + "\"],UUID:[I;" + uuidArray() + "]}", pos);
	}

	private String uuidArray() {
		long msb = id.getMostSignificantBits();
		long lsb = id.getLeastSignificantBits();
		return String.format(Locale.ROOT, "%d,%d,%d,%d", (int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb);
	}

	/** Bloc centré sur l'entité : sa rotation (yaw / pitch) le fait tourner sur lui-même. */
	private static String blockTransform(double scale) {
		double h = -scale / 2.0;
		return String.format(Locale.ROOT,
				"{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[%.4ff,%.4ff,%.4ff],translation:[%.4ff,%.4ff,%.4ff]}",
				scale, scale, scale, h, h, h);
	}

	/**
	 * @param model         nom du modèle (sans "fx/")
	 * @param scale         échelle uniforme (1 = 16 unités du modèle par bloc)
	 * @param ty            décalage vertical (blocs) appliqué après l'échelle
	 * @param teleportTicks durée de l'interpolation des déplacements (1 = mise à jour chaque tick)
	 * @param viewRange     distance d'affichage (multiplicateur vanilla : 1 = 64 blocs)
	 * @param glow          true = toujours pleinement éclairé (énergie, cercles), false = éclairage du monde (roches)
	 */
	public static ZushiDisplay create(ServerLevel level, String model, Vec3 pos, float yaw, float pitch, double scale, double ty,
			int teleportTicks, float viewRange, boolean glow) {
		return new ZushiDisplay(level, model, pos, yaw, pitch, scale, ty, teleportTicks, viewRange, glow);
	}

	/**
	 * Vrai bloc du terrain (block_display, garde la texture du bloc). Pour les débris projetés par la météorite.
	 * @param blockStateSnbt état du bloc au format SNBT (NbtUtils.writeBlockState(state).toString())
	 */
	public static ZushiDisplay createBlock(ServerLevel level, String blockStateSnbt, Vec3 pos, double scale, float viewRange) {
		return new ZushiDisplay(level, blockStateSnbt, pos, 0.0F, 0.0F, scale, 0.0, 1, viewRange, false);
	}

	/**
	 * Modèle étiré (échelle différente sur chaque axe), orienté par yaw / pitch : l'axe +Z du modèle pointe dans la
	 * direction (yaw, pitch). Sert aux segments d'éclair volumétriques du Goro Goro no Mi.
	 */
	public static ZushiDisplay createStretched(ServerLevel level, String model, Vec3 pos, float yaw, float pitch,
			double sx, double sy, double sz, int teleportTicks, float viewRange, boolean glow) {
		return new ZushiDisplay(level, model, pos, yaw, pitch, Math.max(sx, Math.max(sy, sz)), 0.0, teleportTicks, viewRange, glow,
				stretched(sx, sy, sz));
	}

	private static String stretched(double sx, double sy, double sz) {
		return String.format(Locale.ROOT,
				"{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[%.4ff,%.4ff,%.4ff],translation:[0f,0f,0f]}", sx, sy, sz);
	}

	/** Change l'échelle par axe (animée côté client sur "ticks" ticks). */
	public void setStretch(double sx, double sy, double sz, int ticks) {
		if (!isActive()) {
			return;
		}
		run("data merge entity " + id + " {transformation:" + stretched(sx, sy, sz)
				+ ",start_interpolation:0,interpolation_duration:" + Math.max(0, ticks) + "}", null);
	}

	/** Quaternion (axe normalisé, angle en degrés) -> transformation NBT. */
	private static String transform(double ax, double ay, double az, double angleDeg, double scale, double ty) {
		double half = Math.toRadians(angleDeg) / 2.0;
		double len = Math.sqrt(ax * ax + ay * ay + az * az);
		if (len < 1.0E-6) {
			ax = 0; ay = 1; az = 0; len = 1;
		}
		double s = Math.sin(half) / len;
		return String.format(Locale.ROOT,
				"{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],scale:[%.4ff,%.4ff,%.4ff],translation:[0f,%.4ff,0f]}",
				ax * s, ay * s, az * s, Math.cos(half), scale, scale, scale, ty);
	}

	public boolean isActive() {
		return !removed && !disabled;
	}

	public UUID id() {
		return id;
	}

	public double scale() {
		return scale;
	}

	/** L'entité elle-même (null si elle n'existe plus : chunk déchargé, supprimée...). */
	public Entity entity() {
		return isActive() ? level.getEntity(id) : null;
	}

	/** Déplace / oriente le modèle (interpolé côté client sur teleport_duration). */
	public void move(Vec3 pos, float yaw, float pitch) {
		Entity e = entity();
		if (e != null) {
			e.setPos(pos.x, pos.y, pos.z);
			e.setYRot(yaw);
			e.setXRot(pitch);
		}
	}

	/** Rotation (axe, angle en degrés), échelle et décalage vertical, animés côté client sur "ticks" ticks. */
	public void setTransform(double ax, double ay, double az, double angleDeg, double scale, double ty, int ticks) {
		if (!isActive()) {
			return;
		}
		this.scale = scale;
		this.ty = ty;
		run("data merge entity " + id + " {transformation:" + transform(ax, ay, az, angleDeg, scale, ty)
				+ ",start_interpolation:0,interpolation_duration:" + Math.max(0, ticks) + "}", null);
	}

	/** Échelle seule (garde la rotation à zéro). */
	public void scaleTo(double scale, double ty, int ticks) {
		setTransform(0, 1, 0, 0, scale, ty, ticks);
	}

	public void remove() {
		if (removed) {
			return;
		}
		removed = true;
		ALL.remove(id);
		Entity e = level.getEntity(id);
		if (e != null) {
			e.ejectPassengers();
			e.discard();
		}
	}

	public static void cleanupAll() {
		for (ZushiDisplay d : new ArrayList<>(ALL.values())) {
			d.remove();
		}
	}

	/** Vrai si cette entité est un modèle Zushi orphelin (resté dans un chunk après un arrêt brutal). */
	static boolean isStale(Entity entity) {
		return entity.entityTags().contains(TAG) && !ALL.containsKey(entity.getUUID());
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
			MinePiece.LOGGER.error("Modèles 3D du Zushi Zushi désactivés (commande refusée : {})", command, e);
		}
	}
}
