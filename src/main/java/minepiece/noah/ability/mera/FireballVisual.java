package minepiece.noah.ability.mera;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.MinePiece;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Boule de feu géante en 3D, faite d'entités d'affichage vanilla (block_display / item_display) : aucun rendu client
 * à écrire, synchronisée automatiquement en multijoueur, animée avec l'interpolation native de Minecraft.
 *
 * Couches :
 *  - petit noyau blanc lumineux au centre (deux cubes qui tournent, il "flashe" au lancement) ;
 *  - sprite de boule de feu (textures/item/fx/fireball.png, 512x512) qui tourne sur lui-même et fait toujours face au joueur ;
 *  - 6 langues de flammes animées (fx/flame.png) qui orbitent autour de la boule ;
 *  - 2 anneaux de feu (fx/ring_a, fx/ring_b) qui tournent, inclinés ;
 *  - au lancement : traînée de comète (fx/comet) derrière la boule ;
 *  - à l'impact : étoile d'explosion (fx/explosion) et anneaux de choc qui s'étendent.
 * Tout est affiché à pleine luminosité, vibre et accélère avec la charge, et un bloc de lumière invisible éclaire autour.
 *
 * C'est purement visuel : les dégâts, hitboxes et recharges restent dans MeraAbilities / FireProjectile.
 * Si une commande échoue (changement de version...), les entités sont désactivées et il ne reste que les particules.
 */
final class FireballVisual implements ActiveEffect {
	// ---------------------------------------------------------------- réglages (à ajuster à ton goût)
	/** Éclairer l'environnement avec un bloc de lumière qui suit la boule. */
	private static final boolean DYNAMIC_LIGHT = true;
	/** Fréquence (en ticks) d'envoi des nouvelles positions de rotation / taille. Plus petit = plus fluide, plus coûteux. */
	private static final int UPDATE_EVERY = 2;
	/** Amplitude de la vibration (fraction du rayon) au repos et à pleine charge. */
	private static final double SHAKE_MIN = 0.012;
	private static final double SHAKE_MAX = 0.04;
	/** Tag posé sur toutes les entités du mod, pour pouvoir les nettoyer. */
	private static final String TAG = "minepiece_fx";

	private static final Set<FireballVisual> ALL = new HashSet<>();
	/** Passe à true si une commande échoue : on arrête de créer des entités (les particules continuent). */
	private static boolean disabled = false;

	// ---------------------------------------------------------------- description des couches
	private enum Kind {
		/** Cube qui tourne sur lui-même (block_display). */
		CUBE,
		/** Sprite qui fait toujours face au joueur et tourne dans son plan (boule, étoile d'explosion). */
		SPRITE_CENTER,
		/** Sprite qui reste vertical et pivote vers le joueur (langues de flammes, qui orbitent). */
		SPRITE_VERTICAL,
		/** Sprite à plat qui tourne (anneaux de feu). */
		RING,
		/** Comète alignée sur la direction du tir. */
		TRAIL,
		/** Modèle 3D (item_display, fixe) qui tourne sur lui-même autour d'un axe : la sphère de voxels et sa couronne de flammes. */
		MODEL
	}

	private static final class Def {
		final Kind kind;
		final String asset;        // bloc (CUBE) ou modèle d'objet "fx/..." (sprites)
		final double size;         // largeur, en fraction du rayon
		final double aspect;       // hauteur / largeur (sprites)
		final boolean core;        // le noyau "flashe" au lancement (cubes)
		final double ax, ay, az;   // axe de rotation (cubes)
		final double angle0, spin; // rotation initiale et vitesse (par unité de "spinAge")
		final double orbit, orbitSpeed, phase, yFrac; // orbite (langues de flammes)
		final double tilt;         // inclinaison (anneaux) ou roulis autour de l'axe de tir (comète)
		boolean burstOnly;         // n'existe que pendant l'explosion
		boolean slow;              // s'étend plus lentement (onde de choc)

		Def(Kind kind, String asset, double size, double aspect, boolean core, double ax, double ay, double az,
				double angle0, double spin, double orbit, double orbitSpeed, double phase, double yFrac, double tilt) {
			this.kind = kind;
			this.asset = asset;
			this.size = size;
			this.aspect = aspect;
			this.core = core;
			this.ax = ax;
			this.ay = ay;
			this.az = az;
			this.angle0 = angle0;
			this.spin = spin;
			this.orbit = orbit;
			this.orbitSpeed = orbitSpeed;
			this.phase = phase;
			this.yFrac = yFrac;
			this.tilt = tilt;
		}
	}

