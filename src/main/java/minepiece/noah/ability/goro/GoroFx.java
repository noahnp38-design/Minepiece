package minepiece.noah.ability.goro;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import minepiece.noah.ability.zushi.ZushiDisplay;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.ability.zushi.ZushiTerrain;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Effets communs du Goro Goro no Mi, entièrement côté serveur (donc vus par tous les joueurs) :
 * - éclairs VOLUMÉTRIQUES bleus : chaque segment est un modèle 3D (fx/goro_bolt : halo cyan, gaine bleu électrique,
 *   coeur blanc bleuté, texture animée) étiré et orienté le long du zigzag, avec ramifications ; ils clignotent puis
 *   disparaissent ;
 * - connexions électriques VIVANTES (Tether) : une chaîne d'éclairs qui relie la main réelle du joueur à une attaque
 *   (éclair, chien, dragon), redessinée tous les 2 ticks (zigzags et ramifications qui bougent), nombre de modèles fixe ;
 * - position réelle des mains (bras tendu, bras levé, deux mains levées) et arcs qui parcourent bras, épaule et torse ;
 * - cumulonimbus 3D (fx/goro_cloud_a|b|c) qui se forment progressivement, tournent lentement, s'illuminent d'éclairs
 *   internes puis se dissipent ;
 * - explosion électrique commune (lumière, onde de choc 3D, dégâts, projection, cratère, blocs projetés, éclairs
 *   secondaires, poussière, secousse de caméra).
 * Réutilise les outils du Zushi (ZushiDisplay, ZushiFx, ZushiTerrain) : même plafond de modèles, mêmes protections.
 */
public final class GoroFx {
	private static final List<Timed> TEMP = new ArrayList<>();
	private static final List<Tether> TETHERS = new ArrayList<>();
	private static final List<Storm> STORMS = new ArrayList<>();
	private static final String[] CLOUDS = {"goro_cloud_a", "goro_cloud_b", "goro_cloud_c"};
	/** Largeur du modèle de nuage à l'échelle 1 (blocs) : -13..29 unités. */
	private static final double CLOUD_WIDTH = 42.0 / 16.0;

	private GoroFx() {}

	private static final class Timed {
		final ZushiDisplay display;
		final int life;
		final double sx;
		final double sz;
		int age;

		Timed(ZushiDisplay display, int life, double sx, double sz) {
			this.display = display;
			this.life = life;
			this.sx = sx;
			this.sz = sz;
		}
	}

	// ------------------------------------------------------------------ mains réelles du joueur

	/** Vecteur "droite" du corps du joueur (orientation du torse, comme le modèle 3D du joueur). */
	public static Vec3 bodyRight(ServerPlayer p) {
		double yaw = Math.toRadians(p.yBodyRot);
		return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
	}

	/** Épaule droite (pivot du bras du modèle joueur : 5/16 de bloc sur le côté, 22/16 au-dessus des pieds). */
	public static Vec3 shoulder(ServerPlayer p, boolean right) {
		double k = p.getBbHeight() / 1.8;
		double y = 1.375 * k - (p.isCrouching() ? 0.3 : 0.0);
		return p.position().add(0, y, 0).add(bodyRight(p).scale(right ? 0.3125 : -0.3125));
	}

	/**
	 * Main droite, bras TENDU dans la direction du regard (pose ARM_FORWARD : le bras suit la tête). Calculée comme le
	 * modèle du joueur (épaule + longueur du bras) : correcte en 3e personne, pour les autres joueurs, et en 1re
	 * personne elle se trouve en bas à droite de l'écran, à l'endroit où l'on voit sa main.
	 */
	public static Vec3 handForward(ServerPlayer p) {
		return shoulder(p, true).add(p.getLookAngle().scale(0.72));
	}

	/** Main levée vers le ciel (pose ARM_UP / BOTH_UP), légèrement écartée. */
	public static Vec3 handUp(ServerPlayer p, boolean right) {
		return shoulder(p, right).add(bodyRight(p).scale(right ? 0.12 : -0.12)).add(0, 0.72 * p.getBbHeight() / 1.8, 0);
	}

