package minepiece.noah.ability.mera;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
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
 * Boule de feu géante en 3D, faite d'entités d'affichage (block_display) vanilla : aucun rendu client à écrire,
 * synchronisée automatiquement en multijoueur, animée avec l'interpolation native de Minecraft.
 *
 * Couches (de l'intérieur vers l'extérieur) :
 *  - noyau blanc ultra lumineux, puis couche jaune incandescente (glowstone) ;
 *  - couche rouge/orange en magma (texture animée) : plusieurs cubes tournant sur des axes différents,
 *    ce qui donne une boule à facettes ;
 *  - enveloppe de flammes : des cartes de feu (texture de flamme animée) qui orbitent autour de la boule.
 * Toutes les couches sont affichées à pleine luminosité, vibrent, tournent de plus en plus vite avec la charge,
 * et un bloc de lumière invisible suit le centre pour éclairer l'environnement.
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
	private static final class Def {
		final String block;
		final double side;       // taille du cube, en fraction du rayon
		final boolean core;      // le noyau "flashe" au lancement
		final boolean card;      // true = carte de feu qui orbite ; false = cube qui tourne sur lui-même
		final double ax, ay, az; // axe de rotation (cubes)
		final double angle0, spin;
		final double orbit, orbitSpeed, phase, tilt; // cartes de feu

		Def(String block, double side, boolean core, boolean card, double ax, double ay, double az,
				double angle0, double spin, double orbit, double orbitSpeed, double phase, double tilt) {
			this.block = block;
			this.side = side;
			this.core = core;
			this.card = card;
			this.ax = ax;
			this.ay = ay;
			this.az = az;
			this.angle0 = angle0;
			this.spin = spin;
			this.orbit = orbit;
			this.orbitSpeed = orbitSpeed;
			this.phase = phase;
			this.tilt = tilt;
		}
	}

	private static Def cube(String block, double side, boolean core, double ax, double ay, double az, double angle0, double spin) {
		return new Def(block, side, core, false, ax, ay, az, angle0, spin, 0, 0, 0, 0);
	}

	private static Def card(double side, double orbit, double orbitSpeed, double phase, double tilt) {
		return new Def("minecraft:fire", side, false, true, 0, 1, 0, 0, 0, orbit, orbitSpeed, phase, tilt);
	}

	private static final List<Def> DEFS = new ArrayList<>();

	static {
		// Noyau blanc
		DEFS.add(cube("minecraft:white_concrete", 0.52, true, 1, 1, 0, 0.3, 0.21));
		DEFS.add(cube("minecraft:white_concrete", 0.52, true, 0, 1, 1, 0.9, -0.17));
		// Couche jaune incandescente
		DEFS.add(cube("minecraft:glowstone", 0.85, false, 1, 0, 1, 0.6, 0.12));
		DEFS.add(cube("minecraft:glowstone", 0.85, false, 1, 1, 1, 0.2, -0.14));
		// Couche orange / rouge (magma, texture animée)
		DEFS.add(cube("minecraft:magma_block", 1.2, false, 1, 0, 0, 0.78, 0.09));
		DEFS.add(cube("minecraft:magma_block", 1.2, false, 0, 0, 1, 0.78, -0.08));
		DEFS.add(cube("minecraft:magma_block", 1.2, false, 0.3, 1, 0.3, 0.6, 0.06));
		// Enveloppe de flammes animées : 6 cartes de feu qui orbitent
		for (int i = 0; i < 6; i++) {
			boolean even = i % 2 == 0;
			DEFS.add(card(1.8, 0.55, even ? 0.14 : -0.10, i * Math.PI / 3.0, even ? 0.45 : -0.45));
		}
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

	/** Crée la boule (invisible tant qu'elle n'est pas apparue côté client : quelques ticks au plus). */
	static FireballVisual create(ServerLevel level, Vec3 center, double radius) {
		FireballVisual v = new FireballVisual(level, center, radius);
		if (disabled) {
			v.removed = true;
			return v;
		}
		ALL.add(v);
		for (Def def : DEFS) {
			v.parts.add(new Part(def));
		}
		for (Part part : v.parts) {
			v.summon(part, v.transform(part.def, radius, 1.0, 0.0, 0.0));
		}
		return v;
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

		for (Part part : parts) {
			Entity e = level.getEntity(part.id);
			if (e != null) {
				e.setPos(c.x, c.y, c.z);
			}
		}
		if (age % UPDATE_EVERY == 0) {
			double pulse = 1.0 + 0.05 * Math.sin(age * 0.9) * intensity;
			double flash = 1.0 + 0.9 * (flashTicks / 4.0);
			double shake = SHAKE_MIN + (SHAKE_MAX - SHAKE_MIN) * intensity;
			pushAll(UPDATE_EVERY, r * pulse, flash, age * (1.0 + 2.0 * intensity), shake);
			updateLight(c);
		}
		if (flashTicks > 0) {
			flashTicks--;
		}
	}

	/** Au lancement : le noyau s'embrase brièvement. */
	void launchFlash() {
		flashTicks = 4;
		FireFx.launch(level, center, radius);
	}

	/** À l'impact : flash blanc, la boule gonfle en explosion puis se dissipe (elle se gère ensuite toute seule). */
	void burst(Vec3 at, double blastRadius) {
		if (removed) {
			return;
		}
		burst = true;
		burstAge = 0;
		center = at;
		radius = blastRadius;
		FireFx.burst(level, at, blastRadius);
		for (Part part : parts) {
			Entity e = level.getEntity(part.id);
			if (e != null) {
				e.setPos(at.x, at.y, at.z);
			}
		}
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
			pushAll(5, radius * 1.15, 1.0, age, 0.0);
		} else if (burstAge == 8) {
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
			Entity e = level.getEntity(part.id);
			if (e != null) {
				e.discard();
			}
		}
		clearLight();
		ALL.remove(this);
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

	// ---------------------------------------------------------------- commandes (invisibles, sans message)

	private void summon(Part part, String transformation) {
		long msb = part.id.getMostSignificantBits();
		long lsb = part.id.getLeastSignificantBits();
		run(String.format(Locale.ROOT,
				"summon minecraft:block_display %.3f %.3f %.3f {block_state:{Name:\"%s\"},transformation:%s,"
						+ "brightness:{sky:15,block:15},view_range:4f,shadow_radius:0f,shadow_strength:0f,"
						+ "teleport_duration:2,interpolation_duration:2,Tags:[\"%s\"],UUID:[I;%d,%d,%d,%d]}",
				center.x, center.y, center.z, part.def.block, transformation, TAG,
				(int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb));
	}

	/** Nouvelle taille / rotation de toutes les couches, interpolée sur "duration" ticks. */
	private void pushAll(int duration, double r, double flash, double spinAge, double shake) {
		if (disabled) {
			return;
		}
		for (Part part : parts) {
			run("data merge entity " + part.id + " {transformation:" + transform(part.def, r, flash, spinAge, shake)
					+ ",start_interpolation:0,interpolation_duration:" + duration + "}");
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
	 * Un block_display est tourné autour de son coin : on décale donc la translation pour tourner autour du centre du cube.
	 */
	private String transform(Def d, double r, double flash, double spinAge, double shake) {
		double side = Math.max(0.001, d.side * r * (d.core ? flash : 1.0));
		double[] q;
		double px = 0;
		double py = 0;
		double pz = 0;
		if (d.card) {
			double orbitAngle = d.phase + d.orbitSpeed * spinAge;
			double[] qy = axisAngle(0, 1, 0, orbitAngle);
			q = mul(qy, axisAngle(1, 0, 0, d.tilt));
			double[] p = rotate(qy, d.orbit * r, 0, 0);
			px = p[0];
			py = p[1];
			pz = p[2];
		} else {
			q = axisAngle(d.ax, d.ay, d.az, d.angle0 + d.spin * spinAge);
		}
		if (shake > 0) {
			px += (jitter.nextDouble() - 0.5) * 2.0 * shake * r;
			py += (jitter.nextDouble() - 0.5) * 2.0 * shake * r;
			pz += (jitter.nextDouble() - 0.5) * 2.0 * shake * r;
		}
		double h = side / 2.0;
		double[] c = rotate(q, h, h, h);
		return String.format(Locale.ROOT,
				"{left_rotation:[%.4ff,%.4ff,%.4ff,%.4ff],right_rotation:[0f,0f,0f,1f],"
						+ "scale:[%.4ff,%.4ff,%.4ff],translation:[%.4ff,%.4ff,%.4ff]}",
				q[0], q[1], q[2], q[3], side, side, side, px - c[0], py - c[1], pz - c[2]);
	}

	private static double[] axisAngle(double ax, double ay, double az, double angle) {
		double len = Math.sqrt(ax * ax + ay * ay + az * az);
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
