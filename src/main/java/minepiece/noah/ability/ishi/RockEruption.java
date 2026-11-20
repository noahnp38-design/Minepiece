package minepiece.noah.ability.ishi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.ability.zushi.ZushiDisplay;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.ability.zushi.ZushiTerrain;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * W — Éruption rocheuse. Le joueur lève le bras puis tend la main vers le sol visé : le terrain tremble, des fissures
 * réelles apparaissent sur les blocs, poussière et gravier s'échappent, puis d'énormes pics rocheux SORTENT du sol
 * (modèles 3D enterrés qui remontent, interpolés côté client), restent dressés puis s'effondrent dans le sol.
 * - les pics puisent dans la roche du terrain : pas de pierre sous une colonne = pas de pic à cet endroit ;
 * - matière (texture) du pic = roche trouvée sous lui ; petite excavation à sa base si la destruction est autorisée ;
 * - chaque entité n'est touchée qu'UNE fois par lancer (pas de dégâts à chaque tick) ;
 * - version Titan : véritable forêt de pics géants en vague, décalés selon la distance au titan.
 */
public final class RockEruption {
	private static final String[] SHAPES = {"a", "b", "c", "d"};
	private static final Map<UUID, Cast> CASTS = new HashMap<>();

	private RockEruption() {}

	private static final class Spike {
		final BlockPos ground;
		final BlockState rock;
		final int mat;
		final double height;
		final double scale;
		final int delay;
		final String model;
		final float yaw;
		ZushiDisplay d;
		int age = -1;
		boolean done;

		Spike(BlockPos ground, BlockState rock, double height, int delay, RandomSource r) {
			this.ground = ground;
			this.rock = rock;
			this.mat = IshiFx.material(rock);
			this.height = height;
			this.scale = height / 2.7;
			this.delay = delay;
			this.model = "ishi_spike_" + SHAPES[r.nextInt(SHAPES.length)] + "_" + IshiAnim.MATS[mat];
			this.yaw = r.nextFloat() * 360.0F;
		}

		double top() {
			return ground.getY() + 1.0;
		}

		/** Position du centre du modèle (le modèle fait 3 blocs de haut à l'échelle 1, centré). */
		Vec3 up() {
			return new Vec3(ground.getX() + 0.5, top() + 1.5 * scale - 0.35 * scale, ground.getZ() + 0.5);
		}

		Vec3 buried() {
			return new Vec3(ground.getX() + 0.5, top() - 1.5 * scale - 0.3, ground.getZ() + 0.5);
		}
	}

	private static final class Cast {
		final ServerPlayer owner;
		final ServerLevel level;
		final Vec3 target;
		final boolean titan;
		final List<Spike> spikes = new ArrayList<>();
		final Set<UUID> hit = new HashSet<>();
		final BlockState ground;
		int age;

		Cast(ServerPlayer owner, ServerLevel level, Vec3 target, boolean titan, BlockState ground) {
			this.owner = owner;
			this.level = level;
			this.target = target;
			this.titan = titan;
			this.ground = ground;
		}
	}

	static boolean cast(ServerPlayer p, ServerLevel level, boolean titan) {
		if (CASTS.containsKey(p.getUUID())) {
			return false;
		}
		IshiConfig cfg = IshiConfig.get();
		double range = titan ? cfg.titanEruptionRange : cfg.eruptionRange;
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 target;
		if (hit.getType() == HitResult.Type.BLOCK) {
			target = hit.getLocation();
		} else if (titan) {
			// le titan vise loin : point au sol sous l'extrémité du regard
			int x = (int) Math.floor(end.x);
			int z = (int) Math.floor(end.z);
			target = new Vec3(end.x, IshiFx.surface(level, x, z) + 1, end.z);
		} else {
			p.sendSystemMessage(Component.literal("§7Vise le sol pour faire jaillir la pierre."), true);
			return false;
		}
		int tx = (int) Math.floor(target.x);
		int tz = (int) Math.floor(target.z);
		int ty = IshiFx.surface(level, tx, tz);
		BlockState rock = IshiFx.rockBelow(level, tx, ty, tz, cfg.stoneSearchDepth + (titan ? 6 : 0));
		if (rock == null) {
			p.sendSystemMessage(Component.literal("§7Aucune roche sous cette zone : impossible d'y puiser la pierre."), true);
			return false;
		}
		Cast c = new Cast(p, level, new Vec3(target.x, ty + 1, target.z), titan, level.getBlockState(new BlockPos(tx, ty, tz)));
		plan(c, cfg);
		if (c.spikes.isEmpty()) {
			p.sendSystemMessage(Component.literal("§7Pas assez de roche ici."), true);
			return false;
		}
		CASTS.put(p.getUUID(), c);
		p.setAttached(ModAttachments.ISHI_ACTION, IshiAnim.action(level.getGameTime(), IshiFx.material(rock),
				titan ? IshiAnim.TITAN_ERUPT : IshiAnim.ERUPT));
		IshiFx.sound(level, p.position(), SoundEvents.STONE_PLACE, 1.0F, 0.6F);
		return true;
	}

