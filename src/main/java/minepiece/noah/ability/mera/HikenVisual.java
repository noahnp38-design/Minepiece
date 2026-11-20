package minepiece.noah.ability.mera;

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
 * Rendu du poing de feu géant du Hiken : un modèle Blockbench de l'utilisateur (poing_ardent.json : poing, flammes, traînée ; avant du modèle = +Z ; texture animée hiken_fist.png) affiché
 * par UNE entité item_display vanilla (échelle uniforme : 5 x le rayon, soit un poing d'environ 3 blocs et ~10 blocs au total). Même principe que
 * FireGunVisual : synchronisé automatiquement en multijoueur, animé par Minecraft, suivi par
 * setPos sans commande, supprimé à l'impact / fin de vie / arrêt du serveur (tag minepiece_fx).
 * Purement visuel : dégâts, hitbox, vitesse et durée restent dans FireProjectile / MeraConfig.
 */
final class HikenVisual {
	/** Même tag que FireballVisual : le nettoyage au démarrage (killStray) supprime aussi ces entités. */
	private static final String TAG = "minepiece_fx";
	/** Longueur du sprite, en rayons de la balle (rayon 0,4 : sprite de 2,8 blocs dont la tête fait environ 0,8). */
	private static final double LENGTH_PER_RADIUS = 5.0;
	/** Hauteur / longueur du sprite (frames de 256 x 136). */
	private static final double ASPECT = 1.0;
	/** La tête brillante est à 70 % de la longueur du sprite : on décale pour qu'elle soit sur la position de la balle. */
	private static final double HEAD_OFFSET = 0.06;
	/** Mettre à true si, en jeu, la tête du projectile pointe vers l'arrière (inversion du sprite). */
	private static final boolean FLIP_HEAD = false;
	/** Nombre maximal de balles affichées en même temps ; au-delà, anciennes particules. */
	private static final int MAX_SPRITES = 24;

	private static final Set<HikenVisual> ALL = new HashSet<>();
	/** Passe à true si une commande échoue : on arrête de créer des entités (les particules continuent). */
	private static boolean disabled = false;

	private final ServerLevel level;
	private final UUID id = UUID.randomUUID();
	private final Vec3 dir;
	private final Vec3 velocity;
	private final double length;
	private boolean removed = false;

	private HikenVisual(ServerLevel level, Vec3 velocity, double radius) {
		this.level = level;
		this.velocity = velocity;
		this.dir = velocity.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : velocity.normalize();
		this.length = radius * LENGTH_PER_RADIUS;
	}

	/** Crée le sprite au lancement de la balle. Si ce n'est pas possible, le résultat renvoie false à update(). */
	static HikenVisual create(ServerLevel level, Vec3 pos, Vec3 velocity, double radius) {
		HikenVisual v = new HikenVisual(level, velocity, radius);
		if (disabled || velocity.lengthSqr() < 1.0E-6 || ALL.size() >= MAX_SPRITES) {
			v.removed = true;
			return v;
		}
		ALL.add(v);
		v.summon(pos);
		if (disabled) {
			v.remove();
		}
		return v;
	}

	/** Suit la balle (appelé à chaque tick, sans commande). @return false si la balle n'a pas de sprite (particules de secours). */
	boolean update(Vec3 pos) {
		if (removed || disabled) {
			return false;
		}
		Entity e = level.getEntity(id);
		if (e != null) {
			Vec3 p = anchor(pos);
			e.setPos(p.x, p.y, p.z);
		}
		return true;
	}

	/** Supprime le sprite (impact, fin de vie, chunk déchargé). Peut être appelé plusieurs fois. */
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

	/** Arrêt du serveur : rien ne doit rester dans le monde sauvegardé. */
	static void cleanupAll() {
		for (HikenVisual v : new ArrayList<>(ALL)) {
			v.remove();
		}
	}

	/**
	 * Centre du sprite : la tête est ramenée sur la position de la balle, et on avance d'un tick de vitesse pour
	 * compenser l'interpolation du client (teleport_duration:1 : l'entité affichée a un tick de retard).
	 */
	private Vec3 anchor(Vec3 pos) {
		return pos.subtract(dir.scale(HEAD_OFFSET * length)).add(velocity);
	}

	private void summon(Vec3 pos) {
		long msb = id.getMostSignificantBits();
		long lsb = id.getLeastSignificantBits();
		String uuid = String.format(Locale.ROOT, "%d,%d,%d,%d", (int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb);

		// L'item_display tourne le modèle d'un demi-tour autour de Y : le sprite (tête vers +X) est donc aligné sur -dir.
		double[] q = fromToZ(FLIP_HEAD ? dir : dir.scale(-1.0));
		double sx = length;
		double sy = length * ASPECT;
		String transformation = String.format(Locale.ROOT,
				"{left_rotation:[%.4ff,%.4ff,%.4ff,%.4ff],right_rotation:[0f,0f,0f,1f],"
						+ "scale:[%.4ff,%.4ff,%.4ff],translation:[0f,0f,0f]}",
				q[0], q[1], q[2], q[3], sx, sy, sy);

		Vec3 at = anchor(pos);
		String coords = String.format(Locale.ROOT, "%.3f %.3f %.3f", at.x, at.y, at.z);
		run("summon minecraft:item_display " + coords + " {item:{id:\"minecraft:paper\",count:1,components:"
				+ "{\"minecraft:item_model\":\"" + MinePiece.MOD_ID + ":fx/hiken_fist\"}},"
				+ "item_display:\"none\",billboard:\"fixed\",transformation:" + transformation + ","
				+ "brightness:{sky:15,block:15},view_range:1f,shadow_radius:0f,shadow_strength:0f,"
				+ "teleport_duration:1,Tags:[\"" + TAG + "\"],UUID:[I;" + uuid + "]}", at);
	}

	private void run(String command, Vec3 at) {
		if (disabled) {
			return;
		}
		try {
			MinecraftServer server = level.getServer();
			CommandSourceStack source = server.createCommandSourceStack()
					.withLevel(level).withPosition(at).withSuppressedOutput();
			server.getCommands().performPrefixedCommand(source, command);
		} catch (Exception e) {
			disabled = true;
			MinePiece.LOGGER.error("Rendu 3D du Hiken désactivé (commande refusée : {})", command, e);
		}
	}

	/** Quaternion qui amène l'axe +Z (avant du modèle poing_ardent) sur la direction donnée. */
	private static double[] fromToZ(Vec3 d) {
		double dot = d.z;
		if (dot < -0.9999) {
			return new double[] {1.0, 0.0, 0.0, 0.0}; // demi-tour autour de X
		}
		// axe = (0,0,1) x d = (-d.y, d.x, 0)
		double x = -d.y;
		double y = d.x;
		double w = 1.0 + dot;
		double len = Math.sqrt(x * x + y * y + w * w);
		return new double[] {x / len, y / len, 0.0, w / len};
	}

	/** Quaternion qui amène l'axe +X sur la direction donnée (non utilisé : le modèle regarde vers +Z). */
	private static double[] fromTo(Vec3 d) {
		double dot = d.x;
		if (dot < -0.9999) {
			return new double[] {0.0, 1.0, 0.0, 0.0}; // demi-tour autour de Y
		}
		// axe = (1,0,0) x d = (0, -d.z, d.y)
		double x = 0.0;
		double y = -d.z;
		double z = d.y;
		double w = 1.0 + dot;
		double len = Math.sqrt(x * x + y * y + z * z + w * w);
		return new double[] {x / len, y / len, z / len, w / len};
	}
}
