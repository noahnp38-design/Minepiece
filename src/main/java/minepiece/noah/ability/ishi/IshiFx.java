package minepiece.noah.ability.ishi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import minepiece.noah.ability.goro.GoroFx;
import minepiece.noah.ability.zushi.ZushiDisplay;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.ability.zushi.ZushiTerrain;
import minepiece.noah.haki.HakiData;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Outils communs du Ishi Ishi no Mi : reconnaissance de la roche du terrain (et de sa matière), fissures réelles sur les
 * blocs (indicateur de minage vanilla), poussière et fragments gris, onde de choc 3D, blocs projetés, pierre qui
 * converge vers un point (formation du titan), dégâts de zone, cratères.
 * Optimisation : particules via le serveur (réglage "Particules" de chaque joueur respecté, densité de la config Zushi),
 * débris 3D plafonnés, cratères creusés sur plusieurs ticks.
 */
public final class IshiFx {
	private static final List<Crack> CRACKS = new ArrayList<>();
	private static final List<Converge> CONVERGE = new ArrayList<>();
	private static int crackId = -0x4000_0000;
	private static final int MAX_CONVERGE = 90;

	private IshiFx() {}

	// ------------------------------------------------------------------ roche

	/** Vrai si le bloc est de la roche exploitable par le fruit (pas de la terre, du sable meuble, du bois...). */
	public static boolean isRock(BlockState s) {
		if (s.isAir() || !s.getFluidState().isEmpty()) {
			return false;
		}
		if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.STONE_ORE_REPLACEABLES) || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
			return true;
		}
		String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return p.contains("stone") || p.contains("deepslate") || p.contains("andesite") || p.contains("diorite") || p.contains("granite")
				|| p.contains("tuff") || p.contains("basalt") || p.contains("calcite") || p.contains("dripstone") || p.contains("terracotta")
				|| p.contains("cobble") || p.endsWith("_ore");
	}

	/** Matière (index de IshiAnim.MATS) d'un bloc rocheux : pierre, deepslate, andésite, tuf, pierre taillée, grès. */
	public static int material(BlockState s) {
		if (s == null) {
			return 0;
		}
		String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		if (p.contains("deepslate") || p.contains("basalt") || p.contains("blackstone")) {
			return 1;
		}
		if (p.contains("andesite") || p.contains("diorite") || p.contains("calcite")) {
			return 2;
		}
		if (p.contains("tuff") || p.contains("granite") || p.contains("dripstone")) {
			return 3;
		}
		if (p.contains("cobble") || p.contains("mossy")) {
			return 4;
		}
		if (p.contains("sand") || p.contains("terracotta")) {
			return 5;
		}
		return 0;
	}

	/** Hauteur du bloc de surface (sans feuillages) de la colonne (x, z). */
	public static int surface(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
	}

	/**
	 * Premier bloc rocheux de la colonne (x, z) sous yTop (jusqu'à depth blocs). null si aucun : la capacité ne peut pas
	 * puiser de pierre ici (sauf si la config ne l'exige pas : on renvoie alors de la pierre "virtuelle").
	 */
	public static BlockState rockBelow(ServerLevel level, int x, int yTop, int z, int depth) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, yTop, z);
		for (int i = 0; i <= depth; i++) {
			m.setY(yTop - i);
			if (!level.isLoaded(m)) {
				return null;
			}
			BlockState s = level.getBlockState(m);
			if (isRock(s)) {
				return s;
			}
		}
		return IshiConfig.get().requireStone ? null : Blocks.STONE.defaultBlockState();
	}

	/** Nombre de blocs rocheux dans un cylindre (rayon r, profondeur depth) sous le point donné. */
	public static int countRock(ServerLevel level, BlockPos feet, int r, int depth) {
		int n = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				for (int dy = 1; dy <= depth; dy++) {
					m.set(feet.getX() + dx, feet.getY() - dy, feet.getZ() + dz);
					if (level.isLoaded(m) && isRock(level.getBlockState(m))) {
						n++;
					}
				}
			}
		}
		return n;
	}

	public static BlockState materialBlock(int mat) {
		return switch (mat) {
			case 1 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
			case 2 -> Blocks.ANDESITE.defaultBlockState();
			case 3 -> Blocks.TUFF.defaultBlockState();
			case 4 -> Blocks.COBBLESTONE.defaultBlockState();
			case 5 -> Blocks.SANDSTONE.defaultBlockState();
			default -> Blocks.STONE.defaultBlockState();
		};
	}

	public static String fragment(RandomSource r, int mat) {
		return "ishi_frag_" + r.nextInt(3) + "_" + IshiAnim.MATS[Math.max(0, Math.min(5, mat))];
	}

	// ------------------------------------------------------------------ fissures (indicateur de minage)

	private static final class Crack {
		final ServerLevel level;
		final BlockPos pos;
		final int id;
		final int from;
		final int duration;
		int age;

		Crack(ServerLevel level, BlockPos pos, int id, int from, int duration) {
			this.level = level;
			this.pos = pos;
			this.id = id;
			this.from = from;
			this.duration = duration;
		}
	}

	/** Fissures qui progressent sur un bloc réel pendant "duration" ticks (état 0 à 9), puis disparaissent. */
	public static void crack(ServerLevel level, BlockPos pos, int duration) {
		if (level.getBlockState(pos).isAir() || CRACKS.size() > 400) {
			return;
		}
		int id = crackId++;
		if (crackId > -0x3000_0000) {
			crackId = -0x4000_0000;
		}
		CRACKS.add(new Crack(level, pos.immutable(), id, 0, Math.max(2, duration)));
	}

	/** Fissures sur un disque de blocs de surface autour de c. */
	public static void crackDisc(ServerLevel level, Vec3 c, double radius, int count, int duration) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(r.nextDouble()) * radius;
			int x = (int) Math.floor(c.x + Math.cos(a) * d);
			int z = (int) Math.floor(c.z + Math.sin(a) * d);
			BlockPos p = new BlockPos(x, surface(level, x, z), z);
			if (Math.abs(p.getY() - c.y) < 4) {
				crack(level, p, duration);
			}
		}
	}

	// ------------------------------------------------------------------ particules

	public static void dust(ServerLevel level, Vec3 c, double spread, int count, boolean far) {
		if (far) {
			ZushiFx.far(level, ModParticles.STONE_DUST, c.x, c.y, c.z, count, spread, spread * 0.4, spread, 0.03);
		} else {
			ZushiFx.send(level, ModParticles.STONE_DUST, c.x, c.y, c.z, count, spread, spread * 0.4, spread, 0.03);
		}
	}

	/** Éclats de bloc du terrain (texture réelle) + fragments de pierre + poussière. */
	public static void burst(ServerLevel level, Vec3 c, BlockState ground, double spread, int count, boolean far) {
		if (ground != null && !ground.isAir()) {
			ZushiFx.far(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), c.x, c.y, c.z, count, spread, spread * 0.5, spread, 0.25);
		}
		ZushiFx.send(level, ModParticles.STONE_FRAGMENT, c.x, c.y, c.z, count / 2 + 1, spread, spread * 0.5, spread, 0.3);
		ZushiFx.send(level, ModParticles.STONE_SHARD, c.x, c.y, c.z, count / 3 + 1, spread * 0.6, spread * 0.4, spread * 0.6, 0.45);
		dust(level, c, spread, count / 2 + 1, far);
	}

	/** Onde de choc : anneau de poussière 3D qui s'élargit + cercle de particules au sol. */
	public static void shockwave(ServerLevel level, Vec3 c, double radius, int ticks) {
		ZushiDisplay ring = ZushiDisplay.create(level, "ishi_shock", c.add(0, 0.2, 0), 0.0F, 0.0F, 0.15, 0.0, 1, 5.0F, false);
		ring.scaleTo(radius * 2.0 / 3.0, 0.0, ticks);
		GoroFx.temporary(ring, ticks + 1);
		ZushiFx.circle(level, ModParticles.STONE_DUST, c.add(0, 0.3, 0), 0.6, (int) (16 + radius * 5), 0.04, 0.25 + radius * 0.05, true);
		ZushiFx.circle(level, ModParticles.STONE_FRAGMENT, c.add(0, 0.4, 0), 0.4, (int) (8 + radius * 3), 0.25, 0.35 + radius * 0.04, true);
	}

	/** Blocs du terrain projetés (copies visuelles) autour de c ; fragments du mod là où l'on ne peut rien arracher. */
	public static void throwGround(ServerLevel level, ServerPlayer owner, Vec3 c, double radius, int blocks, double power, int mat) {
		RandomSource r = level.getRandom();
		boolean destroy = IshiConfig.get().destroyTerrain;
		for (int i = 0; i < blocks; i++) {
			double a = r.nextDouble() * Math.PI * 2;
			double d = Math.sqrt(r.nextDouble()) * Math.max(1.2, radius);
			int bx = (int) Math.floor(c.x + Math.cos(a) * d);
			int bz = (int) Math.floor(c.z + Math.sin(a) * d);
			BlockPos top = new BlockPos(bx, surface(level, bx, bz), bz);
			if (!level.isLoaded(top) || Math.abs(top.getY() - c.y) > radius + 3) {
				continue;
			}
			BlockState st = level.getBlockState(top);
			double sp = (0.25 + r.nextDouble() * 0.45) * power;
			Vec3 vel = new Vec3(Math.cos(a) * sp, (0.55 + r.nextDouble() * 0.55) * Math.sqrt(power), Math.sin(a) * sp);
			Vec3 from = new Vec3(bx + 0.5, top.getY() + 1.3, bz + 0.5);
			double scale = 0.55 + r.nextDouble() * 0.45;
			if (destroy && st.isSolid() && ZushiTerrain.movable(level, top, st) && ZushiTerrain.allowed(level, top, owner)) {
				ZushiFx.throwStone(level, st, null, from, vel, scale);
			} else {
				ZushiFx.throwStone(level, null, fragment(r, mat), from, vel, scale * 1.2);
			}
		}
	}

	/** Cratère de roche (protections, dureté max et budget par tick communs ; aucun bloc touché si désactivé). */
	public static void crater(ServerLevel level, ServerPlayer owner, Vec3 c, double radius) {
		if (radius >= 1.0) {
			ZushiTerrain.crater(level, c, radius, owner, IshiConfig.get().destroyTerrain, true);
		}
	}

	public static void sound(ServerLevel level, Vec3 c, SoundEvent s, float volume, float pitch) {
		level.playSound(null, c.x, c.y, c.z, s, SoundSource.PLAYERS, volume, pitch);
	}

	// ------------------------------------------------------------------ dégâts

	/**
	 * Dégâts de zone (sphère aplatie) avec projection depuis c. Renvoie le nombre d'entités touchées.
	 * @param lift vitesse verticale donnée aux cibles
	 */
	public static int hurtArea(ServerLevel level, ServerPlayer owner, Vec3 c, double radius, double damage, double push, double lift,
			java.util.Set<java.util.UUID> already) {
		DamageSource src = level.damageSources().playerAttack(owner);
		double mult = IshiConfig.get().damageMultiplier;
		int n = 0;
		AABB box = new AABB(c, c).inflate(radius, radius * 0.75 + 1.0, radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && e != owner)) {
			if (already != null && already.contains(e.getUUID())) {
				continue;
			}
			double d = e.position().distanceTo(c);
			if (d > radius + e.getBbWidth()) {
				continue;
			}
			if (already != null) {
				already.add(e.getUUID());
			}
			float dmg = (float) (damage * (1.0 - 0.4 * Math.min(1.0, d / radius)) * mult);
			// Haki de l'Armement : la pierre recouverte frappe plus fort
			if (HakiData.has(owner, HakiData.F_ARM)) {
				dmg *= 1.15F;
			}
			e.hurtServer(level, src, dmg);
			Vec3 dir = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
			dir = dir.lengthSqr() < 1.0E-4 ? owner.getLookAngle().multiply(1, 0, 1).normalize() : dir.normalize();
			double k = push * (1.0 - 0.5 * Math.min(1.0, d / radius));
			e.setDeltaMovement(e.getDeltaMovement().add(dir.x * k, lift, dir.z * k));
			e.needsSync = true;
			burst(level, e.position().add(0, e.getBbHeight() * 0.5, 0), materialBlock(0), 0.3, 6, false);
			n++;
		}
		return n;
	}

	// ------------------------------------------------------------------ pierre qui converge (formation du titan)

	private static final class Converge {
		final ZushiDisplay d;
		final Vec3 from;
		final Vec3 to;
		final int life;
		final float spin;
		int age;

		Converge(ZushiDisplay d, Vec3 from, Vec3 to, int life, float spin) {
			this.d = d;
			this.from = from;
			this.to = to;
			this.life = life;
			this.spin = spin;
		}
	}

	/** Un bloc de pierre s'arrache du sol en "from" et vole en arc jusqu'à "to", où il s'incruste (disparaît). */
	public static void converge(ServerLevel level, BlockState state, Vec3 from, Vec3 to, double scale, int life) {
		if (CONVERGE.size() >= MAX_CONVERGE) {
			return;
		}
		ZushiDisplay d = ZushiDisplay.createBlock(level, NbtUtils.writeBlockState(state).toString(), from, scale, 4.0F);
		if (d.isActive()) {
			CONVERGE.add(new Converge(d, from, to, life, (level.getRandom().nextFloat() - 0.5F) * 30.0F));
		}
	}

	// ------------------------------------------------------------------ tick

	static void tick() {
		for (Iterator<Crack> it = CRACKS.iterator(); it.hasNext();) {
			Crack c = it.next();
			c.age++;
			if (c.age > c.duration + 6) {
				c.level.destroyBlockProgress(c.id, c.pos, -1);
				it.remove();
			} else if (c.age <= c.duration) {
				int stage = Math.min(9, c.from + c.age * 10 / c.duration);
				c.level.destroyBlockProgress(c.id, c.pos, stage);
			}
		}
		for (Iterator<Converge> it = CONVERGE.iterator(); it.hasNext();) {
			Converge c = it.next();
			c.age++;
			float k = Math.min(1.0F, c.age / (float) c.life);
			if (k >= 1.0F || !c.d.isActive()) {
				c.d.remove();
				it.remove();
				continue;
			}
			double e = k * k * (3 - 2 * k);
			Vec3 p = c.from.lerp(c.to, e).add(0, Math.sin(k * Math.PI) * 2.5, 0);
			c.d.move(p, c.age * c.spin, (float) Math.sin(c.age * 0.4) * 40.0F);
		}
	}

	static void cleanupAll() {
		for (Crack c : CRACKS) {
			c.level.destroyBlockProgress(c.id, c.pos, -1);
		}
		CRACKS.clear();
		for (Converge c : CONVERGE) {
			c.d.remove();
		}
		CONVERGE.clear();
	}
}