	/** Disposition des pics : disque autour de la cible (normal) ; vague elliptique orientée depuis le titan (Titan). */
	private static void plan(Cast c, IshiConfig cfg) {
		RandomSource r = c.level.getRandom();
		int n = c.titan ? cfg.titanEruptionSpikes : cfg.eruptionSpikes;
		double radius = c.titan ? cfg.titanEruptionRadius : cfg.eruptionRadius;
		Vec3 fwd = c.target.subtract(c.owner.position()).multiply(1, 0, 1);
		fwd = fwd.lengthSqr() < 1.0E-3 ? c.owner.getLookAngle().multiply(1, 0, 1).normalize() : fwd.normalize();
		Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
		double near = c.titan ? c.target.distanceTo(c.owner.position()) - radius : 0.0;
		Set<Long> used = new HashSet<>();
		for (int i = 0, tries = 0; c.spikes.size() < n && tries < n * 6; tries++, i++) {
			Vec3 pos;
			if (i == 0) {
				pos = c.target;
			} else if (c.titan) {
				// vague : plus allongée dans l'axe du regard
				double a = r.nextDouble() * Math.PI * 2;
				double d = Math.sqrt(r.nextDouble());
				pos = c.target.add(fwd.scale(Math.cos(a) * d * radius * 1.25)).add(side.scale(Math.sin(a) * d * radius * 0.85));
			} else {
				double a = (i / (double) n) * Math.PI * 2 + r.nextDouble() * 0.6;
				double d = radius * (0.35 + 0.65 * Math.sqrt(r.nextDouble()));
				pos = c.target.add(Math.cos(a) * d, 0, Math.sin(a) * d);
			}
			int x = (int) Math.floor(pos.x);
			int z = (int) Math.floor(pos.z);
			int spacing = c.titan ? 3 : 1;
			long key = BlockPos.asLong(Math.floorDiv(x, spacing), 0, Math.floorDiv(z, spacing));
			if (!used.add(key)) {
				continue;
			}
			BlockPos probe = new BlockPos(x, (int) c.target.y, z);
			if (!c.level.isLoaded(probe)) {
				continue;
			}
			int y = IshiFx.surface(c.level, x, z);
			if (Math.abs(y - c.target.y) > (c.titan ? 14 : 5)) {
				continue;
			}
			BlockState rock = IshiFx.rockBelow(c.level, x, y, z, cfg.stoneSearchDepth + (c.titan ? 6 : 0));
			if (rock == null) {
				continue;
			}
			double lo = c.titan ? cfg.titanEruptionMinHeight : cfg.eruptionMinHeight;
			double hi = c.titan ? cfg.titanEruptionMaxHeight : cfg.eruptionMaxHeight;
			// au centre les pics sont les plus hauts
			double centre = 1.0 - Math.min(1.0, pos.distanceTo(c.target) / (radius + 1.0)) * 0.45;
			double h = (lo + (hi - lo) * r.nextDouble()) * centre;
			int delay;
			if (c.titan) {
				double dist = Math.max(0.0, new Vec3(x, 0, z).distanceTo(c.owner.position().multiply(1, 0, 1)) - near);
				delay = (int) (dist * 0.55) + r.nextInt(3);
			} else {
				delay = i == 0 ? 0 : 1 + r.nextInt(6);
			}
			c.spikes.add(new Spike(new BlockPos(x, y, z), rock, Math.max(1.5, h), delay, r));
		}
	}

