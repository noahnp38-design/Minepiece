package minepiece.noah.ability.goro;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import minepiece.noah.ability.zushi.ZushiDisplay;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.ability.zushi.ZushiTerrain;
import minepiece.noah.particle.ModParticles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Attaque 2 : El Thor — un gigantesque RAYON CYLINDRIQUE de foudre bleu-blanc descend des cumulonimbus jusqu'au sol.
 * 1. Formation : le joueur lève le bras (arcs main -> son nuage) ; de très sombres cumulonimbus 3D se forment au-dessus
 *    de la zone visée, des éclairs bleus les traversent et convergent vers leur centre, où une lumière blanc bleuté
 *    (fx/goro_sphere_core aplatie) devient de plus en plus intense ; l'énergie est aspirée vers ce point.
 * 2. Descente : la colonne (fx/goro_beam : coeur blanc bleuté, gaine, enveloppe cyan, halo bleu, coque d'arcs animés,
 *    texture animée dont les stries défilent vers le bas) descend du nuage jusqu'au sol en quelques ticks.
 * 3. Impact prolongé : explosion électrique, puis pendant elThorBeamTicks la colonne reste là, tourne et palpite :
 *    dégâts continus dans tout son rayon réel (collision cylindrique), cratère qui s'élargit en plusieurs étapes, blocs
 *    projetés, onde de choc, poussière soulevée, particules qui descendent le long du rayon, arcs qui tournent autour,
 *    ramifications qui frappent le terrain environnant.
 * 4. La colonne s'amincit et disparaît progressivement, les nuages se dissipent.
 */
final class ElThor {
	private static final List<Strike> ACTIVE = new ArrayList<>();
	/** Modèle fx/goro_beam à l'échelle 1 : rayon 0,5 bloc (halo), hauteur 3 blocs. */
	private static final double BEAM_MODEL_R = 0.5;
	private static final double BEAM_MODEL_H = 3.0;
	/** Rayon du modèle fx/goro_sphere_core à l'échelle 1. */
	private static final double GLOW_MODEL_R = 0.72;
	private static final int DESCEND = 4;
	private static final int FADE = 12;

	private ElThor() {}

	private static final class Strike {
		final ServerLevel level;
		final UUID owner;
		final Vec3 target;
		final GoroFx.Storm selfStorm;
		final GoroFx.Storm targetStorm;
		final double top;
		final Vec3 glowPos;
		ZushiDisplay glow;
		ZushiDisplay beam;
		double beamYaw;
		int age;

		Strike(ServerLevel level, ServerPlayer p, Vec3 target) {
			this.level = level;
			this.owner = p.getUUID();
			this.target = target;
			double maxY = level.getMaxY() - 8.0;
			this.selfStorm = GoroFx.storm(level, new Vec3(p.getX(), Math.min(maxY, p.getY() + 22), p.getZ()), 18.0, 4, 35);
			this.targetStorm = GoroFx.storm(level, new Vec3(target.x, Math.min(maxY, target.y + 30), target.z), 38.0, 8, 40);
			// la colonne part de l'intérieur du nuage
			this.top = targetStorm.center().y - targetStorm.width() * 0.05 + 1.5;
			this.glowPos = new Vec3(target.x, targetStorm.center().y - targetStorm.width() * 0.05 - 0.5, target.z);
		}

		/** Bas de la colonne : enfoncé dans le cratère (qui se creuse pendant l'impact). */
		double bottom() {
			GoroConfig cfg = GoroConfig.get();
			return target.y - 1.0 - (cfg.destroyTerrain ? cfg.elThorCrater * 0.6 : 0.0);
		}

		void removeModels() {
			if (glow != null) {
				glow.remove();
			}
			if (beam != null) {
				beam.remove();
			}
		}
	}

