package minepiece.noah.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import minepiece.noah.block.ModBlocks;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ice Age (attaque 3 du Hie Hie no Mi) : attaque de ZONE, sans projectile.
 *
 * 1. Préparation courte (main levée, aura, cristaux autour du lanceur).
 * 2. Une onde de froid part du lanceur et avance au sol, anneau par anneau : la SURFACE du terrain naturel devient
 *    du sol gelé (blocs du mod), l'eau devient de la glace, de la neige et des cristaux apparaissent.
 * 3. Les créatures atteintes par le front prennent des dégâts, sont clouées sur place puis ralenties.
 * 4. Brume et neige restent un moment sur la zone, puis le terrain d'origine est remis en place.
 *
 * Seuls des blocs naturels d'une liste précise sont transformés (jamais de coffre, de machine, de bloc de construction),
 * et uniquement leur surface : les bâtiments ne sont pas détruits.
 */
final class IceAge {
	/** Mise à jour envoyée aux clients, sans réaction en chaîne des blocs voisins (rapide et sans effet de bord). */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private record Change(BlockPos pos, BlockState original, BlockState placed) {}

	private static final class Wave {
		final ServerLevel level;
		final UUID owner;
		final BlockPos origin;
		final Vec3 center;
		final int maxRadius;
		final float damage;
		final int lingerTicks;
		final Set<UUID> hit = new HashSet<>();
		final List<Change> changes = new ArrayList<>();
		double radius = 0.0;
		int ring = -1;
		int age = 0;
		int linger = 0;

		Wave(ServerLevel level, UUID owner, Vec3 center, int maxRadius, float damage, int lingerTicks) {
			this.level = level;
			this.owner = owner;
			this.origin = BlockPos.containing(center);
			this.center = center;
			this.maxRadius = maxRadius;
			this.damage = damage;
			this.lingerTicks = lingerTicks;
		}
	}

	private static final class Restore {
		final ServerLevel level;
		final List<Change> changes;
		final long at;

		Restore(ServerLevel level, List<Change> changes, long at) {
			this.level = level;
			this.changes = changes;
			this.at = at;
		}
	}

	private record FrozenEntity(ServerLevel level, UUID id, long until) {}

	/** Joueurs en préparation : game time du début. */
	private static final Map<UUID, Long> CASTS = new HashMap<>();
	private static final List<Wave> WAVES = new ArrayList<>();
	private static final List<Restore> RESTORES = new ArrayList<>();
	private static final List<FrozenEntity> FROZEN = new ArrayList<>();
	/** Positions actuellement gelées par une onde (pour ne jamais enregistrer deux fois le même bloc). */
	private static final Map<ServerLevel, Set<Long>> TOUCHED = new HashMap<>();

	private IceAge() {}

	// ------------------------------------------------------------------ lancement

	/** Touche pressée. @return true si la préparation commence. */
	static boolean start(ServerPlayer player, ServerLevel level) {
		Long current = CASTS.get(player.getUUID());
		if (current != null && current >= 0) {
			return false;
		}
		CASTS.put(player.getUUID(), level.getGameTime());
		player.setAttached(ModAttachments.RAISE_HAND, true);
		level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	/** Annule une préparation en cours (mort, déconnexion, fruit retiré...). */
	static void forget(UUID player) {
		// Marquée "annulée" : le prochain tick baisse la main du joueur et retire l'entrée.
		CASTS.computeIfPresent(player, (id, start) -> -1L);
		IceFx.endCharge(player);
	}

	private static void launch(ServerPlayer player, ServerLevel level) {
		IceAgeConfig cfg = IceAgeConfig.get();
		IceFx.endCharge(player.getUUID());
		player.setAttached(ModAttachments.RAISE_HAND, false);

		WAVES.add(new Wave(level, player.getUUID(), player.position(), cfg.radius, (float) cfg.damage, cfg.lingerTicks));
		IceFx.launch(level, player, cfg.radius, cfg.particleMultiplier);
		IceFx.mistBurst(level, player.position(), cfg.radius, cfg.particleMultiplier * 1.5);

		// Libération du froid
		BlockPos at = player.blockPosition();
		level.playSound(null, at, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 3.0F, 0.5F);
		level.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.5F, 0.55F);
		level.playSound(null, at, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 2.0F, 0.6F);

		FruitAbilities.startCooldown(player, 2, cfg.cooldownTicks);
	}