	static void tick(MinecraftServer server) {
		IshiConfig cfg = IshiConfig.get();
		for (Iterator<Cast> it = CASTS.values().iterator(); it.hasNext();) {
			Cast c = it.next();
			c.age++;
			if (!c.owner.isAlive() || c.owner.isRemoved()) {
				// le lanceur n'est plus là : les pics déjà sortis s'effondrent quand même
				if (c.spikes.stream().noneMatch(s -> s.d != null)) {
					cleanup(c);
					it.remove();
					continue;
				}
			}
			int eruptAt = c.titan ? IshiAnim.TITAN_ERUPT_AT : IshiAnim.ERUPT_AT;
			// 1. tremblement, fissures, poussière et gravier avant l'éruption
			if (c.age >= (c.titan ? 2 : IshiAnim.ERUPT_RAISE) && c.age < eruptAt) {
				prelude(c, eruptAt);
			}
			// 2. éruption
			boolean alive = false;
			for (Spike s : c.spikes) {
				if (s.done) {
					continue;
				}
				alive = true;
				int local = c.age - eruptAt - s.delay;
				if (local < 0) {
					if (c.titan && local > -6 && local % 2 == 0) {
						// la vague approche : le sol se soulève devant chaque pic
						IshiFx.burst(c.level, new Vec3(s.ground.getX() + 0.5, s.top() + 0.2, s.ground.getZ() + 0.5), c.level.getBlockState(s.ground),
								0.8, 4, true);
					}
					continue;
				}
				tickSpike(c, s, local, cfg);
			}
			if (c.age > eruptAt + 4 && c.owner.isAlive()) {
				Long a = c.owner.getAttached(ModAttachments.ISHI_ACTION);
				int total = c.titan ? IshiAnim.TITAN_ERUPT_TOTAL : IshiAnim.ERUPT_TOTAL;
				if (a != null && c.age >= total && IshiAnim.actionStart(a) == c.level.getGameTime() - c.age) {
					c.owner.removeAttached(ModAttachments.ISHI_ACTION);
				}
			}
			if (!alive && c.age > eruptAt) {
				it.remove();
			}
		}
	}

