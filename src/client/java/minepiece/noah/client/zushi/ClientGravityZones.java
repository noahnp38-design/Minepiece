package minepiece.noah.client.zushi;

import java.util.HashMap;
import java.util.Map;

import minepiece.noah.network.GravityZonePayload;
import minepiece.noah.particle.ModParticles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Zones d'Écrasement gravitationnel actives (reçues du serveur). Côté client, sans aucun paquet par tick :
 * - de petites particules de gravité apparaissent régulièrement dans toute la zone et descendent lentement ;
 * - de temps en temps, une petite ondulation circulaire se forme au sol ;
 * - à la fin de la zone, toutes ses particules disparaissent immédiatement.
 * Le réglage "Particules" du joueur (Toutes / Réduites / Minimales) s'applique.
 */
public final class ClientGravityZones {
	private record Zone(int id, double x, double y, double z, float radius) {}

	private static final Map<Integer, Zone> ZONES = new HashMap<>();
	private static final double VIEW = 96.0;

	private ClientGravityZones() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(GravityZonePayload.TYPE, (payload, context) -> {
			if (payload.active()) {
				ZONES.put(payload.id(), new Zone(payload.id(), payload.x(), payload.y(), payload.z(), payload.radius()));
			} else {
				ZONES.remove(payload.id());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientGravityZones::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ZONES.clear());
	}

	/** Vrai si (x, z) est dans une zone encore active. */
	static boolean inside(double x, double z) {
		for (Zone zn : ZONES.values()) {
			double dx = x - zn.x, dz = z - zn.z;
			double r = zn.radius + 1.5;
			if (dx * dx + dz * dz <= r * r) {
				return true;
			}
		}
		return false;
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || ZONES.isEmpty() || mc.isPaused()) {
			return;
		}
		RandomSource r = level.getRandom();
		for (Zone zn : ZONES.values()) {
			if (mc.player.distanceToSqr(zn.x, zn.y, zn.z) > (VIEW + zn.radius) * (VIEW + zn.radius)) {
				continue;
			}
			// densité légère : ~6 particules par tick pour un rayon de 18 blocs
			int n = Math.max(2, Math.round(zn.radius * zn.radius * 0.018F));
			for (int i = 0; i < n; i++) {
				double a = r.nextDouble() * Math.PI * 2.0;
				double d = Math.sqrt(r.nextDouble()) * zn.radius;
				double x = zn.x + Math.cos(a) * d;
				double z = zn.z + Math.sin(a) * d;
				double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
				double y = Math.max(ground, zn.y - 6) + 0.6 + r.nextDouble() * 5.0;
				level.addParticle(ModParticles.GRAVITY_MOTE, x, y, z,
						(r.nextDouble() - 0.5) * 0.01, -(0.025 + r.nextDouble() * 0.035), (r.nextDouble() - 0.5) * 0.01);
			}
			// petite ondulation circulaire au sol, de temps en temps
			if (r.nextInt(5) == 0) {
				double a = r.nextDouble() * Math.PI * 2.0;
				double d = Math.sqrt(r.nextDouble()) * zn.radius * 0.95;
				double cx = zn.x + Math.cos(a) * d;
				double cz = zn.z + Math.sin(a) * d;
				double gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(cx), (int) Math.floor(cz)) + 0.12;
				double rr = 0.6 + r.nextDouble() * 0.9;
				int k = 10;
				for (int i = 0; i < k; i++) {
					double b = i / (double) k * Math.PI * 2.0;
					double cos = Math.cos(b), sin = Math.sin(b);
					level.addParticle(ModParticles.GRAVITY_MOTE, cx + cos * rr, gy, cz + sin * rr, cos * 0.03, -0.001, sin * 0.03);
				}
			}
		}
	}
}
