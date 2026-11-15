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
	 * Vrai pendant que le joueur vole (Skypiéen / Lunarien). Non sauvegardé, synchronisé à tous les clients :
	 * c'est ce qui permet de voir les ailes des AUTRES joueurs battre.
	 */
	public static final AttachmentType<Boolean> FLYING = AttachmentRegistry.<Boolean>create(
			MinePiece.id("flying"),
			builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
	);

	private ModAttachments() {}

	/** Prime du joueur, 0 si elle n'a jamais été définie. */
	public static long bounty(AttachmentTarget target) {
		Long value = target.getAttached(BOUNTY);
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
