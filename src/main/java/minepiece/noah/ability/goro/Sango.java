package minepiece.noah.ability.goro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import minepiece.noah.ability.FruitAbilities;
import minepiece.noah.ability.zushi.ZushiDisplay;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.particle.ModParticles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Attaque 1 : Sango (touche MAINTENUE, 3 niveaux, seuils dans config/minepiece-goro.json). Foudre BLEUE.
 * Le bras se transforme en électricité : la foudre part de la MAIN RÉELLE (bras tendu, GoroFx.handForward) et reste
 * reliée au corps par une connexion électrique vivante (GoroFx.Tether) pendant toute l'attaque.
 * - Niveau 1 : décharge continue main -> cible (zigzags et ramifications qui bougent, suit la cible), puis explosion
 *   électrique sur la cible ; la connexion disparaît.
 * - Niveau 2 : une masse électrique sort de la main et prend la forme du chien de foudre 3D (fx/goro_dog) ; il bondit sur
 *   la cible, toujours relié à la main par une chaîne d'éclairs qui le suit, puis explose.
 * - Niveau 3 : la tête du dragon se forme devant la main, le dragon articulé (tête + 9 segments + queue) sort du bras et
 *   ondule jusqu'à la cible, alimenté par un énorme flux électrique main -> queue ; énorme explosion.
 * Jauge de charge (barre d'action), arcs sur le bras / l'épaule / le torse, particules de charge aspirées vers la main,
 * sons et pose du bras tendu (gardée tant que la foudre est reliée au bras).
 */
final class Sango {
	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	private static final List<Dog> DOGS = new ArrayList<>();
	private static final List<Dragon> DRAGONS = new ArrayList<>();
	private static final Map<UUID, Long> POSE_UNTIL = new HashMap<>();
	private static final List<Discharge> DISCHARGES = new ArrayList<>();
	private static final int DISCHARGE_TICKS = 12;

	private static final double DOG_SCALE = 0.9;
	private static final double DRAGON_SCALE = 2.0;
	private static final int DRAGON_SEGMENTS = 9;
	private static final int DRAGON_SPACING = 2;

	private Sango() {}

	private static final class Charge {
		final ServerLevel level;
		int ticks;
		int lvl = 1;
		boolean released;
		ZushiDisplay preview;

		Charge(ServerLevel level) {
			this.level = level;
		}
	}

	/** Main droite réelle, bras tendu (voir GoroFx.handForward). */
	static Vec3 hand(ServerPlayer p) {
		return GoroFx.handForward(p);
	}

	/** Garde le bras tendu tant que la foudre est reliée à la main. */
	private static void holdPose(ServerPlayer p, ServerLevel level, int ticks) {
		POSE_UNTIL.put(p.getUUID(), level.getGameTime() + ticks);
	}

	static boolean start(ServerPlayer player, ServerLevel level) {
		if (CHARGES.containsKey(player.getUUID())) {
			return false;
		}
		CHARGES.put(player.getUUID(), new Charge(level));
		GoroPose.set(player, GoroPose.ARM_FORWARD);
		level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.8F);
		return true;
	}

	static void release(ServerPlayer player) {
		Charge c = CHARGES.get(player.getUUID());
		if (c != null) {
			c.released = true;
		}
	}

	static void forget(UUID id) {
		Charge c = CHARGES.remove(id);
		if (c != null && c.preview != null) {
			c.preview.remove();
		}
		POSE_UNTIL.remove(id);
	}

	static boolean charging(UUID id) {
		return CHARGES.containsKey(id);
	}

	private static String gauge(int ticks, int lvl) {
		GoroConfig cfg = GoroConfig.get();
		int total = 12;
		int filled = (int) Math.round(total * Math.min(1.0, ticks / (double) cfg.sangoLevel3Ticks));
		StringBuilder sb = new StringBuilder("§bSango §7[");
		for (int i = 0; i < total; i++) {
			sb.append(i < filled ? (lvl == 3 ? "§f" : lvl == 2 ? "§b" : "§3") : "§8").append('|');
		}
		sb.append("§7] §f").append(lvl == 1 ? "Éclair" : lvl == 2 ? "Chien de foudre" : "Dragon de foudre");
		return sb.toString();
	}

	static void tick(MinecraftServer server) {
		GoroConfig cfg = GoroConfig.get();
		for (Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Charge> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Charge c = e.getValue();
			if (p == null || !p.isAlive() || p.level() != c.level) {
				if (c.preview != null) {
					c.preview.remove();
				}
				it.remove();
				continue;
			}
			c.ticks++;
			int lvl = c.ticks >= cfg.sangoLevel3Ticks ? 3 : c.ticks >= cfg.sangoLevel2Ticks ? 2 : 1;
			if (lvl != c.lvl) {
				c.lvl = lvl;
				if (c.preview != null) {
					c.preview.remove();
				}
				Vec3 h = hand(p);
				c.preview = ZushiDisplay.create(c.level, lvl == 2 ? "goro_dog" : "goro_dragon_head", h, p.getYRot(), p.getXRot(),
						0.05, 0.0, 1, 3.0F, true);
				// la masse électrique jaillit de la main
				GoroFx.bolt(c.level, h, h.add(p.getLookAngle().scale(1.5)), 0.08, 3, 2, false);
				c.level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.2F + lvl * 0.2F);
				c.level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, lvl == 3 ? 1.5F : 0.6F, 1.6F);
				ZushiFx.circle(c.level, ModParticles.ELECTRIC_SPARK, p.position().add(0, 1, 0), 1.0, 16 * lvl, 0.1, 0.25, false);
			}
			chargeFx(p, c);
			if (c.ticks % 2 == 0) {
				p.sendSystemMessage(Component.literal(gauge(c.ticks, c.lvl)), true);
			}
			if (c.released || c.ticks >= cfg.sangoMaxChargeTicks) {
				it.remove();
				if (c.preview != null) {
					c.preview.remove();
				}
				launch(p, c.level, c.lvl);
			}
		}
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, Long>> it = POSE_UNTIL.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> e = it.next();
			if (now >= e.getValue()) {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null && GoroPose.pose(p) == GoroPose.ARM_FORWARD && !CHARGES.containsKey(e.getKey())) {
					GoroPose.clear(p);
				}
				it.remove();
			}
		}
		for (Iterator<Discharge> it = DISCHARGES.iterator(); it.hasNext();) {
			if (!it.next().tick(server)) {
				it.remove();
			}
		}
		for (Iterator<Dog> it = DOGS.iterator(); it.hasNext();) {
			if (!it.next().tick(server)) {
				it.remove();
			}
		}
		for (Iterator<Dragon> it = DRAGONS.iterator(); it.hasNext();) {
			if (!it.next().tick(server)) {
				it.remove();
			}
		}
	}

	/** Effets de charge : étincelles dans la main, masse électrique qui prend forme, aura et éclairs au niveau 3. */
	private static void chargeFx(ServerPlayer p, Charge c) {
		ServerLevel level = c.level;
		RandomSource r = level.getRandom();
		Vec3 h = hand(p);
		// le bras se charge : arcs sur le bras, l'épaule et le torse, énergie aspirée vers la main
		GoroFx.armArcs(level, p, h, true, c.lvl / 3.0, c.ticks);
		ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, h.x, h.y, h.z, 2 * c.lvl, 0.12 * c.lvl, 0.12 * c.lvl, 0.12 * c.lvl, 0.04);
		if (c.lvl >= 2) {
			// électricité bleue autour de tout le corps
			ZushiFx.send(level, ModParticles.ELECTRIC_BLUE, p.getX(), p.getY() + 1.0, p.getZ(), c.lvl, 0.45, 0.8, 0.45, 0.02);
			ZushiFx.send(level, ModParticles.ELECTRIC_ARC, p.getX(), p.getY() + 1.0, p.getZ(), c.lvl - 1, 0.4, 0.8, 0.4, 0.0);
		}
		if (c.preview != null) {
			GoroConfig cfg = GoroConfig.get();
			double k = c.lvl == 2 ? Math.min(1.0, (c.ticks - cfg.sangoLevel2Ticks) / 15.0)
					: Math.min(1.0, (c.ticks - cfg.sangoLevel3Ticks) / 20.0);
			double target = c.lvl == 2 ? DOG_SCALE * 0.45 : DRAGON_SCALE * 0.4;
			// la masse sort de la main et s'en éloigne en prenant forme
			Vec3 ahead = h.add(p.getLookAngle().scale((c.lvl == 2 ? 0.8 : 1.4) * (0.3 + 0.7 * k)));
			if (c.ticks % 2 == 0) {
				GoroFx.bolt(level, h, ahead, 0.04 + 0.03 * c.lvl, 2, 0, false);
			}
			c.preview.move(ahead, p.getYRot(), p.getXRot());
			if (c.ticks % 3 == 0) {
				c.preview.scaleTo(Math.max(0.05, target * (0.15 + 0.85 * k) * (1.0 + 0.06 * Math.sin(c.ticks))), 0.0, 3);
			}
		}
		if (c.lvl == 3) {
			// aura autour du corps, éclairs le long des bras, grondement
			ZushiFx.send(level, ModParticles.ELECTRIC_BLUE, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.5, 0.9, 0.5, 0.03);
			Vec3 shoulder = p.position().add(0, 1.4, 0);
			for (int i = 0; i < 4; i++) {
				Vec3 q = shoulder.add(h.subtract(shoulder).scale(r.nextDouble()));
				ZushiFx.send(level, ModParticles.ELECTRIC_ARC, q.x, q.y, q.z, 1, 0.08, 0.08, 0.08, 0.0);
			}
			if (c.ticks % 5 == 0) {
				Vec3 to = h.add((r.nextDouble() - 0.5) * 3, (r.nextDouble() - 0.5) * 2, (r.nextDouble() - 0.5) * 3);
				GoroFx.bolt(level, h, to, 0.06, 2, 1, false);
			}
			if (c.ticks % 20 == 0) {
				level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.4F);
			}
		} else if (c.ticks % 6 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.25F + 0.15F * c.lvl, 1.9F);
		}
	}

	/** Visée : première entité vivante sur la ligne de visée, sinon le bloc visé, sinon le point à portée max. */
	static Target aim(ServerPlayer p, ServerLevel level, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 blockEnd = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		AABB search = new AABB(eye, blockEnd).inflate(1.5);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, search, e -> e.isAlive() && e != p && !e.isSpectator())) {
			Optional<Vec3> o = e.getBoundingBox().inflate(0.4).clip(eye, blockEnd);
			if (o.isPresent()) {
				double d = o.get().distanceToSqr(eye);
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
		}
		if (best != null) {
			return new Target(best.position().add(0, best.getBbHeight() * 0.5, 0), best);
		}
		return new Target(blockEnd, null);
	}

	record Target(Vec3 pos, LivingEntity entity) {}

	private static void launch(ServerPlayer p, ServerLevel level, int lvl) {
		GoroConfig cfg = GoroConfig.get();
		holdPose(p, level, 10);
		FruitAbilities.startCooldown(p, 0, DevilFruit.GORO_GORO_NO_MI.cooldownTicks(0));
		Vec3 h = hand(p);
		Target t = aim(p, level, cfg.sangoRange);
		if (lvl == 1) {
			// décharge continue, reliée à la main
			DISCHARGES.add(new Discharge(level, p, t));
			level.playSound(null, h.x, h.y, h.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 1.6F);
			level.playSound(null, h.x, h.y, h.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 2.0F);
			p.sendSystemMessage(Component.literal("§bSango !"), true);
		} else if (lvl == 2) {
			DOGS.add(new Dog(level, p, h.add(p.getLookAngle().scale(0.8)), t));
			level.playSound(null, h.x, h.y, h.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.6F);
			level.playSound(null, h.x, h.y, h.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.5F, 1.2F);
			p.sendSystemMessage(Component.literal("§bSango : Chien de foudre !"), true);
		} else {
			DRAGONS.add(new Dragon(level, p, h.add(p.getLookAngle().scale(1.4)), t));
			level.playSound(null, h.x, h.y, h.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0F, 1.3F);
			level.playSound(null, h.x, h.y, h.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.5F, 1.0F);
			p.sendSystemMessage(Component.literal("§bSango : Dragon de foudre !"), true);
		}
	}

	/** Lanceur toujours là (même monde, en vie) : sinon la connexion électrique est coupée. */
	private static ServerPlayer connected(MinecraftServer server, UUID owner, ServerLevel level) {
		ServerPlayer p = server.getPlayerList().getPlayer(owner);
		return p != null && p.isAlive() && p.level() == level && !GoroAbilities.isKo(owner) ? p : null;
	}

	// ------------------------------------------------------------------ niveau 1 : décharge reliée à la main

	private static final class Discharge {
		final ServerLevel level;
		final UUID owner;
		final LivingEntity target;
		final GoroFx.Tether tether;
		Vec3 end;
		int age;

		Discharge(ServerLevel level, ServerPlayer p, Target t) {
			this.level = level;
			this.owner = p.getUUID();
			this.target = t.entity();
			this.end = t.pos();
			this.tether = GoroFx.tether(level, 10, 3, 0.16, false);
		}

		boolean tick(MinecraftServer server) {
			age++;
			GoroConfig cfg = GoroConfig.get();
			ServerPlayer p = connected(server, owner, level);
			if (target != null && target.isAlive()) {
				end = target.position().add(0, target.getBbHeight() * 0.5, 0);
			}
			if (p != null) {
				Vec3 h = hand(p);
				tether.update(h, end);
				holdPose(p, level, 6);
				GoroFx.armArcs(level, p, h, true, 0.8, age);
			}
			ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.2);
			ZushiFx.send(level, ModParticles.ELECTRIC_ARC, end.x, end.y, end.z, 2, 0.3, 0.3, 0.3, 0.05);
			if (age == 1 && target != null) {
				target.hurtServer(level, p != null ? level.damageSources().playerAttack(p) : level.damageSources().lightningBolt(),
						(float) (cfg.sangoBoltDamage * cfg.damageMultiplier));
				Vec3 kb = target.position().subtract(end.subtract(0, 1, 0)).normalize().scale(0.2);
				target.setDeltaMovement(target.getDeltaMovement().add(kb.x, 0.1, kb.z));
				target.needsSync = true;
			}
			if (age % 4 == 0) {
				level.playSound(null, end.x, end.y, end.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.7F, 1.7F);
			}
			if (age >= DISCHARGE_TICKS || p == null) {
				tether.remove();
				// la décharge se termine par une explosion électrique sur la cible
				GoroFx.explode(level, p, end, 2.2, cfg.sangoBoltDamage * 0.35, 0.0, 0.15);
				return false;
			}
			return true;
		}
	}

	private static float yawOf(Vec3 d) {
		return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
	}

	private static float pitchOf(Vec3 d) {
		double len = d.length();
		return len < 1.0E-6 ? 0.0F : (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, d.y / len))));
	}

	private static boolean hits(ServerLevel level, Vec3 pos, double radius, UUID owner) {
		if (level.isLoaded(net.minecraft.core.BlockPos.containing(pos)) && level.getBlockState(net.minecraft.core.BlockPos.containing(pos)).isSolid()) {
			return true;
		}
		AABB box = new AABB(pos, pos).inflate(radius);
		return !level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(owner)).isEmpty();
	}

	// ------------------------------------------------------------------ niveau 2 : chien de foudre

	private static final class Dog {
		final ServerLevel level;
		final UUID owner;
		final ZushiDisplay model;
		final LivingEntity target;
		final GoroFx.Tether tether;
		Vec3 pos;
		Vec3 dir;
		int age;

		Dog(ServerLevel level, ServerPlayer p, Vec3 hand, Target t) {
			this.tether = GoroFx.tether(level, 9, 2, 0.12, false);
			this.level = level;
			this.owner = p.getUUID();
			this.target = t.entity();
			this.pos = hand;
			Vec3 d = t.pos().subtract(hand);
			this.dir = d.lengthSqr() < 1.0E-4 ? p.getLookAngle() : d.normalize();
			this.model = ZushiDisplay.create(level, "goro_dog", hand, yawOf(dir), pitchOf(dir), DOG_SCALE * 0.45, 0.0, 1, 3.0F, true);
			this.model.scaleTo(DOG_SCALE, 0.0, 4);   // finit de prendre forme en jaillissant du bras
		}

		boolean tick(MinecraftServer server) {
			age++;
			RandomSource r = level.getRandom();
			if (target != null && target.isAlive()) {
				Vec3 want = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(pos);
				if (want.lengthSqr() > 1.0E-4) {
					dir = dir.add(want.normalize().subtract(dir).scale(0.25)).normalize();
				}
			}
			double speed = 1.15;
			Vec3 bound = new Vec3(0, Math.abs(Math.sin(age * 0.55)) * 0.35 - 0.12, 0);   // bonds
			Vec3 next = pos.add(dir.scale(speed));
			pos = next;
			Vec3 shown = pos.add(bound);
			model.move(shown, yawOf(dir), pitchOf(dir) + (float) Math.sin(age * 0.55) * 12.0F);
			// traînée électrique + petites ramifications autour du corps, des pattes et de la gueule
			ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, shown.x, shown.y, shown.z, 6, 0.4, 0.3, 0.4, 0.05);
			ZushiFx.send(level, ModParticles.ELECTRIC_ARC, shown.x, shown.y, shown.z, 2, 0.5, 0.4, 0.5, 0.02);
			ZushiFx.send(level, ModParticles.ELECTRIC_ARC, shown.x, shown.y - 0.4, shown.z, 2, 0.35, 0.1, 0.35, 0.0);
			Vec3 mouth = shown.add(dir.scale(1.2 * DOG_SCALE));
			ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, mouth.x, mouth.y, mouth.z, 2, 0.15, 0.15, 0.15, 0.08);
			if (age % 3 == 0) {
				Vec3 a = shown.add((r.nextDouble() - 0.5) * 1.2, (r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 1.2);
				GoroFx.bolt(level, shown, a, 0.05, 2, 0, false);
			}
			// la chaîne d'éclairs main -> chien suit ses bonds
			ServerPlayer p = connected(server, owner, level);
			if (p != null) {
				Vec3 h = hand(p);
				tether.update(h, shown.subtract(dir.scale(0.45 * DOG_SCALE)));
				holdPose(p, level, 6);
				GoroFx.armArcs(level, p, h, true, 0.6, age);
			}
			if (age > 2 && (hits(level, pos, 1.1, owner) || age >= 60)) {
				GoroConfig cfg = GoroConfig.get();
				tether.remove();
				model.remove();
				GoroFx.explode(level, p, pos, cfg.sangoDogRadius, cfg.sangoDogDamage, cfg.sangoDogCrater, 0.3);
				return false;
			}
			return true;
		}
	}

	// ------------------------------------------------------------------ niveau 3 : dragon de foudre

	private static final class Dragon {
		final ServerLevel level;
		final UUID owner;
		final LivingEntity target;
		final Vec3 aimPos;
		final ZushiDisplay head;
		final ZushiDisplay[] body = new ZushiDisplay[DRAGON_SEGMENTS + 1];
		final List<Vec3> path = new ArrayList<>();
		final Vec3 side;
		final Vec3 up;
		final GoroFx.Tether tether;
		Vec3 base;
		Vec3 dir;
		int age;

		Dragon(ServerLevel level, ServerPlayer p, Vec3 hand, Target t) {
			this.level = level;
			this.owner = p.getUUID();
			this.target = t.entity();
			this.aimPos = t.pos();
			this.base = hand;
			Vec3 d = t.pos().subtract(hand);
			this.dir = d.lengthSqr() < 1.0E-4 ? p.getLookAngle() : d.normalize();
			Vec3 s = dir.cross(new Vec3(0, 1, 0));
			this.side = s.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : s.normalize();
			this.up = side.cross(dir).normalize();
			this.tether = GoroFx.tether(level, 12, 3, 0.3, true);
			this.head = ZushiDisplay.create(level, "goro_dragon_head", hand, yawOf(dir), pitchOf(dir), DRAGON_SCALE * 0.4, 0.0, 1, 4.0F, true);
			this.head.scaleTo(DRAGON_SCALE, 0.0, 5);
			path.add(hand);
		}

		boolean tick(MinecraftServer server) {
			age++;
			RandomSource r = level.getRandom();
			if (target != null && target.isAlive()) {
				Vec3 want = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(base);
				if (want.lengthSqr() > 1.0E-4) {
					dir = dir.add(want.normalize().subtract(dir).scale(0.15)).normalize();
				}
			}
			base = base.add(dir.scale(1.35));
			// ondulation serpentine autour de la trajectoire
			double amp = Math.min(1.0, age / 8.0);
			Vec3 pos = base.add(side.scale(Math.sin(age * 0.38) * 1.6 * amp)).add(up.scale(Math.cos(age * 0.27) * 0.8 * amp));
			Vec3 prev = path.get(path.size() - 1);
			path.add(pos);
			Vec3 hd = pos.subtract(prev);
			head.move(pos, yawOf(hd), pitchOf(hd));
			// le corps sort du bras segment par segment et suit la tête
			for (int i = 0; i < body.length; i++) {
				int idx = path.size() - 1 - (i + 1) * DRAGON_SPACING;
				if (idx < 0) {
					break;
				}
				Vec3 sp = path.get(idx);
				Vec3 ahead = path.get(Math.min(path.size() - 1, idx + DRAGON_SPACING));
				Vec3 sd = ahead.subtract(sp);
				if (body[i] == null) {
					boolean tail = i == body.length - 1;
					body[i] = ZushiDisplay.create(level, tail ? "goro_dragon_tail" : "goro_dragon_body", sp, yawOf(sd), pitchOf(sd),
							DRAGON_SCALE * (tail ? 0.85 : 1.0 - i * 0.04), 0.0, 1, 4.0F, true);
				} else {
					body[i].move(sp, yawOf(sd), pitchOf(sd));
				}
				if ((age + i) % 2 == 0) {
					ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, sp.x, sp.y, sp.z, 3, 0.6, 0.6, 0.6, 0.06);
				}
			}
			// énorme traînée électrique + éclairs autour du passage
			ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 10, 0.9, 0.9, 0.9, 0.08);
			ZushiFx.far(level, ModParticles.ELECTRIC_BLUE, pos.x, pos.y, pos.z, 5, 1.0, 1.0, 1.0, 0.05);
			ZushiFx.far(level, ModParticles.ELECTRIC_ARC, pos.x, pos.y, pos.z, 4, 1.0, 1.0, 1.0, 0.03);
			if (age % 3 == 0) {
				Vec3 a = pos.add((r.nextDouble() - 0.5) * 5, (r.nextDouble() - 0.5) * 4, (r.nextDouble() - 0.5) * 5);
				GoroFx.bolt(level, pos, a, 0.08, 3, 1, true);
			}
			if (age % 8 == 0) {
				level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0F, 0.8F);
			}
			// décharges qui parcourent le corps (d'un segment au suivant)
			if (age % 2 == 0) {
				int i = r.nextInt(body.length);
				Entity a = body[i] != null ? body[i].entity() : null;
				if (a != null) {
					Vec3 sa = a.position();
					GoroFx.bolt(level, sa, sa.add((r.nextDouble() - 0.5) * 2.5, (r.nextDouble() - 0.5) * 2.5, (r.nextDouble() - 0.5) * 2.5), 0.07, 2, 0, true);
				}
			}
			// énorme flux électrique : main -> queue du dragon (ou le dernier point sorti du bras)
			ServerPlayer caster = connected(server, owner, level);
			if (caster != null) {
				Vec3 h = hand(caster);
				Vec3 tailEnd = path.get(Math.max(0, path.size() - 1 - body.length * DRAGON_SPACING - 2));
				tether.update(h, tailEnd);
				holdPose(caster, level, 6);
				GoroFx.armArcs(level, caster, h, true, 1.0, age);
			}
			boolean reached = pos.distanceToSqr(aimPos) < 2.0 && target == null;
			if (age > 3 && (hits(level, pos, 1.8, owner) || reached || age >= 70)) {
				explode(server, pos);
				return false;
			}
			return true;
		}

		/** Le dragon disparaît dans une grande explosion de foudre (chaque segment éclate). */
		void explode(MinecraftServer server, Vec3 pos) {
			GoroConfig cfg = GoroConfig.get();
			ServerPlayer p = server.getPlayerList().getPlayer(owner);
			RandomSource r = level.getRandom();
			tether.remove();
			head.remove();
			for (ZushiDisplay d : body) {
				if (d == null) {
					continue;
				}
				Entity e = d.entity();
				if (e != null) {
					Vec3 sp = e.position();
					ZushiFx.far(level, ModParticles.ELECTRIC_SPARK, sp.x, sp.y, sp.z, 20, 0.8, 0.8, 0.8, 0.3);
					ZushiFx.far(level, ModParticles.ELECTRIC_ARC, sp.x, sp.y, sp.z, 8, 0.8, 0.8, 0.8, 0.1);
					if (r.nextBoolean()) {
						GoroFx.bolt(level, sp, sp.add((r.nextDouble() - 0.5) * 4, -2 - r.nextDouble() * 3, (r.nextDouble() - 0.5) * 4), 0.1, 3, 0, true);
					}
				}
				d.remove();
			}
			GoroFx.explode(level, p, pos, cfg.sangoDragonRadius, cfg.sangoDragonDamage, cfg.sangoDragonCrater, 0.75);
		}

		void removeNow() {
			tether.remove();
			head.remove();
			for (ZushiDisplay d : body) {
				if (d != null) {
					d.remove();
				}
			}
		}
	}

	static void cleanupAll() {
		for (Charge c : CHARGES.values()) {
			if (c.preview != null) {
				c.preview.remove();
			}
		}
		CHARGES.clear();
		for (Discharge d : DISCHARGES) {
			d.tether.remove();
		}
		DISCHARGES.clear();
		for (Dog d : DOGS) {
			d.tether.remove();
			d.model.remove();
		}
		DOGS.clear();
		for (Dragon d : DRAGONS) {
			d.removeNow();
		}
		DRAGONS.clear();
		POSE_UNTIL.clear();
	}
}
