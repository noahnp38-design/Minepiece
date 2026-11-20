package minepiece.noah.ability.ishi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.ability.goro.GoroFx;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * X — Poing colossal. Le bras se recouvre progressivement de pierre (rendu client : plaques qui s'assemblent du poignet
 * vers l'épaule, poing qui se forme doigt après doigt), le joueur arme son coup, le poing grossit, puis frappe :
 * onde de choc, cratère, blocs projetés, ennemis repoussés. Le serveur fournit la pierre qui monte du sol vers le bras,
 * les dégâts, le terrain et la secousse ; la chronologie (IshiAnim) est la même pour tous les clients.
 * Version Titan : c'est le bras gigantesque du titan qui frappe (portée et zone à sa taille).
 */
public final class ColossalFist {
	private static final Map<UUID, Cast> CASTS = new HashMap<>();

	private ColossalFist() {}

	private static final class Cast {
		final ServerPlayer owner;
		final ServerLevel level;
		final boolean titan;
		final int mat;
		final BlockState rock;
		int age;

		Cast(ServerPlayer owner, ServerLevel level, boolean titan, int mat, BlockState rock) {
			this.owner = owner;
			this.level = level;
			this.titan = titan;
			this.mat = mat;
			this.rock = rock;
		}
	}

	static boolean cast(ServerPlayer p, ServerLevel level, boolean titan) {
		if (CASTS.containsKey(p.getUUID())) {
			return false;
		}
		BlockPos feet = p.blockPosition();
		BlockState rock = IshiFx.rockBelow(level, feet.getX(), feet.getY() - 1, feet.getZ(), IshiConfig.get().stoneSearchDepth + (titan ? 8 : 0));
		if (rock == null) {
			p.sendSystemMessage(Component.literal("§7Aucune roche sous tes pieds pour former ton poing."), true);
			return false;
		}
		int mat = IshiFx.material(rock);
		CASTS.put(p.getUUID(), new Cast(p, level, titan, mat, rock));
		p.setAttached(ModAttachments.ISHI_ACTION, IshiAnim.action(level.getGameTime(), mat, titan ? IshiAnim.TITAN_PUNCH : IshiAnim.FIST));
		IshiFx.sound(level, p.position(), SoundEvents.STONE_PLACE, 1.2F, 0.5F);
		return true;
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Cast> it = CASTS.values().iterator(); it.hasNext();) {
			Cast c = it.next();
			c.age++;
			ServerPlayer p = c.owner;
			if (!p.isAlive() || p.isRemoved()) {
				it.remove();
				continue;
			}
			if (c.titan) {
				tickTitan(c);
			} else {
				tickNormal(c);
			}
			int total = c.titan ? IshiAnim.TITAN_PUNCH_TOTAL : IshiAnim.FIST_TOTAL;
			if (c.age >= total) {
				Long a = p.getAttached(ModAttachments.ISHI_ACTION);
				if (a != null && IshiAnim.actionStart(a) == c.level.getGameTime() - c.age) {
					p.removeAttached(ModAttachments.ISHI_ACTION);
				}
				it.remove();
			}
		}
	}

	/** Main droite (approx.) du joueur, côté serveur. */
	private static Vec3 hand(ServerPlayer p) {
		return GoroFx.handForward(p);
	}

	private static void tickNormal(Cast c) {
		ServerPlayer p = c.owner;
		ServerLevel level = c.level;
		RandomSource r = level.getRandom();
		Vec3 h = hand(p);
		if (c.age < IshiAnim.FIST_FORM) {
			// la pierre du sol monte et s'assemble autour du bras
			for (int i = 0; i < 3; i++) {
				Vec3 off = new Vec3(r.nextDouble() - 0.5, -0.6 - r.nextDouble() * 1.2, r.nextDouble() - 0.5).scale(1.8);
				ZushiFx.velocity(level, ModParticles.STONE_PULL, h, off, false);
			}
			if (c.age % 3 == 0) {
				ZushiFx.send(level, new BlockParticleOption(ParticleTypes.BLOCK, c.rock), p.getX(), p.getY() + 0.1, p.getZ(), 4, 0.6, 0.05, 0.6, 0.08);
				IshiFx.sound(level, h, SoundEvents.STONE_PLACE, 0.8F, 0.6F + c.age * 0.02F);
			}
			if (c.age == 2) {
				IshiFx.crackDisc(level, p.position(), 2.0, 5, IshiAnim.FIST_FORM);
			}
		} else if (c.age < IshiAnim.FIST_WIND) {
			if (c.age == IshiAnim.FIST_FORM) {
				IshiFx.sound(level, h, SoundEvents.ANVIL_LAND, 0.6F, 0.5F);
			}
			ZushiFx.send(level, ModParticles.STONE_FRAGMENT, h.x, h.y, h.z, 1, 0.2, 0.2, 0.2, 0.02);
		} else if (c.age == IshiAnim.FIST_WIND) {
			// élan : le joueur se projette légèrement vers l'avant
			Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
			p.setDeltaMovement(p.getDeltaMovement().add(look.scale(0.55)));
			p.needsSync = true;
			IshiFx.sound(level, h, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4F, 0.5F);
		} else if (c.age == IshiAnim.FIST_IMPACT) {
			IshiConfig cfg = IshiConfig.get();
			impact(c, cfg.fistReach, cfg.fistRadius, cfg.fistDamage, cfg.fistCrater, cfg.fistKnockback, 0.55);
		}
	}

	private static void tickTitan(Cast c) {
		ServerPlayer p = c.owner;
		ServerLevel level = c.level;
		if (c.age < IshiAnim.TITAN_WIND && c.age % 4 == 0) {
			IshiFx.sound(level, p.position(), SoundEvents.BASALT_BREAK, 3.0F, 0.4F);
			IshiFx.dust(level, p.position().add(0, p.getBbHeight() * 0.6, 0), p.getBbWidth() * 0.4, 6, true);
		} else if (c.age == IshiAnim.TITAN_WIND) {
			IshiFx.sound(level, p.position(), SoundEvents.RAVAGER_ROAR, 3.0F, 0.4F);
		} else if (c.age == IshiAnim.TITAN_IMPACT) {
			IshiConfig cfg = IshiConfig.get();
			double reach = p.getBbHeight() * 0.95;
			impact(c, reach, cfg.titanFistRadius, cfg.titanFistDamage, cfg.titanFistCrater, cfg.titanFistKnockback, 1.0);
		}
	}

	/** Point d'impact : bloc ou entité visée dans la portée ; sinon le coup frappe l'air (onde de choc) ou le sol devant. */
	private static void impact(Cast c, double reach, double radius, double damage, double crater, double knockback, double intensity) {
		ServerPlayer p = c.owner;
		ServerLevel level = c.level;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = eye.add(look.scale(reach));
		BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 limit = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : end;
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, limit, new AABB(eye, limit).inflate(1.5 + radius * 0.2),
				e -> e instanceof LivingEntity && e.isAlive() && e != p && !e.isSpectator(), reach * reach);
		Vec3 at;
		boolean ground;
		if (eh != null) {
			at = eh.getLocation();
			ground = false;
		} else if (bh.getType() == HitResult.Type.BLOCK) {
			at = bh.getLocation();
			ground = true;
		} else if (c.titan) {
			// le titan abat son poing sur le sol devant lui
			int x = (int) Math.floor(end.x);
			int z = (int) Math.floor(end.z);
			at = new Vec3(end.x, IshiFx.surface(level, x, z) + 1, end.z);
			ground = true;
		} else {
			at = end;
			ground = false;
		}
		RandomSource r = level.getRandom();
		BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
		BlockState gs = level.getBlockState(below);
		// dégâts + projection dans l'axe du coup
		IshiFx.hurtArea(level, p, at, radius, damage, knockback, 0.35 + 0.25 * intensity, new HashSet<>());
		if (eh != null && eh.getEntity() instanceof LivingEntity target) {
			Vec3 push = look.multiply(1, 0, 1).normalize().scale(knockback * 0.8);
			target.setDeltaMovement(target.getDeltaMovement().add(push.x, 0.25, push.z));
			target.needsSync = true;
		}
		// explosion de fragments, poussière, onde de choc
		IshiFx.burst(level, at, gs.isAir() ? IshiFx.materialBlock(c.mat) : gs, 0.6 + radius * 0.2, (int) (30 + 90 * intensity), true);
		IshiFx.shockwave(level, ground ? new Vec3(at.x, below.getY() + 1.0, at.z) : at, radius * 1.4, 10 + (int) (8 * intensity));
		ZushiFx.far(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2 + (int) (6 * intensity), radius * 0.25, 0.4, radius * 0.25, 0.0);
		ZushiFx.far(level, ParticleTypes.DUST_PLUME, at.x, at.y + 0.3, at.z, (int) (10 + 30 * intensity), radius * 0.3, 0.6, radius * 0.3, 0.05);
		for (int i = 0, n = (int) (6 + 18 * intensity); i < n; i++) {
			// éclats de roche du poing qui volent dans tous les sens
			Vec3 v = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.6 + 0.2, r.nextDouble() - 0.5).normalize().scale(0.5 + r.nextDouble() * 0.6 * (1 + intensity));
			ZushiFx.throwStone(level, null, IshiFx.fragment(r, c.mat), at.add(0, 0.5, 0), v, 0.4 + r.nextDouble() * 0.5 * (1 + intensity));
		}
		if (ground) {
			IshiFx.throwGround(level, p, at, crater + 1.0, (int) (6 + 24 * intensity), 1.0 + intensity, c.mat);
			IshiFx.crater(level, p, at.add(0, -0.3, 0), crater);
			IshiFx.crackDisc(level, at, radius * 1.3, (int) (10 + 20 * intensity), 30);
		}
		ZushiFx.shake(level, at, 24 + 90 * intensity, 2 + (int) Math.round(3 * intensity), (int) (8 + 12 * intensity));
		IshiFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), (float) (1.6 + 4 * intensity), 0.55F);
		IshiFx.sound(level, at, SoundEvents.ANVIL_LAND, (float) (1.0 + 2 * intensity), 0.5F);
		IshiFx.sound(level, at, SoundEvents.IRON_GOLEM_DAMAGE, (float) (1.0 + 2 * intensity), 0.5F);
		IshiFx.sound(level, at, SoundEvents.DEEPSLATE_BREAK, (float) (1.5 + 3 * intensity), 0.5F);
	}

	static void forget(UUID id) {
		CASTS.remove(id);
	}

	static void cleanupAll() {
		CASTS.clear();
	}
}
