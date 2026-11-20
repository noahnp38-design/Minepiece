package minepiece.noah.haki;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.MinePiece;
import minepiece.noah.data.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Évanouissement causé par le Haki des Rois. Valeur synchronisée : temps de jeu * 16 + variante (1..4 : chute ;
 * 9..12 : la même variante, en train de se relever). L'entité s'effondre (variante de chute choisie au hasard, animée côté
 * client pour tous les joueurs : en arrière, vers l'avant, à genoux puis au sol, sur le côté), reste inconsciente
 * kingKoTicks, puis se relève.
 * - Monstres : IA coupée pendant le KO (un repère sauvegardé la rétablit si le serveur s'arrête pendant ce temps).
 * - Joueurs : immobiles et incapables d'attaquer pendant le KO.
 */
public final class HakiKo {
	public static final int BACK = 1;
	public static final int FORWARD = 2;
	public static final int KNEES = 3;
	public static final int SIDE = 4;
	/** Durée de l'animation de relevé (même valeur côté client). */
	public static final int WAKE_TICKS = 14;

	private static final Identifier FREEZE = MinePiece.id("haki_ko");
	private static final Map<UUID, Entry> ACTIVE = new HashMap<>();

	private HakiKo() {}

	private static final class Entry {
		final LivingEntity entity;
		final long end;
		final boolean hadNoAi;
		final int variant;
		long wakeEnd = -1;

		Entry(LivingEntity entity, long end, boolean hadNoAi, int variant) {
			this.entity = entity;
			this.end = end;
			this.hadNoAi = hadNoAi;
			this.variant = variant;
		}
	}

	public static boolean isKo(Entity e) {
		Entry en = ACTIVE.get(e.getUUID());
		return en != null && en.wakeEnd < 0;
	}

	public static void knockOut(LivingEntity e, int ticks) {
		if (ACTIVE.containsKey(e.getUUID()) || !e.isAlive()) {
			return;
		}
		long now = e.level().getGameTime();
		int variant = 1 + e.getRandom().nextInt(4);
		boolean hadNoAi = false;
		if (e instanceof Mob mob) {
			hadNoAi = mob.isNoAi();
			mob.setTarget(null);
			mob.getNavigation().stop();
			mob.setNoAi(true);
			mob.setAttached(ModAttachments.HAKI_KO_NOAI, Boolean.TRUE);
		}
		if (e instanceof ServerPlayer p) {
			freeze(p, true);
		}
		e.setAttached(ModAttachments.HAKI_KO, now * 16L + variant);
		ACTIVE.put(e.getUUID(), new Entry(e, now + ticks, hadNoAi, variant));
	}

	private static void freeze(ServerPlayer p, boolean on) {
		for (AttributeInstance inst : new AttributeInstance[] {p.getAttribute(Attributes.MOVEMENT_SPEED), p.getAttribute(Attributes.JUMP_STRENGTH)}) {
			if (inst == null) {
				continue;
			}
			if (on) {
				inst.addOrUpdateTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else {
				inst.removeModifier(FREEZE);
			}
		}
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Entry> it = ACTIVE.values().iterator(); it.hasNext();) {
			Entry en = it.next();
			LivingEntity e = en.entity;
			if (e.isRemoved() || !e.isAlive()) {
				restore(en);
				it.remove();
				continue;
			}
			long now = e.level().getGameTime();
			if (en.wakeEnd < 0) {
				if (now >= en.end) {
					// se relève
					en.wakeEnd = now + WAKE_TICKS;
					e.setAttached(ModAttachments.HAKI_KO, now * 16L + 8 + en.variant);
					restore(en);
				} else if (e instanceof ServerPlayer p) {
					p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
				}
			} else if (now >= en.wakeEnd) {
				e.removeAttached(ModAttachments.HAKI_KO);
				it.remove();
			}
		}
	}

	private static void restore(Entry en) {
		LivingEntity e = en.entity;
		if (e instanceof Mob mob && mob.hasAttached(ModAttachments.HAKI_KO_NOAI)) {
			mob.setNoAi(en.hadNoAi);
			mob.removeAttached(ModAttachments.HAKI_KO_NOAI);
		}
		if (e instanceof ServerPlayer p) {
			freeze(p, false);
		}
		if (e.isRemoved() || !e.isAlive()) {
			e.removeAttached(ModAttachments.HAKI_KO);
		}
	}

	/** Chargement d'une entité : un monstre resté "sans IA" après un arrêt du serveur pendant son KO retrouve son IA. */
	static void onLoad(Entity e) {
		if (e instanceof Mob mob && mob.hasAttached(ModAttachments.HAKI_KO_NOAI) && !ACTIVE.containsKey(mob.getUUID())) {
			mob.setNoAi(false);
			mob.removeAttached(ModAttachments.HAKI_KO_NOAI);
			mob.removeAttached(ModAttachments.HAKI_KO);
		}
	}

	static void forget(ServerPlayer p) {
		Entry en = ACTIVE.remove(p.getUUID());
		if (en != null) {
			restore(en);
			p.removeAttached(ModAttachments.HAKI_KO);
		}
	}

	static void wakeAll() {
		for (Entry en : ACTIVE.values()) {
			restore(en);
			en.entity.removeAttached(ModAttachments.HAKI_KO);
		}
		ACTIVE.clear();
	}
}
