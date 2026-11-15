package minepiece.noah.data;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Effets de gameplay liés à la race (taille, portée...). */
public final class RaceEffects {
	/** Un joueur normal mesure 1.8 bloc. Pour faire 3 blocs : 3.0 / 1.8. */
	public static final double GIANT_SCALE = 3.0 / 1.8;

	private static final double DEFAULT_SCALE = 1.0;
	private static final double DEFAULT_BLOCK_REACH = 4.5;
	private static final double DEFAULT_ENTITY_REACH = 3.0;

	private RaceEffects() {}

	public static void init() {
		// Par sécurité, on réapplique les attributs après un respawn.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> apply(newPlayer));
	}

	/** (Ré)applique les effets de la race du joueur. Sans profil : valeurs par défaut. */
	public static void apply(ServerPlayer player) {
		PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
		boolean giant = profile != null && profile.race() == Race.GIANT;

		double scale = giant ? GIANT_SCALE : DEFAULT_SCALE;
		set(player.getAttribute(Attributes.SCALE), scale);
		set(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), DEFAULT_BLOCK_REACH * scale);
		set(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), DEFAULT_ENTITY_REACH * scale);
	}

	private static void set(AttributeInstance attribute, double value) {
		if (attribute != null) {
			attribute.setBaseValue(value);
		}
	}
}