	private static void prelude(Cast c, int eruptAt) {
		RandomSource r = c.level.getRandom();
		int remain = eruptAt - c.age;
		if (c.age % 2 == 0) {
			for (Spike s : c.spikes) {
				if (r.nextInt(c.titan ? 3 : 1) == 0) {
					IshiFx.crack(c.level, s.ground, remain + 4);
					if (r.nextBoolean()) {
						IshiFx.crack(c.level, s.ground.offset(r.nextInt(3) - 1, 0, r.nextInt(3) - 1), remain + 4);
					}
				}
				Vec3 g = new Vec3(s.ground.getX() + 0.5, s.top() + 0.1, s.ground.getZ() + 0.5);
				ZushiFx.send(c.level, ModParticles.STONE_DUST, g.x, g.y, g.z, 2, 0.5, 0.05, 0.5, 0.01);
				BlockState gs = c.level.getBlockState(s.ground);
				if (!gs.isAir()) {
					ZushiFx.send(c.level, new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, gs),
							g.x, g.y, g.z, 3, 0.4, 0.05, 0.4, 0.05);
				}
				ZushiFx.send(c.level, ModParticles.STONE_FRAGMENT, g.x, g.y, g.z, 1, 0.4, 0.1, 0.4, 0.12);
			}
		}
		if (c.age % 6 == 0) {
			IshiFx.sound(c.level, c.target, SoundEvents.WARDEN_DIG, c.titan ? 3.0F : 1.2F, 0.6F + r.nextFloat() * 0.2F);
			ZushiFx.shake(c.level, c.target, c.titan ? 60 : 18, c.titan ? 2 : 1, 6);
		}
	}

	private static void tickSpike(Cast c, Spike s, int local, IshiConfig cfg) {
		s.age = local;
		ServerLevel level = c.level;
		RandomSource r = level.getRandom();
		Vec3 base = new Vec3(s.ground.getX() + 0.5, s.top(), s.ground.getZ() + 0.5);
		int hold = cfg.eruptionHoldTicks + (c.titan ? 30 : 0);
		if (local == 0) {
			// modèle enterré (sous la surface), il remontera au tick suivant (interpolation de 4 ticks)
			s.d = ZushiDisplay.create(level, s.model, s.buried(), s.yaw, 0.0F, s.scale, 0.0, 4, c.titan ? 8.0F : 2.5F, false);
			IshiFx.burst(level, base.add(0, 0.3, 0), c.level.getBlockState(s.ground), 0.4 + s.scale * 0.25, (int) (8 + s.height * 2), c.titan);
		} else if (local == 1) {
			s.d.move(s.up(), s.yaw, 0.0F);
			IshiFx.sound(level, base, SoundEvents.DRIPSTONE_BLOCK_BREAK, c.titan ? 4.0F : 1.6F, 0.5F + r.nextFloat() * 0.2F);
			IshiFx.sound(level, base, SoundEvents.STONE_BREAK, c.titan ? 4.0F : 1.5F, 0.55F);
		} else if (local == 3) {
			// le pic surgit sous les ennemis : gros dégâts + projection vers le haut (une fois par lanceur)
			double radius = 0.9 + s.scale * 0.55;
			double dmg = c.titan ? cfg.titanEruptionDamage : cfg.eruptionDamage;
			IshiFx.hurtArea(level, c.owner, base.add(0, Math.min(2.0, s.height * 0.3), 0), radius, dmg, 0.35, 0.65 + s.height * 0.05, c.hit);
			IshiFx.burst(level, base.add(0, 0.6, 0), c.level.getBlockState(s.ground), 0.6 + s.scale * 0.3, (int) (10 + s.height * 2), c.titan);
			IshiFx.throwGround(level, c.owner, base, 1.2 + s.scale * 0.4, c.titan ? 4 : 2, 0.8 + s.scale * 0.2, s.mat);
			// la pierre est arrachée du terrain : petite excavation à la base
			double crater = c.titan ? cfg.titanEruptionCrater : cfg.eruptionCrater;
			if (!cfg.permanentSpikes) {
				IshiFx.crater(level, c.owner, base.add(0, -0.5, 0), crater * (0.7 + 0.3 * r.nextDouble()));
			}
			ZushiFx.shake(level, base, c.titan ? 50 : 14, c.titan ? 3 : 2, 6);
		} else if (local > 3 && local < hold && local % 10 == 0) {
			ZushiFx.send(level, ModParticles.STONE_FRAGMENT, base.x, base.y + s.height * 0.5, base.z, 2, 0.4 * s.scale, s.height * 0.3, 0.4 * s.scale, 0.02);
		} else if (local == hold) {
			if (cfg.permanentSpikes && cfg.destroyTerrain) {
				solidify(c, s);
				s.d.remove();
				s.d = null;
				s.done = true;
				return;
			}
			IshiFx.sound(level, base, SoundEvents.DEEPSLATE_BREAK, c.titan ? 3.0F : 1.2F, 0.5F);
		} else if (local > hold) {
			// effondrement : le pic redescend dans le sol en s'émiettant
			int k = local - hold;
			int sink = 14;
			double e = Math.min(1.0, k / (double) sink);
			s.d.move(s.up().lerp(s.buried(), e * e), s.yaw + k * 1.5F, 0.0F);
			if (k % 2 == 0) {
				IshiFx.burst(level, base.add(0, 0.4 + s.height * 0.4 * (1 - e), 0), IshiFx.materialBlock(s.mat), 0.3 + s.scale * 0.3,
						(int) (4 + s.height), c.titan);
			}
			if (k >= sink) {
				s.d.remove();
				s.d = null;
				s.done = true;
			}
		}
	}

	/** Relief permanent (option) : la forme du pic est figée en vrais blocs de roche, colonne effilée. */
	private static void solidify(Cast c, Spike s) {
		BlockState block = IshiFx.materialBlock(s.mat);
		int h = (int) Math.round(s.height);
		for (int y = 0; y < h; y++) {
			double rad = Math.max(0.0, (1.0 - y / (double) h) * (0.45 * s.scale + 0.4));
			int r = (int) Math.ceil(rad);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > rad * rad + 0.3) {
						continue;
					}
					BlockPos p = s.ground.offset(dx, y + 1, dz);
					BlockState cur = c.level.getBlockState(p);
					if ((cur.isAir() || cur.canBeReplaced()) && ZushiTerrain.allowed(c.level, p, c.owner)) {
						c.level.setBlock(p, y == h - 1 ? Blocks.POINTED_DRIPSTONE.defaultBlockState() : block, 3);
					}
				}
			}
		}
	}

	private static void cleanup(Cast c) {
		for (Spike s : c.spikes) {
			if (s.d != null) {
				s.d.remove();
			}
		}
	}

	static void forget(UUID id) {
		Cast c = CASTS.get(id);
		if (c != null && c.age < (c.titan ? IshiAnim.TITAN_ERUPT_AT : IshiAnim.ERUPT_AT)) {
			// pas encore sorti : annulé
			cleanup(c);
			CASTS.remove(id);
		}
	}

	static void cleanupAll() {
		for (Cast c : CASTS.values()) {
			cleanup(c);
		}
		CASTS.clear();
	}
}
