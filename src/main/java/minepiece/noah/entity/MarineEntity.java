package minepiece.noah.entity;

import minepiece.noah.data.Faction;
import minepiece.noah.data.ModAttachments;
import minepiece.noah.data.PlayerProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Marine de base. IA 100 % native (GoalSelector / TargetGoal), factions lues dans le profil du joueur (PlayerProfile).
 *
 * Priorité des cibles (numéro de goal de ciblage : plus petit = plus prioritaire, et un goal plus prioritaire reprend la main) :
 *  1. joueurs Pirates                                       4. joueur neutre qui l'a frappé (poursuite 30 s)
 *  2. joueurs Révolutionnaires                              -  joueurs Marines : jamais ciblés (ni par riposte)
 *  3. créatures hostiles (tout ce qui est "Enemy")
 * Les recherches de cible se font toutes les ~10 ticks (comportement vanille de NearestAttackableTargetGoal), jamais à chaque tick,
 * et une cible conservée ne change pas tant qu'elle est valide.
 */
public class MarineEntity extends PathfinderMob {
	/** Durée de poursuite d'un joueur neutre qui l'a attaqué (ticks) : 30 s, renouvelée à chaque coup reçu. */
	private static final int ANGER_TICKS = 600;
	private static final double ANGER_MAX_DISTANCE = 40.0;

	private LivingEntity angryAt;
	private long angryUntil;

	public MarineEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		// Équipement de base. (Un Marine chargé depuis la sauvegarde retrouve ses vrais objets : la sauvegarde écrase ceci.)
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(minepiece.noah.item.ModItems.MARINE_SABER));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.04F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.FOLLOW_RANGE, 24.0);          // champ de détection
	}

	// ------------------------------------------------------------------ factions

	/** Faction d'un joueur (null = pas de profil / pas un joueur). Utilise le système de factions déjà présent dans le mod. */
	public static Faction factionOf(LivingEntity entity) {
		if (entity instanceof Player player) {
			PlayerProfile profile = player.getAttached(ModAttachments.PROFILE);
			return profile == null ? null : profile.faction();
		}
		return null;
	}

	private static boolean isFaction(LivingEntity entity, Faction faction) {
		return factionOf(entity) == faction;
	}

	// ------------------------------------------------------------------ IA

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, false));
		this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

		// 1 et 2 : Pirates puis Révolutionnaires, dès qu'ils entrent dans le champ de détection (et sont visibles).
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				(target, level) -> isFaction(target, Faction.PIRATE)));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
				(target, level) -> isFaction(target, Faction.REVOLUTIONARY)));
		// 3 : toutes les créatures hostiles (zombies, squelettes, creepers, araignées...).
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
				(target, level) -> target instanceof Enemy));
		// 4 : riposte contre un joueur neutre qui l'a frappé.
		this.targetSelector.addGoal(4, new RetaliateGoal(this));
	}

	/** Riposte : cible le joueur neutre qui a frappé le Marine, et le poursuit tant que la colère dure. */
	private static final class RetaliateGoal extends TargetGoal {
		private final MarineEntity marine;
		private LivingEntity victim;

		RetaliateGoal(MarineEntity marine) {
			super(marine, false);
			this.marine = marine;
		}

		@Override
		public boolean canUse() {
			this.victim = marine.angerTarget();
			return this.victim != null;
		}

		@Override
		public void start() {
			this.mob.setTarget(this.victim);
			super.start();
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity current = marine.angerTarget();
			return current != null && current == this.mob.getTarget();
		}
	}

	/** Le joueur neutre à poursuivre, ou null (colère terminée, cible morte / trop loin / devenue alliée). */
	private LivingEntity angerTarget() {
		LivingEntity target = this.angryAt;
		if (target == null) {
			return null;
		}
		boolean expired = this.level().getGameTime() >= this.angryUntil;
		boolean invalid = !target.isAlive() || target.isRemoved() || target.level() != this.level()
				|| this.distanceToSqr(target) > ANGER_MAX_DISTANCE * ANGER_MAX_DISTANCE
				|| isFaction(target, Faction.MARINE)
				|| (target instanceof Player p && (p.isCreative() || p.isSpectator()));
		if (expired || invalid) {
			this.angryAt = null;
			return null;
		}
		return target;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		// Seuls les joueurs non-Marine déclenchent la riposte (Pirates / Révolutionnaires sont déjà des cibles prioritaires).
		if (hurt && source.getEntity() instanceof Player attacker && !isFaction(attacker, Faction.MARINE)
				&& !attacker.isCreative() && !attacker.isSpectator()) {
			this.angryAt = attacker;
			this.angryUntil = level.getGameTime() + ANGER_TICKS;
		}
		return hurt;
	}

	// ------------------------------------------------------------------ apparition

	/**
	 * Apparition naturelle : en plein jour (luminosité > 8), sur un sol où les mobs peuvent apparaître. Les spawners et
	 * les oeufs ne sont pas limités par la lumière.
	 */
	public static boolean checkSpawnRules(EntityType<MarineEntity> type, ServerLevelAccessor level, EntitySpawnReason reason,
			BlockPos pos, RandomSource random) {
		if (reason == EntitySpawnReason.SPAWNER || reason == EntitySpawnReason.TRIAL_SPAWNER) {
			return Mob.checkMobSpawnRules(type, level, reason, pos, random);
		}
		return level.getRawBrightness(pos, 0) > 8 && Mob.checkMobSpawnRules(type, level, reason, pos, random);
	}

	// ------------------------------------------------------------------ sons

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.PLAYER_DEATH;
	}
}
