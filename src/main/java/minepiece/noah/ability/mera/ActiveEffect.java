package minepiece.noah.ability.mera;

import net.minecraft.server.MinecraftServer;

/** Effet qui vit plusieurs ticks côté serveur (projectile, onde de choc, destruction de blocs...). */
interface ActiveEffect {
	/** @return false quand l'effet est terminé et doit être retiré. */
	boolean tick(MinecraftServer server);
}