	/**
	 * Vague de gel au sol partant d'un point (impact du Pheasant Beak) : même terrain gelé, même gel des créatures et même
	 * dégel que l'Ice Age, avec son propre rayon et ses propres dégâts. Le lanceur n'est jamais touché.
	 */
	static void freezeArea(ServerLevel level, UUID owner, Vec3 center, int radius, float damage) {
		IceAgeConfig cfg = IceAgeConfig.get();
		WAVES.add(new Wave(level, owner, center, radius, damage, Math.max(20, cfg.lingerTicks / 2)));
	}

	// ------------------------------------------------------------------ tick

	static void tick(MinecraftServer server) {
		IceAgeConfig cfg = IceAgeConfig.get();
		tickCasts(server, cfg);
		tickWaves(server, cfg);
		tickRestores();
		tickFrozen(cfg);
	}

	private static void tickCasts(MinecraftServer server, IceAgeConfig cfg) {
		if (CASTS.isEmpty()) {
			return;
		}
		List<ServerPlayer> ready = new ArrayList<>();
		Iterator<Map.Entry<UUID, Long>> it = CASTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (entry.getValue() < 0 || player == null || !player.isAlive() || player.isSpectator()
					|| player.getAttached(ModAttachments.FRUIT) != DevilFruit.HIE_HIE_NO_MI
					|| !(player.level() instanceof ServerLevel level)) {
				if (player != null) {
					player.setAttached(ModAttachments.RAISE_HAND, false);
				}
				IceFx.endCharge(entry.getKey());
				it.remove();
				continue;
			}
			int held = (int) (level.getGameTime() - entry.getValue());
			double fraction = cfg.prepTicks <= 0 ? 1.0 : Math.min(1.0, held / (double) cfg.prepTicks);
			IceFx.charge(level, player, fraction, held);
			IceFx.aura(level, player, cfg.particleMultiplier);
			IceFx.mistAt(level, player.position().add(0, 0.3, 0), 0.6 + fraction * 1.2, 2);
			if (held >= cfg.prepTicks) {
				it.remove();
				ready.add(player);
			}
		}
		for (ServerPlayer player : ready) {
			if (player.level() instanceof ServerLevel level) {
				launch(player, level);
			}
		}
	}

	private static void tickWaves(MinecraftServer server, IceAgeConfig cfg) {
		Iterator<Wave> it = WAVES.iterator();
		while (it.hasNext()) {
			Wave w = it.next();
			w.age++;
			if (w.radius < w.maxRadius) {
				// ---- propagation : le front avance, on gèle les anneaux qu'il vient de franchir
				w.radius = Math.min(w.maxRadius, w.radius + cfg.speedBlocksPerTick);
				boolean water = false;
				while (w.ring < (int) w.radius) {
					w.ring++;
					water |= freezeRing(w, w.ring, cfg);
				}
				hitEntities(server, w, cfg);
				IceFx.waveStep(w.level, w.origin, w.radius, w.maxRadius, cfg.particleMultiplier);
				if (w.age % 3 == 0) {
					float pitch = 1.3F + w.level.getRandom().nextFloat() * 0.5F;
					w.level.playSound(null, w.origin, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.6F, pitch);
					w.level.playSound(null, w.origin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0F, 0.7F);
				}
				if (water) {
					w.level.playSound(null, w.origin, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.8F, 0.5F);
				}
			} else {
				// ---- la zone reste glaciale un moment : brume, neige, et les nouveaux arrivants sont gelés aussi
				w.linger++;
				if (w.linger % 4 == 0) {
					IceFx.ambient(w.level, w.origin, w.maxRadius, cfg.particleMultiplier);
				}
				if (w.linger % 5 == 0) {
					hitEntities(server, w, cfg);
				}
				if (w.linger % 40 == 0) {
					w.level.playSound(null, w.origin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 0.5F);
				}
				if (w.linger >= w.lingerTicks) {
					it.remove();
					if (cfg.restoreBlocks) {
						RESTORES.add(new Restore(w.level, w.changes, w.level.getGameTime() + cfg.restoreAfterTicks));
					} else {
						forgetTouched(w.level, w.changes);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ terrain

	/** Gèle la surface des colonnes situées à la distance "ring" du centre. @return true si de l'eau a gelé. */
	private static boolean freezeRing(Wave w, int ring, IceAgeConfig cfg) {
		ServerLevel level = w.level;
		RandomSource rnd = level.getRandom();
		Set<Long> touched = TOUCHED.computeIfAbsent(level, l -> new HashSet<>());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos up = new BlockPos.MutableBlockPos();
		double inner = ring <= 0 ? -1.0 : (ring - 1.0) * (ring - 1.0);
		double outer = (double) ring * ring;
		int vr = cfg.verticalRange;
		boolean water = false;

		for (int dx = -ring; dx <= ring; dx++) {
			for (int dz = -ring; dz <= ring; dz++) {
				double d2 = dx * dx + dz * dz;
				if (d2 <= inner || d2 > outer) {
					continue;
				}
				int x = w.origin.getX() + dx;
				int z = w.origin.getZ() + dz;
				pos.set(x, w.origin.getY(), z);
				if (!level.isLoaded(pos)) {
					continue; // on ne charge jamais de chunk pour ça
				}
				for (int y = w.origin.getY() + vr; y >= w.origin.getY() - vr; y--) {
					pos.set(x, y, z);
					if (!level.isInWorldBounds(pos)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					boolean isWater = state.is(Blocks.WATER);
					BlockState frozen = isWater ? frozenWater(rnd) : frozenGround(state, cfg);
					if (frozen == null) {
						continue;
					}
					up.set(x, y + 1, z);
					BlockState above = level.getBlockState(up);
					// Seule la surface gèle : il faut de l'air (ou une petite plante / de la neige) juste au-dessus.
					boolean plant = isSmallPlant(above);
					if (!(above.isAir() || plant || above.is(Blocks.SNOW) || above.is(ModBlocks.ICE_CRYSTAL))) {
						continue;
					}
					if (!touched.add(pos.asLong())) {
						continue;
					}
					place(w, pos, state, frozen);
					water |= isWater;

					// Décor posé sur le sol gelé : neige, cristaux ; les petites plantes disparaissent sous le givre.
					if (!isWater && (above.isAir() || plant) && touched.add(up.asLong())) {
						double roll = rnd.nextDouble();
						BlockState deco;
						if (roll < cfg.snowChance) {
							deco = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, rnd.nextInt(5) == 0 ? 2 : 1);
						} else if (roll < cfg.snowChance + cfg.crystalChance) {
							deco = ModBlocks.ICE_CRYSTAL.defaultBlockState();
						} else {
							deco = plant ? Blocks.AIR.defaultBlockState() : null;
						}
						if (deco != null) {
							place(w, up, above, deco);
						} else {
							touched.remove(up.asLong());
						}
					}
				}
			}
		}
		return water;
	}

	private static void place(Wave w, BlockPos pos, BlockState original, BlockState placed) {
		BlockPos fixed = pos.immutable();
		w.level.setBlock(fixed, placed, FLAGS);
		w.changes.add(new Change(fixed, original, placed));
	}

	/** Bloc gelé correspondant à un bloc NATUREL, ou null si on n'y touche pas (tout le reste : constructions, coffres...). */
	private static BlockState frozenGround(BlockState state, IceAgeConfig cfg) {
		if (!cfg.freezeGround) {
			return null;
		}
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
			return ModBlocks.FROZEN_GRASS_BLOCK.defaultBlockState();
		}
		if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
				|| state.is(Blocks.MUD) || state.is(Blocks.CLAY)) {
			return ModBlocks.FROZEN_DIRT.defaultBlockState();
		}
		if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)) {
			return ModBlocks.FROZEN_SAND.defaultBlockState();
		}
		if (cfg.freezeStone && (state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE)
				|| state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE) || state.is(Blocks.DEEPSLATE))) {
			return ModBlocks.FROZEN_STONE.defaultBlockState();
		}
		return null;
	}

	/** Eau -> glace, avec quelques plaques de glace compacte et de glace bleue pour les nuances. */
	private static BlockState frozenWater(RandomSource rnd) {
		int roll = rnd.nextInt(20);
		if (roll == 0) {
			return Blocks.BLUE_ICE.defaultBlockState();
		}
		if (roll <= 3) {
			return Blocks.PACKED_ICE.defaultBlockState();
		}
		return Blocks.ICE.defaultBlockState();
	}

	private static boolean isSmallPlant(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.DEAD_BUSH) || state.is(BlockTags.SMALL_FLOWERS);
	}

	// ------------------------------------------------------------------ dégel

	private static void tickRestores() {
		Iterator<Restore> it = RESTORES.iterator();
		while (it.hasNext()) {
			Restore r = it.next();
			if (r.level.getGameTime() < r.at) {
				continue;
			}
			// Dégel progressif, du bord vers le centre (la liste a été remplie du centre vers le bord).
			int n = Math.max(24, r.changes.size() / 40 + 1);
			for (int i = 0; i < n && !r.changes.isEmpty(); i++) {
				restoreOne(r.level, r.changes.remove(r.changes.size() - 1));
			}
			if (r.changes.isEmpty()) {
				it.remove();
			}
		}
	}

	private static void restoreOne(ServerLevel level, Change c) {
		Set<Long> touched = TOUCHED.get(level);
		if (touched != null) {
			touched.remove(c.pos().asLong());
		}
		// On ne remet le bloc d'origine que si le bloc gelé est toujours là (pas cassé / remplacé par un joueur).
		if (level.getBlockState(c.pos()) != c.placed()) {
			return;
		}
		boolean fluid = c.original().is(Blocks.WATER);
		level.setBlock(c.pos(), c.original(), fluid ? Block.UPDATE_ALL : FLAGS);
	}

	private static void forgetTouched(ServerLevel level, List<Change> changes) {
		Set<Long> touched = TOUCHED.get(level);
		if (touched != null) {
			for (Change c : changes) {
				touched.remove(c.pos().asLong());
			}
		}
	}

	/** Arrêt du serveur : tout le terrain gelé est remis en place avant la sauvegarde. */
	static void restoreAll() {
		boolean restore = IceAgeConfig.get().restoreBlocks;
		for (Wave w : WAVES) {
			if (restore) {
				for (int i = w.changes.size() - 1; i >= 0; i--) {
					restoreOne(w.level, w.changes.get(i));
				}
			}
		}
		for (Restore r : RESTORES) {
			for (int i = r.changes.size() - 1; i >= 0; i--) {
				restoreOne(r.level, r.changes.get(i));
			}
		}
		WAVES.clear();
		RESTORES.clear();
		FROZEN.clear();
		CASTS.clear();
		TOUCHED.clear();
	}

	// ------------------------------------------------------------------ créatures

	private static void hitEntities(MinecraftServer server, Wave w, IceAgeConfig cfg) {
		ServerLevel level = w.level;
		double r = w.radius;
		AABB box = new AABB(w.center, w.center).inflate(r + 1.0, cfg.verticalRange + 3.0, r + 1.0);
		ServerPlayer owner = server.getPlayerList().getPlayer(w.owner);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(w.owner))) {
			double dx = target.getX() - w.center.x;
			double dz = target.getZ() - w.center.z;
			if (dx * dx + dz * dz > r * r || !w.hit.add(target.getUUID())) {
				continue;
			}
			DamageSource source = owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().freeze();
			if (w.damage > 0.0F) {
				target.hurtServer(level, source, w.damage);
			}
			if (!target.isAlive()) {
				continue;
			}
			// Gel : givre vanilla sur le corps, clouée sur place, puis ralentissement plus léger.
			target.setTicksFrozen(target.getTicksRequiredToFreeze() + cfg.immobilizeTicks * 2);
			if (cfg.immobilizeTicks > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.immobilizeTicks, 9));
				target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
				IceFx.encase(level, target, cfg.immobilizeTicks);
			}
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.slowTicks, cfg.slowLevel));
			FROZEN.add(new FrozenEntity(level, target.getUUID(), level.getGameTime() + Math.max(20, cfg.immobilizeTicks)));
			IceFx.entityHit(level, target, cfg.particleMultiplier);
			level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.2F, 0.8F);
			level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
	}

	/** Particules de cristallisation autour des créatures encore prises dans la glace. */
	private static void tickFrozen(IceAgeConfig cfg) {
		if (FROZEN.isEmpty()) {
			return;
		}
		Iterator<FrozenEntity> it = FROZEN.iterator();
		while (it.hasNext()) {
			FrozenEntity f = it.next();
			long now = f.level().getGameTime();
			if (now >= f.until() || !(f.level().getEntity(f.id()) instanceof LivingEntity e) || !e.isAlive()) {
				it.remove();
				continue;
			}
			if (now % 4 == 0) {
				IceFx.frozenTick(f.level(), e, cfg.particleMultiplier);
			}
		}
	}
}
