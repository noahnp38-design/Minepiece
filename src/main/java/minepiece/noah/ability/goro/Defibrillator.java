package minepiece.noah.ability.goro;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.MinePiece;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.particle.ModParticles;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Passif du Goro Goro no Mi : Défibrillateur.
 * Au premier coup fatal (depuis la dernière vraie réapparition), le joueur ne meurt pas : il tombe KO sur le dos (pose
 * synchronisée, visible par tous), invulnérable et immobile ; des étincelles puis des arcs électriques parcourent son
 * torse, plusieurs impulsions, une grosse décharge, une dernière décharge ; il se relève et récupère 20 % de ses PV max.
 * - Une seule fois par vie : l'indicateur GORO_REVIVE_USED est sauvegardé (déconnexion = pas de nouvelle charge) mais
 *   n'est PAS conservé à la mort : une vraie mort puis la réapparition rendent le passif à nouveau disponible.
 * - Seulement avec le Goro Goro no Mi ; jamais contre /kill ou le vide (dégâts qui ignorent l'invulnérabilité) ;
 *   un totem d'immortalité tenu en main a la priorité (pas de conflit).
 */
public final class Defibrillator {
	private static final Identifier KO_MODIFIER = MinePiece.id("goro_ko");
	private static final int LIE_TICKS = 70;
	private static final Map<UUID, Ko> ACTIVE = new HashMap<>();

	private Defibrillator() {}

	private static final class Ko {
		final ServerLevel level;
		int age;

		Ko(ServerLevel level) {
			this.level = level;
		}
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !tryRevive(entity, source));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p && ACTIVE.containsKey(p.getUUID())) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				ACTIVE.containsKey(player.getUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
				ACTIVE.containsKey(player.getUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	public static boolean isKo(UUID player) {
		return ACTIVE.containsKey(player);
	}

	private static boolean tryRevive(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer p) || !GoroConfig.get().defibrillatorEnabled) {
			return false;
		}
		if (ACTIVE.containsKey(p.getUUID())) {
			return true;   // déjà en réanimation : aucun coup ne peut l'achever (sauf /kill, géré plus haut)
		}
		if (p.getAttached(ModAttachments.FRUIT) != DevilFruit.GORO_GORO_NO_MI
				|| Boolean.TRUE.equals(p.getAttached(ModAttachments.GORO_REVIVE_USED))
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return false;
		}
		p.setHealth(1.0F);
		p.setAttached(ModAttachments.GORO_REVIVE_USED, Boolean.TRUE);
		p.clearFire();
		ServerLevel level = (ServerLevel) p.level();
		ACTIVE.put(p.getUUID(), new Ko(level));
		GoroAbilities.forgetAttacks(p);
		GoroPose.set(p, GoroPose.KO);
		freeze(p, true);
		p.setDeltaMovement(Vec3.ZERO);
		p.needsSync = true;
		p.sendSystemMessage(Component.literal("§bDéfibrillateur..."), true);
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	private static void freeze(ServerPlayer p, boolean on) {
		freeze(p.getAttribute(Attributes.MOVEMENT_SPEED), on);
		freeze(p.getAttribute(Attributes.JUMP_STRENGTH), on);
	}

	private static void freeze(AttributeInstance inst, boolean on) {
		if (inst == null) {
			return;
		}
		if (on) {
			inst.addOrUpdateTransientModifier(new AttributeModifier(KO_MODIFIER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			inst.removeModifier(KO_MODIFIER);
		}
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Ko>> it = ACTIVE.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Ko> e = it.next();
			Ko ko = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !p.isAlive()) {
				it.remove();
				continue;
			}
			ko.age++;
			if (!tick(p, ko)) {
				finish(p);
				it.remove();
			}
		}
	}

	/** @return false à la fin (relevé) */
	private static boolean tick(ServerPlayer p, Ko ko) {
		ServerLevel level = ko.level;
		RandomSource r = level.getRandom();
		int a = ko.age;
		Vec3 v = p.getDeltaMovement();
		p.setDeltaMovement(0, Math.min(0, v.y), 0);
		// torse : le joueur est allongé sur le dos, la poitrine est juste au-dessus du sol, devant ses pieds
		Vec3 chest = p.position().add(0, 0.35, 0);
		if (a > 10 && a < LIE_TICKS) {
			// 1. étincelles sur la poitrine, puis 2. arcs de plus en plus nombreux
			ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, chest.x, chest.y, chest.z, 1 + a / 20, 0.25, 0.08, 0.25, 0.02);
			if (a > 20) {
				ZushiFx.send(level, ModParticles.ELECTRIC_ARC, chest.x, chest.y, chest.z, 1 + a / 25, 0.3, 0.05, 0.3, 0.0);
			}
			if (a > 40) {
				// l'électricité bleue se rassemble sur la poitrine avant les grosses décharges
				Vec3 off = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.6, r.nextDouble() - 0.5).normalize().scale(1.4);
				ZushiFx.velocity(level, ModParticles.ELECTRIC_CHARGE, chest, off, false);
			}
		}
		// 3. impulsions électriques
		if (a == 24 || a == 32 || a == 40) {
			pulse(level, chest, 0.06, 12);
			level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.8F);
		}
		// 4. grosse décharge, 6. dernière décharge
		if (a == 52 || a == 62) {
			boolean last = a == 62;
			GoroFx.bolt(level, chest.add(0, 4, 0), chest, last ? 0.2 : 0.14, 3, 2, false);
			pulse(level, chest, last ? 0.12 : 0.09, last ? 40 : 26);
			ZushiFx.circle(level, ModParticles.ELECTRIC_SPARK, chest, 0.6, last ? 24 : 16, 0.05, 0.3, false);
			ZushiFx.send(level, ModParticles.ELECTRIC_BRANCH, chest.x, chest.y + 0.2, chest.z, last ? 4 : 2, 0.3, 0.1, 0.3, 0.0);
			level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, last ? 1.0F : 1.3F);
		}
		if (a == LIE_TICKS) {
			GoroPose.set(p, GoroPose.GET_UP);
			level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.6F);
		}
		return a < LIE_TICKS + GoroPose.GET_UP_TICKS;
	}

	private static void pulse(ServerLevel level, Vec3 chest, double speed, int count) {
		ZushiFx.circle(level, ModParticles.ELECTRIC_BLUE, chest, 0.3, count / 2, 0.02, speed * 3, false);
		ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, chest.x, chest.y, chest.z, count, 0.2, 0.1, 0.2, speed * 2);
	}

	/** 5./Étape 3 : relevé terminé, 20 % des PV max, petite aura électrique. */
	private static void finish(ServerPlayer p) {
		GoroConfig cfg = GoroConfig.get();
		freeze(p, false);
		GoroPose.clear(p);
		p.setHealth(Math.max(p.getHealth(), (float) (p.getMaxHealth() * cfg.defibrillatorHealth)));
		ServerLevel level = (ServerLevel) p.level();
		ZushiFx.send(level, ModParticles.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.9, 0.5, 0.15);
		ZushiFx.send(level, ModParticles.ELECTRIC_BLUE, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.9, 0.5, 0.08);
		ZushiFx.circle(level, ModParticles.ELECTRIC_ARC, p.position().add(0, 0.1, 0), 1.0, 20, 0.2, 0.15, false);
		level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.6F);
		p.sendSystemMessage(Component.literal("§bDéfibrillateur ! §7(passif utilisé jusqu'à ta prochaine mort)"), true);
	}

	/** Déconnexion / arrêt pendant le KO : la réanimation se termine tout de suite (déjà consommée, pas de doublon). */
	static void forget(UUID id, ServerPlayer p) {
		if (ACTIVE.remove(id) != null && p != null) {
			finish(p);
		}
	}

	static void cleanupAll(MinecraftServer server) {
		for (UUID id : ACTIVE.keySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				finish(p);
			}
		}
		ACTIVE.clear();
	}
}