	/**
	 * Le bras se charge d'électricité : arcs qui courent de l'épaule à la main, étincelles sur l'épaule, arcs sur le
	 * torse, particules de charge aspirées vers la main.
	 * @param intensity 0..1
	 */
	public static void armArcs(ServerLevel level, ServerPlayer p, Vec3 hand, boolean right, double intensity, int tick) {
		RandomSource r = level.getRandom();
		Vec3 sh = shoulder(p, right);
		Vec3 chest = p.position().add(0, 1.05 * p.getBbHeight() / 1.8, 0);
		int along = 2 + (int) (4 * intensity);
		for (int i = 0; i < along; i++) {
			Vec3 q = sh.add(hand.subtract(sh).scale(r.nextDouble()));
			ZushiFx.send(level, ModParticles.ELECTRIC_ARC, q.x, q.y, q.z, 1, 0.06, 0.06, 0.06, 0.0);
		}
		ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, sh.x, sh.y, sh.z, 1 + (int) (2 * intensity), 0.08, 0.08, 0.08, 0.03);
		ZushiFx.send(level, ModParticles.ELECTRIC_ARC, chest.x, chest.y, chest.z, 1 + (int) (3 * intensity), 0.22, 0.32, 0.22, 0.0);
		ZushiFx.send(level, ModParticles.ELECTRIC_BLUE, hand.x, hand.y, hand.z, 1 + (int) (3 * intensity), 0.08, 0.08, 0.08, 0.02);
		for (int i = 0; i < 1 + (int) (3 * intensity); i++) {
			Vec3 off = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize().scale(0.8 + 1.4 * intensity);
			ZushiFx.velocity(level, ModParticles.ELECTRIC_CHARGE, hand, off, false);
		}
		// un vrai petit éclair 3D court le long du bras (épaule -> main), puis de la main vers le torse
		if (tick % 3 == 0) {
			bolt(level, sh, hand, 0.025 + 0.025 * intensity, 2, 0, false);
		}
		if (intensity > 0.5 && tick % 5 == 2) {
			bolt(level, hand, chest.add((r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.4),
					0.025, 2, 0, false);
		}
	}

	// ------------------------------------------------------------------ connexion électrique vivante

	/**
	 * Chaîne d'éclairs qui relie la main du joueur à une attaque. Nombre de segments FIXE (pas de création d'entités à
	 * chaque tick) : les mêmes modèles sont redéplacés / réorientés / réétirés tous les 2 ticks avec interpolation côté
	 * client, si bien que la connexion se tord en permanence. Disparaît d'elle-même si elle n'est plus mise à jour
	 * (attaque terminée, lanceur parti) : aucune connexion permanente ne peut rester.
	 */
	public static final class Tether {
		private static final int BRANCH_SEGMENTS = 3;
		private final ServerLevel level;
		private final double thickness;
		private final ZushiDisplay[] main;
		private final ZushiDisplay[] branch;
		private final boolean farView;
		private int age;
		private int lastUpdate;
		private boolean removed;
		private final double phase;

		private Tether(ServerLevel level, int segments, int branches, double thickness, boolean farView) {
			this.level = level;
			this.thickness = thickness;
			this.main = new ZushiDisplay[segments];
			this.branch = new ZushiDisplay[branches * BRANCH_SEGMENTS];
			this.farView = farView;
			this.phase = level.getRandom().nextDouble() * 10.0;
		}

