package minepiece.noah.data;

import com.mojang.serialization.Codec;
import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModAttachments {
	/**
	 * Profil du joueur (race / faction / style).
	 * Sauvegardé avec le joueur, conservé à la mort, et synchronisé à tous les clients
	 * qui voient le joueur (pour afficher ailes, nageoires, etc.).
	 */
	public static final AttachmentType<PlayerProfile> PROFILE = AttachmentRegistry.<PlayerProfile>create(
			MinePiece.id("profile"),
			builder -> builder
					.persistent(PlayerProfile.CODEC)
					.copyOnDeath()
					.syncWith(PlayerProfile.STREAM_CODEC, AttachmentSyncPredicate.all())
	);

	/**
	 * Fruit du démon mangé par le joueur (absent = aucun). Sauvegardé et conservé à la mort.
	 * Synchronisé uniquement vers le joueur lui-même (pour l'onglet Fruit de l'interface K).
	 */
	public static final AttachmentType<DevilFruit> FRUIT = AttachmentRegistry.<DevilFruit>create(
			MinePiece.id("devil_fruit"),
			builder -> builder
					.persistent(DevilFruit.CODEC)
					.copyOnDeath()
					.syncWith(DevilFruit.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
	);

	/**
	 * Niveau et XP du joueur. Sauvegardé, conservé à la mort, synchronisé seulement vers le joueur concerné.
	 * Le client ne peut jamais l'écrire : seul le serveur modifie cette donnée (voir LevelSystem).
	 */
	public static final AttachmentType<PlayerLevel> LEVEL = AttachmentRegistry.<PlayerLevel>create(
			MinePiece.id("level"),
			builder -> builder
					.persistent(PlayerLevel.CODEC)
					.copyOnDeath()
					.syncWith(PlayerLevel.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
	);

	/**
	 * Prime du joueur (en berries). Absente = 0.
	 * Sauvegardée, conservée à la mort et synchronisée (le client en a besoin pour l'interface du profil).
	 */
	public static final AttachmentType<Long> BOUNTY = AttachmentRegistry.<Long>create(
			MinePiece.id("bounty"),
			builder -> builder
					.persistent(Codec.LONG)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
	);

	/**
	 * Argent du joueur (berries), pour les shops. Absent = 0. Sauvegardé, conservé à la mort, synchronisé seulement vers le
	 * joueur concerné ; seul le serveur le modifie (voir economy/BerryBank).
	 */
	public static final AttachmentType<Long> BERRIES = AttachmentRegistry.<Long>create(
			MinePiece.id("berries"),
			builder -> builder
					.persistent(Codec.LONG)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly())
	);

	/**
	 * Vrai pendant que le joueur vole (Skypiéen / Lunarien). Non sauvegardé, synchronisé à tous les clients :
	 * c'est ce qui permet de voir les ailes des AUTRES joueurs battre.
	 */
	public static final AttachmentType<Boolean> FLYING = AttachmentRegistry.<Boolean>create(
			MinePiece.id("flying"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
	);

	/**
	 * Vrai pendant que le joueur charge le Dai Enkai : Entei (main droite levée). Synchronisé à tous les joueurs proches ;
	 * le client lève le bras droit dans le modèle du joueur. Non sauvegardé.
	 */
	public static final AttachmentType<Boolean> RAISE_HAND = AttachmentRegistry.<Boolean>create(
			MinePiece.id("raise_hand"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
	);

	/**
	 * Pheasant Beak (Hie Hie no Mi) : game time du début de l'attaque tant que le bras du joueur est couvert de glace.
	 * Non sauvegardé, synchronisé à tous les clients (chacun calcule la progression du gel et lève le bras).
	 */
	public static final AttachmentType<Long> ICE_ARM = AttachmentRegistry.<Long>create(
			MinePiece.id("ice_arm"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
	);

	/**
	 * Zushi Zushi no Mi : vrai pendant que le joueur se tient sur sa Plateforme gravitationnelle. Non sauvegardé,
	 * synchronisé à tous les clients : le joueur est alors dessiné DEBOUT sur la roche (et non assis comme sur une monture).
	 */
	public static final AttachmentType<Boolean> GRAVITY_RIDE = AttachmentRegistry.<Boolean>create(
			MinePiece.id("gravity_ride"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
	);

	/**
	 * Goro Goro no Mi : pose animée du joueur (temps de jeu du début * 8 + pose, voir ability/goro/GoroPose). Non sauvegardée,
	 * synchronisée à tous les clients (bras tendu, bras levé, deux mains levées, lancer, KO sur le dos, relevé).
	 */
	public static final AttachmentType<Long> GORO_POSE = AttachmentRegistry.<Long>create(
			MinePiece.id("goro_pose"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
	);

	/**
	 * Goro Goro no Mi : vrai quand le Défibrillateur a servi. Sauvegardé (une déconnexion ne le recharge pas) mais PAS
	 * conservé à la mort : il redevient disponible après une vraie mort et la réapparition.
	 */
	// ------------------------------------------------------------------ Haki (voir minepiece.noah.haki)

	/** Maîtrises (4 bits chacune) : Armement | Observation << 4 | Rois << 8. Sauvegardé, gardé à la mort, envoyé au joueur seul. */
	public static final AttachmentType<Integer> HAKI_MASTERY = AttachmentRegistry.<Integer>create(
			MinePiece.id("haki_mastery"),
			builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
	);

	/** Expérience de Haki (20 bits chacune : Armement | Observation << 20 | Rois << 40). Serveur seulement. */
	public static final AttachmentType<Long> HAKI_XP = AttachmentRegistry.<Long>create(
			MinePiece.id("haki_xp"), builder -> builder.persistent(Codec.LONG).copyOnDeath()
	);

	/**
	 * Ishi Ishi no Mi : animation en cours (Éruption, Poing colossal, versions Titan), visible par tous :
	 * temps de jeu du début * 256 + matière * 32 + type (voir IshiAnim).
	 */
	public static final AttachmentType<Long> ISHI_ACTION = AttachmentRegistry.<Long>create(
			MinePiece.id("ishi_action"), builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
	);

	/** Ishi Ishi no Mi : Titan de pierre (début de la phase * 64 + matière * 4 + phase), visible par tous. */
	public static final AttachmentType<Long> ISHI_TITAN = AttachmentRegistry.<Long>create(
			MinePiece.id("ishi_titan"), builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
	);

	/** Ishi Ishi no Mi : taille du titan (échelle * 10 * 2048 + hauteur visuelle * 10), réglée côté serveur. */
	public static final AttachmentType<Integer> ISHI_TITAN_SIZE = AttachmentRegistry.<Integer>create(
			MinePiece.id("ishi_titan_size"), builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
	);

	/** Ishi Ishi no Mi : structure restante du titan (0..1), pour la jauge du joueur concerné. */
	public static final AttachmentType<Float> ISHI_INTEGRITY = AttachmentRegistry.<Float>create(
			MinePiece.id("ishi_integrity"), builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
	);

	/** Monde : le village de départ a été traité (construit, ou monde déjà existant ignoré). */
	public static final AttachmentType<Boolean> VILLAGE_DONE = AttachmentRegistry.<Boolean>create(
			MinePiece.id("village_done"), builder -> builder.persistent(Codec.BOOL)
	);

	/** Monde : point d'apparition sur la place du village de départ (absent si aucun village). */
	public static final AttachmentType<net.minecraft.core.BlockPos> VILLAGE_SPAWN = AttachmentRegistry.<net.minecraft.core.BlockPos>create(
			MinePiece.id("village_spawn"), builder -> builder.persistent(net.minecraft.core.BlockPos.CODEC)
	);

	/** Joueur : déjà placé une première fois sur la place du village. */
	public static final AttachmentType<Boolean> VILLAGE_ARRIVED = AttachmentRegistry.<Boolean>create(
			MinePiece.id("village_arrived"), builder -> builder.persistent(Codec.BOOL).copyOnDeath()
	);

	/** Le tirage (rare) de l'éveil du Haki des Rois a déjà été fait pour ce joueur. */
	public static final AttachmentType<Boolean> HAKI_KING_ROLLED = AttachmentRegistry.<Boolean>create(
			MinePiece.id("haki_king_rolled"), builder -> builder.persistent(Codec.BOOL).copyOnDeath()
	);

	/**
	 * Hakis actifs (bits : 1 Armement, 2 Observation, 4 Vision du futur, 8 imprégnation des Rois) + maîtrise de
	 * l'Armement (bits 4-5, pour l'étendue du recouvrement). Visible par tous (rendu). Remis à zéro à la reconnexion.
	 */
	public static final AttachmentType<Integer> HAKI_FLAGS = AttachmentRegistry.<Integer>create(
			MinePiece.id("haki_flags"), builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
	);

	/** Temps de jeu de la dernière activation / désactivation de l'Armement (animation de recouvrement). */
	public static final AttachmentType<Long> HAKI_ARM_TIME = AttachmentRegistry.<Long>create(
			MinePiece.id("haki_arm_time"), builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
	);

	/** Énergie de Haki (jauge), envoyée au joueur seul. */
	public static final AttachmentType<Float> HAKI_ENERGY = AttachmentRegistry.<Float>create(
			MinePiece.id("haki_energy"), builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
	);

	/** Pose animée de Haki : temps de jeu * 32 + pose (voir HakiPose). Visible par tous. */
	public static final AttachmentType<Long> HAKI_POSE = AttachmentRegistry.<Long>create(
			MinePiece.id("haki_pose"), builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
	);

	/** Entité assommée par le Haki des Rois : temps de jeu * 16 + variante de chute (+ 8 = se relève). Visible par tous. */
	public static final AttachmentType<Long> HAKI_KO = AttachmentRegistry.<Long>create(
			MinePiece.id("haki_ko"), builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all())
	);

	/** Repère sauvegardé : l'IA de ce monstre a été coupée par un KO (rétablie au chargement si le serveur s'est arrêté). */
	public static final AttachmentType<Boolean> HAKI_KO_NOAI = AttachmentRegistry.<Boolean>create(
			MinePiece.id("haki_ko_noai"), builder -> builder.persistent(Codec.BOOL)
	);

	public static final AttachmentType<Boolean> GORO_REVIVE_USED = AttachmentRegistry.<Boolean>create(
			MinePiece.id("goro_revive_used"),
			builder -> builder.persistent(Codec.BOOL)
	);

	/**
	 * Ushi Ushi no Mi : forme (humain / hybride / girafe), transition et attaque en cours. Non sauvegardé (une
	 * déconnexion ramène à la forme humaine), synchronisé à TOUS les clients pour voir la transformation des autres joueurs.
	 */
	public static final AttachmentType<minepiece.noah.ability.giraffe.GiraffeState> GIRAFFE = AttachmentRegistry.<minepiece.noah.ability.giraffe.GiraffeState>create(
			MinePiece.id("giraffe"),
			builder -> builder.syncWith(minepiece.noah.ability.giraffe.GiraffeState.STREAM_CODEC, AttachmentSyncPredicate.all())
	);

	private ModAttachments() {}

	/** Prime du joueur, 0 si elle n'a jamais été définie. */
	public static long bounty(AttachmentTarget target) {
		Long value = target.getAttached(BOUNTY);
		return value == null ? 0L : value;
	}

	/** Argent du joueur en berries, 0 s'il n'en a jamais eu. */
	public static long berries(AttachmentTarget target) {
		Long value = target.getAttached(BERRIES);
		return value == null ? 0L : value;
	}

	/** Niveau du joueur, niveau 1 sans XP s'il n'a jamais rien gagné. */
	public static PlayerLevel level(AttachmentTarget target) {
		PlayerLevel value = target.getAttached(LEVEL);
		return value == null ? PlayerLevel.START : value;
	}

	/** Appelé au démarrage pour forcer le chargement de la classe. */
	public static void init() {}
}
