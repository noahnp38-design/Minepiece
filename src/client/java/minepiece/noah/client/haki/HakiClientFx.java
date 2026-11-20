package minepiece.noah.client.haki;

import minepiece.noah.data.ModAttachments;
import minepiece.noah.item.ModItems;
import minepiece.noah.network.HakiEventPayload;
import minepiece.noah.particle.ModParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Effets de Haki calculés côté client, à chaque tick, à la position ACTUELLE des joueurs (ils suivent donc tous les
 * mouvements, sans jamais rester figés dans le monde) :
 * - imprégnation des Rois : arcs noirs et rouges autour des deux mains, plus intenses pendant la préparation d'un coup
 *   (barre d'attaque qui se recharge), et le long du sabre tenu ;
 * - Vision du futur (joueur local) : trajectoires des projectiles qui arrivent (points rouges), silhouettes fantômes
 *   à la position probable des ennemis qui bougent ;
 * - effets d'écran reçus du serveur : esquive parfaite (léger ralenti visuel), pression des Rois subie, clash proche.
 */
public final class HakiClientFx {
	private static final DustParticleOptions TRAJECTORY = new DustParticleOptions(0xFF2030, 0.6F);
	static int perfectTicks;
	static int pressureTicks;
	static int clashTicks;

	private HakiClientFx() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(HakiEventPayload.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			switch (payload.kind()) {
				case HakiEventPayload.PERFECT_DODGE -> {
					perfectTicks = 12;
					if (mc.player != null) {
						mc.player.playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 1.6F);
					}
				}
				case HakiEventPayload.PRESSURE -> pressureTicks = 18 + 6 * payload.strength();
				case HakiEventPayload.CLASH -> clashTicks = 10;
				default -> {
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(HakiClientFx::tick);
	}

	private static void tick(Minecraft mc) {
		if (perfectTicks > 0) {
			perfectTicks--;
		}
		if (pressureTicks > 0) {
			pressureTicks--;
		}
		if (clashTicks > 0) {
			clashTicks--;
		}
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		RandomSource r = level.getRandom();
		for (AbstractClientPlayer p : level.players()) {
			if (p.distanceToSqr(mc.player) > 64 * 64 || p.isInvisible()) {
				continue;
			}
			Integer f = p.getAttached(ModAttachments.HAKI_FLAGS);
			if (f == null || (f & (8 | 16)) == 0) {
				continue;   // imprégnation ou Haki des Rois maintenu : éclairs autour des mains
			}
			float charge = p == mc.player ? 1.0F - p.getAttackStrengthScale(0.0F) : 0.3F;
			for (boolean right : new boolean[] {true, false}) {
				Vec3 h = hand(p, right);
				int n = 1 + (int) (charge * 3);
				for (int i = 0; i < n; i++) {
					level.addParticle(ModParticles.HAKI_AURA, h.x + (r.nextDouble() - 0.5) * 0.4, h.y + (r.nextDouble() - 0.5) * 0.6,
							h.z + (r.nextDouble() - 0.5) * 0.4, 0, 0, 0);
				}
				if (r.nextFloat() < 0.25F + charge * 0.5F) {
					level.addParticle(ModParticles.HAKI_ARC, h.x, h.y + 0.1, h.z, 0, 0.01, 0);
				}
			}
			ItemStack main = p.getMainHandItem();
			if ((main.is(ModItems.SHISUI) || main.is(ModItems.WADO_ICHIMONJI) || main.is(ModItems.SANDAI_KITETSU)) && r.nextFloat() < 0.7F) {
				Vec3 h = hand(p, true);
				Vec3 along = p.getLookAngle().scale(0.25 + r.nextDouble() * 0.6);
				level.addParticle(r.nextBoolean() ? ModParticles.HAKI_AURA : ModParticles.HAKI_ARC, h.x + along.x, h.y + along.y + 0.15,
						h.z + along.z, 0, 0, 0);
			}
		}
		future(mc, level, r);
	}

	/** Vision du futur : ce qui va arriver. */
	private static void future(Minecraft mc, ClientLevel level, RandomSource r) {
		Integer f = mc.player.getAttached(ModAttachments.HAKI_FLAGS);
		if (f == null || (f & 4) == 0 || level.getGameTime() % 2 != 0) {
			return;
		}
		Vec3 me = mc.player.position().add(0, 1, 0);
		AABB box = mc.player.getBoundingBox().inflate(32);
		for (Entity e : level.getEntities(mc.player, box)) {
			if (e instanceof Projectile pr) {
				Vec3 v = pr.getDeltaMovement();
				if (v.lengthSqr() < 0.09 || v.dot(me.subtract(pr.position())) <= 0) {
					continue;   // immobile ou qui s'éloigne
				}
				for (int k = 1; k <= 8; k++) {
					Vec3 q = pr.position().add(v.scale(k * 1.5)).add(0, -0.03 * k * k, 0);
					level.addParticle(TRAJECTORY, q.x, q.y, q.z, 0, 0, 0);
				}
			} else if (e instanceof Monster m && level.getGameTime() % 10 == 0 && m.distanceToSqr(mc.player) < 20 * 20) {
				Vec3 v = m.getDeltaMovement().multiply(1, 0, 1);
				if (v.lengthSqr() > 0.002) {
					Vec3 q = m.position().add(v.scale(10));
					level.addParticle(ModParticles.HAKI_GHOST, q.x, q.y + m.getBbHeight() * 0.5, q.z, 0, 0, 0);
				}
			}
		}
	}

	/** Position approximative de la main (bras le long du corps), calculée comme le modèle du joueur. */
	static Vec3 hand(LivingEntity p, boolean right) {
		double yaw = Math.toRadians(p.yBodyRot);
		Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(right ? 0.36 : -0.36);
		double k = p.getBbHeight() / 1.8;
		return p.position().add(side).add(0, 0.78 * k - (p.isCrouching() ? 0.3 : 0.0), 0)
				.add(p.getLookAngle().multiply(1, 0, 1).scale(0.12));
	}
}