	private static Def cube(String block, double side, boolean core, double ax, double ay, double az, double angle0, double spin) {
		return new Def(Kind.CUBE, block, side, 1, core, ax, ay, az, angle0, spin, 0, 0, 0, 0, 0);
	}

	private static Def model(String model, double size, double ax, double ay, double az, double angle0, double spin) {
		return new Def(Kind.MODEL, model, size, 1, false, ax, ay, az, angle0, spin, 0, 0, 0, 0, 0);
	}

	private static Def center(String model, double size, double spin) {
		return new Def(Kind.SPRITE_CENTER, model, size, 1, false, 0, 0, 1, 0, spin, 0, 0, 0, 0, 0);
	}

	private static Def tongue(double size, double aspect, double orbit, double orbitSpeed, double phase) {
		return new Def(Kind.SPRITE_VERTICAL, "fx/flame", size, aspect, false, 0, 1, 0, 0, 0, orbit, orbitSpeed, phase, 0.05, 0);
	}

	private static Def ring(String model, double size, double tilt, double spin) {
		return new Def(Kind.RING, model, size, 1, false, 0, 1, 0, 0, spin, 0, 0, 0, 0, tilt);
	}

	private static Def trail(double size, double roll) {
		return new Def(Kind.TRAIL, "fx/comet", size, 0.5, false, 0, 0, 1, 0, 0, 0, 0, 0, 0, roll);
	}

	/** Couches présentes pendant la charge et le vol. */
	private static final List<Def> BASE_DEFS = new ArrayList<>();
	/** Couches ajoutées à l'impact. */
	private static final List<Def> BURST_DEFS = new ArrayList<>();
	/** Couches ajoutées au lancement. */
	private static final List<Def> TRAIL_DEFS = new ArrayList<>();

	static {
		// Petit noyau blanc au centre : il reste noyé dans le cœur jaune-blanc de la texture (les anciens gros cubes
		// glowstone / magma ressortaient devant le sprite comme des blocs opaques et cachaient la nouvelle texture).
		BASE_DEFS.add(cube("minecraft:white_concrete", 0.28, true, 1, 1, 0, 0.3, 0.21));
		BASE_DEFS.add(cube("minecraft:white_concrete", 0.28, true, 0, 1, 1, 0.9, -0.17));
		// Boule de feu en vrai 3D : une sphère de voxels (fx/sun_core) dont les cellules scintillent du blanc-jaune au rouge
		// (texture animée de flammes qui montent) : uniquement la sphère, sans couronne de flammes.
		// Diamètre de la sphère : environ 2,4 x le rayon de l'attaque.
		BASE_DEFS.add(model("fx/sun_core", 2.7, 0.3, 1.0, 0.2, 0.0, 0.05));
		// Boule uniforme : plus de langues ni d'anneaux autour (les flammes bougent à la surface de la sphère).

		// Impact : étoile d'explosion + deux anneaux de choc qui s'étendent
		Def star = center("fx/explosion", 2.6, 0.0);
		star.burstOnly = true;
		BURST_DEFS.add(star);
		Def shockA = ring("fx/ring_a", 3.2, 0.0, 0.05);
		shockA.burstOnly = true;
		shockA.slow = true;
		BURST_DEFS.add(shockA);
		Def shockB = ring("fx/ring_b", 2.6, 0.45, -0.05);
		shockB.burstOnly = true;
		shockB.slow = true;
		BURST_DEFS.add(shockB);

		// Lancement : deux plans de comète croisés, pour qu'elle se voie sous tous les angles
		// (Comète supprimée : plus aucune image de traînée au lancement ; la traînée reste en particules.)
	}

	private static final class Part {
		final Def def;
		final UUID id = UUID.randomUUID();

		Part(Def def) {
			this.def = def;
		}
	}

	// ---------------------------------------------------------------- état
	private final ServerLevel level;
	private final List<Part> parts = new ArrayList<>();
	private final Random jitter = new Random();
	private Vec3 center;
	private double radius;
	private double intensity;
	private Vec3 trailDir = null;
	private int age = 0;
	private int flashTicks = 0;
	private boolean burst = false;
	private int burstAge = 0;
	private boolean removed = false;
	private BlockPos lightPos;

	private FireballVisual(ServerLevel level, Vec3 center, double radius) {
		this.level = level;
		this.center = center;
		this.radius = radius;
	}

	/** Crée la boule (les entités apparaissent immédiatement côté client). */
	static FireballVisual create(ServerLevel level, Vec3 center, double radius) {
		FireballVisual v = new FireballVisual(level, center, radius);
		if (disabled) {
			v.removed = true;
			return v;
		}
		ALL.add(v);
		for (Def def : BASE_DEFS) {
			v.addPart(def, radius);
		}
		return v;
	}

