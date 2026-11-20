package minepiece.noah.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.MinePiece;
import minepiece.noah.particle.ModParticles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Effets visuels de l'Ice Age (Hie Hie no Mi), purement visuels : les dégâts, le gel du terrain et la recharge sont
 * dans IceAge.
 *  - Préparation : 8 cristaux de glace 3D qui poussent et tournent autour du joueur, et des particules de glace (flocons,
 *    poussière glacée) qui se répandent de plus en plus loin avec la charge. (Plus de cercle au sol.)
 *  - Vague : un anneau de flocons et de poussière glacée qui avance avec le front de gel, et des cristaux 3D qui jaillissent
 *    du sol sur son passage (puis rétrécissent et disparaissent).
 * Tout repose sur des item_display vanilla (comme les VFX du Mera) : synchronisés automatiquement en multijoueur,
 * tag minepiece_fx, supprimés à la fin / à l'arrêt du serveur. Si une commande échoue, il ne reste que les particules.
 */
final class IceFx {
	private static final String TAG = "minepiece_fx";
	private static final int CRYSTALS = 8;
	private static final int MAX_SPIKES = 64;
	private static final int SPIKE_LIFE = 20;

	private static final DustParticleOptions CYAN = new DustParticleOptions(0x7FD8FF, 1.5F);
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xEAFBFF, 1.3F);
	private static final DustParticleOptions BLUE = new DustParticleOptions(0x3F8CE8, 1.6F);
	/** Brume glaciale : grosses particules très claires qui flottent au ras du sol. */
	private static final DustParticleOptions FOG = new DustParticleOptions(0xE4F4FF, 3.5F);
	private static final DustParticleOptions FOG_BLUE = new DustParticleOptions(0xBFE6FA, 3.0F);

	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	private static final List<Spike> SPIKES = new ArrayList<>();
	private static boolean disabled = false;

	private IceFx() {}

	// ------------------------------------------------------------------ charge

	/** Chaque tick de charge : crée si besoin puis met à jour le cercle et les cristaux, ajoute quelques particules. */
	static void charge(ServerLevel level, ServerPlayer player, double fraction, int held) {
		Charge c = CHARGES.get(player.getUUID());
		if (c == null) {
			c = new Charge(level, player);
			CHARGES.put(player.getUUID(), c);
		}
		c.update(player, fraction, held);

		// Particules de glace qui se répandent autour du joueur : de plus en plus loin et nombreuses avec la charge.
		RandomSource rnd = level.getRandom();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		int n = 3 + (int) (fraction * 6);
		for (int i = 0; i < n; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double speed = 0.08 + fraction * 0.22 + rnd.nextDouble() * 0.1;
			double rise = rnd.nextDouble() * 0.12;
			level.sendParticles(ParticleTypes.SNOWFLAKE, x + Math.cos(a) * 0.4, y + 0.15 + rnd.nextDouble() * 0.3, z + Math.sin(a) * 0.4,
					0, Math.cos(a), rise, Math.sin(a), speed);
		}
		if (held % 2 == 0) {
			for (int i = 0; i < 2 + (int) (fraction * 3); i++) {
				double a = rnd.nextDouble() * Math.PI * 2.0;
				double rr = 0.5 + rnd.nextDouble() * (1.0 + fraction * 3.0);
				level.sendParticles(i % 2 == 0 ? CYAN : WHITE, x + Math.cos(a) * rr, y + 0.1 + rnd.nextDouble() * 0.5, z + Math.sin(a) * rr,
						1, 0.1, 0.1, 0.1, 0.0);
			}
		}
		if (held % 5 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, x, y + 0.2, z, 0, rnd.nextDouble() - 0.5, 0.02, rnd.nextDouble() - 0.5, 0.12 + fraction * 0.15);
		}
	}

	/** Fin de la charge (lancement, annulation, déconnexion, mort...). Sans effet si aucune charge n'est en cours. */
	static void endCharge(UUID player) {
		Charge c = CHARGES.remove(player);
		if (c != null) {
			c.remove();
		}
	}

	// ------------------------------------------------------------------ lancement et vague

	private static int n(double base, double mult) {
		return (int) Math.round(base * mult);
	}

	/** Aura glaciale autour du corps pendant la préparation (particules du mod). */
	static void aura(ServerLevel level, ServerPlayer player, double mult) {
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		level.sendParticles(ModParticles.ICE_SPARKLE, x, y + 1.0, z, n(3, mult), 0.35, 0.6, 0.35, 0.01);
		level.sendParticles(ModParticles.ICE_SHARD, x, y + 1.2, z, n(2, mult), 0.4, 0.6, 0.4, 0.02);
		level.sendParticles(ModParticles.FROST_MIST, x, y + 0.15, z, n(1, mult), 0.5, 0.05, 0.5, 0.01);
	}

	// ------------------------------------------------------------------ brume glaciale

	/** Nuage de brume qui jaillit d'un point et roule vers l'extérieur au ras du sol. */
	static void mistBurst(ServerLevel level, Vec3 c, double radius, double mult) {
		RandomSource rnd = level.getRandom();
		int count = n(18 + radius * 2.5, mult);
		for (int i = 0; i < count; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double speed = 0.08 + rnd.nextDouble() * (0.12 + radius * 0.02);
			double y = c.y + 0.1 + rnd.nextDouble() * 0.6;
			level.sendParticles(ModParticles.FROST_MIST, c.x, y, c.z, 0, Math.cos(a), 0.02, Math.sin(a), speed);
			if (i % 2 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, c.x, y, c.z, 0, Math.cos(a), 0.01, Math.sin(a), speed * 0.8);
			}
			if (i % 3 == 0) {
				double r = rnd.nextDouble() * radius * 0.4;
				level.sendParticles(i % 2 == 0 ? FOG : FOG_BLUE, c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r, 1, 0.3, 0.1, 0.3, 0.0);
			}
		}
	}

	/** Nappe de brume posée sur une zone (disque), qui suit le relief. */
	static void mistField(ServerLevel level, BlockPos origin, double radius, double mult) {
		RandomSource rnd = level.getRandom();
		double cx = origin.getX() + 0.5;
		double cz = origin.getZ() + 0.5;
		int count = n(4 + radius * 0.6, mult);
		for (int i = 0; i < count; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double rr = Math.sqrt(rnd.nextDouble()) * radius;
			double px = cx + Math.cos(a) * rr;
			double pz = cz + Math.sin(a) * rr;
			double py = groundY(level, px, origin.getY(), pz) + 0.15 + rnd.nextDouble() * 0.5;
			level.sendParticles(i % 3 == 0 ? FOG_BLUE : FOG, px, py, pz, 1, 0.4, 0.08, 0.4, 0.0);
			level.sendParticles(ModParticles.FROST_MIST, px, py, pz, 1, 0.5, 0.05, 0.5, 0.004);
		}
	}

	/** Brume froide qui s'échappe d'un point (bras, oiseau) et retombe doucement. */
	static void mistAt(ServerLevel level, Vec3 p, double spread, int count) {
		level.sendParticles(ModParticles.FROST_MIST, p.x, p.y, p.z, count, spread, spread * 0.5, spread, 0.01);
		level.sendParticles(FOG, p.x, p.y, p.z, Math.max(1, count / 2), spread, spread * 0.5, spread, 0.0);
	}

	/** Au lancement : éclat blanc-bleu, anneau de givre et souffle de brume qui partent dans toutes les directions. */
	static void launch(ServerLevel level, ServerPlayer player, int radius, double mult) {
		double x = player.getX();
		double y = player.getY() + 0.2;
		double z = player.getZ();
		level.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.8, z, n(70, mult), 0.6, 0.6, 0.6, 0.25);
		level.sendParticles(ModParticles.ICE_SPARKLE, x, y + 0.8, z, n(40, mult), 0.5, 0.5, 0.5, 0.25);
		level.sendParticles(ModParticles.ICE_SHARD, x, y + 1.0, z, n(50, mult), 0.5, 0.5, 0.5, 0.35);
		level.sendParticles(WHITE, x, y + 0.5, z, n(16, mult), 0.5, 0.4, 0.5, 0.0);
		int points = n(48, mult);
		for (int i = 0; i < points; i++) {
			double a = 2.0 * Math.PI * i / points;
			double speed = 0.5 + radius * 0.02;
			level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 0, Math.cos(a), 0.05, Math.sin(a), speed);
			if (i % 2 == 0) {
				level.sendParticles(ModParticles.FROST_MIST, x, y + 0.1, z, 0, Math.cos(a), 0.02, Math.sin(a), speed * 0.6);
			}
		}
	}

	/** Une étape de l'onde (chaque tick, après avoir fait avancer le front) : anneau de givre qui suit le relief. */
	static void waveStep(ServerLevel level, BlockPos origin, double radius, int maxRadius, double mult) {
		RandomSource rnd = level.getRandom();
		double cx = origin.getX() + 0.5;
		double cz = origin.getZ() + 0.5;

		int points = n(Math.min(72, 14 + radius * 4), mult);
		double offset = rnd.nextDouble() * Math.PI * 2.0;
		for (int i = 0; i < points; i++) {
			double a = 2.0 * Math.PI * i / points + offset;
			double px = cx + Math.cos(a) * radius;
			double pz = cz + Math.sin(a) * radius;
			double py = groundY(level, px, origin.getY(), pz) + 0.2;
			switch (i % 4) {
				case 0 -> level.sendParticles(ModParticles.FROST_MIST, px, py, pz, 0, Math.cos(a), 0.05, Math.sin(a), 0.12);
				case 1 -> level.sendParticles(ModParticles.ICE_SHARD, px, py + 0.2, pz, 2, 0.15, 0.3, 0.15, 0.08);
				case 2 -> level.sendParticles(i % 8 == 2 ? WHITE : CYAN, px, py, pz, 1, 0.12, 0.1, 0.12, 0.0);
				default -> level.sendParticles(ParticleTypes.SNOWFLAKE, px, py, pz, 0, 0.0, 1.0, 0.0, 0.1);
			}
		}
		// Brume épaisse qui roule juste derrière le front.
		int fog = n(Math.min(30, 6 + radius * 1.2), mult);
		for (int i = 0; i < fog; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double rr = Math.max(0.0, radius - rnd.nextDouble() * 2.5);
			double px = cx + Math.cos(a) * rr;
			double pz = cz + Math.sin(a) * rr;
			double py = groundY(level, px, origin.getY(), pz) + 0.2 + rnd.nextDouble() * 0.4;
			level.sendParticles(i % 2 == 0 ? FOG : FOG_BLUE, px, py, pz, 1, 0.3, 0.08, 0.3, 0.0);
			if (i % 2 == 0) {
				level.sendParticles(ModParticles.FROST_MIST, px, py, pz, 0, Math.cos(a), 0.01, Math.sin(a), 0.06);
			}
		}
		// Étincelles et brume derrière le front.
		int trail = n(4, mult);
		for (int i = 0; i < trail && radius > 2; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double rr = radius * (0.3 + rnd.nextDouble() * 0.65);
			double px = cx + Math.cos(a) * rr;
			double pz = cz + Math.sin(a) * rr;
			double py = groundY(level, px, origin.getY(), pz);
			level.sendParticles(ModParticles.ICE_SPARKLE, px, py + 0.3, pz, 2, 0.3, 0.2, 0.3, 0.01);
			level.sendParticles(ModParticles.FROST_MIST, px, py + 0.2, pz, 1, 0.3, 0.05, 0.3, 0.01);
		}

		// Cristaux 3D qui jaillissent du sol sur le front de l'onde.
		if (radius >= 2 && !disabled) {
			int count = radius >= maxRadius - 1 ? 1 : 2 + (radius > 8 ? 1 : 0);
			for (int i = 0; i < count && SPIKES.size() < MAX_SPIKES; i++) {
				double a = rnd.nextDouble() * Math.PI * 2.0;
				double px = cx + Math.cos(a) * (radius - rnd.nextDouble() * 0.8);
				double pz = cz + Math.sin(a) * (radius - rnd.nextDouble() * 0.8);
				double gy = groundY(level, px, origin.getY(), pz);
				SPIKES.add(new Spike(level, new Vec3(px, gy, pz), 0.9 + rnd.nextDouble() * 1.6, rnd.nextDouble() * Math.PI * 2.0, SPIKE_LIFE));
			}
		}
	}

	/** Zone gelée après le passage de l'onde : neige qui tombe, brume au sol, givre qui scintille. */
	static void ambient(ServerLevel level, BlockPos origin, int radius, double mult) {
		mistField(level, origin, radius, mult);
		RandomSource rnd = level.getRandom();
		double cx = origin.getX() + 0.5;
		double cz = origin.getZ() + 0.5;
		int count = n(Math.max(6, radius * 0.7), mult);
		for (int i = 0; i < count; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double rr = Math.sqrt(rnd.nextDouble()) * radius;
			double px = cx + Math.cos(a) * rr;
			double pz = cz + Math.sin(a) * rr;
			double py = groundY(level, px, origin.getY(), pz);
			switch (i % 3) {
				case 0 -> level.sendParticles(ParticleTypes.SNOWFLAKE, px, py + 3.0 + rnd.nextDouble() * 3.0, pz, 2, 0.6, 0.4, 0.6, 0.0);
				case 1 -> level.sendParticles(ModParticles.FROST_MIST, px, py + 0.25, pz, 1, 0.5, 0.1, 0.5, 0.005);
				default -> level.sendParticles(ModParticles.ICE_SPARKLE, px, py + 0.4 + rnd.nextDouble(), pz, 1, 0.2, 0.2, 0.2, 0.0);
			}
		}
	}

	/** Une créature vient d'être gelée : éclat de glace sur elle. */
	static void entityHit(ServerLevel level, Entity e, double mult) {
		double h = e.getBbHeight();
		double w = e.getBbWidth() * 0.5 + 0.2;
		level.sendParticles(ModParticles.ICE_SHARD, e.getX(), e.getY() + h * 0.5, e.getZ(), n(24, mult), w, h * 0.4, w, 0.12);
		level.sendParticles(ModParticles.ICE_SPARKLE, e.getX(), e.getY() + h * 0.5, e.getZ(), n(12, mult), w, h * 0.4, w, 0.05);
		level.sendParticles(BLUE, e.getX(), e.getY() + h * 0.5, e.getZ(), n(8, mult), w, h * 0.4, w, 0.0);
	}

	/** Tant qu'elle est prise dans la glace : cristallisation sur le corps. */
	static void frozenTick(ServerLevel level, Entity e, double mult) {
		double h = e.getBbHeight();
		double w = e.getBbWidth() * 0.5 + 0.1;
		level.sendParticles(ModParticles.ICE_SPARKLE, e.getX(), e.getY() + h * 0.5, e.getZ(), n(3, mult), w, h * 0.4, w, 0.0);
		level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + h, e.getZ(), n(2, mult), w, 0.1, w, 0.0);
	}

	/** Impact du Pheasant Beak : couronne de cristaux 3D qui jaillissent autour du point d'impact. */
	static void burst(ServerLevel level, Vec3 at) {
		if (disabled) {
			return;
		}
		RandomSource rnd = level.getRandom();
		for (int i = 0; i < 10 && SPIKES.size() < MAX_SPIKES + 32; i++) {
			double a = i * Math.PI * 2.0 / 10 + rnd.nextDouble() * 0.4;
			double r = 1.0 + rnd.nextDouble() * 2.5;
			double px = at.x + Math.cos(a) * r;
			double pz = at.z + Math.sin(a) * r;
			double gy = groundY(level, px, (int) Math.floor(at.y), pz);
			SPIKES.add(new Spike(level, new Vec3(px, gy, pz), 1.2 + rnd.nextDouble() * 1.8, rnd.nextDouble() * Math.PI * 2.0, 40));
		}
	}

	/** Bloc de glace 3D qui enferme la créature pendant qu'elle est immobilisée. */
	static void encase(ServerLevel level, Entity e, int life) {
		if (disabled || SPIKES.size() >= MAX_SPIKES + 32) {
			return;
		}
		double h = Math.max(1.0, e.getBbHeight() * 1.2);
		SPIKES.add(new Spike(level, e.position(), h, level.getRandom().nextDouble() * Math.PI * 2.0, Math.max(10, life)));
	}

	/** Hauteur du sol (dessus du premier bloc solide) près de la hauteur de départ de la vague. */
	private static double groundY(ServerLevel level, double x, int startY, double z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = startY + 2; y >= startY - 6; y--) {
			p.set((int) Math.floor(x), y, (int) Math.floor(z));
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return y + 1.0;
			}
		}
		return startY;
	}

	// ------------------------------------------------------------------ cycle de vie

	/** Chaque tick : fait vieillir les cristaux de la vague (rétrécissent puis disparaissent). */
	static void tick(MinecraftServer server) {
		for (Iterator<Spike> it = SPIKES.iterator(); it.hasNext();) {
			if (!it.next().tick()) {
				it.remove();
			}
		}
	}

	/** Un joueur se déconnecte / meurt. */
	static void forget(UUID player) {
		endCharge(player);
	}

	/** Arrêt du serveur : rien ne doit rester dans le monde sauvegardé. */
	static void cleanupAll() {
		for (Charge c : new ArrayList<>(CHARGES.values())) {
			c.remove();
		}
		CHARGES.clear();
		for (Spike s : new ArrayList<>(SPIKES)) {
			s.remove();
		}
		SPIKES.clear();
	}

	// ------------------------------------------------------------------ entités

	private static String uuidSnbt(UUID id) {
		long msb = id.getMostSignificantBits();
		long lsb = id.getLeastSignificantBits();
		return String.format(Locale.ROOT, "%d,%d,%d,%d", (int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb);
	}

	private static String transform(double yaw, double sx, double sy, double sz, double ty) {
		double s = Math.sin(yaw / 2.0);
		double c = Math.cos(yaw / 2.0);
		return String.format(Locale.ROOT,
				"{left_rotation:[0f,%.4ff,0f,%.4ff],right_rotation:[0f,0f,0f,1f],scale:[%.4ff,%.4ff,%.4ff],translation:[0f,%.4ff,0f]}",
				s, c, sx, sy, sz, ty);
	}

	private static void summon(ServerLevel level, UUID id, String model, Vec3 at, String transformation) {
		run(level, at, "summon minecraft:item_display " + String.format(Locale.ROOT, "%.3f %.3f %.3f", at.x, at.y, at.z)
				+ " {item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"" + MinePiece.MOD_ID + ":" + model + "\"}},"
				+ "item_display:\"none\",billboard:\"fixed\",transformation:" + transformation + ","
				+ "brightness:{sky:15,block:15},view_range:3f,shadow_radius:0f,shadow_strength:0f,"
				+ "teleport_duration:2,interpolation_duration:2,Tags:[\"" + TAG + "\"],UUID:[I;" + uuidSnbt(id) + "]}");
	}

	private static void merge(ServerLevel level, UUID id, Vec3 at, String transformation, int duration) {
		run(level, at, "data merge entity " + id + " {transformation:" + transformation
				+ ",start_interpolation:0,interpolation_duration:" + duration + "}");
	}

	private static void discard(ServerLevel level, UUID id) {
		Entity e = level.getEntity(id);
		if (e != null) {
			e.discard();
		}
	}

	private static void run(ServerLevel level, Vec3 at, String command) {
		if (disabled) {
			return;
		}
		try {
			MinecraftServer server = level.getServer();
			CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPosition(at).withSuppressedOutput();
			server.getCommands().performPrefixedCommand(source, command);
		} catch (Exception e) {
			disabled = true;
			MinePiece.LOGGER.error("Rendu 3D de l'Ice Age désactivé (commande refusée : {})", command, e);
		}
	}

	/** Cercle runique + cristaux autour du lanceur pendant la charge. */
	private static final class Charge {
		private final ServerLevel level;
		private final UUID[] crystals = new UUID[CRYSTALS];
		private double lastCrystalH = -1;

		Charge(ServerLevel level, ServerPlayer player) {
			this.level = level;
			Vec3 feet = player.position();
			for (int i = 0; i < CRYSTALS; i++) {
				crystals[i] = UUID.randomUUID();
				summon(level, crystals[i], "fx/ice_crystal", feet, transform(i * 0.8, 0.5, 0.5, 0.5, 0.25));
			}
		}

		void update(ServerPlayer player, double fraction, int held) {
			Vec3 feet = player.position();
			double ringR = 1.0 + fraction * 3.0;
			double h = 0.6 + 1.4 * fraction;

			boolean rescale = Math.abs(h - lastCrystalH) > 0.08;
			if (rescale) {
				lastCrystalH = h;
			}
			for (int i = 0; i < CRYSTALS; i++) {
				double a = i * 2.0 * Math.PI / CRYSTALS + held * 0.04;
				double px = feet.x + Math.cos(a) * ringR;
				double pz = feet.z + Math.sin(a) * ringR;
				Entity e = level.getEntity(crystals[i]);
				if (e != null) {
					e.setPos(px, feet.y + h * 0.5, pz);
				}
				if (rescale) {
					merge(level, crystals[i], feet, transform(-a + i, h, h, h, 0), 4);
				}
			}
		}

		void remove() {
			for (UUID id : crystals) {
				discard(level, id);
			}
		}
	}

	/** Cristal qui jaillit du sol sur le passage de la vague. */
	private static final class Spike {
		private final ServerLevel level;
		private final UUID id = UUID.randomUUID();
		private final Vec3 base;
		private final double height;
		private final double yaw;
		private final int life;
		private int age = 0;

		Spike(ServerLevel level, Vec3 base, double height, double yaw, int life) {
			this.life = life;
			this.level = level;
			this.base = base;
			this.height = height;
			this.yaw = yaw;
			summon(level, id, "fx/ice_crystal", base, transform(yaw, 0.02, 0.02, 0.02, 0));
			merge(level, id, base, transform(yaw, height, height, height, height * 0.5), 3);
		}

		boolean tick() {
			age++;
			if (age == life - 5) {
				merge(level, id, base, transform(yaw, 0.02, 0.02, 0.02, 0), 5);
			}
			if (age >= life) {
				remove();
				return false;
			}
			return true;
		}

		void remove() {
			discard(level, id);
		}
	}
}
