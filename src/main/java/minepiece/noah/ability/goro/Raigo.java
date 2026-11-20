package minepiece.noah.ability.goro;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.ability.FruitAbilities;
import minepiece.noah.ability.zushi.ZushiDisplay;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Attaque 3 : Raigo (touche MAINTENUE) — une immense SPHÈRE D'ORAGE sombre d'où jaillissent des éclairs bleus.
 * Les deux mains levées, le joueur fait naître d'immenses cumulonimbus noirs qui tournent ; au-dessus de lui se forme une
 * vraie sphère 3D (fx/goro_sphere : 288 plaques, bleu nuit presque noir, nuances bleutées, zones de lumière blanc bleuté,
 * éclairs bleus animés à la surface) entourée d'une enveloppe d'éclairs animés qui tourne en sens inverse
 * (fx/goro_sphere_veins) et de 2 anneaux électriques. Le modèle grossit réellement et en continu avec la charge ; les
 * éclairs qui jaillissent de sa surface dans toutes les directions (ramifiés, visibles de loin) se multiplient, le
 * grondement s'intensifie. Au relâchement, il la projette : elle avance de façon imposante, toujours sombre, crache des
 * éclairs et frappe le sol sous son passage ; au contact elle se contracte puis explose (lumière bleu-blanc, onde de choc
 * géante, éclairs partout, cratère, blocs projetés) d'autant plus fort qu'elle a été chargée. Puis les nuages se dissipent.
 */
final class Raigo {
	/** Rayon visuel du modèle de sphère à l'échelle 1 (sphère de 11,5 unités de rayon). */
	private static final double SPHERE_R = 0.72;
	/** Rayon de l'anneau à l'échelle 1 (plan de 48 unités, anneau à 86 %). */
	private static final double RING_R = 1.29;
	private static final int OVERCHARGE_HOLD = 80;

	private static final Map<UUID, Orb> ORBS = new HashMap<>();

	private Raigo() {}

	private static final class Orb {
		final ServerLevel level;
		final UUID owner;
		final GoroFx.Storm storm;
		final ZushiDisplay shell;
		final ZushiDisplay core;
		final ZushiDisplay ringA;
		final ZushiDisplay ringB;
		int ticks;
		boolean released;
		boolean flying;
		int impactAge = -1;
		Vec3 pos;
		Vec3 dir = Vec3.ZERO;
		Vec3 impact;
		double charge;   // 0 .. 1
		double spin;
		int flightAge;

		Orb(ServerLevel level, ServerPlayer p) {
			this.level = level;
			this.owner = p.getUUID();
			GoroConfig cfg = GoroConfig.get();
			this.pos = above(p, cfg.raigoMinSphere);
			double maxY = level.getMaxY() - 10.0;
			this.storm = GoroFx.storm(level, new Vec3(p.getX(), Math.min(maxY, p.getY() + 34), p.getZ()), 52.0, 9, 70);
			this.shell = ZushiDisplay.create(level, "goro_sphere", pos, 0.0F, 0.0F, 0.05, 0.0, 1, 8.0F, true);
			// enveloppe d'éclairs animés (tourne en sens inverse de la sphère : les éclairs bougent à la surface)
			this.core = ZushiDisplay.create(level, "goro_sphere_veins", pos, 0.0F, 0.0F, 0.05, 0.0, 1, 8.0F, true);
			this.ringA = ZushiDisplay.create(level, "goro_ring", pos, 0.0F, 0.0F, 0.05, 0.0, 1, 8.0F, true);
			this.ringB = ZushiDisplay.create(level, "goro_ring", pos, 0.0F, 0.0F, 0.05, 0.0, 1, 8.0F, true);
		}

		double radius() {
			GoroConfig cfg = GoroConfig.get();
			double t = charge * charge * (3 - 2 * charge);
			return cfg.raigoMinSphere + (cfg.raigoMaxSphere - cfg.raigoMinSphere) * t;
		}

