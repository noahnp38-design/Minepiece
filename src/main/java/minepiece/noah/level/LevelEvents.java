package minepiece.noah.level;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/** Évènements de progression. C'est ici que tu brancheras plus tard les récompenses et bonus de niveau. */
public final class LevelEvents {
	private LevelEvents() {}

	/**
	 * Appelé côté serveur après une montée de niveau (même si plusieurs niveaux sont gagnés d'un coup :
	 * oldLevel = niveau avant, newLevel = niveau après).
	 * Exemple : LevelEvents.LEVEL_UP.register((player, oldLevel, newLevel) -> { ...donner une récompense... });
	 */
	public static final Event<LevelUp> LEVEL_UP = EventFactory.createArrayBacked(LevelUp.class,
			listeners -> (player, oldLevel, newLevel) -> {
				for (LevelUp listener : listeners) {
					listener.onLevelUp(player, oldLevel, newLevel);
				}
			});

	@FunctionalInterface
	public interface LevelUp {
		void onLevelUp(ServerPlayer player, int oldLevel, int newLevel);
	}
}
