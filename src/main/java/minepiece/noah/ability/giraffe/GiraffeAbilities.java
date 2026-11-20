package minepiece.noah.ability.giraffe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.MinePiece;
import minepiece.noah.ability.DemonCurse;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ushi Ushi no Mi, modèle Girafe (Zoan, Kaku). Tout est décidé par le serveur ; le client ne fait que dessiner
 * (GiraffeState synchronisé + animations calculées depuis le temps de jeu, voir GiraffeAnim).
 *
 * FORMES : Humain / Hybride / Girafe. Une seule forme active par joueur (attachement GIRAFFE). Changement : touche V
 * (Humain -> Hybride -> Girafe -> Humain) ou touche B (retour humain immédiat, à tout moment). Pendant une animation de
 * transformation on ne peut pas changer de forme (sauf revenir à l'humain). Taille = attribut SCALE (la hitbox suit),
 * vérifiée contre les blocs avant et pendant la croissance. Les stats sont des modificateurs d'attributs transitoires
 * (jamais sauvegardés) : une mort, une déconnexion ou un redémarrage ramènent donc toujours à la forme humaine.
 *
 * ATTAQUES : 1 Rankyaku (hybride / girafe), 2 Bigan (hybride), 3 Kirin Roundabout (hybride / girafe). Aucun bloc détruit,
 * jamais de dégât au lanceur, une seule touche par cible et par attaque (ensembles d'UUID).
 */
public final class GiraffeAbilities {
	/** Slots réseau : touche V (forme suivante) et touche B (forme humaine). */
	public static final int SLOT_CYCLE = 6;
	public static final int SLOT_HUMAN = 7;

	// ---- Statistiques des formes (modifier ici pour équilibrer)
	private static final double HYBRID_SCALE = 0.15;       // +15 % de taille
	private static final double GIRAFFE_SCALE = 0.85;      // +85 % de taille
	private static final float HYBRID_RESIST = 0.80F;      // -20 % de dégâts
	private static final float GIRAFFE_RESIST = 0.75F;     // -25 % de dégâts

	// ---- Rankyaku
	private static final int KICK_DELAY = 3;                // ticks entre le début du coup de pied et la lame
	private static final double RANKYAKU_SPEED = 1.8;
	private static final double RANKYAKU_RANGE = 25.0;
	private static final double RANKYAKU_HALF_WIDTH = 1.0;  // largeur 2 blocs
	private static final double RANKYAKU_HALF_HEIGHT = 1.5;
	private static final float RANKYAKU_DAMAGE = 12.0F;     // 6 cœurs
	private static final double BLADE_SCALE = 1.5;
	private static final double BLADE_TY = 0.0;

	// ---- Bigan
	private static final float BIGAN_DAMAGE = 16.0F;        // 8 cœurs
	private static final double BIGAN_HALF = 0.8;

	// ---- Kirin Roundabout
	private static final float KIRIN_DAMAGE = 4.0F;         // 2 cœurs
	private static final float KIRIN_FINAL_BONUS = 10.0F;   // +5 cœurs au dernier tour
	private static final int KIRIN_INTERVAL = 10;           // 0,5 s
	private static final int KIRIN_MAX_HITS = 5;
	private static final double KIRIN_R0 = 4.0;
	private static final double KIRIN_R1 = 6.0;

	private static final DustParticleOptions WHITE = new DustParticleOptions(0xF6F8FF, 1.4F);
	private static final DustParticleOptions BLUE = new DustParticleOptions(0xCFE6FF, 1.2F);
	private static final DustParticleOptions BEIGE = new DustParticleOptions(0xE8D9B8, 1.5F);
	private static final DustParticleOptions BROWN = new DustParticleOptions(0x8A5A2B, 1.3F);

	// Identifiants fixes des modificateurs : les réappliquer REMPLACE, rien ne se cumule jamais.
	private static final Identifier ID_SCALE = MinePiece.id("giraffe_scale");
	private static final Identifier ID_SPEED = MinePiece.id("giraffe_speed");
	private static final Identifier ID_SLOW = MinePiece.id("giraffe_slow");
	private static final Identifier ID_DAMAGE = MinePiece.id("giraffe_damage");
	private static final Identifier ID_HEALTH = MinePiece.id("giraffe_health");
	private static final Identifier ID_REACH = MinePiece.id("giraffe_reach");
	private static final Identifier ID_JUMP = MinePiece.id("giraffe_jump");
	private static final Identifier ID_STEP = MinePiece.id("giraffe_step");
	private static final Identifier ID_FALL = MinePiece.id("giraffe_fall");

	/** Joueurs qui ne sont pas (ou plus) en forme humaine ou dont une attaque est en cours : seuls eux coûtent du temps de tick. */
	private static final Set<UUID> ACTIVE = new HashSet<>();
	private static final Map<UUID, Long> TRANSFORM_END = new HashMap<>();
	private static final List<Kick> KICKS = new ArrayList<>();
	private static final List<Blade> BLADES = new ArrayList<>();
	private static final Map<UUID, Bigan> BIGANS = new HashMap<>();
	private static final Map<UUID, Kirin> KIRINS = new HashMap<>();

	private GiraffeAbilities() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GiraffeAbilities::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			GiraffeVisual.cleanupAll();
			KICKS.clear();
			BLADES.clear();
			BIGANS.clear();
			KIRINS.clear();
			ACTIVE.clear();
			TRANSFORM_END.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reset(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> reset(handler.getPlayer()));
		// Résistance aux dégâts : branchée sur ALLOW_DAMAGE n'est pas possible (il ne modifie pas le montant) ;
		// elle est appliquée par LivingEntityMixin via modifyDamage().
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				reset(player);
			}
		});
	}

	// ------------------------------------------------------------------ état

	public static GiraffeState state(LivingEntity player) {
		GiraffeState s = player.getAttached(ModAttachments.GIRAFFE);
		return s == null ? GiraffeState.HUMAN : s;
	}

	private static long now(ServerPlayer player) {
		return player.level().getGameTime();
	}

	public static boolean hasFruit(ServerPlayer player) {
		return player.getAttached(ModAttachments.FRUIT) == DevilFruit.USHI_USHI_NO_MI && !DemonCurse.isSuppressed(player);
	}

	/** Forme dont les statistiques s'appliquent MAINTENANT (la forme visée seulement une fois la transformation terminée). */
	public static int effectiveForm(LivingEntity entity) {
		GiraffeState s = state(entity);
		if (s.form() != s.prev() && TRANSFORM_END.containsKey(entity.getUUID())) {
			return s.prev();
		}
		return s.form();
	}

	private static boolean transforming(ServerPlayer player) {
		Long end = TRANSFORM_END.get(player.getUUID());
		return end != null && now(player) < end;
	}

	// ------------------------------------------------------------------ résistance (appelée par LivingEntityMixin)

	/** Hybride : -20 % de dégâts reçus ; Girafe : -25 %. Les dégâts qui ignorent tout (vide, /kill) ne sont pas réduits. */
	public static float modifyDamage(LivingEntity victim, ServerLevel level, DamageSource source, float amount) {
		if (amount <= 0.0F || !(victim instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		return switch (effectiveForm(player)) {
			case GiraffeAnim.HYBRID -> amount * HYBRID_RESIST;
			case GiraffeAnim.GIRAFFE -> amount * GIRAFFE_RESIST;
			default -> amount;
		};
	}

	// ------------------------------------------------------------------ changement de forme

	/** Touche V : forme suivante. Touche B : retour humain. Tout est revérifié ici. */
	public static void requestForm(ServerPlayer player, boolean toHuman) {
		if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		GiraffeState s = state(player);
		if (!hasFruit(player)) {
			if (s.form() != GiraffeAnim.HUMAN) {
				reset(player);
			} else if (player.getAttached(ModAttachments.FRUIT) == DevilFruit.USHI_USHI_NO_MI) {
				player.sendSystemMessage(Component.literal("Malédiction du Démon : ton fruit est scellé sous l'eau."), true);
			}
			return;
		}
		long now = level.getGameTime();
		boolean busy = transforming(player);
		int target;
		if (toHuman) {
			if (s.form() == GiraffeAnim.HUMAN && !busy) {
				return;
			}
			target = GiraffeAnim.HUMAN;
		} else {
			if (busy) {
				player.sendSystemMessage(Component.literal("Transformation en cours..."), true);
				return;
			}
			target = switch (s.form()) {
				case GiraffeAnim.HUMAN -> GiraffeAnim.HYBRID;
				case GiraffeAnim.HYBRID -> GiraffeAnim.GIRAFFE;
				default -> GiraffeAnim.HUMAN;
			};
		}

		// Un changement de forme interrompt les attaques en cours.
		cancelAttacks(player);

		if (target != GiraffeAnim.HUMAN && !hasRoom(level, player, scaleOf(target))) {
			player.sendSystemMessage(Component.literal("Pas assez de place pour te transformer ici."), true);
			return;
		}

		// Retour humain pendant une transformation : on repart de la forme actuellement visible.
		int from = busy ? (visualMain(s, now)) : s.form();
		GiraffeState next = new GiraffeState(target, from, now, GiraffeAnim.ANIM_NONE, -10_000L);
		player.setAttached(ModAttachments.GIRAFFE, next);
		TRANSFORM_END.put(player.getUUID(), now + GiraffeAnim.transitionTicks(from, target));
		ACTIVE.add(player.getUUID());
		applySlow(player, true);

		Vec3 c = player.position();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.8F, target == GiraffeAnim.HUMAN ? 1.6F : 0.6F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.7F, 0.6F);
		player.sendSystemMessage(Component.literal(switch (target) {
			case GiraffeAnim.HYBRID -> "Forme hybride";
			case GiraffeAnim.GIRAFFE -> "Forme girafe";
			default -> "Forme humaine";
		}), true);
	}

	private static int visualMain(GiraffeState s, long now) {
		return GiraffeAnim.transition(s, now) >= 0.5F ? s.form() : s.prev();
	}

	private static double scaleOf(int form) {
		return switch (form) {
			case GiraffeAnim.HYBRID -> HYBRID_SCALE;
			case GiraffeAnim.GIRAFFE -> GIRAFFE_SCALE;
			default -> 0.0;
		};
	}

	/** Y a-t-il la place pour la hitbox de la forme visée (sans toucher de bloc) ? */
	private static boolean hasRoom(ServerLevel level, ServerPlayer player, double targetScaleMod) {
		AABB box = boxAtScale(player, targetScaleMod);
		return level.noCollision(player, box);
	}

	private static AABB boxAtScale(ServerPlayer player, double scaleMod) {
		double base = player.getAttributeBaseValue(Attributes.SCALE);
		AttributeInstance inst = player.getAttribute(Attributes.SCALE);
		double current = player.getAttributeValue(Attributes.SCALE);
		AttributeModifier mine = inst == null ? null : inst.getModifier(ID_SCALE);
		double without = current - (mine == null ? 0.0 : mine.amount() * base);
		double scale = Math.max(0.0625, without + scaleMod * base);
		double w = 0.6 * scale / 2.0;
		double h = 1.8 * scale;
		return new AABB(player.getX() - w, player.getY(), player.getZ() - w, player.getX() + w, player.getY() + h, player.getZ() + w);
	}

	/** Fait redevenir humain immédiatement (déconnexion, mort, fruit scellé / changé) : état, attributs, attaques. */
	public static void reset(ServerPlayer player) {
		cancelAttacks(player);
		if (player.hasAttached(ModAttachments.GIRAFFE)) {
			player.removeAttached(ModAttachments.GIRAFFE);
		}
		ACTIVE.remove(player.getUUID());
		TRANSFORM_END.remove(player.getUUID());
		removeAllModifiers(player);
	}

	/** Coupe les attaques en cours (changement de forme, mort, déconnexion). */
	public static void cancel(ServerPlayer player) {
		cancelAttacks(player);
	}

	private static void cancelAttacks(ServerPlayer player) {
		UUID id = player.getUUID();
		KICKS.removeIf(k -> k.owner.equals(id));
		for (Iterator<Blade> it = BLADES.iterator(); it.hasNext();) {
			Blade b = it.next();
			if (b.owner.equals(id)) {
				b.visual.remove();
				it.remove();
			}
		}
		if (BIGANS.remove(id) != null || KIRINS.remove(id) != null) {
			applySlowKirin(player, false);
		}
		GiraffeState s = state(player);
		if (s.anim() != GiraffeAnim.ANIM_NONE) {
			player.setAttached(ModAttachments.GIRAFFE, s.noAnim());
		}
	}

	// ------------------------------------------------------------------ attributs

	private static void put(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, Operation op) {
		AttributeInstance inst = player.getAttribute(attribute);
		if (inst != null) {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	private static void drop(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance inst = player.getAttribute(attribute);
		if (inst != null && inst.hasModifier(id)) {
			inst.removeModifier(id);
		}
	}

	private static void removeAllModifiers(ServerPlayer player) {
		drop(player, Attributes.SCALE, ID_SCALE);
		drop(player, Attributes.MOVEMENT_SPEED, ID_SPEED);
		drop(player, Attributes.MOVEMENT_SPEED, ID_SLOW);
		drop(player, Attributes.ATTACK_DAMAGE, ID_DAMAGE);
		drop(player, Attributes.MAX_HEALTH, ID_HEALTH);
		drop(player, Attributes.ENTITY_INTERACTION_RANGE, ID_REACH);
		drop(player, Attributes.JUMP_STRENGTH, ID_JUMP);
		drop(player, Attributes.STEP_HEIGHT, ID_STEP);
		drop(player, Attributes.SAFE_FALL_DISTANCE, ID_FALL);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void applySlow(ServerPlayer player, boolean on) {
		if (on) {
			put(player, Attributes.MOVEMENT_SPEED, ID_SLOW, -0.85, Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			drop(player, Attributes.MOVEMENT_SPEED, ID_SLOW);
		}
	}

	/** Pendant le Kirin / le Bigan, le joueur ne court pas : il tourne sur place (ralentissement fort). */
	private static void applySlowKirin(ServerPlayer player, boolean on) {
		if (!transforming(player)) {
			applySlow(player, on);
			if (on) {
				put(player, Attributes.MOVEMENT_SPEED, ID_SLOW, -0.6, Operation.ADD_MULTIPLIED_TOTAL);
			}
		}
	}

	/** Statistiques d'une forme (après la transformation). Retire d'abord celles de l'ancienne forme. */
	private static void applyStats(ServerPlayer player, int form) {
		drop(player, Attributes.MOVEMENT_SPEED, ID_SPEED);
		drop(player, Attributes.ATTACK_DAMAGE, ID_DAMAGE);
		drop(player, Attributes.ENTITY_INTERACTION_RANGE, ID_REACH);
		drop(player, Attributes.JUMP_STRENGTH, ID_JUMP);
		drop(player, Attributes.STEP_HEIGHT, ID_STEP);
		drop(player, Attributes.SAFE_FALL_DISTANCE, ID_FALL);

		boolean hadHealth = player.getAttribute(Attributes.MAX_HEALTH) != null
				&& player.getAttribute(Attributes.MAX_HEALTH).hasModifier(ID_HEALTH);
		if (form == GiraffeAnim.HYBRID) {
			put(player, Attributes.MOVEMENT_SPEED, ID_SPEED, 0.20, Operation.ADD_MULTIPLIED_TOTAL);       // +20 % vitesse
			put(player, Attributes.ATTACK_DAMAGE, ID_DAMAGE, 0.35, Operation.ADD_MULTIPLIED_TOTAL);      // +35 % mêlée
			put(player, Attributes.ENTITY_INTERACTION_RANGE, ID_REACH, 1.0, Operation.ADD_VALUE);          // portée de mêlée +1
			put(player, Attributes.STEP_HEIGHT, ID_STEP, 0.4, Operation.ADD_VALUE);
		} else if (form == GiraffeAnim.GIRAFFE) {
			put(player, Attributes.MOVEMENT_SPEED, ID_SPEED, -0.10, Operation.ADD_MULTIPLIED_TOTAL);      // un peu plus lent
			put(player, Attributes.JUMP_STRENGTH, ID_JUMP, 0.60, Operation.ADD_MULTIPLIED_TOTAL);         // sauts plus puissants
			put(player, Attributes.ENTITY_INTERACTION_RANGE, ID_REACH, 2.0, Operation.ADD_VALUE);
			put(player, Attributes.STEP_HEIGHT, ID_STEP, 0.6, Operation.ADD_VALUE);
			put(player, Attributes.SAFE_FALL_DISTANCE, ID_FALL, 3.0, Operation.ADD_VALUE);
			// +50 % de points de vie TEMPORAIRES : retirés (et la vie ramenée au maximum) en quittant la forme.
			float before = player.getMaxHealth();
			put(player, Attributes.MAX_HEALTH, ID_HEALTH, 0.5, Operation.ADD_MULTIPLIED_BASE);
			if (!hadHealth) {
				player.heal(player.getMaxHealth() - before);
			}
		}
		if (form != GiraffeAnim.GIRAFFE) {
			drop(player, Attributes.MAX_HEALTH, ID_HEALTH);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty() && KICKS.isEmpty() && BLADES.isEmpty()) {
			return;
		}
		for (UUID id : new ArrayList<>(ACTIVE)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				ACTIVE.remove(id);
				TRANSFORM_END.remove(id);
				continue;
			}
			tickPlayer(player);
		}
		for (Iterator<Kick> it = KICKS.iterator(); it.hasNext();) {
			if (!it.next().tick(server)) {
				it.remove();
			}
		}
		for (Iterator<Blade> it = BLADES.iterator(); it.hasNext();) {
			Blade b = it.next();
			if (!b.tick(server)) {
				b.visual.remove();
				it.remove();
			}
		}
		for (Iterator<Map.Entry<UUID, Bigan>> it = BIGANS.entrySet().iterator(); it.hasNext();) {
			Bigan b = it.next().getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(b.owner);
			if (p == null || !b.tick(p)) {
				if (p != null) {
					endAttack(p);
				}
				it.remove();
			}
		}
		for (Iterator<Map.Entry<UUID, Kirin>> it = KIRINS.entrySet().iterator(); it.hasNext();) {
			Kirin k = it.next().getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(k.owner);
			if (p == null || !k.tick(p)) {
				if (p != null) {
					endAttack(p);
				}
				it.remove();
			}
		}
	}

	private static void endAttack(ServerPlayer player) {
		applySlowKirin(player, false);
		GiraffeState s = state(player);
		if (s.anim() != GiraffeAnim.ANIM_NONE) {
			player.setAttached(ModAttachments.GIRAFFE, s.noAnim());
		}
		keepActive(player);
	}

	private static void keepActive(ServerPlayer player) {
		if (state(player).form() == GiraffeAnim.HUMAN && !transforming(player)
				&& !BIGANS.containsKey(player.getUUID()) && !KIRINS.containsKey(player.getUUID())) {
			ACTIVE.remove(player.getUUID());
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator()) {
			reset(player);
			return;
		}
		GiraffeState s = state(player);
		long now = now(player);

		// Le fruit a été scellé (eau) ou remplacé : retour humain.
		if (s.form() != GiraffeAnim.HUMAN && (now % 10 == 0 || transforming(player)) && !hasFruit(player)) {
			reset(player);
			return;
		}

		// Animation d'attaque terminée (le coup de pied n'a pas d'objet serveur : on la coupe ici) : on libère les attaques suivantes.
		if (s.anim() != GiraffeAnim.ANIM_NONE) {
			boolean running = switch (s.anim()) {
				case GiraffeAnim.ANIM_RANKYAKU -> now - s.animStart() < GiraffeAnim.RANKYAKU_TICKS;
				case GiraffeAnim.ANIM_BIGAN -> BIGANS.containsKey(player.getUUID());
				case GiraffeAnim.ANIM_KIRIN -> KIRINS.containsKey(player.getUUID());
				default -> false;
			};
			if (!running) {
				player.setAttached(ModAttachments.GIRAFFE, s.noAnim());
				s = state(player);
			}
		}

		Long end = TRANSFORM_END.get(player.getUUID());
		if (end != null) {
			ServerLevel level = player.level() instanceof ServerLevel l ? l : null;
			if (level == null) {
				return;
			}
			float p = GiraffeAnim.transition(s, now);
			// Taille : interpolée entre l'ancienne et la nouvelle forme. On ne grandit jamais dans un bloc.
			double wanted = scaleOf(s.prev()) + (scaleOf(s.form()) - scaleOf(s.prev())) * p;
			AttributeInstance sc = player.getAttribute(Attributes.SCALE);
			AttributeModifier cur = sc == null ? null : sc.getModifier(ID_SCALE);
			double curAmount = cur == null ? 0.0 : cur.amount();
			if (wanted < curAmount || hasRoom(level, player, wanted)) {
				if (Math.abs(wanted - curAmount) > 1.0E-4) {
					put(player, Attributes.SCALE, ID_SCALE, wanted, Operation.ADD_MULTIPLIED_BASE);
				}
			}
			transformFx(level, player, s, p, now);

			if (now >= end) {
				TRANSFORM_END.remove(player.getUUID());
				applySlow(player, false);
				double target = scaleOf(s.form());
				AttributeModifier fin = sc == null ? null : sc.getModifier(ID_SCALE);
				double finAmount = fin == null ? 0.0 : fin.amount();
				if (target > finAmount + 0.01 && !hasRoom(level, player, target)) {
					// Coincé : on revient à la forme d'avant plutôt que de rester bloqué dans un plafond.
					player.setAttached(ModAttachments.GIRAFFE, new GiraffeState(s.prev(), s.prev(), now, 0, -10_000L));
					put(player, Attributes.SCALE, ID_SCALE, scaleOf(s.prev()), Operation.ADD_MULTIPLIED_BASE);
					applyStats(player, s.prev());
					player.sendSystemMessage(Component.literal("Transformation annulée : pas assez de place."), true);
				} else {
					put(player, Attributes.SCALE, ID_SCALE, target, Operation.ADD_MULTIPLIED_BASE);
					applyStats(player, s.form());
					Vec3 c = player.position();
					level.sendParticles(WHITE, c.x, c.y + 1.0, c.z, 20, 0.6, 0.9, 0.6, 0.02);
					level.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.2, c.z, 14, 0.7, 0.1, 0.7, 0.08);
				}
				if (s.form() == GiraffeAnim.HUMAN) {
					removeAllModifiers(player);
				}
				keepActive(player);
			}
		}
	}

	/** Petites particules de transformation (blanc, beige, brun) : peu nombreuses, synchronisées par le serveur. */
	private static void transformFx(ServerLevel level, ServerPlayer player, GiraffeState s, float p, long now) {
		if (now % 2 != 0) {
			return;
		}
		double h = Math.max(1.0, player.getBbHeight());
		Vec3 c = player.position();
		level.sendParticles(BEIGE, c.x, c.y + h * 0.5, c.z, 3, 0.4, h * 0.4, 0.4, 0.0);
		level.sendParticles(BROWN, c.x, c.y + h * 0.6, c.z, 2, 0.4, h * 0.4, 0.4, 0.0);
		if (now % 4 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 2, 0.4, 0.05, 0.4, 0.02);
		}
	}

	// ------------------------------------------------------------------ lancement des attaques

	/** @return true si l'attaque démarre (la recharge est alors lancée par FruitAbilities). */
	public static boolean cast(ServerPlayer player, ServerLevel level, int slot) {
		GiraffeState s = state(player);
		int form = s.form();
		boolean ready = form != GiraffeAnim.HUMAN && !transforming(player);
		if (!ready) {
			player.sendSystemMessage(Component.literal("Transforme-toi d'abord (touche V : hybride / girafe)."), true);
			return false;
		}
		if (BIGANS.containsKey(player.getUUID()) || KIRINS.containsKey(player.getUUID()) || s.anim() != GiraffeAnim.ANIM_NONE) {
			return false;     // une attaque à la fois : pas de cumul d'animations
		}
		return switch (slot) {
			case 0 -> rankyaku(player, level);
			case 1 -> {
				if (form != GiraffeAnim.HYBRID) {
					player.sendSystemMessage(Component.literal("Bigan : forme hybride uniquement."), true);
					yield false;
				}
				yield bigan(player, level);
			}
			case 2 -> kirin(player, level);
			default -> false;
		};
	}

	private static void startAnim(ServerPlayer player, int anim, long now) {
		player.setAttached(ModAttachments.GIRAFFE, state(player).withAnim(anim, now));
		ACTIVE.add(player.getUUID());
	}

	private static boolean rankyaku(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		startAnim(player, GiraffeAnim.ANIM_RANKYAKU, now);
		KICKS.add(new Kick(level, player.getUUID(), now + KICK_DELAY));
		Vec3 c = player.position();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	private static boolean bigan(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		startAnim(player, GiraffeAnim.ANIM_BIGAN, now);
		BIGANS.put(player.getUUID(), new Bigan(level, player.getUUID(), now));
		Vec3 c = player.position();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GOAT_LONG_JUMP, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}

	private static boolean kirin(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		startAnim(player, GiraffeAnim.ANIM_KIRIN, now);
		KIRINS.put(player.getUUID(), new Kirin(level, player.getUUID(), now));
		applySlowKirin(player, true);
		Vec3 c = player.position();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	// ------------------------------------------------------------------ outils communs

	private static boolean validTarget(ServerPlayer owner, LivingEntity e) {
		return e != owner && e.isAlive() && !e.isSpectator() && !e.isInvulnerable();
	}

	/** Dégâts d'une attaque (jamais au lanceur). L'invulnérabilité est remise à zéro pour que chaque coup compte. */
	private static void hurt(ServerLevel level, ServerPlayer owner, LivingEntity target, float damage) {
		target.setInvulnerableTime(0);
		target.hurtServer(level, level.damageSources().playerAttack(owner), damage);
	}

	private static void knock(LivingEntity target, Vec3 dir, double force, double up) {
		Vec3 d = new Vec3(dir.x, 0, dir.z);
		d = d.lengthSqr() < 1.0E-4 ? Vec3.ZERO : d.normalize();
		target.push(d.x * force, up, d.z * force);
		target.needsSync = true;
	}

	/** Blocs solides sur le segment ? Renvoie le point d'impact ou null. */
	private static Vec3 blockHit(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
		var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
	}

	private static void cutFx(ServerLevel level, Vec3 p, double power) {
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(WHITE, p.x, p.y, p.z, (int) (6 * power), 0.5, 0.6, 0.5, 0.0);
		level.sendParticles(BLUE, p.x, p.y, p.z, (int) (4 * power), 0.5, 0.6, 0.5, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, (int) (3 * power), 0.3, 0.4, 0.3, 0.05);
	}

	private static void ringFx(ServerLevel level, Vec3 center, double radius, int points, boolean dust) {
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2.0 * i / points;
			double x = center.x + Math.cos(a) * radius;
			double z = center.z + Math.sin(a) * radius;
			level.sendParticles(i % 2 == 0 ? WHITE : BEIGE, x, center.y + 0.15, z, 1, 0.05, 0.05, 0.05, 0.0);
			if (dust && i % 3 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, x, center.y + 0.1, z, 1, 0.1, 0.02, 0.1, 0.03);
			}
		}
	}

	// ------------------------------------------------------------------ Rankyaku

	/** Coup de pied : la lame part quand la jambe est levée. */
	private static final class Kick {
		final ServerLevel level;
		final UUID owner;
		final long fireAt;

		Kick(ServerLevel level, UUID owner, long fireAt) {
			this.level = level;
			this.owner = owner;
			this.fireAt = fireAt;
		}

		boolean tick(MinecraftServer server) {
			ServerPlayer p = server.getPlayerList().getPlayer(owner);
			if (p == null || !p.isAlive()) {
				return false;
			}
			if (level.getGameTime() < fireAt) {
				return true;
			}
			Vec3 look = p.getLookAngle().normalize();
			Vec3 start = p.getEyePosition().add(look.scale(1.0)).subtract(0, 0.35, 0);
			BLADES.add(new Blade(level, owner, start, look));
			level.playSound(null, start.x, start.y, start.z, SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.2F, 0.5F);
			level.playSound(null, start.x, start.y, start.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.5F);
			level.sendParticles(ParticleTypes.CLOUD, start.x, start.y, start.z, 8, 0.4, 0.3, 0.4, 0.1);
			return false;
		}
	}

	/** Lame d'air : 1,8 bloc / tick, 25 blocs, traverse les ennemis, disparaît sur un bloc. */
	private static final class Blade {
		final ServerLevel level;
		final UUID owner;
		final Vec3 dir;
		final GiraffeVisual visual;
		final Set<UUID> hit = new HashSet<>();
		Vec3 pos;
		double travelled = 0.0;

		Blade(ServerLevel level, UUID owner, Vec3 start, Vec3 dir) {
			this.level = level;
			this.owner = owner;
			this.dir = dir;
			this.pos = start;
			float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			float pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dir.y))));
			this.visual = GiraffeVisual.create(level, "giraffe_blade", start, yaw, pitch,
					BLADE_SCALE, BLADE_SCALE, BLADE_SCALE, BLADE_TY, 1);
		}

		boolean tick(MinecraftServer server) {
			ServerPlayer p = server.getPlayerList().getPlayer(owner);
			if (p == null) {
				return false;
			}
			Vec3 next = pos.add(dir.scale(RANKYAKU_SPEED));
			Vec3 wall = blockHit(level, p, pos, next);
			Vec3 stop = wall == null ? next : wall;

			AABB sweep = box(pos).minmax(box(stop));
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep)) {
				if (validTarget(p, e) && !hit.contains(e.getUUID())) {
					hit.add(e.getUUID());
					hurt(level, p, e, RANKYAKU_DAMAGE);
					knock(e, dir, 0.9, 0.3);               // recul modéré
					cutFx(level, e.position().add(0, e.getBbHeight() * 0.6, 0), 1.5);
				}
			}

			// Traînée : vent blanc / bleuté derrière la lame.
			Vec3 back = dir.scale(-1.0);
			level.sendParticles(WHITE, stop.x, stop.y, stop.z, 3, 0.15, 1.1, 0.5, 0.0);
			level.sendParticles(BLUE, stop.x + back.x, stop.y + back.y, stop.z + back.z, 2, 0.15, 1.1, 0.5, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, stop.x, stop.y, stop.z, 0, back.x, back.y, back.z, 0.35);
			if (travelled % 3.6 < RANKYAKU_SPEED) {
				level.sendParticles(ParticleTypes.CLOUD, stop.x, stop.y, stop.z, 2, 0.3, 0.9, 0.3, 0.05);
			}

			if (wall != null) {
				cutFx(level, wall, 2.0);                   // effet de découpe à l'impact (aucun bloc détruit)
				level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.2F);
				return false;
			}
			pos = next;
			travelled += RANKYAKU_SPEED;
			visual.move(pos, visualYaw(), visualPitch());
			return travelled < RANKYAKU_RANGE;
		}

		private float visualYaw() {
			return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
		}

		private float visualPitch() {
			return (float) -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dir.y))));
		}

		private AABB box(Vec3 c) {
			return new AABB(c.x - RANKYAKU_HALF_WIDTH, c.y - RANKYAKU_HALF_HEIGHT, c.z - RANKYAKU_HALF_WIDTH,
					c.x + RANKYAKU_HALF_WIDTH, c.y + RANKYAKU_HALF_HEIGHT, c.z + RANKYAKU_HALF_WIDTH);
		}
	}

	// ------------------------------------------------------------------ Bigan

	/** Nez pistolet : le cou s'allonge de 8 blocs en 0,35 s (voir GiraffeAnim.biganReach, identique côté client). */
	private static final class Bigan {
		final ServerLevel level;
		final UUID owner;
		final long start;
		final Set<UUID> hit = new HashSet<>();
		Vec3 dir;
		double lastReach = 0.0;
		boolean blocked = false;

		Bigan(ServerLevel level, UUID owner, long start) {
			this.level = level;
			this.owner = owner;
			this.start = start;
		}

		boolean tick(ServerPlayer p) {
			if (!p.isAlive()) {
				return false;
			}
			int age = (int) (level.getGameTime() - start);
			int total = GiraffeAnim.BIGAN_PREP + GiraffeAnim.BIGAN_FLY + GiraffeAnim.BIGAN_RETURN;
			if (age >= total) {
				return false;
			}
			if (age == GiraffeAnim.BIGAN_PREP) {
				dir = p.getLookAngle().normalize();
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8F, 1.6F);
				// Petite fente vers l'avant (la physique normale empêche de traverser un mur).
				Vec3 flat = new Vec3(dir.x, 0, dir.z);
				if (flat.lengthSqr() > 1.0E-4 && blockHit(level, p, p.position().add(0, 0.5, 0), p.position().add(0, 0.5, 0).add(flat.normalize().scale(1.5))) == null) {
					flat = flat.normalize().scale(0.55);
					p.setDeltaMovement(flat.x, Math.max(p.getDeltaMovement().y, 0.0), flat.z);
					p.needsSync = true;
				}
			}
			if (dir == null) {
				if (age % 2 == 0) {
					Vec3 e = p.getEyePosition();
					level.sendParticles(BLUE, e.x, e.y, e.z, 2, 0.3, 0.3, 0.3, 0.0);
				}
				return true;
			}

			Vec3 origin = p.getEyePosition();
			double reach = GiraffeAnim.biganReach(age);
			boolean extending = age < GiraffeAnim.BIGAN_PREP + GiraffeAnim.BIGAN_FLY;
			if (extending && !blocked) {
				Vec3 wall = blockHit(level, p, origin, origin.add(dir.scale(reach + 0.5)));
				if (wall != null) {
					double d = Math.max(0.0, wall.subtract(origin).length() - 0.5);
					if (d < reach) {
						reach = d;
						blocked = true;
						level.sendParticles(ParticleTypes.CLOUD, wall.x, wall.y, wall.z, 8, 0.3, 0.3, 0.3, 0.08);
						level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 1.8F);
					}
				}
			}

			if (extending && reach > lastReach) {
				Vec3 a = origin.add(dir.scale(lastReach));
				Vec3 b = origin.add(dir.scale(reach));
				AABB sweep = new AABB(a, b).inflate(BIGAN_HALF);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep)) {
					if (validTarget(p, e) && !hit.contains(e.getUUID())) {
						hit.add(e.getUUID());
						hurt(level, p, e, BIGAN_DAMAGE);
						knock(e, dir, 1.7, 0.4);               // recul important
						impactFx(e.position().add(0, e.getBbHeight() * 0.7, 0));
					}
				}
				lastReach = reach;
			}

			// Effet de vitesse autour de la tête + traînée blanche.
			Vec3 head = origin.add(dir.scale(reach));
			if (reach > 0.8 && age < GiraffeAnim.BIGAN_PREP + GiraffeAnim.BIGAN_FLY + 1) {
				level.sendParticles(WHITE, head.x, head.y, head.z, 3, 0.25, 0.25, 0.25, 0.0);
				level.sendParticles(ParticleTypes.CLOUD, head.x, head.y, head.z, 2, 0.2, 0.2, 0.2, 0.06);
				level.sendParticles(ParticleTypes.END_ROD, head.x, head.y, head.z, 0, -dir.x, -dir.y, -dir.z, 0.3);
			}
			return true;
		}

		private void impactFx(Vec3 p) {
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 10, 0.4, 0.4, 0.4, 0.1);
			level.sendParticles(BEIGE, p.x, p.y, p.z, 12, 0.5, 0.5, 0.5, 0.0);
			ringFx(level, new Vec3(p.x, p.y - 0.6, p.z), 1.2, 10, true);     // petite onde de choc
			level.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.3F, 0.6F);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
	}

	// ------------------------------------------------------------------ Kirin Roundabout

	private static final class Kirin {
		final ServerLevel level;
		final UUID owner;
		final long start;
		final Map<UUID, Integer> hits = new HashMap<>();

		Kirin(ServerLevel level, UUID owner, long start) {
			this.level = level;
			this.owner = owner;
			this.start = start;
		}

		boolean tick(ServerPlayer p) {
			if (!p.isAlive()) {
				return false;
			}
			int age = (int) (level.getGameTime() - start);
			int spinEnd = GiraffeAnim.KIRIN_PREP + GiraffeAnim.KIRIN_SPIN;
			if (age > spinEnd) {
				return false;
			}
			Vec3 pos = p.position();
			float t = age;
			if (age < GiraffeAnim.KIRIN_PREP) {
				if (age % 2 == 0) {
					level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.1, pos.z, 3, 0.8, 0.05, 0.8, 0.04);
				}
				return true;
			}

			int spinAge = age - GiraffeAnim.KIRIN_PREP;
			double radius = KIRIN_R0 + (KIRIN_R1 - KIRIN_R0) * (spinAge / (double) GiraffeAnim.KIRIN_SPIN);

			// Traînées circulaires : 3 bras qui tournent avec le corps, sur la longueur du cou.
			double reach = Math.min(radius, GiraffeAnim.kirinReach(t) + 1.2);
			double ang = Math.toRadians(GiraffeAnim.kirinAngle(t));
			for (int arm = 0; arm < 3; arm++) {
				double a = ang + arm * Math.PI * 2.0 / 3.0;
				for (int k = 1; k <= 3; k++) {
					double r = reach * k / 3.0;
					level.sendParticles(k % 2 == 0 ? WHITE : BLUE, pos.x + Math.cos(a) * r, pos.y + 1.4, pos.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.0);
				}
			}
			if (age % 2 == 0) {
				ringFx(level, pos, radius, 14, true);
			}
			if (age % 6 == 0) {
				level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8F, 1.0F + 0.6F * spinAge / GiraffeAnim.KIRIN_SPIN);
			}

			// Un impact toutes les 0,5 s (5 au maximum par cible) ; le dernier est le coup final.
			if (spinAge > 0 && spinAge % KIRIN_INTERVAL == 0) {
				boolean last = spinAge == GiraffeAnim.KIRIN_SPIN;
				AABB zone = new AABB(pos.x - radius, pos.y - 1.0, pos.z - radius, pos.x + radius, pos.y + p.getBbHeight() + 1.5, pos.z + radius);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, zone)) {
					if (!validTarget(p, e)) {
						continue;
					}
					double dx = e.getX() - pos.x;
					double dz = e.getZ() - pos.z;
					if (dx * dx + dz * dz > radius * radius) {
						continue;
					}
					int n = hits.getOrDefault(e.getUUID(), 0);
					if (n >= KIRIN_MAX_HITS) {
						continue;
					}
					hits.put(e.getUUID(), n + 1);
					hurt(level, p, e, last ? KIRIN_DAMAGE + KIRIN_FINAL_BONUS : KIRIN_DAMAGE);
					Vec3 away = e.position().subtract(pos);
					knock(e, away, last ? 2.2 : 0.4, last ? 0.7 : 0.15);
					cutFx(level, e.position().add(0, e.getBbHeight() * 0.6, 0), last ? 1.8 : 1.0);
				}
				level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
				if (last) {
					finalWave(pos, radius);
				}
			}
			return true;
		}

		private void finalWave(Vec3 pos, double radius) {
			for (int r = 1; r <= 3; r++) {
				ringFx(level, pos, radius * r / 3.0, 24 + r * 8, true);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 1, 0, 0, 0, 0);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.3F, 0.8F);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.2F, 0.7F);
		}
	}
}