		void place(Vec3 p, int interp) {
			double r = radius();
			shell.move(p, 0.0F, 0.0F);
			core.move(p, 0.0F, 0.0F);
			ringA.move(p, 0.0F, 0.0F);
			ringB.move(p, 0.0F, 0.0F);
			if (interp > 0) {
				spin += 9.0;
				shell.setTransform(0.15, 1, 0.1, spin, r / SPHERE_R, 0.0, interp);
				core.setTransform(0.3, -1, 0.2, -spin * 1.3, r / SPHERE_R * (1.0 + 0.03 * Math.sin(spin * 0.3)), 0.0, interp);
				ringA.setTransform(0.35, 1, 0, spin * 2.2, r * 1.35 / RING_R, 0.0, interp);
				ringB.setTransform(1, 0.25, 0, spin * 1.7 + 90, r * 1.55 / RING_R, 0.0, interp);
			}
		}

		void removeModels() {
			shell.remove();
			core.remove();
			ringA.remove();
			ringB.remove();
		}
	}

	private static Vec3 above(ServerPlayer p, double r) {
		return p.position().add(0, p.getBbHeight() + 2.5 + r, 0);
	}

	static boolean start(ServerPlayer p, ServerLevel level) {
		Orb o = ORBS.get(p.getUUID());
		if (o != null) {
			return false;
		}
		ORBS.put(p.getUUID(), new Orb(level, p));
		GoroPose.set(p, GoroPose.BOTH_UP);
		level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 0.5F);
		return true;
	}

	static void release(ServerPlayer p) {
		Orb o = ORBS.get(p.getUUID());
		if (o != null && !o.flying) {
			o.released = true;
		}
	}

	static boolean charging(UUID id) {
		Orb o = ORBS.get(id);
		return o != null && !o.flying;
	}

	static void tick(MinecraftServer server) {
		GoroConfig cfg = GoroConfig.get();
		for (Iterator<Map.Entry<UUID, Orb>> it = ORBS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Orb> e = it.next();
			Orb o = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (!o.flying) {
				if (p == null || !p.isAlive() || p.level() != o.level) {
					// le lanceur est parti : la sphère se disperse
					o.removeModels();
					o.storm.dissipate();
					it.remove();
					continue;
				}
				o.ticks++;
				o.charge = Math.min(1.0, o.ticks / (double) cfg.raigoMaxChargeTicks);
				o.pos = above(p, o.radius());
				o.place(o.pos, o.ticks % 3 == 0 ? 3 : 0);
				chargeFx(p, o);
				if (o.ticks % 2 == 0) {
					int pct = (int) Math.round(o.charge * 100);
					StringBuilder sb = new StringBuilder("§bRaigo §7[");
					for (int i = 0; i < 14; i++) {
						sb.append(i < Math.round(14 * o.charge) ? (o.charge >= 1.0 ? "§f" : "§b") : "§8").append('|');
					}
					p.sendSystemMessage(Component.literal(sb.append("§7] §f").append(pct).append(" %").toString()), true);
				}
				if (o.released || o.ticks >= cfg.raigoMaxChargeTicks + OVERCHARGE_HOLD) {
					launch(p, o);
				}
			} else if (!tickFlight(server, o)) {
				it.remove();
			}
		}
	}

	private static void chargeFx(ServerPlayer p, Orb o) {
		ServerLevel level = o.level;
		RandomSource r = level.getRandom();
		double rad = o.radius();
		// aura électrique, éclairs sur les bras levés et le corps, particules qui montent du sol
		Vec3 handR = GoroFx.handUp(p, true);
		Vec3 handL = GoroFx.handUp(p, false);
		GoroFx.armArcs(level, p, handR, true, 0.4 + 0.6 * o.charge, o.ticks);
		GoroFx.armArcs(level, p, handL, false, 0.4 + 0.6 * o.charge, o.ticks + 1);
		ZushiFx.send(level, ModParticles.ELECTRIC_BLUE, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.45, 0.9, 0.45, 0.02);
		ZushiFx.send(level, ModParticles.ELECTRIC_ARC, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.35, 0.8, 0.35, 0.01);
		for (int i = 0; i < 2 + (int) (4 * o.charge); i++) {
			double a = r.nextDouble() * Math.PI * 2, d = 1 + r.nextDouble() * (3 + 5 * o.charge);
			ZushiFx.velocity(level, ModParticles.ELECTRIC_SPARK, p.position().add(Math.cos(a) * d, 0.1, Math.sin(a) * d),
					new Vec3(0, 0.15 + r.nextDouble() * 0.2, 0), false);
		}
		// surface de la sphère crépitante, nuages sombres autour, énergie aspirée vers la sphère
		ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, o.pos.x, o.pos.y, o.pos.z, (int) (4 + 10 * o.charge), rad * 0.7, rad * 0.7, rad * 0.7, 0.08);
		ZushiFx.far(level, ModParticles.ELECTRIC_BLUE, o.pos.x, o.pos.y, o.pos.z, (int) (2 + 6 * o.charge), rad * 0.8, rad * 0.8, rad * 0.8, 0.05);
		ZushiFx.far(level, ModParticles.ELECTRIC_BRANCH, o.pos.x, o.pos.y, o.pos.z, 1 + (int) (3 * o.charge), rad * 0.75, rad * 0.75, rad * 0.75, 0.0);
		if (o.ticks % 2 == 0) {
			ZushiFx.far(level, ModParticles.STORM_SMOKE, o.pos.x, o.pos.y - rad * 0.3, o.pos.z, 2 + (int) (4 * o.charge),
					rad * 1.1, rad * 0.4, rad * 1.1, 0.01);
		}
		for (int i = 0; i < 1 + (int) (3 * o.charge); i++) {
			Vec3 off = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize().scale(rad * 1.6 + 3);
			ZushiFx.velocity(level, ModParticles.ELECTRIC_CHARGE, o.pos, off, true);
		}
		// arcs entre les deux mains levées et la sphère
		if (o.ticks % 4 == 0) {
			Vec3 below = o.pos.add(0, -rad * 0.9, 0);
			GoroFx.bolt(level, (o.ticks / 4) % 2 == 0 ? handR : handL, below, 0.06 + 0.07 * o.charge, 2, 1, true);
		}
		// éclairs qui JAILLISSENT de la surface dans toutes les directions (ramifiés, de plus en plus nombreux)
		int out = o.charge < 0.15 ? (o.ticks % 3 == 0 ? 1 : 0) : 1 + (int) (1.6 * o.charge);
		for (int i = 0; i < out; i++) {
			Vec3 dir = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize();
			Vec3 a = o.pos.add(dir.scale(rad * 0.98));
			Vec3 side = dir.cross(new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5)).normalize();
			Vec3 b = a.add(dir.scale(rad * (0.4 + r.nextDouble() * 0.9) + 1.0)).add(side.scale(rad * 0.4 * r.nextDouble()));
			GoroFx.bolt(level, a, b, 0.07 + 0.16 * o.charge, 3, 2, true);
		}
		// arcs qui rampent sur la surface
		if (o.ticks % 3 == 1) {
			Vec3 d1 = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize();
			Vec3 d2 = d1.add(new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).scale(0.9)).normalize();
			GoroFx.bolt(level, o.pos.add(d1.scale(rad * 1.03)), o.pos.add(d2.scale(rad * 1.03)), 0.05 + 0.08 * o.charge, 2, 1, true);
		}
		if (o.ticks % Math.max(2, 6 - (int) (4 * o.charge)) == 0) {
			Vec3 dirUp = new Vec3((r.nextDouble() - 0.5) * 1.2, 1, (r.nextDouble() - 0.5) * 1.2).normalize();
			GoroFx.bolt(level, o.pos.add(dirUp.scale(rad)), o.storm.underside(r, 0.6), 0.1 + 0.2 * o.charge, 3, 1, true);
		}
		// bruit de l'électricité de plus en plus intense
		int every = Math.max(3, 14 - (int) (11 * o.charge));
		if (o.ticks % every == 0) {
			level.playSound(null, o.pos.x, o.pos.y, o.pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS,
					(float) (0.6 + 2.0 * o.charge), (float) (1.8 - 0.8 * o.charge));
		}
		if (o.ticks % 40 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, (float) (1.5 + 3 * o.charge), 0.6F);
		}
	}

	private static void launch(ServerPlayer p, Orb o) {
		GoroConfig cfg = GoroConfig.get();
		o.flying = true;
		Vec3 t = Sango.aim(p, o.level, cfg.raigoRange).pos();
		Vec3 d = t.subtract(o.pos);
		o.dir = d.lengthSqr() < 1.0E-4 ? p.getLookAngle() : d.normalize();
		GoroPose.set(p, GoroPose.THROW);
		FruitAbilities.startCooldown(p, 2, DevilFruit.GORO_GORO_NO_MI.cooldownTicks(2));
		p.sendSystemMessage(Component.literal("§bRaigo ! §7(" + Math.round(o.charge * 100) + " %)"), true);
		o.level.playSound(null, o.pos.x, o.pos.y, o.pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0F, 0.5F);
		o.level.playSound(null, o.pos.x, o.pos.y, o.pos.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0F, 0.5F);
	}

	/** @return false quand tout est fini */
	private static boolean tickFlight(MinecraftServer server, Orb o) {
		GoroConfig cfg = GoroConfig.get();
		ServerLevel level = o.level;
		RandomSource r = level.getRandom();
		ServerPlayer p = server.getPlayerList().getPlayer(o.owner);
		double rad = o.radius();
		if (o.impactAge >= 0) {
			o.impactAge++;
			if (o.impactAge == 3) {
				// après la brève contraction : explosion
				o.removeModels();
				double k = o.charge;
				double radius = cfg.raigoMinRadius + (cfg.raigoMaxRadius - cfg.raigoMinRadius) * k;
				double damage = cfg.raigoMinDamage + (cfg.raigoMaxDamage - cfg.raigoMinDamage) * k;
				double crater = cfg.raigoMinCrater + (cfg.raigoMaxCrater - cfg.raigoMinCrater) * k;
				GoroFx.explode(level, p, o.impact, radius, damage, crater, 1.0 + k);
				// énorme boule de lumière + seconde onde de choc
				// énorme boule de lumière bleu-blanc
				ZushiDisplay flash = ZushiDisplay.create(level, "goro_sphere_core", o.impact.add(0, 1, 0), 0.0F, 0.0F, rad / SPHERE_R, 0.0, 1, 8.0F, true);
				flash.setTransform(0.2, 1, 0.1, 60, radius * 0.7 / SPHERE_R, 0.0, 5);
				GoroFx.temporary(flash, 7);
				ZushiDisplay shock = ZushiDisplay.create(level, "goro_shock", o.impact.add(0, 0.5, 0), 0.0F, 0.0F, 0.3, 0.0, 1, 8.0F, true);
				shock.setTransform(0, 1, 0, 45, radius * 3.0 / 1.5, 0.0, 16);
				GoroFx.temporary(shock, 17);
				for (int i = 0; i < 6 + (int) (8 * k); i++) {
					double a = r.nextDouble() * Math.PI * 2;
					Vec3 g = o.impact.add(Math.cos(a) * radius * (0.5 + r.nextDouble() * 0.8), 0.3, Math.sin(a) * radius * (0.5 + r.nextDouble() * 0.8));
					GoroFx.bolt(level, o.storm.underside(r, 1.0), g, 0.3 + 0.4 * k, 6, 2, true);
				}
				level.playSound(null, o.impact.x, o.impact.y, o.impact.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 16.0F, 0.5F);
			}
			if (o.impactAge == 30) {
				o.storm.dissipate();
			}
			return o.impactAge < 32;
		}
		o.flightAge++;
		if (o.flightAge == GoroPose.THROW_TICKS && p != null && GoroPose.pose(p) == GoroPose.THROW) {
			GoroPose.clear(p);
		}
		Vec3 prev = o.pos;
		double speed = 0.75 + 0.25 * Math.min(1.0, o.flightAge / 20.0);
		o.pos = o.pos.add(o.dir.scale(speed));
		o.place(o.pos, o.flightAge % 2 == 0 ? 2 : 0);
		// traînée énorme + éclairs émis en continu
		double rr = rad * 0.8;
		ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, o.pos.x, o.pos.y, o.pos.z, (int) (10 + 20 * o.charge), rr, rr, rr, 0.1);
		ZushiFx.far(level, ModParticles.ELECTRIC_BLUE, prev.x, prev.y, prev.z, (int) (6 + 12 * o.charge), rr, rr, rr, 0.04);
		ZushiFx.far(level, ModParticles.STORM_SMOKE, prev.x, prev.y, prev.z, (int) (2 + 4 * o.charge), rr * 0.6, rr * 0.6, rr * 0.6, 0.01);
		// les éclairs continuent de jaillir de la surface
		for (int i = 0; i < 1 + (int) (2 * o.charge); i++) {
			Vec3 out = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5).normalize();
			GoroFx.bolt(level, o.pos.add(out.scale(rad)), o.pos.add(out.scale(rad + 2 + r.nextDouble() * (2 + rad * 0.6))), 0.1 + 0.15 * o.charge, 3, 2, true);
		}
		ZushiFx.far(level, ModParticles.ELECTRIC_BRANCH, o.pos.x, o.pos.y, o.pos.z, 2, rad * 0.8, rad * 0.8, rad * 0.8, 0.0);
		// des arcs frappent le sol sous son passage
		if (o.flightAge % 5 == 0) {
			double a = r.nextDouble() * Math.PI * 2, d = rad * (0.5 + r.nextDouble() * 1.5);
			int gx = (int) Math.floor(o.pos.x + Math.cos(a) * d), gz = (int) Math.floor(o.pos.z + Math.sin(a) * d);
			BlockPos gp = new BlockPos(gx, (int) o.pos.y, gz);
			if (level.isLoaded(gp)) {
				int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
				if (o.pos.y - gy < 40) {
					Vec3 g = new Vec3(gx + 0.5, gy, gz + 0.5);
					GoroFx.bolt(level, o.pos.add(0, -rad * 0.9, 0), g, 0.15 + 0.15 * o.charge, 4, 2, true);
					ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, g.x, g.y + 0.3, g.z, 14, 0.5, 0.2, 0.5, 0.25);
					ZushiFx.far(level, ModParticles.STORM_DUST, g.x, g.y + 0.5, g.z, 5, 0.6, 0.3, 0.6, 0.03);
				}
			}
		}
		if (o.flightAge % 10 == 0) {
			level.playSound(null, o.pos.x, o.pos.y, o.pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 4.0F, 0.6F);
		}
		Vec3 hit = collide(level, o, prev);
		if (hit != null || o.flightAge > 220) {
			o.impact = hit != null ? hit : o.pos;
			o.impactAge = 0;
			// contraction très brève avant l'explosion
			o.shell.setTransform(0.15, 1, 0.1, o.spin, rad * 0.7 / SPHERE_R, 0.0, 2);
			o.core.setTransform(0, 1, 0, -o.spin, rad * 0.9 / SPHERE_R, 0.0, 2);
		}
		return true;
	}

	/** Collision avec le terrain (dessous et avant de la sphère, taille réelle) ou un ennemi. Ne charge aucun chunk. */
	private static Vec3 collide(ServerLevel level, Orb o, Vec3 prev) {
		double rc = o.radius() * 0.95;   // collision à la taille réelle de la sphère
		Vec3 seg = o.pos.subtract(prev);
		int steps = Math.max(1, (int) Math.ceil(seg.length()));
		Vec3[] probes = {new Vec3(0, -rc, 0), o.dir.scale(rc), o.dir.scale(rc * 0.6).add(0, -rc * 0.6, 0)};
		for (int i = 1; i <= steps; i++) {
			Vec3 c = prev.add(seg.scale(i / (double) steps));
			for (Vec3 off : probes) {
				Vec3 q = c.add(off);
				BlockPos bp = BlockPos.containing(q);
				if (!level.isLoaded(bp)) {
					continue;
				}
				BlockState st = level.getBlockState(bp);
				if (st.isSolid() && !st.is(BlockTags.LEAVES)) {
					o.pos = c;
					return new Vec3(q.x, bp.getY() + 1.0, q.z);
				}
			}
		}
		AABB box = new AABB(o.pos, o.pos).inflate(rc);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(o.owner))) {
			int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.getBlockX(), e.getBlockZ());
			return new Vec3(e.getX(), Math.max(gy, e.getY() - 1), e.getZ());
		}
		return null;
	}

	static void forget(UUID id) {
		Orb o = ORBS.get(id);
		if (o != null && !o.flying) {
			o.removeModels();
			o.storm.dissipate();
			ORBS.remove(id);
		}
	}

	static void cleanupAll() {
		for (Orb o : ORBS.values()) {
			o.removeModels();
		}
		ORBS.clear();
	}
}