		/** À appeler chaque tick tant que l'attaque dure. */
		public void update(Vec3 from, Vec3 to) {
			if (removed) {
				return;
			}
			lastUpdate = age;
			if (age % 2 != 0 && main[0] != null) {
				return;
			}
			RandomSource r = level.getRandom();
			Vec3 d = to.subtract(from);
			double len = d.length();
			if (len < 0.2) {
				return;
			}
			Vec3 dir = d.scale(1.0 / len);
			Vec3 a = dir.cross(new Vec3(0, 1, 0));
			a = a.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : a.normalize();
			Vec3 b = a.cross(dir).normalize();
			int n = main.length;
			double amp = Math.min(len * 0.07, 1.6) + thickness * 2.0;
			Vec3[] pts = new Vec3[n + 1];
			double t0 = age * 0.55 + phase;
			for (int i = 0; i <= n; i++) {
				double u = i / (double) n;
				double env = Math.sin(Math.PI * u);   // fixée aux deux bouts : part de la main, rejoint la cible
				double w1 = Math.sin(u * 9.0 + t0) * 0.6 + (r.nextDouble() - 0.5) * 0.9;
				double w2 = Math.cos(u * 7.0 - t0 * 1.3) * 0.6 + (r.nextDouble() - 0.5) * 0.9;
				pts[i] = from.add(d.scale(u)).add(a.scale(w1 * amp * env)).add(b.scale(w2 * amp * env));
			}
			pts[0] = from;
			pts[n] = to;
			for (int i = 0; i < n; i++) {
				main[i] = place(main[i], pts[i], pts[i + 1], thickness * (0.75 + r.nextDouble() * 0.5));
			}
			// ramifications : repartent de points au hasard de la chaîne, changent à chaque mise à jour
			for (int bIdx = 0; bIdx < branch.length / BRANCH_SEGMENTS; bIdx++) {
				Vec3 start = pts[1 + r.nextInt(Math.max(1, n - 1))];
				Vec3 side = a.scale(Math.cos(r.nextDouble() * 6.283)).add(b.scale(Math.sin(r.nextDouble() * 6.283))).normalize();
				double bl = Math.min(len * 0.22, 4.5) + thickness * 4.0;
				Vec3 end = start.add(dir.scale(bl * (r.nextDouble() - 0.2))).add(side.scale(bl));
				Vec3 prev = start;
				for (int s = 0; s < BRANCH_SEGMENTS; s++) {
					double u = (s + 1) / (double) BRANCH_SEGMENTS;
					Vec3 q = start.add(end.subtract(start).scale(u))
							.add(new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).scale(bl * 0.3 * (1 - u * 0.5)));
					int idx = bIdx * BRANCH_SEGMENTS + s;
					branch[idx] = place(branch[idx], prev, q, thickness * 0.45 * (1.0 - u * 0.4));
					prev = q;
				}
			}
			// particules qui suivent le flux
			for (int k = 0; k < 3; k++) {
				Vec3 p = pts[r.nextInt(n + 1)];
				ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, p.x, p.y, p.z, 2, thickness, thickness, thickness, 0.08);
				ZushiFx.send(level, ModParticles.ELECTRIC_BLUE, p.x, p.y, p.z, 1, thickness * 2, thickness * 2, thickness * 2, 0.03);
			}
			Vec3 p = pts[r.nextInt(n + 1)];
			ZushiFx.send(level, ModParticles.ELECTRIC_BRANCH, p.x, p.y, p.z, 1, thickness * 2, thickness * 2, thickness * 2, 0.0);
		}

		private ZushiDisplay place(ZushiDisplay seg, Vec3 a, Vec3 b, double thick) {
			Vec3 d = b.subtract(a);
			double len = Math.max(0.05, d.length());
			Vec3 mid = a.add(d.scale(0.5));
			float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, d.y / len))));
			double sx = thick / 0.2;
			if (seg == null || !seg.isActive()) {
				return ZushiDisplay.createStretched(level, "goro_bolt", mid, yaw, pitch, sx, sx, len * 1.06, 2, farView ? 6.0F : 3.0F, true);
			}
			seg.move(mid, yaw, pitch);
			seg.setStretch(sx, sx, len * 1.06, 2);
			return seg;
		}

		public boolean isRemoved() {
			return removed;
		}

		/** Fin de l'attaque : toute la chaîne disparaît. */
		public void remove() {
			if (removed) {
				return;
			}
			removed = true;
			for (ZushiDisplay d : main) {
				if (d != null) {
					d.remove();
				}
			}
			for (ZushiDisplay d : branch) {
				if (d != null) {
					d.remove();
				}
			}
		}

		boolean tick() {
			age++;
			if (!removed && age - lastUpdate > 3) {
				remove();   // plus mise à jour : l'attaque est finie
			}
			return !removed;
		}
	}

	/**
	 * Nouvelle connexion électrique.
	 * @param segments  segments de la chaîne principale
	 * @param branches  nombre de ramifications (3 segments chacune)
	 * @param thickness épaisseur (blocs)
	 */
	public static Tether tether(ServerLevel level, int segments, int branches, double thickness, boolean farView) {
		Tether t = new Tether(level, segments, branches, thickness, farView);
		TETHERS.add(t);
		return t;
	}

	// ------------------------------------------------------------------ éclairs volumétriques

	/**
	 * Éclair volumétrique de from à to.
	 * @param thickness épaisseur (blocs)
	 * @param life      durée de vie (ticks)
	 * @param branches  nombre de ramifications
	 */
	public static void bolt(ServerLevel level, Vec3 from, Vec3 to, double thickness, int life, int branches, boolean farView) {
		bolt(level, from, to, thickness, life, branches, farView, "goro_bolt");
	}

	/**
	 * Même éclair volumétrique avec un autre modèle de segment (ex. "haki_bolt" : éclair noir et rouge du Haki des Rois).
	 * Les étincelles bleues ne sont ajoutées que pour la foudre du Goro Goro no Mi.
	 */
	public static void bolt(ServerLevel level, Vec3 from, Vec3 to, double thickness, int life, int branches, boolean farView, String model) {
		List<Vec3> pts = zigzag(level.getRandom(), from, to, thickness);
		for (int i = 1; i < pts.size(); i++) {
			segment(level, pts.get(i - 1), pts.get(i), thickness, life, farView, model);
		}
		RandomSource r = level.getRandom();
		for (int b = 0; b < branches && pts.size() > 2; b++) {
			Vec3 start = pts.get(1 + r.nextInt(pts.size() - 2));
			Vec3 dir = to.subtract(from).normalize();
			Vec3 side = randomPerp(r, dir);
			double len = from.distanceTo(to) * (0.15 + r.nextDouble() * 0.2);
			Vec3 end = start.add(dir.scale(len * 0.6)).add(side.scale(len * 0.8));
			List<Vec3> bp = zigzag(r, start, end, thickness * 0.5);
			for (int i = 1; i < bp.size(); i++) {
				segment(level, bp.get(i - 1), bp.get(i), thickness * 0.5, Math.max(2, life - 1), farView, model);
			}
		}
		if (!"goro_bolt".equals(model)) {
			return;
		}
		// étincelles le long de l'éclair
		for (int i = 0; i < pts.size(); i += 2) {
			Vec3 p = pts.get(i);
			ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, p.x, p.y, p.z, 3, thickness, thickness, thickness, 0.15);
			ZushiFx.far(level, ModParticles.ELECTRIC_ARC, p.x, p.y, p.z, 1, thickness, thickness, thickness, 0.05);
		}
	}

	private static Vec3 randomPerp(RandomSource r, Vec3 dir) {
		Vec3 a = dir.cross(new Vec3(0, 1, 0));
		if (a.lengthSqr() < 1.0E-4) {
			a = new Vec3(1, 0, 0);
		}
		a = a.normalize();
		Vec3 b = a.cross(dir).normalize();
		double ang = r.nextDouble() * Math.PI * 2;
		return a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
	}

	private static List<Vec3> zigzag(RandomSource r, Vec3 from, Vec3 to, double thickness) {
		List<Vec3> pts = new ArrayList<>();
		Vec3 d = to.subtract(from);
		double len = d.length();
		int n = (int) Math.max(2, Math.min(14, Math.round(len / 2.4)));
		Vec3 dir = len < 1.0E-6 ? new Vec3(0, -1, 0) : d.scale(1.0 / len);
		pts.add(from);
		for (int i = 1; i < n; i++) {
			double jitter = len * 0.05 + thickness * 1.5;
			pts.add(from.add(d.scale(i / (double) n)).add(randomPerp(r, dir).scale((r.nextDouble() * 0.7 + 0.3) * jitter)));
		}
		pts.add(to);
		return pts;
	}

	private static void segment(ServerLevel level, Vec3 a, Vec3 b, double thickness, int life, boolean farView, String model) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 0.05) {
			return;
		}
		Vec3 mid = a.add(d.scale(0.5));
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, d.y / len))));
		double sx = thickness / 0.2;   // la gaine du modèle fait 0,2 bloc d'épaisseur à l'échelle 1
		double sz = len * 1.02;
		ZushiDisplay seg = ZushiDisplay.createStretched(level, model, mid, yaw, pitch, sx, sx, sz, 0, farView ? 6.0F : 2.5F, true);
		if (seg.isActive()) {
			TEMP.add(new Timed(seg, life, sx, sz));
		}
	}

	/** Garde un modèle temporaire "life" ticks puis le supprime. */
	public static void temporary(ZushiDisplay d, int life) {
		if (d.isActive()) {
			TEMP.add(new Timed(d, life, -1, -1));
		}
	}

	// ------------------------------------------------------------------ cumulonimbus

	/** Groupe de cumulonimbus 3D. */
	public static final class Storm {
		final ServerLevel level;
		final Vec3 center;
		final double width;
		final List<ZushiDisplay> clouds = new ArrayList<>();
		final List<double[]> shape = new ArrayList<>(); // {scale, angle, vitesse de rotation}
		int age;
		int dissipate = -1;
		final int grow;

		Storm(ServerLevel level, Vec3 center, double width, int count, int grow) {
			this.level = level;
			this.center = center;
			this.width = width;
			this.grow = grow;
			RandomSource r = level.getRandom();
			for (int i = 0; i < count; i++) {
				double a = i / (double) count * Math.PI * 2 + r.nextDouble() * 0.6;
				double dist = i == 0 ? 0 : width * (0.18 + r.nextDouble() * 0.22);
				Vec3 p = center.add(Math.cos(a) * dist, (r.nextDouble() - 0.5) * width * 0.08 + (i == 0 ? width * 0.05 : 0), Math.sin(a) * dist);
				double s = (i == 0 ? 0.75 : 0.45 + r.nextDouble() * 0.2) * width / CLOUD_WIDTH;
				ZushiDisplay d = ZushiDisplay.create(level, CLOUDS[i % CLOUDS.length], p, r.nextFloat() * 360.0F, 0.0F, s * 0.05, 0.0, 1,
						(float) Math.max(4.0, width / 6.0), false);
				clouds.add(d);
				shape.add(new double[] {s, r.nextDouble() * 360.0, (r.nextBoolean() ? 1 : -1) * (2.0 + r.nextDouble() * 3.0)});
			}
		}

		/** Les nuages commencent à se dissiper (environ 2 s). */
		public void dissipate() {
			if (dissipate < 0) {
				dissipate = 0;
			}
		}

		public Vec3 center() {
			return center;
		}

		public double width() {
			return width;
		}

		/** Point aléatoire sous la masse nuageuse (départ des éclairs). */
		public Vec3 underside(RandomSource r, double spread) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(r.nextDouble()) * width * 0.3 * spread;
			return center.add(Math.cos(a) * d, -width * 0.05, Math.sin(a) * d);
		}

		boolean tick() {
			age++;
			RandomSource r = level.getRandom();
			boolean fading = dissipate >= 0;
			if (fading) {
				dissipate++;
			}
			// formation progressive puis rotation lente (mouvements internes), interpolées côté client
			if (age % 10 == 1 || (fading && dissipate == 1)) {
				double k = fading ? 0.02 : Math.min(1.0, age / (double) grow);
				int dur = fading ? 40 : 10;
				for (int i = 0; i < clouds.size(); i++) {
					double[] s = shape.get(i);
					s[1] += s[2] * (dur / 10.0);
					double scale = s[0] * (0.25 + 0.75 * k) * (1.0 + 0.04 * Math.sin(age * 0.1 + i));
					clouds.get(i).setTransform(0, 1, 0, s[1], Math.max(0.02, scale), 0.0, dur);
				}
			}
			// fumée, condensation et éclairs internes
			double grown = fading ? Math.max(0.0, 1.0 - dissipate / 40.0) : Math.min(1.0, age / (double) grow);
			if (age % 3 == 0) {
				Vec3 p = underside(r, 1.0);
				ZushiFx.far(level, ModParticles.STORM_SMOKE, p.x, p.y, p.z, (int) (2 + 4 * grown), width * 0.15, width * 0.03, width * 0.15, 0.01);
			}
			if (!fading && grown > 0.4 && r.nextInt(6) == 0) {
				Vec3 a = underside(r, 0.9).add(0, width * 0.06, 0);
				Vec3 b = a.add((r.nextDouble() - 0.5) * width * 0.4, (r.nextDouble() - 0.3) * width * 0.08, (r.nextDouble() - 0.5) * width * 0.4);
				bolt(level, a, b, 0.12 + width * 0.004, 3, 0, true);
				ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, a.x, a.y, a.z, 6, width * 0.1, width * 0.04, width * 0.1, 0.05);
				if (r.nextInt(3) == 0) {
					level.playSound(null, a.x, a.y, a.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F, 0.7F + r.nextFloat() * 0.3F);
				}
			}
			if (fading && dissipate >= 42) {
				for (ZushiDisplay d : clouds) {
					d.remove();
				}
				return false;
			}
			return true;
		}

		void removeNow() {
			for (ZushiDisplay d : clouds) {
				d.remove();
			}
		}
	}

	public static Storm storm(ServerLevel level, Vec3 center, double width, int count, int growTicks) {
		Storm s = new Storm(level, center, width, count, growTicks);
		STORMS.add(s);
		return s;
	}

	// ------------------------------------------------------------------ explosion électrique

	/**
	 * Explosion électrique commune.
	 * @param intensity 0 (petite) à 1+ (énorme) : quantité d'effets, éclairs secondaires, blocs projetés
	 */
	public static void explode(ServerLevel level, ServerPlayer owner, Vec3 c, double radius, double damage, double crater, double intensity) {
		GoroConfig cfg = GoroConfig.get();
		RandomSource r = level.getRandom();
		// lumière centrale (sphère blanche qui gonfle puis s'éteint)
		ZushiDisplay flash = ZushiDisplay.create(level, "goro_sphere_core", c.add(0, 0.5, 0), 0.0F, 0.0F, 0.3, 0.0, 1, 4.0F, true);
		flash.setTransform(0.2, 1, 0.3, 30, radius * 0.45 / 0.7, 0.0, 3);
		temporary(flash, 4);
		// onde de choc 3D
		ZushiDisplay shock = ZushiDisplay.create(level, "goro_shock", c.add(0, 0.25, 0), 0.0F, 0.0F, 0.2, 0.0, 1, 4.0F, true);
		shock.scaleTo(radius * 2.2 / 1.5, 0.0, 12);
		temporary(shock, 13);
		// dégâts + souffle
		DamageSource src = owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().lightningBolt();
		AABB box = new AABB(c, c).inflate(radius, radius * 0.8, radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && (owner == null || e != owner))) {
			double d = e.position().distanceTo(c);
			if (d > radius) {
				continue;
			}
			e.hurtServer(level, src, (float) (damage * (1.0 - 0.45 * d / radius) * cfg.damageMultiplier));
			Vec3 push = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
			push = push.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : push.normalize();
			double k = (1.0 - d / radius * 0.7) * (0.6 + 1.2 * intensity);
			e.setDeltaMovement(e.getDeltaMovement().add(push.x * k, 0.4 + 0.35 * k, push.z * k));
			e.needsSync = true;
			ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8, 0.3, 0.5, 0.3, 0.1);
		}
		// blocs du terrain projetés (copies visuelles) puis cratère réel
		throwGround(level, owner, c, crater, (int) (4 + 30 * intensity), intensity);
		ZushiTerrain.crater(level, c, crater, owner, cfg.destroyTerrain);
		explodeParticles(level, c, radius, intensity);
	}

	/**
	 * Projette des blocs du terrain autour de c (vraies textures, copies visuelles) si la destruction est autorisée, sinon
	 * des roches. Respecte les protections (ZushiTerrain.allowed) et le réglage destroyTerrain.
	 */
	public static void throwGround(ServerLevel level, ServerPlayer owner, Vec3 c, double crater, int blocks, double intensity) {
		GoroConfig cfg = GoroConfig.get();
		RandomSource r = level.getRandom();
		for (int i = 0; i < blocks; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(r.nextDouble()) * Math.max(1.5, crater);
			int bx = (int) Math.floor(c.x + Math.cos(a) * d), bz = (int) Math.floor(c.z + Math.sin(a) * d);
			BlockPos top = BlockPos.containing(bx, c.y, bz);
			if (!level.isLoaded(top)) {
				continue;
			}
			int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
			if (Math.abs(gy - c.y) > crater + 2) {
				gy = (int) Math.floor(c.y) - 1;
			}
			BlockPos bp = new BlockPos(bx, gy, bz);
			BlockState st = level.getBlockState(bp);
			double sp = 0.35 + r.nextDouble() * 0.5 + intensity * 0.3;
			Vec3 vel = new Vec3(Math.cos(a) * sp, 0.6 + r.nextDouble() * 0.6, Math.sin(a) * sp);
			Vec3 from = new Vec3(bx + 0.5, gy + 1.3, bz + 0.5);
			if (cfg.destroyTerrain && crater >= 1.0 && st.isSolid() && ZushiTerrain.movable(level, bp, st) && ZushiTerrain.allowed(level, bp, owner)) {
				ZushiFx.throwBlock(level, st, from, vel, 0.6 + r.nextDouble() * 0.35);
			} else if (i % 3 == 0) {
				ZushiFx.throwRock(level, from, vel, 0.5 + r.nextDouble() * 0.4);
			}
		}
	}

	private static void explodeParticles(ServerLevel level, Vec3 c, double radius, double intensity) {
		RandomSource r = level.getRandom();
		// particules : étincelles cyan, électricité bleue, arcs, éclairs ramifiés, poussière, fumée
		int n = (int) (20 + 120 * intensity);
		ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, c.x, c.y + 1, c.z, n, radius * 0.4, 1.2, radius * 0.4, 0.6);
		ZushiFx.far(level, ModParticles.ELECTRIC_BLUE, c.x, c.y + 1, c.z, n / 2, radius * 0.4, 1.0, radius * 0.4, 0.4);
		ZushiFx.far(level, ModParticles.ELECTRIC_ARC, c.x, c.y + 1, c.z, n / 2, radius * 0.3, 1.0, radius * 0.3, 0.2);
		ZushiFx.far(level, ModParticles.STORM_DUST, c.x, c.y + 1, c.z, n, radius * 0.5, 1.5, radius * 0.5, 0.06);
		ZushiFx.far(level, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 1 + (int) (3 * intensity), radius * 0.2, 0.5, radius * 0.2, 0.0);
		BlockPos below = BlockPos.containing(c).below();
		BlockState belowState = level.getBlockState(below);
		if (!belowState.isAir()) {
			ZushiFx.far(level, new BlockParticleOption(ParticleTypes.BLOCK, belowState), c.x, c.y + 0.5, c.z, (int) (10 + 40 * intensity),
					radius * 0.25, 0.3, radius * 0.25, 0.2);
		}
		ZushiFx.far(level, ModParticles.ELECTRIC_BRANCH, c.x, c.y + 1.5, c.z, (int) (4 + 12 * intensity), radius * 0.35, 1.5, radius * 0.35, 0.0);
		ZushiFx.circle(level, ModParticles.STORM_DUST, c.add(0, 0.4, 0), 1.0, (int) (20 + 40 * intensity), 0.03, 0.5 + 0.5 * intensity, true);
		ZushiFx.circle(level, ModParticles.ELECTRIC_SPARK, c.add(0, 0.6, 0), 1.0, (int) (16 + 30 * intensity), 0.02, 0.8 + 0.6 * intensity, true);
		// éclairs secondaires autour de l'impact
		for (int i = 0, b = (int) (1 + 6 * intensity); i < b; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			Vec3 g = c.add(Math.cos(a) * radius * (0.4 + r.nextDouble() * 0.6), 0.2, Math.sin(a) * radius * (0.4 + r.nextDouble() * 0.6));
			bolt(level, g.add((r.nextDouble() - 0.5) * 4, 8 + r.nextDouble() * 10, (r.nextDouble() - 0.5) * 4), g, 0.15 + 0.15 * intensity, 4, 1, true);
		}
		ZushiFx.shake(level, c, 16 + 30 * intensity, 1 + (int) Math.round(3 * intensity), (int) (8 + 14 * intensity));
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, (float) (3 + 5 * intensity), 0.8F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, (float) (2 + 3 * intensity), 0.7F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, (float) (2 + 4 * intensity), 0.6F);
		// poussière qui persiste brièvement + étincelles résiduelles
		AFTER.add(new After(level, c, radius, intensity));
	}

	private static final List<After> AFTER = new ArrayList<>();

	private static final class After {
		final ServerLevel level;
		final Vec3 c;
		final double radius;
		final double intensity;
		int age;

		After(ServerLevel level, Vec3 c, double radius, double intensity) {
			this.level = level;
			this.c = c;
			this.radius = radius;
			this.intensity = intensity;
		}
	}

	// ------------------------------------------------------------------ tick

	static void tick() {
		for (Iterator<Timed> it = TEMP.iterator(); it.hasNext();) {
			Timed t = it.next();
			t.age++;
			if (t.sx > 0 && t.age == Math.max(1, t.life / 2)) {
				t.display.setStretch(t.sx * 0.45, t.sx * 0.45, t.sz, 2);   // l'éclair s'amincit avant de disparaître
			}
			if (t.age >= t.life) {
				t.display.remove();
				it.remove();
			}
		}
		for (Iterator<Storm> it = STORMS.iterator(); it.hasNext();) {
			if (!it.next().tick()) {
				it.remove();
			}
		}
		TETHERS.removeIf(t -> !t.tick());
		for (Iterator<After> it = AFTER.iterator(); it.hasNext();) {
			After a = it.next();
			a.age++;
			RandomSource r = a.level.getRandom();
			if (a.age % 3 == 0) {
				double ang = r.nextDouble() * Math.PI * 2, d = a.radius * (0.3 + r.nextDouble() * 0.7);
				ZushiFx.far(a.level, ModParticles.STORM_DUST, a.c.x + Math.cos(ang) * d, a.c.y + 0.8, a.c.z + Math.sin(ang) * d,
						(int) (2 + 4 * a.intensity), a.radius * 0.25, 0.6, a.radius * 0.25, 0.01);
				ZushiFx.send(a.level, ModParticles.ELECTRIC_SPARK, a.c.x + Math.cos(-ang) * d, a.c.y + 0.3, a.c.z + Math.sin(-ang) * d,
						2, 0.4, 0.1, 0.4, 0.05);
			}
			if (a.age >= 50) {
				it.remove();
			}
		}
	}

	static void cleanupAll() {
		for (Timed t : TEMP) {
			t.display.remove();
		}
		TEMP.clear();
		for (Storm s : STORMS) {
			s.removeNow();
		}
		STORMS.clear();
		for (Tether t : TETHERS) {
			t.remove();
		}
		TETHERS.clear();
		AFTER.clear();
	}
}