	static boolean cast(ServerPlayer p, ServerLevel level) {
		GoroConfig cfg = GoroConfig.get();
		Vec3 t = Sango.aim(p, level, cfg.elThorRange).pos();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(t.x), (int) Math.floor(t.z));
		if (t.y > ground + 0.5 || t.y < ground - 6) {
			t = new Vec3(t.x, ground, t.z);
		}
		ACTIVE.add(new Strike(level, p, t));
		GoroPose.set(p, GoroPose.ARM_UP);
		level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 0.6F);
		return true;
	}

	static void tick(MinecraftServer server) {
		GoroConfig cfg = GoroConfig.get();
		for (Iterator<Strike> it = ACTIVE.iterator(); it.hasNext();) {
			Strike s = it.next();
			s.age++;
			ServerPlayer p = server.getPlayerList().getPlayer(s.owner);
			if (p != null && (p.level() != s.level || !p.isAlive())) {
				p = null;
			}
			int prep = cfg.elThorPrepareTicks;
			int impact = prep + DESCEND;
			int end = impact + cfg.elThorBeamTicks;
			if (s.age < prep) {
				prepare(s, p, cfg, prep);
			} else if (s.age < impact) {
				descend(s, cfg, s.age - prep);
			} else if (s.age == impact) {
				hit(s, p, cfg);
				sustain(s, p, cfg, 0);
			} else if (s.age < end) {
				sustain(s, p, cfg, s.age - impact);
			} else if (s.age == end) {
				// la colonne s'amincit puis disparaît
				double sx = cfg.elThorBeamRadius / BEAM_MODEL_R;
				if (s.beam != null) {
					s.beam.setStretch(sx * 0.04, (s.top - s.bottom()) / BEAM_MODEL_H, sx * 0.04, FADE);
				}
				if (s.glow != null) {
					s.glow.setStretch(0.05, 0.05, 0.05, FADE);
				}
				if (p != null && GoroPose.pose(p) == GoroPose.ARM_UP) {
					GoroPose.clear(p);
				}
				ZushiFx.far(s.level, ModParticles.ELECTRIC_SPARK, s.target.x, s.target.y + 1, s.target.z, 40, cfg.elThorBeamRadius, 1.0,
						cfg.elThorBeamRadius, 0.2);
			} else if (s.age == end + FADE) {
				s.removeModels();
			} else if (s.age == end + 6) {
				s.selfStorm.dissipate();
				s.targetStorm.dissipate();
			} else if (s.age > end + FADE + 4) {
				s.removeModels();
				it.remove();
			}
			// résidus : étincelles qui grésillent sur le sol après la disparition de la colonne
			if (s.age > end && s.age < end + FADE + 4 && s.age % 2 == 0) {
				ZushiFx.send(s.level, ModParticles.ELECTRIC_ARC, s.target.x, s.target.y + 0.3, s.target.z, 4,
						cfg.elThorBeamRadius, 0.1, cfg.elThorBeamRadius, 0.0);
			}
		}
	}

	/** Phase 1 : nuages sombres, éclairs internes, concentration de l'électricité au centre, lumière croissante. */
	private static void prepare(Strike s, ServerPlayer p, GoroConfig cfg, int prep) {
		ServerLevel level = s.level;
		RandomSource r = level.getRandom();
		if (p != null) {
			// main levée : le bras se charge, arcs entre la main et le nuage du lanceur
			Vec3 hand = GoroFx.handUp(p, true);
			GoroFx.armArcs(level, p, hand, true, 0.6, s.age);
			ZushiFx.send(level, ModParticles.ELECTRIC_BLUE, p.getX(), p.getY() + 1, p.getZ(), 2, 0.4, 0.8, 0.4, 0.02);
			if (s.age % 8 == 4) {
				GoroFx.bolt(level, hand, s.selfStorm.underside(r, 0.3), 0.08, 3, 1, true);
			}
		}
		int glowStart = (int) (prep * 0.3);
		if (s.age >= glowStart) {
			double k = (s.age - glowStart) / (double) Math.max(1, prep - glowStart);
			double w = cfg.elThorBeamRadius * (0.3 + 1.1 * k) / GLOW_MODEL_R;
			double h = cfg.elThorBeamRadius * (0.12 + 0.25 * k) / GLOW_MODEL_R;
			if (s.glow == null) {
				s.glow = ZushiDisplay.createStretched(level, "goro_sphere_core", s.glowPos, 0.0F, 0.0F, 0.05, 0.05, 0.05, 1, 8.0F, true);
			}
			if (s.age % 4 == 0) {
				s.glow.setStretch(w, h, w, 4);
			}
			// l'électricité converge vers le centre du nuage
			for (int i = 0; i < 2 + (int) (6 * k); i++) {
				double a = r.nextDouble() * Math.PI * 2, d = s.targetStorm.width() * (0.15 + r.nextDouble() * 0.25);
				ZushiFx.velocity(level, ModParticles.ELECTRIC_CHARGE, s.glowPos,
						new Vec3(Math.cos(a) * d, (r.nextDouble() - 0.3) * 4.0, Math.sin(a) * d), true);
			}
			if (s.age % Math.max(2, 6 - (int) (4 * k)) == 0) {
				GoroFx.bolt(level, s.targetStorm.underside(r, 1.0), s.glowPos, 0.15 + 0.2 * k, 3, 2, true);
			}
			ZushiFx.far(level, ModParticles.ELECTRIC_BRANCH, s.glowPos.x, s.glowPos.y, s.glowPos.z, 1 + (int) (3 * k),
					cfg.elThorBeamRadius * k, 0.6, cfg.elThorBeamRadius * k, 0.0);
			if (s.age % 10 == 0) {
				level.playSound(null, s.glowPos.x, s.glowPos.y, s.glowPos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.WEATHER,
						(float) (3.0 + 4.0 * k), (float) (0.5 + 0.6 * k));
			}
		}
		// zone visée : poussière soulevée, étincelles au sol, de plus en plus intenses
		if (s.age > prep / 2 && s.age % 2 == 0) {
			ZushiFx.circle(level, ModParticles.STORM_DUST, s.target.add(0, 0.2, 0), cfg.elThorRadius * 0.8, 10, 0.05, -0.05, true);
			ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, s.target.x, s.target.y + 0.3, s.target.z, 4,
					cfg.elThorRadius * 0.4, 0.1, cfg.elThorRadius * 0.4, 0.05);
		}
	}

	/** Phase 2 : la colonne descend du nuage jusqu'au sol (quelques ticks). */
	private static void descend(Strike s, GoroConfig cfg, int t) {
		ServerLevel level = s.level;
		double sx = cfg.elThorBeamRadius / BEAM_MODEL_R;
		double k = (t + 1) / (double) DESCEND;
		double yb = s.top - (s.top - s.bottom()) * k;
		Vec3 mid = new Vec3(s.target.x, (s.top + yb) / 2.0, s.target.z);
		double sy = Math.max(0.05, (s.top - yb) / BEAM_MODEL_H);
		if (s.beam == null) {
			s.beam = ZushiDisplay.createStretched(level, "goro_beam", mid, 0.0F, 0.0F, sx * 0.6, sy, sx * 0.6, 1, 10.0F, true);
			level.playSound(null, s.target.x, s.top, s.target.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 12.0F, 0.5F);
		} else {
			s.beam.move(mid, 0.0F, 0.0F);
			s.beam.setStretch(sx * (0.6 + 0.4 * k), sy, sx * (0.6 + 0.4 * k), 1);
		}
		ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, s.target.x, yb, s.target.z, 30, cfg.elThorBeamRadius * 0.6, 1.0, cfg.elThorBeamRadius * 0.6, 0.3);
	}

	/** Phase 3 : contact avec le sol. */
	private static void hit(Strike s, ServerPlayer p, GoroConfig cfg) {
		ServerLevel level = s.level;
		RandomSource r = level.getRandom();
		double sx = cfg.elThorBeamRadius / BEAM_MODEL_R;
		s.beam.move(new Vec3(s.target.x, (s.top + s.bottom()) / 2.0, s.target.z), 0.0F, 0.0F);
		s.beam.setStretch(sx * 1.25, (s.top - s.bottom()) / BEAM_MODEL_H, sx * 1.25, 1);   // éclat à l'impact
		GoroFx.explode(level, p, s.target, cfg.elThorRadius, cfg.elThorDamage, cfg.elThorCrater * 0.55, 1.0);
		for (int i = 0; i < 6; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			Vec3 g = s.target.add(Math.cos(a) * cfg.elThorRadius * 1.3, 0.2, Math.sin(a) * cfg.elThorRadius * 1.3);
			GoroFx.bolt(level, s.target.add(Math.cos(a) * cfg.elThorBeamRadius, 3 + r.nextDouble() * 6, Math.sin(a) * cfg.elThorBeamRadius),
					g, 0.3, 5, 2, true);
		}
		ZushiFx.shake(level, s.target, 60.0, 4, 26);
		level.playSound(null, s.target.x, s.target.y, s.target.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 14.0F, 0.5F);
		level.playSound(null, s.target.x, s.target.y, s.target.z, SoundEvents.GENERIC_EXPLODE, SoundSource.WEATHER, 8.0F, 0.5F);
	}

	/** Phase 3 (suite) : la colonne reste, écrase le terrain, frappe tout autour. */
	private static void sustain(Strike s, ServerPlayer p, GoroConfig cfg, int t) {
		ServerLevel level = s.level;
		RandomSource r = level.getRandom();
		double R = cfg.elThorBeamRadius;
		double height = s.top - s.bottom();
		double sx = R / BEAM_MODEL_R;
		Vec3 c = s.target;
		// la colonne tourne et palpite (animation interpolée côté client)
		if (t % 2 == 0 && s.beam != null) {
			s.beamYaw += 17.0;
			double w = sx * (0.93 + r.nextDouble() * 0.14);
			s.beam.move(new Vec3(c.x, (s.top + s.bottom()) / 2.0, c.z), (float) s.beamYaw, 0.0F);
			s.beam.setStretch(w, height / BEAM_MODEL_H, w, 2);
		}
		// dégâts continus dans le cylindre réel de la colonne
		if (t % 5 == 0) {
			DamageSource src = p != null ? level.damageSources().playerAttack(p) : level.damageSources().lightningBolt();
			AABB box = new AABB(c.x - R - 1, s.bottom() - 3, c.z - R - 1, c.x + R + 1, s.top, c.z + R + 1);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
					e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(s.owner))) {
				double dx = e.getX() - c.x, dz = e.getZ() - c.z;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > R + e.getBbWidth() * 0.5) {
					continue;
				}
				e.hurtServer(level, src, (float) (cfg.elThorBeamDamage * cfg.damageMultiplier));
				// écrasé vers le sol et repoussé vers le bord de la colonne
				Vec3 out = d < 1.0E-3 ? new Vec3(1, 0, 0) : new Vec3(dx / d, 0, dz / d);
				e.setDeltaMovement(out.x * 0.35, -0.6, out.z * 0.35);
				e.needsSync = true;
				ZushiFx.send(level, ModParticles.ELECTRIC_ARC, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.3, 0.5, 0.3, 0.0);
			}
		}
		// destruction progressive : le cratère s'élargit
		if (t == 10 || t == 22) {
			double k = t == 10 ? 0.8 : 1.0;
			GoroFx.throwGround(level, p, c, cfg.elThorCrater * k, 16, 0.8);
			ZushiTerrain.crater(level, c, cfg.elThorCrater * k, p, cfg.destroyTerrain);
			ZushiDisplay shock = ZushiDisplay.create(level, "goro_shock", c.add(0, 0.3, 0), 0.0F, 0.0F, 0.2, 0.0, 1, 6.0F, true);
			shock.scaleTo(cfg.elThorRadius * 2.6 / 1.5, 0.0, 12);
			GoroFx.temporary(shock, 13);
			ZushiFx.shake(level, c, 40.0, 2, 12);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.WEATHER, 5.0F, 0.6F);
		}
		// particules qui descendent le long du rayon
		for (int i = 0; i < 6; i++) {
			double a = r.nextDouble() * Math.PI * 2, rr = R * (0.6 + r.nextDouble() * 0.5);
			Vec3 q = new Vec3(c.x + Math.cos(a) * rr, s.bottom() + r.nextDouble() * height, c.z + Math.sin(a) * rr);
			ZushiFx.velocity(level, i % 2 == 0 ? ModParticles.ELECTRIC_BLUE : ModParticles.ELECTRIC_SPARK, q,
					new Vec3(0, -1.2 - r.nextDouble() * 0.8, 0), true);
		}
		for (int i = 0; i < 2; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			ZushiFx.far(level, ModParticles.ELECTRIC_BRANCH, c.x + Math.cos(a) * R, s.bottom() + r.nextDouble() * height, c.z + Math.sin(a) * R,
					1, 0.3, 1.0, 0.3, 0.0);
		}
		// jaillissements au pied de la colonne + poussière soulevée
		if (t % 2 == 0) {
			ZushiFx.circle(level, ModParticles.ELECTRIC_SPARK, c.add(0, 0.6, 0), R, 14, 0.25, 0.7, true);
			ZushiFx.circle(level, ModParticles.STORM_DUST, c.add(0, 0.4, 0), R * 1.2, 12, 0.12, 0.3, true);
		}
		// arcs électriques qui tournent autour de la colonne
		if (t % 2 == 1) {
			double a = r.nextDouble() * Math.PI * 2, h = s.bottom() + 3 + r.nextDouble() * Math.max(1.0, height - 6);
			Vec3 from = new Vec3(c.x + Math.cos(a) * R * 1.05, h, c.z + Math.sin(a) * R * 1.05);
			Vec3 to = new Vec3(c.x + Math.cos(a + 0.9) * R * 1.05, h - 5, c.z + Math.sin(a + 0.9) * R * 1.05);
			GoroFx.bolt(level, from, to, 0.16, 3, 1, true);
		}
		// ramifications qui frappent le terrain environnant
		if (t % 3 == 0) {
			double a = r.nextDouble() * Math.PI * 2, d = R * (1.6 + r.nextDouble() * 2.2);
			int gx = (int) Math.floor(c.x + Math.cos(a) * d), gz = (int) Math.floor(c.z + Math.sin(a) * d);
			double gy = level.isLoaded(new net.minecraft.core.BlockPos(gx, (int) c.y, gz))
					? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz) : c.y;
			if (Math.abs(gy - c.y) > 12) {
				gy = c.y;
			}
			Vec3 ground = new Vec3(gx + 0.5, gy, gz + 0.5);
			GoroFx.bolt(level, new Vec3(c.x + Math.cos(a) * R, s.bottom() + 4 + r.nextDouble() * 10, c.z + Math.sin(a) * R),
					ground, 0.22, 4, 1, true);
			ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, ground.x, ground.y + 0.3, ground.z, 12, 0.4, 0.2, 0.4, 0.25);
			ZushiFx.far(level, ModParticles.STORM_DUST, ground.x, ground.y + 0.5, ground.z, 4, 0.5, 0.3, 0.5, 0.03);
		}
		if (t % 10 == 5) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.0F, 0.6F + r.nextFloat() * 0.2F);
			ZushiFx.shake(level, c, 30.0, 1, 8);
		}
		if (t % 4 == 0) {
			level.playSound(null, c.x, c.y + 2, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 4.0F, 0.5F + r.nextFloat() * 0.3F);
		}
	}

	static void forget(UUID id, ServerPlayer p) {
		// la frappe continue même si le lanceur part ; seule la pose est retirée
		if (p != null && GoroPose.pose(p) == GoroPose.ARM_UP) {
			GoroPose.clear(p);
		}
	}

	static void cleanupAll() {
		for (Strike s : ACTIVE) {
			s.removeModels();
		}
		ACTIVE.clear();
	}
}
