package minepiece.noah.haki;

import minepiece.noah.ability.goro.GoroFx;
import minepiece.noah.ability.zushi.ZushiDisplay;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.particle.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Effets visuels du Haki côté serveur (envoyés à tous les joueurs proches) :
 * - anneaux 3D (pression des Rois, perception de l'Observation, air comprimé de l'émission) qui s'agrandissent ;
 * - éclairs 3D noirs et rouges (segments fx/haki_bolt, même système que la foudre du Goro Goro no Mi) ;
 * - particules dédiées (textures 1 à 5 : éclair, arc, décharge, aura, clash ; pression ; énergie sombre ; silhouette).
 * Les arcs qui suivent en permanence les bras et les sabres sont, eux, dessinés côté client (voir HakiClientFx et la
 * texture animée des sabres) pour ne jamais rester figés dans le monde.
 */
public final class HakiFx {
	private HakiFx() {}

	/** Anneau horizontal (diamètre en blocs) qui grandit pendant "ticks" puis disparaît. */
	public static void ring(ServerLevel level, String model, Vec3 at, double fromDiameter, double toDiameter, int ticks) {
		ZushiDisplay d = ZushiDisplay.create(level, model, at, 0.0F, 0.0F, fromDiameter / 1.5, 0.0, 1, 6.0F, true);
		d.scaleTo(toDiameter / 1.5, 0.0, ticks);
		GoroFx.temporary(d, ticks + 1);
	}

	/** Anneau vertical face à une direction (onde d'émission, impact). */
	public static void ringFacing(ServerLevel level, String model, Vec3 at, Vec3 dir, double fromDiameter, double toDiameter, int ticks) {
		float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
		float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, dir.y)))) + 90.0F;
		ZushiDisplay d = ZushiDisplay.create(level, model, at, yaw, pitch, fromDiameter / 1.5, 0.0, 1, 4.0F, true);
		d.scaleTo(toDiameter / 1.5, 0.0, ticks);
		GoroFx.temporary(d, ticks + 1);
	}

	/** Éclair 3D noir et rouge. */
	public static void bolt(ServerLevel level, Vec3 from, Vec3 to, double thickness, int life, int branches) {
		GoroFx.bolt(level, from, to, thickness, life, branches, true, "haki_bolt");
		Vec3 m = from.add(to).scale(0.5);
		ZushiFx.far(level, ModParticles.HAKI_ARC, m.x, m.y, m.z, 2, Math.abs(to.x - from.x) * 0.3 + 0.2,
				Math.abs(to.y - from.y) * 0.3 + 0.2, Math.abs(to.z - from.z) * 0.3 + 0.2, 0.0);
	}

	/** Gerbe d'éclairs noirs et rouges qui jaillissent d'un point dans toutes les directions. */
	public static void burst(ServerLevel level, Vec3 c, int count, double length, double thickness) {
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			Vec3 dir = new Vec3(r.nextDouble() - 0.5, (r.nextDouble() - 0.35) * 0.9, r.nextDouble() - 0.5).normalize();
			bolt(level, c, c.add(dir.scale(length * (0.6 + r.nextDouble() * 0.6))), thickness, 3 + r.nextInt(3), 1 + r.nextInt(2));
		}
		ZushiFx.far(level, ModParticles.HAKI_DISCHARGE, c.x, c.y, c.z, 2 + count / 2, 0.3, 0.3, 0.3, 0.0);
		ZushiFx.far(level, ModParticles.HAKI_BOLT, c.x, c.y, c.z, count * 2, length * 0.4, length * 0.3, length * 0.4, 0.0);
	}

	/** Énergie sombre autour des poings (activation de l'Armement). */
	public static void darkHands(ServerLevel level, ServerPlayer p, int count) {
		for (boolean right : new boolean[] {true, false}) {
			Vec3 h = hand(p, right);
			ZushiFx.send(level, ModParticles.HAKI_DARK, h.x, h.y, h.z, count, 0.12, 0.12, 0.12, 0.01);
		}
	}

	/** Main du joueur, bras le long du corps (position du poing du modèle). */
	public static Vec3 hand(ServerPlayer p, boolean right) {
		return GoroFx.shoulder(p, right).add(0, -0.62 * p.getBbHeight() / 1.8, 0).add(p.getLookAngle().multiply(1, 0, 1).scale(0.12));
	}
}
