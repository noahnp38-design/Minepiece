package minepiece.noah.data;

import java.util.List;

import minepiece.noah.MinePiece;
import minepiece.noah.ability.RacePowers;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Effets "passifs" d'une race : taille, portée et modificateurs d'attributs.
 * Les pouvoirs actifs (vol, planeur, particules...) sont dans RacePowers.
 */
public final class RaceEffects {
	/** Un joueur normal mesure 1.8 bloc. Pour faire 3 blocs : 3.0 / 1.8. */
	public static final double GIANT_SCALE = 3.0 / 1.8;

	/** Lunarien : dégâts reçus x0.7 (-30 %). Appliqué par LivingEntityMixin. */
	public static final float LUNARIAN_DAMAGE_FACTOR = 0.7F;

	private static final double DEFAULT_SCALE = 1.0;
	private static final double DEFAULT_BLOCK_REACH = 4.5;
	private static final double DEFAULT_ENTITY_REACH = 3.0;

	/**
	 * Un bonus de race = un modificateur d'attribut permanent.
	 * L'identifiant est fixe : le réappliquer REMPLACE l'ancien, donc rien ne se cumule jamais.
	 *
	 * @param amount   ADD_VALUE : +amount ; ADD_MULTIPLIED_BASE : +amount*100 % de la valeur de base
	 * @param landOnly vrai = actif seulement hors de l'eau (retiré / remis par RacePowers)
	 */
	private record Mod(String name, Holder<Attribute> attribute, double amount, Operation op, boolean landOnly) {
		Identifier id() {
			return MinePiece.id("race_" + name);
		}
	}

	/**
	 * ===== Tous les bonus d'attributs de chaque race. Pour équilibrer, change les nombres. =====
	 */
	private static List<Mod> mods(Race race) {
		return switch (race) {
			// Mink : +20 % de vitesse de déplacement, sur terre uniquement.
			case MINK -> List.of(new Mod("mink_speed", Attributes.MOVEMENT_SPEED, 0.20, Operation.ADD_MULTIPLIED_BASE, true));

			// Homme-Poisson : +25 % de vitesse de nage environ. Cet attribut n'agit que dans l'eau.
			// (Dans l'eau, le jeu divise la valeur par 2 quand on ne touche pas le sol : 0.21 donne ~ +25 % en nageant.)
			case FISHMAN -> List.of(new Mod("fishman_swim", Attributes.WATER_MOVEMENT_EFFICIENCY, 0.21, Operation.ADD_VALUE, false));

			// Pas d'attribut : Humain (aucun bonus), Skypiéen et Lunarien (vol = RacePowers, -30 % de dégâts
			// du Lunarien = LivingEntityMixin), Géant (taille et portée = apply()).
			// La respiration infinie et la vitesse de nage de l'Homme-Poisson sont dans RacePowers / RaceMovement.
			case HUMAN, SKYPIEAN, GIANT, LUNARIAN -> List.of();
		};
	}

	/**
	 * Anciens bonus qui n'existent plus : on les retire à chaque apply(), pour qu'un joueur qui les avait
	 * encore dans sa sauvegarde ne les garde pas. (Ex. : les 2 barres de vie du Lunarien.)
	 */
	private static final List<Mod> REMOVED_BONUSES = List.of(
			new Mod("lunarian_health", Attributes.MAX_HEALTH, 0.0, Operation.ADD_VALUE, false));

	private RaceEffects() {}

	public static void init() {
		// Par sécurité, on réapplique tout après un respawn.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> apply(newPlayer));
	}

	/**
	 * (Ré)applique les effets de la race du joueur. Sans profil : valeurs par défaut.
	 * Peut être appelée autant de fois qu'on veut : le résultat est toujours le même.
	 */
	public static void apply(ServerPlayer player) {
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
		Race race = profile == null ? null : profile.race();
		float healthBefore = player.getMaxHealth();

		// 1) On enlève tout ce que l'ancienne race avait donné (vol, planeur, modificateurs).
		RacePowers.reset(player);
		for (Race r : Race.values()) {
			for (Mod mod : mods(r)) {
				removeModifier(player, mod);
			}
		}
		for (Mod mod : REMOVED_BONUSES) {
			removeModifier(player, mod);
		}

		// 2) Taille et portée
		double scale = race == Race.GIANT ? GIANT_SCALE : DEFAULT_SCALE;
		set(player.getAttribute(Attributes.SCALE), scale);
		set(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), DEFAULT_BLOCK_REACH * scale);
		set(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), DEFAULT_ENTITY_REACH * scale);

		// 3) Bonus de la nouvelle race
		if (race != null) {
			for (Mod mod : mods(race)) {
				if (mod.landOnly() && player.isInWater()) {
					continue; // sera ajouté par RacePowers dès que le joueur sort de l'eau
				}
				AttributeInstance instance = player.getAttribute(mod.attribute());
				if (instance != null) {
					instance.addOrReplacePermanentModifier(new AttributeModifier(mod.id(), mod.amount(), mod.op()));
				}
			}
		}

		// 4) Vie : si le maximum monte (ex. Lunarien), on soigne de la différence ;
		//    s'il baisse, on ramène la vie actuelle sous le nouveau maximum.
		float healthAfter = player.getMaxHealth();
		if (healthAfter > healthBefore) {
			player.heal(healthAfter - healthBefore);
		} else if (player.getHealth() > healthAfter) {
			player.setHealth(healthAfter);
		}
	}

	/**
	 * Appelée à chaque tick par RacePowers : ajoute ou retire les bonus "terre uniquement"
	 * selon que le joueur est dans l'eau ou non.
	 */
	public static void updateLandBonuses(ServerPlayer player, Race race) {
		boolean onLand = !player.isInWater();
		for (Mod mod : mods(race)) {
			if (!mod.landOnly()) {
				continue;
			}
			AttributeInstance instance = player.getAttribute(mod.attribute());
			if (instance == null) {
				continue;
			}
			boolean has = instance.hasModifier(mod.id());
			if (onLand && !has) {
				instance.addOrReplacePermanentModifier(new AttributeModifier(mod.id(), mod.amount(), mod.op()));
			} else if (!onLand && has) {
				instance.removeModifier(mod.id());
			}
		}
	}

	private static void removeModifier(ServerPlayer player, Mod mod) {
		AttributeInstance instance = player.getAttribute(mod.attribute());
		if (instance != null) {
			instance.removeModifier(mod.id());
		}
	}

	private static void set(AttributeInstance attribute, double value) {
		if (attribute != null) {
			attribute.setBaseValue(value);
		}
	}
}