	private void addPart(Def def, double r) {
		Part part = new Part(def);
		parts.add(part);
		summon(part, position(def, center, r, age), transform(def, r, 1.0, age, 0.0));
	}

	// ---------------------------------------------------------------- cycle de vie

	/** Chaque tick, pendant la charge puis pendant le vol : suit la position, grossit, vibre, tourne. */
	void update(Vec3 c, double r, double intensity) {
		if (removed || burst) {
			return;
		}
		this.center = c;
		this.radius = r;
		this.intensity = intensity;
		age++;
		double spinAge = age * (1.0 + 2.0 * intensity);

		positionAll(c, r, spinAge);
		if (age % UPDATE_EVERY == 0) {
			double pulse = 1.0 + 0.05 * Math.sin(age * 0.9) * intensity;
			double flash = 1.0 + 0.9 * (flashTicks / 4.0);
			double shake = SHAKE_MIN + (SHAKE_MAX - SHAKE_MIN) * intensity;
			pushAll(UPDATE_EVERY, r * pulse, flash, spinAge, shake);
			updateLight(c);
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
	}

	/** Au lancement : le noyau s'embrase brièvement et la comète apparaît derrière la boule. */
	void launchFlash(Vec3 direction) {
		if (removed) {
			return;
		}
		flashTicks = 4;
		FireFx.launch(level, center, radius);
		if (direction.lengthSqr() > 1.0E-6) {
			trailDir = direction.normalize();
			for (Def def : TRAIL_DEFS) {
				addPart(def, radius);
			}
		}
	}

	/** À l'impact : flash blanc, étoile et anneaux d'explosion, la boule gonfle puis se dissipe (elle se gère ensuite seule). */
	void burst(Vec3 at, double blastRadius) {
		if (removed) {
			return;
		}
		burst = true;
		burstAge = 0;
		center = at;
		radius = blastRadius;
		FireFx.burst(level, at, blastRadius);

		// La comète disparaît ; l'explosion apparaît (petite, elle grossit dans la foulée).
		for (Iterator<Part> it = parts.iterator(); it.hasNext();) {
			Part part = it.next();
			if (part.def.kind == Kind.TRAIL) {
				discard(part);
				it.remove();
			}
		}
		for (Def def : BURST_DEFS) {
			addPart(def, blastRadius * 0.3);
		}
		positionAll(at, blastRadius * 0.9, age);
		pushAll(2, blastRadius * 0.9, 1.7, age, 0.0);
		forceLight(at);
		MeraAbilities.schedule(this);
	}

	/** Phase d'explosion (appelée comme un effet actif). */
	@Override
	public boolean tick(MinecraftServer server) {
		if (removed || !burst) {
			return false;
		}
		burstAge++;
		age += 3;
		if (burstAge == 3) {
			positionAll(center, radius * 1.15, age);
			pushAll(5, radius * 1.15, 1.0, age, 0.0);
		} else if (burstAge == 8) {
			positionAll(center, radius * 0.25, age);
			pushAll(4, radius * 0.25, 0.5, age, 0.0);
		} else if (burstAge >= 12) {
			remove();
			return false;
		}
		return true;
	}

	/** Supprime les entités et la lumière. Peut être appelé plusieurs fois. */
	void remove() {
		if (removed) {
			return;
		}
		removed = true;
		for (Part part : parts) {
			discard(part);
		}
		parts.clear();
		clearLight();
		ALL.remove(this);
	}

	private void discard(Part part) {
		Entity e = level.getEntity(part.id);
		if (e != null) {
			e.discard();
		}
	}

	/** Arrêt du serveur : rien ne doit rester dans le monde sauvegardé. */
	static void cleanupAll() {
		for (FireballVisual v : new ArrayList<>(ALL)) {
			v.remove();
		}
	}

	/** Démarrage : supprime d'éventuels restes d'un crash précédent (dans les zones chargées). */
	static void killStray(MinecraftServer server) {
		try {
			server.getCommands().performPrefixedCommand(
					server.createCommandSourceStack().withSuppressedOutput(),
					"kill @e[tag=" + TAG + "]");
		} catch (Exception e) {
			MinePiece.LOGGER.warn("Nettoyage des effets de feu impossible : {}", e.toString());
		}
	}

	// ---------------------------------------------------------------- lumière

	private void updateLight(Vec3 c) {
		if (!DYNAMIC_LIGHT) {
			return;
		}
		BlockPos p = BlockPos.containing(c);
		if (p.equals(lightPos)) {
			return;
		}
		clearLight();
		placeLight(p);
	}

	private void forceLight(Vec3 c) {
		if (!DYNAMIC_LIGHT) {
			return;
		}
		BlockPos p = BlockPos.containing(c);
		if (!p.equals(lightPos)) {
			clearLight();
			placeLight(p);
		}
	}

	/** Bloc de lumière invisible, posé uniquement dans l'air (ne détruit jamais rien). */
	private void placeLight(BlockPos p) {
		if (level.getBlockState(p).isAir()) {
			run("setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " minecraft:light[level=15]");
			lightPos = p;
		}
	}

	private void clearLight() {
		if (lightPos != null && level.getBlockState(lightPos).is(Blocks.LIGHT)) {
			level.setBlock(lightPos, Blocks.AIR.defaultBlockState(), 3);
		}
		lightPos = null;
	}

	// ---------------------------------------------------------------- positions et commandes (invisibles, sans message)

	/** Position d'une couche : le centre, décalé pour les langues de flammes (orbite) et la comète (derrière la boule). */
	private Vec3 position(Def d, Vec3 c, double r, double spinAge) {
		switch (d.kind) {
			case SPRITE_VERTICAL: {
				double a = d.phase + d.orbitSpeed * spinAge;
				return c.add(Math.cos(a) * d.orbit * r, d.yFrac * r, Math.sin(a) * d.orbit * r);
			}
			case TRAIL: {
				if (trailDir == null) {
					return c;
				}
				double length = d.size * r;
				return c.add(trailDir.scale(-(length / 2.0) + 0.4 * r));
			}
			default:
				return c;
		}
	}

	private void positionAll(Vec3 c, double r, double spinAge) {
		for (Part part : parts) {
			Entity e = level.getEntity(part.id);
			if (e != null) {
				Vec3 p = position(part.def, c, r, spinAge);
				e.setPos(p.x, p.y, p.z);
			}
		}
	}

	private void summon(Part part, Vec3 at, String transformation) {
		long msb = part.id.getMostSignificantBits();
		long lsb = part.id.getLeastSignificantBits();
		String uuid = String.format(Locale.ROOT, "%d,%d,%d,%d", (int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb);
		String common = String.format(Locale.ROOT,
				"transformation:%s,brightness:{sky:15,block:15},view_range:4f,shadow_radius:0f,shadow_strength:0f,"
						+ "teleport_duration:2,interpolation_duration:2,Tags:[\"%s\"],UUID:[I;%s]",
				transformation, TAG, uuid);
		String coords = String.format(Locale.ROOT, "%.3f %.3f %.3f", at.x, at.y, at.z);
		if (part.def.kind == Kind.CUBE) {
			run("summon minecraft:block_display " + coords + " {block_state:{Name:\"" + part.def.asset + "\"}," + common + "}");
		} else {
			String billboard = switch (part.def.kind) {
				case SPRITE_CENTER -> "center";
				case SPRITE_VERTICAL -> "vertical";
				default -> "fixed";
			};
			run("summon minecraft:item_display " + coords + " {item:{id:\"minecraft:paper\",count:1,components:"
					+ "{\"minecraft:item_model\":\"" + MinePiece.MOD_ID + ":" + part.def.asset + "\"}},"
					+ "item_display:\"none\",billboard:\"" + billboard + "\"," + common + "}");
		}
	}

	/** Nouvelle taille / rotation de toutes les couches, interpolée sur "duration" ticks. */
	private void pushAll(int duration, double r, double flash, double spinAge, double shake) {
		if (disabled) {
			return;
		}
		for (Part part : parts) {
			int d = part.def.slow ? Math.min(59, duration * 3) : duration;
			run("data merge entity " + part.id + " {transformation:" + transform(part.def, r, flash, spinAge, shake)
					+ ",start_interpolation:0,interpolation_duration:" + d + "}");
		}
	}

	private void run(String command) {
		if (disabled) {
			return;
		}
		try {
			MinecraftServer server = level.getServer();
			CommandSourceStack source = server.createCommandSourceStack()
					.withLevel(level).withPosition(center).withSuppressedOutput();
			server.getCommands().performPrefixedCommand(source, command);
		} catch (Exception e) {
			disabled = true;
			MinePiece.LOGGER.error("Effets 3D du Mera Mera no Mi désactivés (commande refusée : {})", command, e);
		}
	}

	// ---------------------------------------------------------------- maths : transformation d'une couche

	/**
	 * Transformation SNBT (rotation, échelle, translation) d'une couche, centrée sur l'entité.
	 * Un block_display tourne autour de son coin : pour les cubes on décale la translation afin de tourner autour du centre.
	 * Les sprites (item_display) sont déjà centrés sur l'entité.
	 */
	private String transform(Def d, double r, double flash, double spinAge, double shake) {
		double[] q;
		double sx;
		double sy;
		double sz = 1.0;
		double tx = 0;
		double ty = 0;
		double tz = 0;
		switch (d.kind) {
			case CUBE: {
				double side = Math.max(0.001, d.size * r * (d.core ? flash : 1.0));
				q = axisAngle(d.ax, d.ay, d.az, d.angle0 + d.spin * spinAge);
				double h = side / 2.0;
				double[] c = rotate(q, h, h, h);
				sx = side;
				sy = side;
				sz = side;
				tx = -c[0];
				ty = -c[1];
				tz = -c[2];
				break;
			}
			case MODEL: {
				q = axisAngle(d.ax, d.ay, d.az, d.angle0 + d.spin * spinAge);
				sx = Math.max(0.001, d.size * r);
				sy = sx;
				sz = sx;
				break;
			}
			case SPRITE_VERTICAL: {
				// léger balancement : la flamme "danse"
				q = axisAngle(0, 0, 1, Math.sin(spinAge * 0.45 + d.phase * 3.0) * 0.14);
				sx = Math.max(0.001, d.size * r);
				sy = sx * d.aspect;
				break;
			}
			case RING: {
				q = mul(axisAngle(0, 1, 0, d.angle0 + d.spin * spinAge), axisAngle(1, 0, 0, Math.PI / 2.0 + d.tilt));
				sx = Math.max(0.001, d.size * r);
				sy = sx;
				break;
			}
			case TRAIL: {
				Vec3 dir = trailDir == null ? new Vec3(1, 0, 0) : trailDir;
				q = mul(axisAngle(dir.x, dir.y, dir.z, d.tilt), fromTo(dir));
				sx = Math.max(0.001, d.size * r * (1.0 + 0.08 * Math.sin(spinAge * 1.7)));
				sy = sx * d.aspect;
				break;
			}
			default: { // SPRITE_CENTER
				q = axisAngle(0, 0, 1, d.angle0 + d.spin * spinAge);
				sx = Math.max(0.001, d.size * r);
				sy = sx * d.aspect;
				break;
			}
		}
		if (shake > 0) {
			tx += (jitter.nextDouble() - 0.5) * 2.0 * shake * r;
			ty += (jitter.nextDouble() - 0.5) * 2.0 * shake * r;
			tz += (jitter.nextDouble() - 0.5) * 2.0 * shake * r;
		}
		return String.format(Locale.ROOT,
				"{left_rotation:[%.4ff,%.4ff,%.4ff,%.4ff],right_rotation:[0f,0f,0f,1f],"
						+ "scale:[%.4ff,%.4ff,%.4ff],translation:[%.4ff,%.4ff,%.4ff]}",
				q[0], q[1], q[2], q[3], sx, sy, sz, tx, ty, tz);
	}

	/** Quaternion qui amène l'axe +X sur la direction donnée (pour aligner la comète sur le tir). */
	private static double[] fromTo(Vec3 dir) {
		double dot = dir.x; // dot((1,0,0), dir)
		if (dot < -0.9999) {
			return axisAngle(0, 1, 0, Math.PI);
		}
		// axe = (1,0,0) x dir = (0, -dir.z, dir.y)
		double x = 0.0;
		double y = -dir.z;
		double z = dir.y;
		double w = 1.0 + dot;
		double len = Math.sqrt(x * x + y * y + z * z + w * w);
		return new double[] {x / len, y / len, z / len, w / len};
	}

	private static double[] axisAngle(double ax, double ay, double az, double angle) {
		double len = Math.sqrt(ax * ax + ay * ay + az * az);
		if (len < 1.0E-9) {
			return new double[] {0, 0, 0, 1};
		}
		double s = Math.sin(angle / 2.0) / len;
		return new double[] {ax * s, ay * s, az * s, Math.cos(angle / 2.0)};
	}

	private static double[] mul(double[] a, double[] b) {
		return new double[] {
				a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
				a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
				a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
				a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
	}

	private static double[] rotate(double[] q, double vx, double vy, double vz) {
		double tx = 2.0 * (q[1] * vz - q[2] * vy);
		double ty = 2.0 * (q[2] * vx - q[0] * vz);
		double tz = 2.0 * (q[0] * vy - q[1] * vx);
		return new double[] {
				vx + q[3] * tx + (q[1] * tz - q[2] * ty),
				vy + q[3] * ty + (q[2] * tx - q[0] * tz),
				vz + q[3] * tz + (q[0] * ty - q[1] * tx)};
	}
}
