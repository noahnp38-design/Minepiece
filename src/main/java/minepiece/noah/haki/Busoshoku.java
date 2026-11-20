package minepiece.noah.haki;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.MinePiece;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.item.ModItems;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Haki de l'Armement (touche J).
 * - Durcissement : posture de concentration, énergie sombre autour des poings, puis le Koka (noir brillant aux reflets
 *   bleus qui glissent, comme le bras de Luffy) recouvre le corps selon la maîtrise : 1 avant-bras, 2 bras, 3 jambes,
 *   4 tête, 5 corps entier — voir le rendu client. Bonus de dégâts physiques, d'armure et de résistance ; touche les Logia (voir LogiaProtection).
 * - Sabres (Shisui, Wado Ichimonji, Sandai Kitetsu) : une vague noire part de la garde et parcourt la lame (6 étapes) ; le tranchant
 *   garde ses reflets. Seule la TEXTURE change (donnée de modèle personnalisée sur l'objet tenu), jamais la géométrie,
 *   et elle disparaît dès que le Haki est coupé ou que le sabre quitte la main.
 */
public final class Busoshoku {
	private static final Identifier DMG = MinePiece.id("haki_arm_damage");
	private static final Identifier ARMOR = MinePiece.id("haki_arm_armor");
	private static final Identifier TOUGH = MinePiece.id("haki_arm_toughness");
	private static final Identifier KING_DMG = MinePiece.id("haki_king_damage");
	public static final String MARK_BUSO = "buso";
	public static final String MARK_KING = "king";
	/** Durée de la vague noire sur la lame (ticks). */
	private static final int SWORD_WAVE = 16;

	private static final Map<UUID, Coat> COATS = new HashMap<>();

	private record Coat(ItemStack stack, String mode, long start) {}

	private Busoshoku() {}

	static void toggle(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		boolean on = !HakiData.has(p, HakiData.F_ARM);
		if (on && HakiManager.energy(p) < 8.0) {
			p.sendSystemMessage(Component.literal("§8Pas assez d'énergie de Haki."), true);
			return;
		}
		HakiData.setFlag(p, HakiData.F_ARM, on);
		p.setAttached(ModAttachments.HAKI_ARM_TIME, level.getGameTime());
		modifiers(p);
		if (on) {
			HakiPose.set(p, HakiPose.ARM_FOCUS);
			HakiFx.darkHands(level, p, 10);
			level.playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
			p.sendSystemMessage(Component.literal("§7Haki de l'Armement §8: §f" + HakiData.levelName(HakiData.ARM, HakiData.mastery(p, HakiData.ARM))), true);
		} else {
			level.playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
		}
	}

	/**
	 * Phase 3 (durcissement) : quand tout le revêtement est en place (une partie toutes les 14 ticks, comme le rendu
	 * client), courte impulsion de Haki : anneau d'air, éclats sombres et violets, son métallique.
	 */
	static void hardenTick(ServerPlayer p, long age) {
		int level = HakiData.mastery(p, HakiData.ARM);
		int parts = 1 + (level >= 3 ? 1 : 0) + (level >= 4 ? 1 : 0) + (level >= 5 ? 1 : 0);
		if (age != parts * 14L + 4L) {
			return;
		}
		ServerLevel level2 = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.55, 0);
		HakiFx.ring(level2, "haki_ring_emission", p.position().add(0, 0.9, 0), 0.6, 3.2, 6);
		ZushiFx.send(level2, ModParticles.HAKI_DARK, c.x, c.y, c.z, 24, 0.35, 0.6, 0.35, 0.12);
		ZushiFx.send(level2, ModParticles.HAKI_PRESSURE, c.x, c.y, c.z, 2, 0.2, 0.3, 0.2, 0.0);
		level2.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level2.playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.7F);
	}

	/** Met à jour les bonus (Armement, imprégnation des Rois) selon les Hakis actifs et la maîtrise. */
	static void modifiers(ServerPlayer p) {
		HakiConfig cfg = HakiConfig.get();
		boolean arm = HakiData.has(p, HakiData.F_ARM);
		int m = HakiData.mastery(p, HakiData.ARM);
		set(p.getAttribute(Attributes.ATTACK_DAMAGE), DMG, arm ? HakiConfig.at(cfg.armDamageBonus, m) : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(p.getAttribute(Attributes.ARMOR), ARMOR, arm ? HakiConfig.at(cfg.armArmor, m) : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(p.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGH, arm ? HakiConfig.at(cfg.armToughness, m) : 0.0, AttributeModifier.Operation.ADD_VALUE);
		boolean king = HakiData.has(p, HakiData.F_INFUSE);
		set(p.getAttribute(Attributes.ATTACK_DAMAGE), KING_DMG, king ? cfg.infuseDamageBonus : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void set(AttributeInstance inst, Identifier id, double value, AttributeModifier.Operation op) {
		if (inst == null) {
			return;
		}
		if (value == 0.0) {
			inst.removeModifier(id);
		} else {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, value, op));
		}
	}

	// ------------------------------------------------------------------ Logia

	/**
	 * Vrai si l'attaque vient d'un utilisateur du Haki de l'Armement : elle touche alors le corps réel d'un Logia
	 * (l'intangibilité n'est contournée QUE pour ce coup). Petit éclat de Haki à l'impact.
	 */
	public static boolean piercesLogia(LivingEntity target, DamageSource source) {
		Entity attacker = source.getEntity();
		if (!(attacker instanceof ServerPlayer p) || !HakiData.has(p, HakiData.F_ARM)) {
			return false;
		}
		ServerLevel level = (ServerLevel) target.level();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
		ZushiFx.send(level, ModParticles.HAKI_DARK, c.x, c.y, c.z, 12, 0.25, 0.3, 0.25, 0.08);
		HakiFx.ringFacing(level, "haki_ring_emission", c, target.position().subtract(p.position()).normalize(), 0.3, 1.8, 5);
		level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.6F);
		return true;
	}

	// ------------------------------------------------------------------ coups

	/** Coup au corps à corps porté avec l'Armement actif : expérience. */
	static void onMeleeHit(ServerPlayer p, LivingEntity target, float damage) {
		if (HakiData.has(p, HakiData.F_ARM)) {
			HakiData.addXp(p, HakiData.ARM, 1);
		}
	}

	// ------------------------------------------------------------------ sabres

	static boolean isHakiSword(ItemStack s) {
		return s.is(ModItems.SHISUI) || s.is(ModItems.WADO_ICHIMONJI) || s.is(ModItems.SANDAI_KITETSU);
	}

	/** Recouvrement des sabres (chaque tick) : vague noire sur la lame, version imprégnée des Rois, nettoyage. */
	static void swordTick(ServerPlayer p, long now) {
		ItemStack main = p.getMainHandItem();
		String mode = !isHakiSword(main) ? null
				: HakiData.has(p, HakiData.F_INFUSE) ? MARK_KING : HakiData.has(p, HakiData.F_ARM) ? MARK_BUSO : null;
		Coat c = COATS.get(p.getUUID());
		if (mode != null) {
			if (c == null || c.stack() != main || !mode.equals(c.mode())) {
				if (c != null && c.stack() != main) {
					clear(c.stack());
				}
				c = new Coat(main, mode, now);
				COATS.put(p.getUUID(), c);
			}
			float prog = MARK_KING.equals(mode) ? 1.0F : Math.min(1.0F, (now - c.start()) / (float) SWORD_WAVE);
			float q = Math.round(prog * 6.0F) / 6.0F;
			apply(main, mode, q);
			if (prog < 1.0F && now % 2 == 0) {
				ServerLevel level = (ServerLevel) p.level();
				Vec3 h = HakiFx.hand(p, true);
				ZushiFx.send(level, ModParticles.HAKI_DARK, h.x, h.y, h.z, 3, 0.15, 0.3, 0.15, 0.02);
			}
		} else if (c != null) {
			clear(c.stack());
			COATS.remove(p.getUUID());
		}
		if (now % 20 == 0) {
			// aucun autre sabre de l'inventaire ne doit garder le revêtement
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s != main || mode == null) {
					clear(s);
				}
			}
		}
	}

	private static void apply(ItemStack s, String mode, float progress) {
		CustomModelData cur = s.get(DataComponents.CUSTOM_MODEL_DATA);
		if (cur != null && mode.equals(cur.getString(0)) && cur.getFloat(0) != null && Math.abs(cur.getFloat(0) - progress) < 1.0E-3) {
			return;
		}
		s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(progress), List.of(), List.of(mode), List.of()));
	}

	/** Retire le revêtement de Haki d'un objet (seulement s'il vient de nous). */
	static void clear(ItemStack s) {
		if (s.isEmpty()) {
			return;
		}
		CustomModelData cur = s.get(DataComponents.CUSTOM_MODEL_DATA);
		if (cur != null && (MARK_BUSO.equals(cur.getString(0)) || MARK_KING.equals(cur.getString(0)))) {
			s.remove(DataComponents.CUSTOM_MODEL_DATA);
		}
	}

	/** Déconnexion : tous les sabres redeviennent normaux, les bonus sont retirés. */
	static void forget(ServerPlayer p) {
		COATS.remove(p.getUUID());
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			clear(inv.getItem(i));
		}
	}
}
