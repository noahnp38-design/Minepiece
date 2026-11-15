package minepiece.noah.data;

import minepiece.noah.MinePiece;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

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

	private ModAttachments() {}

	/** Appelé au démarrage pour forcer le chargement de la classe. */
	public static void init() {}
}
