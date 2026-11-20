package minepiece.noah.ability.ishi;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.MinePiece;
import minepiece.noah.ability.FruitAbilities;
import minepiece.noah.ability.zushi.ZushiFx;
import minepiece.noah.ability.zushi.ZushiTerrain;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.haki.HakiData;
import minepiece.noah.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * V — Titan de pierre (comme Pica) : le joueur s'immobilise, se concentre, s'enfonce dans la roche et FUSIONNE avec le
 * terrain ; la pierre du sol s'arrache et vole vers lui pendant qu'un corps gigantesque se forme (jambes, torse, épaules
 * et bras, tête, mains). Le joueur contrôle ce corps : il marche (lourdement), frappe, et W / X deviennent leurs versions
 * Titan. V à nouveau (ou structure détruite, ou durée max) : le titan se fissure, s'effondre, et le joueur ressort du sol.
 *
 * Mécanique (pas un Logia) :
 * - le corps du titan EST le joueur agrandi (attribut minecraft:scale, max 16 en vanilla) : collision, caméra, portée
 *   et rendu à sa taille ; le modèle 3D du titan remplace celui du joueur (rendu client, un seul modèle animé) ;
 * - les dégâts reçus usent la roche (points de structure), pas le joueur ; le Haki de l'Armement de l'attaquant
 *   fissure davantage la pierre ; à zéro le titan s'effondre et le joueur est sonné ;
 * - il faut une masse de roche suffisante sous les pieds, et la roche est réellement prélevée (fosse) si la destruction
 *   est autorisée ; elle retourne en éboulis à l'effondrement.
 */
public final class StoneTitan {
	private static final Identifier SCALE = MinePiece.id("ishi_titan_scale");
	private static final Identifier FREEZE = MinePiece.id("ishi_titan_freeze");
	private static final Identifier FREEZE_JUMP = MinePiece.id("ishi_titan_freeze_jump");
	private static final Identifier SPEED = MinePiece.id("ishi_titan_speed");
	private static final Identifier STEP = MinePiece.id("ishi_titan_step");
	private static final Identifier REACH = MinePiece.id("ishi_titan_reach");
	private static final Identifier BLOCK_REACH = MinePiece.id("ishi_titan_block_reach");
	private static final Identifier MELEE = MinePiece.id("ishi_titan_melee");
	private static final Identifier KNOCKBACK = MinePiece.id("ishi_titan_kb");
	private static final int COOLDOWN_SLOT = 2;

	private static final Map<UUID, Titan> TITANS = new HashMap<>();

	private StoneTitan() {}

	private static final class Titan {
		final ServerPlayer player;
		final ServerLevel level;
		final int mat;
		final BlockState rock;
		final BlockPos origin;
		final double scale;
		long start;
		int phase = IshiAnim.PHASE_FORM;
		int age;
		double integrity;
		boolean forced;
		int stepTick;

		Titan(ServerPlayer player, ServerLevel level, int mat, BlockState rock, double scale) {
			this.player = player;
			this.level = level;
			this.mat = mat;
			this.rock = rock;
			this.origin = player.blockPosition();
			this.scale = scale;
			this.integrity = IshiConfig.get().titanIntegrity;
		}
	}

	// ------------------------------------------------------------------ état

	public static boolean isTitan(UUID id) {
		Titan t = TITANS.get(id);
		return t != null;
	}

	/** Titan entièrement formé et contrôlable (W / X deviennent leurs versions Titan). */
	public static boolean isActive(UUID id) {
		Titan t = TITANS.get(id);
		return t != null && t.phase == IshiAnim.PHASE_FORM && t.age >= IshiAnim.FORM_END;
	}

	// ------------------------------------------------------------------ V

