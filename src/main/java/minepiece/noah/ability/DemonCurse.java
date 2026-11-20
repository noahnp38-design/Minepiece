package minepiece.noah.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import minepiece.noah.MinePiece;
import minepiece.noah.data.DevilFruit;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.effect.ModEffects;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Faiblesse à l'eau des utilisateurs de fruits du démon.
 *
 * Quand un joueur AYANT UN FRUIT est totalement immergé :
 *  1. il est attiré vers le fond (gravité ajoutée, de plus en plus forte) ;
 *  2. après un court délai, la Malédiction du Démon apparaît : dégâts réguliers et croissants, niveau de l'effet qui monte,
 *     particules sombres, et ses pouvoirs de fruit sont SCELLÉS (voir {@link #isSuppressed}).
 * Dès qu'il n'est plus totalement immergé, tout est retiré immédiatement et les pouvoirs reviennent.
 *
 * Tout est décidé par le serveur. L'attraction passe par l'attribut de gravité (synchronisé au client par le jeu) : la
 * nage reste fluide, sans téléportation ni paquet de vitesse à chaque tick. L'état n'est PAS sauvegardé : il est recalculé
 * à chaque tick d'après la situation réelle, donc mort, réapparition, changement de dimension et déconnexion le remettent à zéro.
 */
public final class DemonCurse {
	private static final Identifier PULL_ID = MinePiece.id("demon_curse_pull");
	/** Durée (ticks) de l'effet à chaque rafraîchissement : s'il n'est plus rafraîchi, il disparaît seul très vite. */
	private static final int EFFECT_REFRESH = 10;
	private static final int EFFECT_DURATION = 40;

	private static final class State {
		final Object dimension;
		int ticks;          // ticks passés à remplir la condition d'immersion
		boolean cursed;
		double appliedPull = -1.0;

		State(Object dimension) {
			this.dimension = dimension;
		}
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private DemonCurse() {}

	public static void init() {
		CurseConfig.load();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
	}

	// ------------------------------------------------------------------ API centrale

	/**
	 * Vrai si ce joueur est temporairement privé des pouvoirs de son fruit (Malédiction du Démon active).
	 * Toute capacité d'un fruit DOIT vérifier cet état avant de s'activer. Le fruit n'est jamais retiré.
	 */
	public static boolean isSuppressed(ServerPlayer player) {
		return isSuppressed(player.getUUID());
	}

	public static boolean isSuppressed(UUID id) {
		State state = STATES.get(id);
		return state != null && state.cursed;
	}

	// ------------------------------------------------------------------ tick

	public static void tick(MinecraftServer server) {
		CurseConfig cfg = CurseConfig.get();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			update(player, cfg);
		}
		// Joueurs qui ne sont plus connectés (sécurité).
		if (!STATES.isEmpty()) {
			Iterator<UUID> it = STATES.keySet().iterator();
			while (it.hasNext()) {
				if (server.getPlayerList().getPlayer(it.next()) == null) {
					it.remove();
				}
			}
		}
	}

	private static void update(ServerPlayer player, CurseConfig cfg) {
		State state = STATES.get(player.getUUID());
		DevilFruit fruit = player.getAttached(ModAttachments.FRUIT);
		boolean eligible = cfg.enabled && fruit != null && player.isAlive()
				&& !player.isSpectator() && !player.isCreative();
		if (!eligible) {
			if (state != null) {
				end(player);
			} else if (player.hasEffect(ModEffects.DEMON_CURSE)) {
				player.removeEffect(ModEffects.DEMON_CURSE);   // reste d'une ancienne session
			}
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (state != null && !state.dimension.equals(level.dimension())) {
			end(player);              // changement de dimension : on repart de zéro
			state = null;
		}

		// Hystérésis : pour DÉMARRER on exige d'être bien sous la surface ; une fois lancé, on ne s'arrête que quand
		// les yeux ou les pieds sortent de l'eau. Évite les allers-retours à la limite de la surface.
		boolean under = fullyUnderwater(player, level, cfg, state == null || state.ticks < cfg.enterTicks);
		if (!under) {
			if (state != null) {
				end(player);
			} else if (player.hasEffect(ModEffects.DEMON_CURSE)) {
				player.removeEffect(ModEffects.DEMON_CURSE);
			}
			return;
		}
		if (state == null) {
			state = new State(level.dimension());
			STATES.put(player.getUUID(), state);
		}
		state.ticks++;
		if (state.ticks <= cfg.enterTicks) {
			return;     // confirmation en cours
		}
		int t = state.ticks - cfg.enterTicks;     // ticks de vraie immersion

		applyPull(player, state, cfg, t);

		// ---- Malédiction
		int curseTicks = t - cfg.curseDelayTicks;
		if (curseTicks < 0) {
			return;
		}
		if (!state.cursed) {
			state.cursed = true;
			FruitAbilities.cancelActive(player);    // coupe proprement les capacités en cours
			player.sendSystemMessage(Component.literal("Malédiction du Démon ! Ton fruit est scellé tant que tu es sous l'eau."), true);
		}
		int step = curseTicks / cfg.escalationIntervalTicks;
		if (curseTicks % EFFECT_REFRESH == 0 || !player.hasEffect(ModEffects.DEMON_CURSE)) {
			int amplifier = Math.min(cfg.maxCurseLevel, step);
			player.addEffect(new MobEffectInstance(ModEffects.DEMON_CURSE, EFFECT_DURATION, amplifier, false, true, true));
		}
		if (curseTicks > 0 && curseTicks % cfg.damageIntervalTicks == 0) {
			float damage = (float) Math.min(cfg.damageMax, cfg.damageAmount + step * cfg.escalationDamage);
			player.hurtServer(level, level.damageSources().drown(), damage);
		}
		if (cfg.particles && cfg.particleCount > 0 && curseTicks % cfg.particleIntervalTicks == 0) {
			level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 0.9, player.getZ(),
					cfg.particleCount, 0.35, 0.6, 0.35, 0.03);
			level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.9, player.getZ(),
					Math.max(1, cfg.particleCount / 3), 0.3, 0.5, 0.3, 0.02);
		}
	}

	/** Gravité ajoutée qui monte avec le temps. Mise à jour par paliers pour ne pas spammer le réseau. */
	private static void applyPull(ServerPlayer player, State state, CurseConfig cfg, int t) {
		double pull = Math.min(cfg.pullMax, cfg.pullStart + cfg.pullRampPerSecond * (t / 20.0));
		if (state.appliedPull >= 0 && Math.abs(pull - state.appliedPull) < 0.03 && pull < cfg.pullMax) {
			return;
		}
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		if (gravity != null) {
			gravity.addOrUpdateTransientModifier(new AttributeModifier(PULL_ID, pull, AttributeModifier.Operation.ADD_VALUE));
			state.appliedPull = pull;
		}
	}

	/** Retire tout : attraction, effet, état. Les pouvoirs reviennent d'eux-mêmes (isSuppressed repasse à false). */
	private static void end(ServerPlayer player) {
		STATES.remove(player.getUUID());
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		if (gravity != null) {
			gravity.removeModifier(PULL_ID);
		}
		if (player.hasEffect(ModEffects.DEMON_CURSE)) {
			player.removeEffect(ModEffects.DEMON_CURSE);
		}
	}

	// ------------------------------------------------------------------ détection

	/** Corps entièrement sous l'eau : les yeux ET les pieds sont dans l'eau (strict : en plus assez profond). */
	private static boolean fullyUnderwater(ServerPlayer player, ServerLevel level, CurseConfig cfg, boolean strict) {
		if (!player.isEyeInFluid(FluidTags.WATER)) {
			return false;
		}
		if (!isWater(level, BlockPos.containing(player.getX(), player.getY() + 0.1, player.getZ()))) {
			return false;
		}
		if (!strict || cfg.minEyeDepth <= 0.0) {
			return true;
		}
		double eyeY = player.getEyeY();
		BlockPos pos = BlockPos.containing(player.getX(), eyeY, player.getZ());
		for (int i = 0; i < 8 && isWater(level, pos.above()); i++) {
			pos = pos.above();
		}
		double surface = pos.getY() + level.getFluidState(pos).getHeight(level, pos);
		return surface - eyeY >= cfg.minEyeDepth;
	}

	private static boolean isWater(ServerLevel level, BlockPos pos) {
		Fluid fluid = level.getFluidState(pos).getType();
		return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
	}
}