	static boolean toggle(ServerPlayer p, ServerLevel level) {
		Titan t = TITANS.get(p.getUUID());
		if (t != null) {
			if (isActive(p.getUUID())) {
				collapse(t, false);
				return true;
			}
			return false;   // formation / effondrement en cours
		}
		IshiConfig cfg = IshiConfig.get();
		if (!p.onGround()) {
			p.sendSystemMessage(Component.literal("§7Tu dois avoir les pieds sur la roche."), true);
			return false;
		}
		BlockPos feet = p.blockPosition();
		int stone = IshiFx.countRock(level, feet, 4, 6);
		if (cfg.requireStone && stone < cfg.titanMinStone) {
			p.sendSystemMessage(Component.literal("§7Pas assez de roche sous tes pieds pour former un titan (" + stone + " / " + cfg.titanMinStone + ")."), true);
			return false;
		}
		int sky = level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ());
		if (sky > feet.getY() + 1) {
			p.sendSystemMessage(Component.literal("§7Il faut un espace dégagé au-dessus de toi pour que le titan se dresse."), true);
			return false;
		}
		BlockState rock = IshiFx.rockBelow(level, feet.getX(), feet.getY() - 1, feet.getZ(), 8);
		if (rock == null) {
			rock = Blocks.STONE.defaultBlockState();
		}
		int mat = IshiFx.material(rock);
		Titan t2 = new Titan(p, level, mat, rock, cfg.titanScale);
		t2.start = level.getGameTime();
		TITANS.put(p.getUUID(), t2);
		p.setAttached(ModAttachments.ISHI_TITAN, IshiAnim.titan(t2.start, mat, IshiAnim.PHASE_FORM));
		p.setAttached(ModAttachments.ISHI_TITAN_SIZE, (int) Math.round(cfg.titanScale * 10) * 2048 + (int) Math.round(cfg.titanVisualHeight * 10));
		p.setAttached(ModAttachments.ISHI_INTEGRITY, 1.0F);
		p.removeAttached(ModAttachments.ISHI_ACTION);
		freeze(p, true);
		p.sendSystemMessage(Component.literal("§7Tu fusionnes avec la roche..."), true);
		IshiFx.sound(level, p.position(), SoundEvents.WARDEN_DIG, 2.0F, 0.5F);
		return true;
	}

	/** Déclenche l'effondrement (désactivation volontaire, structure détruite, durée max, fruit retiré...). */
	private static void collapse(Titan t, boolean forced) {
		if (t.phase == IshiAnim.PHASE_COLLAPSE) {
			return;
		}
		t.phase = IshiAnim.PHASE_COLLAPSE;
		t.forced = forced;
		t.age = 0;
		t.start = t.level.getGameTime();
		t.player.setAttached(ModAttachments.ISHI_TITAN, IshiAnim.titan(t.start, t.mat, IshiAnim.PHASE_COLLAPSE));
		t.player.removeAttached(ModAttachments.ISHI_ACTION);
		freeze(t.player, true);
		bonuses(t.player, false, 0);
		IshiFx.sound(t.level, t.player.position(), SoundEvents.IRON_GOLEM_DEATH, 4.0F, 0.4F);
		IshiFx.sound(t.level, t.player.position(), SoundEvents.DEEPSLATE_BREAK, 4.0F, 0.5F);
		if (forced) {
			t.player.sendSystemMessage(Component.literal("§cTon titan de pierre se brise !"), true);
		}
	}

	// ------------------------------------------------------------------ dégâts reçus

	/** Appelé AVANT les dégâts : false = annulés (la roche du titan encaisse à la place du joueur). */
	public static boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		Titan t = TITANS.get(p.getUUID());
		if (t == null) {
			return true;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		boolean inside = t.phase == IshiAnim.PHASE_FORM ? t.age >= IshiAnim.SINK_START : t.age < IshiAnim.COLLAPSE_END;
		if (!inside) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FIRE)) {
			return false;
		}
		if (t.phase != IshiAnim.PHASE_FORM) {
			return false;
		}
		double k = 1.0;
		if (source.getEntity() instanceof ServerPlayer attacker && HakiData.has(attacker, HakiData.F_ARM)) {
			k = 1.5;   // le Haki de l'Armement fissure la pierre
		}
		t.integrity -= amount * k;
		p.setAttached(ModAttachments.ISHI_INTEGRITY, (float) Math.max(0.0, t.integrity / IshiConfig.get().titanIntegrity));
		Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : p.position().add(0, p.getBbHeight() * 0.5, 0);
		IshiFx.burst(t.level, at, IshiFx.materialBlock(t.mat), 0.6, (int) Math.min(30, 6 + amount), true);
		IshiFx.sound(t.level, at, SoundEvents.STONE_BREAK, 2.0F, 0.6F);
		if (t.integrity <= 0.0) {
			collapse(t, true);
		}
		return false;
	}

	// ------------------------------------------------------------------ tick

	static void tick(MinecraftServer server) {
		for (Iterator<Titan> it = TITANS.values().iterator(); it.hasNext();) {
			Titan t = it.next();
			ServerPlayer p = t.player;
			if (p.isRemoved() || !p.isAlive() || p.level() != t.level) {
				reset(p);
				it.remove();
				continue;
			}
			if (p.getAttached(ModAttachments.FRUIT) != DevilFruit.ISHI_ISHI_NO_MI && t.phase == IshiAnim.PHASE_FORM) {
				collapse(t, true);
			}
			t.age++;
			if (t.phase == IshiAnim.PHASE_FORM) {
				if (!tickForm(t)) {
					it.remove();
				}
			} else if (!tickCollapse(t)) {
				it.remove();
			}
		}
	}

	private static boolean tickForm(Titan t) {
		ServerPlayer p = t.player;
		ServerLevel level = t.level;
		RandomSource r = level.getRandom();
		Vec3 feet = p.position();
		int a = t.age;
		IshiConfig cfg = IshiConfig.get();
		if (a < IshiAnim.SINK_START) {
			// concentration : fissures sous les pieds
			if (a == 2) {
				IshiFx.crackDisc(level, feet, 2.5, 10, IshiAnim.SINK_END);
			}
			ZushiFx.send(level, ModParticles.STONE_DUST, feet.x, feet.y + 0.1, feet.z, 2, 0.6, 0.05, 0.6, 0.01);
		} else if (a < IshiAnim.SINK_END) {
			// le joueur s'enfonce dans la pierre (rendu client), le sol vibre
			ZushiFx.send(level, new BlockParticleOption(ParticleTypes.BLOCK, t.rock), feet.x, feet.y + 0.1, feet.z, 6, 0.4, 0.1, 0.4, 0.1);
			ZushiFx.send(level, ModParticles.STONE_FRAGMENT, feet.x, feet.y + 0.2, feet.z, 3, 0.5, 0.2, 0.5, 0.1);
			if (a % 6 == 0) {
				IshiFx.sound(level, feet, SoundEvents.WARDEN_DIG, 2.0F, 0.5F);
				ZushiFx.shake(level, feet, 24, 1, 6);
			}
			if (a == IshiAnim.SINK_END - 4) {
				IshiFx.sound(level, feet, SoundEvents.WARDEN_EMERGE, 4.0F, 0.6F);
			}
		} else if (a < IshiAnim.FORM_END) {
			float k = IshiAnim.formProgress(a);
			double s = 1.0 + (t.scale - 1.0) * IshiAnim.smooth(k);
			setScale(p, s);
			if (a == IshiAnim.SINK_END) {
				// la roche est puisée dans le terrain : fosse sous le titan
				IshiFx.crater(level, p, feet.add(0, -1.0, 0), 4.5);
				ZushiFx.shake(level, feet, 80, 4, 20);
				IshiFx.shockwave(level, feet, 12, 14);
			}
			// la pierre s'arrache du sol et vole vers la partie du corps en formation
			double h = p.getBbHeight();
			double partY = feet.y + h * Math.min(0.95, 0.15 + k * 0.85);
			for (int i = 0; i < 2; i++) {
				double ang = r.nextDouble() * Math.PI * 2;
				double dist = 6 + r.nextDouble() * 10 * (0.5 + k);
				int x = (int) Math.floor(feet.x + Math.cos(ang) * dist);
				int z = (int) Math.floor(feet.z + Math.sin(ang) * dist);
				int y = IshiFx.surface(level, x, z);
				BlockState gs = level.getBlockState(new BlockPos(x, y, z));
				if (!IshiFx.isRock(gs)) {
					gs = IshiFx.materialBlock(t.mat);
				}
				Vec3 from = new Vec3(x + 0.5, y + 1.0, z + 0.5);
				Vec3 to = new Vec3(feet.x + (r.nextDouble() - 0.5) * p.getBbWidth(), partY, feet.z + (r.nextDouble() - 0.5) * p.getBbWidth());
				IshiFx.converge(level, gs, from, to, 0.8 + r.nextDouble() * 1.2 * (0.5 + k), 10 + r.nextInt(6));
			}
			if (a % 2 == 0) {
				IshiFx.dust(level, feet.add(0, 0.5, 0), 3.0 + k * 6.0, 6, true);
				IshiFx.burst(level, feet.add(0, h * k * 0.8, 0), t.rock, 1.0 + k * 3.0, 6, true);
			}
			if (a % 10 == 0) {
				ZushiFx.shake(level, feet, 60, 2, 8);
				IshiFx.sound(level, feet, SoundEvents.BASALT_BREAK, 4.0F, 0.4F + k * 0.2F);
				IshiFx.sound(level, feet, SoundEvents.STONE_PLACE, 4.0F, 0.4F);
			}
		} else if (a == IshiAnim.FORM_END) {
			setScale(p, t.scale);
			freeze(p, false);
			bonuses(p, true, t.scale);
			IshiFx.shockwave(level, feet, 18, 16);
			ZushiFx.shake(level, feet, 90, 4, 16);
			IshiFx.sound(level, feet, SoundEvents.RAVAGER_ROAR, 5.0F, 0.35F);
			p.sendSystemMessage(Component.literal("§7Titan de pierre §8: §fW / X amplifiés, V pour te libérer."), true);
		} else {
			active(t, cfg);
		}
		return true;
	}

	/** Titan contrôlé : pas lourds (secousse, poussière), arbres écrasés, structure affichée, durée max. */
	private static void active(Titan t, IshiConfig cfg) {
		ServerPlayer p = t.player;
		ServerLevel level = t.level;
		Vec3 v = p.getDeltaMovement();
		double speed = Math.sqrt(v.x * v.x + v.z * v.z);
		if (speed > 0.03 && p.onGround()) {
			t.stepTick++;
			if (t.stepTick % 12 == 0) {
				boolean right = (t.stepTick / 12) % 2 == 0;
				Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
				Vec3 side = new Vec3(-fwd.z, 0, fwd.x).scale(right ? -0.18 * p.getBbWidth() * 1.5 : 0.18 * p.getBbWidth() * 1.5);
				Vec3 foot = p.position().add(side).add(fwd.scale(p.getBbWidth() * 0.3));
				BlockPos fb = BlockPos.containing(foot.x, foot.y - 0.5, foot.z);
				BlockState gs = level.getBlockState(fb);
				IshiFx.dust(level, foot.add(0, 0.3, 0), 2.0, 10, true);
				if (!gs.isAir()) {
					ZushiFx.far(level, new BlockParticleOption(ParticleTypes.BLOCK, gs), foot.x, foot.y + 0.2, foot.z, 16, 1.5, 0.2, 1.5, 0.2);
				}
				ZushiFx.shake(level, foot, 40, 1, 5);
				IshiFx.sound(level, foot, SoundEvents.RAVAGER_STEP, 3.0F, 0.35F);
				IshiFx.sound(level, foot, SoundEvents.BASALT_BREAK, 2.0F, 0.4F);
			}
		}
		if (cfg.titanCrushTrees && cfg.destroyTerrain && t.age % 5 == 0) {
			crushTrees(t);
		}
		if (t.age - IshiAnim.FORM_END > cfg.titanMaxSeconds * 20) {
			collapse(t, false);
		}
	}

	/** Feuillages, troncs et plantes dans les jambes du titan sont écrasés (60 blocs max par passage). */
	private static void crushTrees(Titan t) {
		ServerPlayer p = t.player;
		ServerLevel level = t.level;
		int budget = 60;
		var box = p.getBoundingBox();
		int x0 = (int) Math.floor(box.minX), x1 = (int) Math.floor(box.maxX);
		int z0 = (int) Math.floor(box.minZ), z1 = (int) Math.floor(box.maxZ);
		int y0 = (int) Math.floor(box.minY), y1 = (int) Math.floor(box.minY + box.getYsize() * 0.6);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1 && budget > 0; x++) {
			for (int z = z0; z <= z1 && budget > 0; z++) {
				for (int y = y0; y <= y1 && budget > 0; y++) {
					m.set(x, y, z);
					BlockState s = level.getBlockState(m);
					if ((s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.REPLACEABLE)) && !s.isAir()
							&& s.getFluidState().isEmpty() && ZushiTerrain.allowed(level, m, p)) {
						level.destroyBlock(m, false, p, 512);
						budget--;
					}
				}
			}
		}
	}

	private static boolean tickCollapse(Titan t) {
		ServerPlayer p = t.player;
		ServerLevel level = t.level;
		RandomSource r = level.getRandom();
		int a = t.age;
		double h = p.getBbHeight();
		Vec3 base = p.position();
		if (a < IshiAnim.COLLAPSE_FALL) {
			// le titan se fissure : éclats sur tout le corps
			IshiFx.burst(level, base.add((r.nextDouble() - 0.5) * p.getBbWidth(), h * r.nextDouble(), (r.nextDouble() - 0.5) * p.getBbWidth()),
					IshiFx.materialBlock(t.mat), 1.0, 8, true);
			if (a % 3 == 0) {
				IshiFx.sound(level, base, SoundEvents.STONE_BREAK, 4.0F, 0.4F);
			}
		} else if (a < IshiAnim.COLLAPSE_END) {
			// les blocs se détachent et tombent
			float k = (a - IshiAnim.COLLAPSE_FALL) / (float) (IshiAnim.COLLAPSE_END - IshiAnim.COLLAPSE_FALL);
			for (int i = 0; i < 3; i++) {
				Vec3 from = base.add((r.nextDouble() - 0.5) * p.getBbWidth() * 1.4, h * (1.0 - k) * r.nextDouble() + 2.0,
						(r.nextDouble() - 0.5) * p.getBbWidth() * 1.4);
				Vec3 vel = new Vec3((r.nextDouble() - 0.5) * 0.6, r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.6);
				ZushiFx.throwStone(level, IshiFx.materialBlock(t.mat), r.nextBoolean() ? null : IshiFx.fragment(r, t.mat), from, vel,
						1.0 + r.nextDouble() * 1.8);
			}
			if (a % 2 == 0) {
				IshiFx.dust(level, base.add(0, h * (1.0 - k) * 0.6 + 1, 0), 3.0 + 5.0 * k, 10, true);
			}
			if (a % 6 == 0) {
				ZushiFx.shake(level, base, 70, 3, 8);
				IshiFx.sound(level, base, SoundEvents.DEEPSLATE_BREAK, 4.0F, 0.4F + r.nextFloat() * 0.2F);
				IshiFx.sound(level, base, SoundEvents.GRAVEL_BREAK, 4.0F, 0.5F);
			}
			// le titan "fond" pendant qu'il s'écroule
			setScale(p, 1.0 + (t.scale - 1.0) * (1.0 - IshiAnim.smooth(k)) * 0.15 + (t.scale - 1.0) * (1.0 - k) * 0.85);
		} else if (a == IshiAnim.COLLAPSE_END) {
			setScale(p, 1.0);
			// le joueur ressort du sol à une position sûre
			BlockPos safe = safeSpot(level, p.blockPosition());
			p.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
			rubble(t, safe);
			IshiFx.shockwave(level, Vec3.atBottomCenterOf(safe), 10, 12);
			IshiFx.dust(level, Vec3.atBottomCenterOf(safe).add(0, 0.5, 0), 4.0, 30, true);
			IshiFx.sound(level, Vec3.atCenterOf(safe), SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.5F);
		} else if (a >= IshiAnim.EMERGE_END) {
			reset(p);
			if (t.forced) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2));
				p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
			}
			FruitAbilities.startCooldown(p, COOLDOWN_SLOT, DevilFruit.ISHI_ISHI_NO_MI.cooldownTicks(COOLDOWN_SLOT));
			return false;
		}
		return true;
	}

	/** Bloc libre (2 de haut) au-dessus du sol le plus proche de la colonne. */
	private static BlockPos safeSpot(ServerLevel level, BlockPos near) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
		return new BlockPos(near.getX(), Math.max(level.getMinY() + 1, y), near.getZ());
	}

	/** La roche retourne au sol : éboulis autour du point de sortie (si la destruction est autorisée). */
	private static void rubble(Titan t, BlockPos at) {
		if (!IshiConfig.get().destroyTerrain) {
			return;
		}
		ServerLevel level = t.level;
		RandomSource r = level.getRandom();
		for (int i = 0; i < 26; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double d = 2.5 + r.nextDouble() * 6.0;
			int x = (int) Math.floor(at.getX() + 0.5 + Math.cos(ang) * d);
			int z = (int) Math.floor(at.getZ() + 0.5 + Math.sin(ang) * d);
			BlockPos p = new BlockPos(x, IshiFx.surface(level, x, z) + 1, z);
			if (level.getBlockState(p).isAir() && ZushiTerrain.allowed(level, p, t.player)) {
				BlockState s = switch (r.nextInt(4)) {
					case 0 -> Blocks.GRAVEL.defaultBlockState();
					case 1 -> Blocks.COBBLESTONE.defaultBlockState();
					default -> IshiFx.materialBlock(t.mat);
				};
				level.setBlock(p, s, 3);
			}
		}
	}

	// ------------------------------------------------------------------ attributs

	private static void setScale(ServerPlayer p, double scale) {
		set(p.getAttribute(Attributes.SCALE), SCALE, scale - 1.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void freeze(ServerPlayer p, boolean on) {
		set(p.getAttribute(Attributes.MOVEMENT_SPEED), FREEZE, on ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(p.getAttribute(Attributes.JUMP_STRENGTH), FREEZE_JUMP, on ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void bonuses(ServerPlayer p, boolean on, double scale) {
		IshiConfig cfg = IshiConfig.get();
		set(p.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, on ? cfg.titanSpeedBonus : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(p.getAttribute(Attributes.STEP_HEIGHT), STEP, on ? Math.min(9.0, scale * 0.55) : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), REACH, on ? scale * 1.2 : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(p.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), BLOCK_REACH, on ? scale * 1.2 : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(p.getAttribute(Attributes.ATTACK_DAMAGE), MELEE, on ? cfg.titanMeleeBonus : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK, on ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void set(AttributeInstance inst, Identifier id, double value, AttributeModifier.Operation op) {
		if (inst == null) {
			return;
		}
		if (value == 0.0) {
			inst.removeModifier(id);
		} else {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, value, op));
		}
	}

	/** Remet le joueur à l'état normal (taille, mouvements, attachements). */
	private static void reset(ServerPlayer p) {
		setScale(p, 1.0);
		freeze(p, false);
		bonuses(p, false, 0);
		p.removeAttached(ModAttachments.ISHI_TITAN);
		p.removeAttached(ModAttachments.ISHI_TITAN_SIZE);
		p.removeAttached(ModAttachments.ISHI_INTEGRITY);
	}

	static void forget(UUID id, ServerPlayer p) {
		TITANS.remove(id);
		if (p != null) {
			reset(p);
		}
	}

	static void cleanupAll() {
		for (Titan t : TITANS.values()) {
			reset(t.player);
		}
		TITANS.clear();
	}
}
