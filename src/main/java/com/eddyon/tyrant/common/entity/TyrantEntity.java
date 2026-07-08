package com.eddyon.tyrant.common.entity;

import com.eddyon.tyrant.common.config.TyrantConfig;
import com.eddyon.tyrant.common.entity.tyrant.TyrantCombatContext;
import com.eddyon.tyrant.common.entity.tyrant.TyrantCombatDirector;
import com.eddyon.tyrant.common.entity.tyrant.TyrantCommand;
import com.eddyon.tyrant.common.entity.tyrant.TyrantCommandController;
import com.eddyon.tyrant.common.network.payload.TyrantExecutionPayload;
import com.eddyon.tyrant.common.network.payload.TyrantScreenShakePayload;
import com.eddyon.tyrant.common.registry.ModEffects;
import com.eddyon.tyrant.common.registry.ModParticles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TyrantEntity extends Monster implements GeoEntity {
   private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(TyrantEntity.class, EntityDataSerializers.INT);
   private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
   private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
   private static final float STEP_BREAK_SPEED = 0.2F;
   private static final float IMPACT_BREAK_SPEED = 4.8F;
   private static final EquipmentSlot[] DISARM_ARMOR_SLOTS = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
   private static final double KING_OPPRESSION_RADIUS = 16.0D;
   private static final double PLAYER_PRIORITY_TARGET_RADIUS = 22.0D;
   private static final double TIMIDITY_COMBAT_RADIUS = 18.0D;
   private static final int STARE_PULSE_TICKS = 30;
   private static final int FEAR_DOMAIN_BOSSBAR_TICKS = 12;
   private static final int EXECUTION_QUOTE_COUNT = 6;
   private static final int LEAP_SLAM_FAMILY_COOLDOWN_TICKS = 200;
   private final TyrantPart hipsPart;
   private final TyrantPart chestPart;
   private final TyrantPart headPart;
   private final TyrantPart leftUpperArmPart;
   private final TyrantPart rightUpperArmPart;
   private final TyrantPart leftForearmPart;
   private final TyrantPart rightForearmPart;
   private final TyrantPart[] tyrantParts;
   private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
   private final Map<UUID, Integer> oppressionFocusTicks = new HashMap<>();
   private final TyrantCommandController commandController = new TyrantCommandController();
   private int actionTick;
   private int actionStep;
   private int actionImpactTick = -1;
   private Vec3 lastHeavyPunchCenter = Vec3.ZERO;
   private Vec3 leapSlamFacing = Vec3.ZERO;
   private Vec3 leapSlamTravelDirection = Vec3.ZERO;
   private Vec3 leapSlamImpactCenter = Vec3.ZERO;
   private int attackCooldown;
   private int leapSlamCooldown;
   private int combatDisplayTicks;
   private int fearDomainDisplayTicks;
   private int lastStepSoundTick;
   private int repeatedActionCount;
   private TyrantAction lastAction = TyrantAction.NONE;
   private Vec3 deathAnchorPos;
   private Float deathLockedYaw;
   private Float deathLockedPitch;
   private boolean introPlayed;
   private boolean phaseShiftPlayed;
   private boolean phaseTwoActive;
   private boolean configuredAttributesApplied;
   private final ServerBossEvent bossEvent = new ServerBossEvent(Component.empty(), BossBarColor.BLUE, BossBarOverlay.NOTCHED_10);

   public TyrantEntity(EntityType<? extends Monster> entityType, Level level) {
      super(entityType, level);
      this.hipsPart = new TyrantPart(this, 2.7F, 3.15F);
      this.chestPart = new TyrantPart(this, 3.75F, 2.45F);
      this.headPart = new TyrantPart(this, 2.35F, 1.65F);
      this.leftUpperArmPart = new TyrantPart(this, 1.65F, 2.9F);
      this.rightUpperArmPart = new TyrantPart(this, 1.65F, 2.9F);
      this.leftForearmPart = new TyrantPart(this, 2.05F, 3.55F);
      this.rightForearmPart = new TyrantPart(this, 2.05F, 3.55F);
      this.tyrantParts = new TyrantPart[]{
         this.hipsPart,
         this.chestPart,
         this.headPart,
         this.leftUpperArmPart,
         this.rightUpperArmPart,
         this.leftForearmPart,
         this.rightForearmPart
      };
      this.setId(ENTITY_COUNTER.getAndAdd(this.tyrantParts.length + 1) + 1);
      this.xpReward = 25;
      this.bossEvent.setDarkenScreen(true);
      this.bossEvent.setCreateWorldFog(true);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return TyrantCombatDirector.createAttributes();
   }

   private void applyConfiguredAttributes() {
      if (this.configuredAttributesApplied) {
         return;
      }

      this.configuredAttributesApplied = true;
      AttributeInstance maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
      if (maxHealth != null) {
         double configuredMaxHealth = TyrantConfig.bossMaxHealth();
         if (Math.abs(maxHealth.getBaseValue() - configuredMaxHealth) > 1.0E-4D) {
            float oldMaxHealth = this.getMaxHealth();
            float healthRatio = oldMaxHealth > 0.0F ? this.getHealth() / oldMaxHealth : 1.0F;
            maxHealth.setBaseValue(configuredMaxHealth);
            this.setHealth((float)Mth.clamp((double)healthRatio * configuredMaxHealth, 1.0D, configuredMaxHealth));
         }
      }

      AttributeInstance attackDamage = this.getAttribute(Attributes.ATTACK_DAMAGE);
      if (attackDamage != null) {
         attackDamage.setBaseValue(TyrantConfig.bossAttackDamage());
      }

      AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
      if (armor != null) {
         armor.setBaseValue(TyrantConfig.bossArmor());
      }
   }

   private float scaleCommandPenaltyDamage(float damage) {
      return TyrantConfig.scaleCommandPenaltyDamage(damage);
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9D));
      this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
      this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 4, true, false, living -> living instanceof Player player && this.canPriorityTargetPlayer(player)));
      this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 6, true, false, living -> this.canTargetEntity(living)));
   }

   protected void defineSynchedData(SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(ACTION, TyrantAction.NONE.id());
   }

   public void tick() {
      super.tick();
      this.updateHitboxParts();
      if (!this.level().isClientSide()) {
         this.applyConfiguredAttributes();
         if (!this.isDeadOrDying()) {
            this.deathAnchorPos = null;
            this.deathLockedYaw = null;
            this.deathLockedPitch = null;
            if (this.isNoGravity()) {
               this.setNoGravity(false);
            }
         }

         if (this.combatDisplayTicks > 0) {
            --this.combatDisplayTicks;
         }

         if (this.fearDomainDisplayTicks > 0) {
            --this.fearDomainDisplayTicks;
         }

         LivingEntity trackedTarget = this.getTarget();
         if (trackedTarget != null && trackedTarget.isAlive()) {
            this.combatDisplayTicks = Math.max(this.combatDisplayTicks, this.phaseTwoActive ? 150 : 110);
         }

         if (this.getCurrentAction().isActive() && this.getCurrentAction() != TyrantAction.DEATH) {
            this.combatDisplayTicks = Math.max(this.combatDisplayTicks, 70);
         }

         if (this.attackCooldown > 0) {
            --this.attackCooldown;
         }

         if (this.leapSlamCooldown > 0) {
            --this.leapSlamCooldown;
         }

         if (this.tickCount % 4 == 0) {
            this.prioritizeNearbyPlayerTarget();
         }

         this.commandController.tick(this);

         if (this.tickCount % 5 == 0) {
            this.applyKingOppressionAura();
         }

         if (this.phaseTwoActive && !this.isDeadOrDying()) {
            this.tickPhaseTwoDomain();
         }

         this.updateBossEvent();
         if (!this.isDeadOrDying()) {
            if (this.commandController.shouldHoldCombatForPardon() && !this.isUninterruptibleCeremonyAction(this.getCurrentAction())) {
               if (this.getCurrentAction().isActive()) {
                  this.finishAction();
               }

               this.tickPardonMenace();
            } else if (this.getCurrentAction().isActive()) {
               this.tickCurrentAction();
            } else {
               this.tickCombatBrain();
            }

         }
      }
   }

   public void setId(int id) {
      super.setId(id);
      if (this.tyrantParts != null) {
         for(int i = 0; i < this.tyrantParts.length; ++i) {
            this.tyrantParts[i].setId(id + i + 1);
         }
      }
   }

   public boolean isMultipartEntity() {
      return true;
   }

   public PartEntity<?>[] getParts() {
      return this.tyrantParts;
   }

   private void updateHitboxParts() {
      Vec3[] previousPositions = new Vec3[this.tyrantParts.length];

      for(int i = 0; i < this.tyrantParts.length; ++i) {
         previousPositions[i] = this.tyrantParts[i].position();
      }

      Vec3 facing = this.getFacingVector();
      Vec3 side = this.getSideVector(facing);
      this.moveHitboxPart(this.hipsPart, facing, side, 0.0D, 0.1D, -0.05D);
      this.moveHitboxPart(this.chestPart, facing, side, 0.0D, 2.55D, 0.12D);
      this.moveHitboxPart(this.headPart, facing, side, 0.0D, 4.72D, 0.18D);
      this.moveHitboxPart(this.leftUpperArmPart, facing, side, 2.35D, 1.55D, 0.18D);
      this.moveHitboxPart(this.rightUpperArmPart, facing, side, -2.35D, 1.55D, 0.18D);
      this.moveHitboxPart(this.leftForearmPart, facing, side, 3.05D, -0.25D, 0.55D);
      this.moveHitboxPart(this.rightForearmPart, facing, side, -3.05D, -0.25D, 0.55D);

      boolean hadPreviousPartPositions = this.tickCount > 1;
      for(int i = 0; i < this.tyrantParts.length; ++i) {
         TyrantPart part = this.tyrantParts[i];
         Vec3 previous = hadPreviousPartPositions ? previousPositions[i] : part.position();
         part.xo = previous.x;
         part.yo = previous.y;
         part.zo = previous.z;
         part.xOld = previous.x;
         part.yOld = previous.y;
         part.zOld = previous.z;
      }
   }

   private void moveHitboxPart(TyrantPart part, Vec3 facing, Vec3 side, double sideOffset, double yOffset, double forwardOffset) {
      Vec3 position = this.position().add(side.scale(sideOffset)).add(facing.scale(forwardOffset));
      part.setPos(position.x, this.getY() + yOffset, position.z);
   }

   private void tickCombatBrain() {
      LivingEntity target = this.getTarget();
      if (target != null && target.isAlive()) {
         this.getLookControl().setLookAt(target, 35.0F, 25.0F);
         Vec3 delta = target.position().subtract(this.position());
         double horizontalDistanceSqr = delta.x * delta.x + delta.z * delta.z;
         double verticalGap = Math.abs(delta.y);
         boolean hasSight = this.hasLineOfSight(target);
         TyrantCombatContext combatContext = this.buildCombatContext(target, horizontalDistanceSqr, verticalGap, hasSight);
         if (!this.introPlayed && this.onGround()) {
            this.startAction(TyrantAction.INTRO_ROAR);
         } else if (!this.phaseShiftPlayed && TyrantCombatDirector.shouldEnterPhaseTwo(this.getHealth(), this.getMaxHealth()) && this.onGround()) {
            this.startAction(TyrantAction.PHASE_SHIFT);
         } else {
            if (TyrantCombatDirector.shouldHoldPosition(combatContext)) {
               this.getNavigation().stop();
            } else {
               this.getNavigation().moveTo(target, TyrantCombatDirector.getChaseSpeed(combatContext));
            }

            if (this.onGround() && this.tickCount % 12 == 0 && TyrantCombatDirector.shouldJumpToClose(combatContext)) {
               this.getJumpControl().jump();
            }

            if (this.attackCooldown <= 0 && this.onGround()) {
               TyrantAction nextAction = TyrantCombatDirector.chooseAction(this.random, combatContext, this.leapSlamCooldown <= 0);
               if (nextAction != TyrantAction.NONE) {
                  this.startAction(nextAction);
               }

            }
         }
      }
   }

   private void tickPardonMenace() {
      this.getNavigation().stop();
      LivingEntity target = this.getTarget();
      if (target != null && target.isAlive()) {
         this.getLookControl().setLookAt(target, 35.0F, 25.0F);
      }

      if (this.tickCount % 28 == 0) {
         Vec3 origin = this.position().add(0.0D, 1.2D, 0.0D);
         this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.72F, this.phaseTwoActive ? 0.5F : 0.6F);
         this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.72F, 0.42F);
         this.triggerScreenShake(origin, 18.0F, this.phaseTwoActive ? 0.58F : 0.42F, 8);
         if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, origin.x, origin.y + 0.2D, origin.z, 7, 0.48D, 0.2D, 0.48D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.1D, this.getZ(), 8, 0.7D, 0.06D, 0.7D, 0.006D);
            this.spawnGroundPressureRing(this.position(), this.phaseTwoActive ? 2.8F : 2.2F, this.phaseTwoActive);
         }
      }
   }

   private void applyKingOppressionAura() {
      double auraSqr = KING_OPPRESSION_RADIUS * KING_OPPRESSION_RADIUS;
      double timidityCombatSqr = TIMIDITY_COMBAT_RADIUS * TIMIDITY_COMBAT_RADIUS;
      Set<UUID> seenPlayers = new HashSet<>();
      AABB auraBox = this.getBoundingBox().inflate(TIMIDITY_COMBAT_RADIUS, 6.0D, TIMIDITY_COMBAT_RADIUS);
      boolean inCombat = this.combatDisplayTicks > 0 || this.getTarget() != null || this.getCurrentAction().isActive();
      boolean playerInFearDomain = false;

      for(Player player : this.level().getEntitiesOfClass(Player.class, auraBox, this::canAffectPlayer)) {
         UUID uuid = player.getUUID();
         seenPlayers.add(uuid);
         double distanceSqr = this.distanceToSqr(player);
         boolean closeEnough = distanceSqr <= timidityCombatSqr;
         boolean withinAura = distanceSqr <= auraSqr;
         boolean hasSight = this.hasLineOfSight(player);
         int stareTicks = this.oppressionFocusTicks.getOrDefault(uuid, 0);
         boolean softenedByCommand = this.commandController.reducesFearFor(player);
         if (hasSight && this.isPlayerFacingTyrant(player)) {
            stareTicks = Math.min(STARE_PULSE_TICKS + 20, stareTicks + 1);
         } else {
            stareTicks = Math.max(0, stareTicks - 2);
         }

         if (withinAura && (hasSight || closeEnough)) {
            player.addEffect(new MobEffectInstance(ModEffects.KING_OPPRESSION, 50, 0, true, false, true));
            playerInFearDomain = true;
         }

         if (inCombat && closeEnough && !softenedByCommand) {
            player.addEffect(new MobEffectInstance(ModEffects.TIMIDITY, 100, 0, true, false, true));
            if (stareTicks == STARE_PULSE_TICKS) {
               this.triggerOppressionPulse(player);
            }
         }

         if (this.level() instanceof ServerLevel serverLevel) {
            this.spawnOppressionParticles(serverLevel, player, closeEnough, stareTicks >= STARE_PULSE_TICKS);
         }

         if (stareTicks > 0 || withinAura) {
            this.oppressionFocusTicks.put(uuid, stareTicks);
         } else {
            this.oppressionFocusTicks.remove(uuid);
         }
      }

      this.oppressionFocusTicks.keySet().removeIf(uuid -> !seenPlayers.contains(uuid));
      if (playerInFearDomain) {
         this.fearDomainDisplayTicks = Math.max(this.fearDomainDisplayTicks, FEAR_DOMAIN_BOSSBAR_TICKS);
      }
   }

   private boolean canAffectPlayer(Player player) {
      return player.isAlive() && !player.isSpectator() && !player.isCreative();
   }

   private boolean canPriorityTargetPlayer(Player player) {
      return this.canAffectPlayer(player) && this.canTargetEntity(player);
   }

   private void prioritizeNearbyPlayerTarget() {
      if (this.isDeadOrDying() || this.getCurrentAction() == TyrantAction.DEATH) {
         return;
      }

      AABB searchBox = this.getBoundingBox().inflate(PLAYER_PRIORITY_TARGET_RADIUS, 8.0D, PLAYER_PRIORITY_TARGET_RADIUS);
      Player nearest = null;
      double nearestDistanceSqr = PLAYER_PRIORITY_TARGET_RADIUS * PLAYER_PRIORITY_TARGET_RADIUS;
      for(Player player : this.level().getEntitiesOfClass(Player.class, searchBox, this::canPriorityTargetPlayer)) {
         double distanceSqr = this.distanceToSqr(player);
         if (distanceSqr < nearestDistanceSqr) {
            nearest = player;
            nearestDistanceSqr = distanceSqr;
         }
      }

      if (nearest == null) {
         return;
      }

      LivingEntity currentTarget = this.getTarget();
      boolean shouldSwitch = currentTarget == null || !currentTarget.isAlive() || !(currentTarget instanceof Player);
      if (currentTarget instanceof Player currentPlayer) {
         double currentDistanceSqr = this.distanceToSqr(currentPlayer);
         shouldSwitch = !this.canPriorityTargetPlayer(currentPlayer)
               || currentDistanceSqr > PLAYER_PRIORITY_TARGET_RADIUS * PLAYER_PRIORITY_TARGET_RADIUS
               || nearestDistanceSqr + 16.0D < currentDistanceSqr;
      }

      if (shouldSwitch && currentTarget != nearest) {
         this.setTarget(nearest);
         this.combatDisplayTicks = Math.max(this.combatDisplayTicks, this.phaseTwoActive ? 150 : 110);
      }
   }

   private boolean isPlayerFacingTyrant(Player player) {
      return this.isPlayerLookingAtTyrant(player, 0.82D);
   }

   public boolean isPlayerLookingAtTyrant(Player player, double dotThreshold) {
      Vec3 toTyrant = this.getEyePosition().subtract(player.getEyePosition());
      double length = toTyrant.length();
      if (length < 1.0E-4D) {
         return true;
      } else {
         Vec3 look = player.getViewVector(1.0F).normalize();
         return look.dot(toTyrant.scale(1.0D / length)) > dotThreshold;
      }
   }

   public boolean canStartTyrantCommand() {
      LivingEntity target = this.getTarget();
      return this.getCurrentAction() == TyrantAction.NONE && target != null && target.isAlive() && !this.isDeadOrDying();
   }

   public boolean canTyrantCommandAffect(Player player) {
      return this.canAffectPlayer(player);
   }

   public boolean isTyrantPhaseTwoActive() {
      return this.phaseTwoActive;
   }

   public void onTyrantCommandStarted(TyrantCommand command, List<Player> players) {
      this.combatDisplayTicks = Math.max(this.combatDisplayTicks, command == TyrantCommand.KNEEL ? 120 : 90);
      Vec3 origin = this.position().add(0.0D, 1.1D, 0.0D);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 1.0F, 0.48F);
      this.level().playSound((Player)null, origin.x, origin.y + 0.4D, origin.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.72F, 0.58F);
      this.triggerScreenShake(origin, 18.0F, 0.38F, 8);
      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, origin.x, origin.y + 0.3D, origin.z, 12, 0.45D, 0.28D, 0.45D, 0.018D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y - 0.15D, origin.z, 8, 0.62D, 0.08D, 0.62D, 0.012D);
         this.spawnGroundPressureRing(this.position(), command == TyrantCommand.KNEEL ? 3.6F : 2.8F, this.phaseTwoActive);
      }
   }

   public void punishKneelViolation(Player player, int executionMarks) {
      Vec3 origin = player.position();
      Vec3 motion = player.getDeltaMovement();
      player.setSprinting(false);
      player.setDeltaMovement(motion.x * 0.18D, Math.min(motion.y, -0.58D), motion.z * 0.18D);
      player.hurt(this.damageSources().mobAttack(this), this.scaleCommandPenaltyDamage(this.phaseTwoActive ? 4.8F : 3.6F));
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1, true, true, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 65, executionMarks > 1 ? 1 : 0, true, true, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.0F, 0.54F);
      this.level().playSound((Player)null, origin.x, origin.y + 0.2D, origin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.82F, 0.42F);
      this.triggerScreenShake(origin.add(0.0D, 0.7D, 0.0D), 12.0F, 0.72F, 9);
      if (this.level() instanceof ServerLevel serverLevel) {
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, origin).getY() + 1.04D;
         serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, TyrantTerrainHelper.findImpactState(this, origin)), origin.x, y, origin.z, 12, 0.32D, 0.08D, 0.32D, 0.08D);
         serverLevel.sendParticles(ParticleTypes.SMOKE, origin.x, y + 0.12D, origin.z, 7, 0.28D, 0.08D, 0.28D, 0.02D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, origin.x, y + 0.35D, origin.z, 5, 0.2D, 0.16D, 0.2D, 0.012D);
      }
   }

   public void punishAudienceViolation(Player player, int severity) {
      Vec3 origin = player.position().add(0.0D, 0.7D, 0.0D);
      Vec3 pull = this.position().subtract(player.position());
      pull = new Vec3(pull.x, 0.0D, pull.z);
      if (pull.lengthSqr() < 1.0E-4D) {
         pull = this.getFacingVector().scale(-1.0D);
      } else {
         pull = pull.normalize();
      }

      double strength = severity > 1 ? 1.38D : 1.02D;
      player.push(pull.x * strength, 0.12D + severity * 0.05D, pull.z * strength);
      player.hurt(this.damageSources().mobAttack(this), this.scaleCommandPenaltyDamage(severity > 1 ? 4.2F : 2.4F));
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 48, 0, true, true, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.68F, 1.18F);
      this.triggerScreenShake(origin, 14.0F, severity > 1 ? 0.58F : 0.38F, severity > 1 ? 8 : 6);
      if (this.level() instanceof ServerLevel serverLevel) {
         Vec3 start = origin;
         Vec3 end = this.position().add(0.0D, 1.4D, 0.0D);

         for(int i = 0; i < 7; ++i) {
            double t = (double)i / 6.0D;
            Vec3 point = start.lerp(end, t);
            serverLevel.sendParticles(i % 2 == 0 ? ParticleTypes.SCULK_SOUL : ModParticles.FEAR_STATIC.get(), point.x, point.y, point.z, 1, 0.06D, 0.06D, 0.06D, 0.004D);
         }
      }
   }

   public void punishRetreatViolation(Player player) {
      Vec3 origin = player.position();
      Vec3 away = player.position().subtract(this.position());
      away = new Vec3(away.x, 0.0D, away.z);
      if (away.lengthSqr() < 1.0E-4D) {
         away = this.getFacingVector();
      } else {
         away = away.normalize();
      }

      player.push(away.x * (this.phaseTwoActive ? 1.65D : 1.35D), 0.22D, away.z * (this.phaseTwoActive ? 1.65D : 1.35D));
      player.hurt(this.damageSources().mobAttack(this), this.scaleCommandPenaltyDamage(this.phaseTwoActive ? 4.6F : 3.4F));
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 78, 0, true, true, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 36, 0, true, false, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.08F, 0.62F);
      this.triggerScreenShake(origin.add(0.0D, 0.8D, 0.0D), 15.0F, 0.74F, 9);
      if (this.level() instanceof ServerLevel serverLevel) {
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, origin).getY() + 1.04D;
         serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, TyrantTerrainHelper.findImpactState(this, origin)), origin.x, y, origin.z, 10, 0.34D, 0.08D, 0.34D, 0.07D);
         serverLevel.sendParticles(ParticleTypes.CLOUD, origin.x, y + 0.08D, origin.z, 6, 0.32D, 0.06D, 0.32D, 0.025D);
         this.spawnGroundPressureRing(origin, 2.45F, this.phaseTwoActive);
      }
   }

   public void punishApproachViolation(Player player) {
      Vec3 origin = player.position();
      player.hurt(this.damageSources().mobAttack(this), this.scaleCommandPenaltyDamage(this.phaseTwoActive ? 6.2F : 4.8F));
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, true, true, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 0, true, false, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.78F, 0.82F);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.54F, 0.9F);
      this.triggerScreenShake(origin.add(0.0D, 0.6D, 0.0D), 16.0F, 0.82F, 10);
      if (this.level() instanceof ServerLevel serverLevel) {
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, origin).getY() + 1.05D;

         for(int i = 0; i < 14; ++i) {
            double angle = (double)i / 14.0D * 6.283185307179586D;
            double radius = 0.45D + (double)(i % 3) * 0.28D;
            double x = origin.x + Math.cos(angle) * radius;
            double z = origin.z + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.03D, z, 1, 0.02D, 0.02D, 0.02D, 0.002D);
            if (i % 3 == 0) {
               Vec3 point = new Vec3(x, origin.y, z);
               serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, TyrantTerrainHelper.findImpactState(this, point)), x, y, z, 1, 0.06D, 0.08D, 0.06D, 0.05D);
            }
         }

         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, origin.x, y + 0.28D, origin.z, 7, 0.25D, 0.12D, 0.25D, 0.012D);
      }
   }

   public void rewardTyrantCommandObedience(Player player, TyrantCommand command) {
      int duration = command == TyrantCommand.KNEEL ? 80 : 50;
      player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0, true, false, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, Math.max(45, duration - 10), 0, true, false, true), this);
      if (player instanceof ServerPlayer serverPlayer) {
         serverPlayer.displayClientMessage(Component.translatable("command.tyrant.obeyed"), true);
      }
   }

   public void updateRoyalDecreeEffect(Player player, int marks, int threshold) {
      if (marks <= 0) {
         player.removeEffect(ModEffects.ROYAL_DECREE);
         return;
      }

      int amplifier = Mth.clamp(marks, 1, threshold) - 1;
      player.addEffect(new MobEffectInstance(ModEffects.ROYAL_DECREE, 7200, amplifier, true, true, true), this);
   }

   public void rewardPardon(Player player, boolean clearedMark) {
      Vec3 origin = player.position().add(0.0D, 0.85D, 0.0D);
      player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, clearedMark ? 90 : 55, 0, true, false, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, clearedMark ? 70 : 40, 0, true, false, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.55F, 0.62F);
      this.level().playSound((Player)null, this.getX(), this.getY() + 1.1D, this.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.7F, 0.48F);
      if (player instanceof ServerPlayer serverPlayer) {
         serverPlayer.displayClientMessage(Component.translatable(clearedMark ? "command.tyrant.pardon_cleared" : "command.tyrant.pardon_granted"), true);
      }

      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, origin.x, origin.y, origin.z, 10, 0.26D, 0.18D, 0.26D, 0.01D);
         serverLevel.sendParticles(ParticleTypes.SOUL, origin.x, origin.y + 0.12D, origin.z, 8, 0.22D, 0.14D, 0.22D, 0.006D);
      }
   }

   public void announceExecutionMark(Player player, int marks, int threshold) {
      if (player instanceof ServerPlayer serverPlayer) {
         serverPlayer.displayClientMessage(Component.translatable("command.tyrant.execution_mark", marks, threshold), true);
      }
   }

   public void punishExecutionMarked(Player player) {
      Vec3 origin = player.position();
      this.startExecutionCeremony(player);
      player.invulnerableTime = 0;
      player.setAbsorptionAmount(0.0F);
      if (player.getHealth() > 1.0F) {
         player.setHealth(1.0F);
      }

      player.getFoodData().setFoodLevel(0);
      player.getFoodData().setSaturation(0.0F);
      player.getFoodData().setExhaustion(0.0F);
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 90, 2, true, true, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 90, 1, true, true, true), this);
      this.level().playSound((Player)null, origin.x, origin.y + 0.4D, origin.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.72F, 0.64F);
      this.triggerScreenShake(origin.add(0.0D, 0.8D, 0.0D), 18.0F, 1.05F, 12);
      if (this.level() instanceof ServerLevel serverLevel) {
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, origin).getY() + 1.06D;
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, y, origin.z, 18, 0.42D, 0.08D, 0.42D, 0.02D);
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), origin.x, y + 0.45D, origin.z, 10, 0.34D, 0.18D, 0.34D, 0.018D);
         serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, TyrantTerrainHelper.findImpactState(this, origin)), origin.x, y, origin.z, 14, 0.36D, 0.1D, 0.36D, 0.08D);
      }
   }

   private void startExecutionCeremony(Player player) {
      this.setTarget(player);
      if (player instanceof ServerPlayer serverPlayer) {
         int quoteIndex = this.random.nextInt(EXECUTION_QUOTE_COUNT);
         PacketDistributor.sendToPlayer(serverPlayer, new TyrantExecutionPayload(quoteIndex, 74));
      }

      TyrantAction action = this.getCurrentAction();
      if (action == TyrantAction.DEATH || action == TyrantAction.INTRO_ROAR || action == TyrantAction.PHASE_SHIFT || action == TyrantAction.COMMAND_EXECUTION) {
         return;
      }

      if (action.isActive()) {
         this.finishAction();
      }

      this.startAction(TyrantAction.COMMAND_EXECUTION);
   }

   public void punishDisarmLock(Player player) {
      Vec3 origin = player.position().add(0.0D, 0.8D, 0.0D);
      player.stopUsingItem();
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 55, 0, true, false, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 0.78F, 0.52F);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.55F, 0.42F);
      this.triggerScreenShake(origin, 10.0F, 0.28F, 5);
      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z, 7, 0.28D, 0.12D, 0.28D, 0.01D);
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), origin.x, origin.y + 0.1D, origin.z, 4, 0.2D, 0.08D, 0.2D, 0.014D);
      }
   }

   public void punishDisarmItemDrop(Player player, boolean includeOffhand) {
      Vec3 origin = player.position().add(0.0D, 0.65D, 0.0D);
      boolean dropped = this.dropHotbarItem(player, player.getInventory().selected, origin, 0.42D);
      if (!dropped && includeOffhand) {
         dropped = this.dropOffhandItem(player, origin, 0.42D);
      }

      if (!dropped) {
         dropped = this.dropArmorItem(player, origin, 0.42D);
      }

      player.stopUsingItem();
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 70, 0, true, true, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 0.9F, 0.64F);
      this.triggerScreenShake(origin, 12.0F, dropped ? 0.44F : 0.28F, 7);
      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 8, 0.34D, 0.12D, 0.34D, 0.02D);
         serverLevel.sendParticles(ParticleTypes.SOUL, origin.x, origin.y + 0.22D, origin.z, 6, 0.24D, 0.14D, 0.24D, 0.01D);
      }
   }

   public void punishDisarmSevere(Player player, int lockedHotbarMask, int maxDrops) {
      Vec3 origin = player.position().add(0.0D, 0.65D, 0.0D);
      int drops = 0;

      for(int slot = 0; slot < 9 && drops < maxDrops; ++slot) {
         if ((lockedHotbarMask & 1 << slot) != 0 && this.dropHotbarItem(player, slot, origin, 0.56D)) {
            ++drops;
         }
      }

      if (drops == 0) {
         if (this.dropHotbarItem(player, player.getInventory().selected, origin, 0.56D)) {
            ++drops;
         }
      }

      if (drops < maxDrops && this.dropOffhandItem(player, origin, 0.56D)) {
         ++drops;
      }

      while(drops < maxDrops && this.dropArmorItem(player, origin, 0.56D)) {
         ++drops;
      }

      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 65, 1, true, true, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1, true, true, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 0.96F, 0.7F);
      this.triggerScreenShake(origin, 14.0F, 0.66F, 9);
      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z, 12, 0.42D, 0.12D, 0.42D, 0.02D);
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), origin.x, origin.y + 0.24D, origin.z, 7, 0.32D, 0.14D, 0.32D, 0.016D);
      }
   }

   public void punishBowViolation(Player player) {
      Vec3 origin = player.position().add(0.0D, 1.1D, 0.0D);
      this.setTarget(player);
      this.getNavigation().moveTo(player, this.phaseTwoActive ? 1.34D : 1.12D);
      player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, true, false, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 42, 0, true, false, true), this);
      player.addEffect(new MobEffectInstance(ModEffects.TIMIDITY, 130, 0, true, false, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 0.86F, 0.62F);
      this.triggerScreenShake(origin, 13.0F, 0.4F, 7);
      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, origin.x, origin.y, origin.z, 10, 0.34D, 0.18D, 0.34D, 0.012D);
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), origin.x, origin.y + 0.15D, origin.z, 5, 0.22D, 0.1D, 0.22D, 0.014D);
      }
   }

   public void punishPardonViolation(Player player, int executionMarks) {
      Vec3 origin = player.position().add(0.0D, 0.8D, 0.0D);
      this.setTarget(player);
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 75, 1, true, true, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 90, 0, true, true, true), this);
      player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, true, false, true), this);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.62F, 0.82F);
      this.level().playSound((Player)null, this.getX(), this.getY() + 1.1D, this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.85F, executionMarks > 1 ? 0.48F : 0.56F);
      this.triggerScreenShake(origin, 16.0F, executionMarks > 1 ? 0.82F : 0.62F, 10);
      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y + 0.15D, origin.z, 12, 0.38D, 0.18D, 0.38D, 0.02D);
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), origin.x, origin.y + 0.24D, origin.z, 6, 0.24D, 0.12D, 0.24D, 0.016D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, this.getX(), this.getY() + 1.2D, this.getZ(), 8, 0.4D, 0.18D, 0.4D, 0.01D);
      }
   }

   private boolean dropHotbarItem(Player player, int slot, Vec3 origin, double force) {
      if (slot < 0 || slot >= 9) {
         return false;
      }

      ItemStack stack = player.getInventory().getItem(slot);
      if (stack.isEmpty()) {
         return false;
      }

      ItemStack dropped = stack.copy();
      player.getInventory().setItem(slot, ItemStack.EMPTY);
      player.getInventory().setChanged();
      if (player instanceof ServerPlayer serverPlayer) {
         serverPlayer.containerMenu.broadcastChanges();
      }

      this.scatterDroppedItem(player, dropped, origin, force);
      return true;
   }

   private boolean dropArmorItem(Player player, Vec3 origin, double force) {
      for(EquipmentSlot slot : DISARM_ARMOR_SLOTS) {
         ItemStack stack = player.getItemBySlot(slot);
         if (stack.isEmpty()) {
            continue;
         }

         ItemStack dropped = stack.copy();
         player.setItemSlot(slot, ItemStack.EMPTY);
         if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
         }

         this.scatterDroppedItem(player, dropped, origin, force);
         return true;
      }

      return false;
   }

   private boolean dropOffhandItem(Player player, Vec3 origin, double force) {
      ItemStack stack = player.getOffhandItem();
      if (stack.isEmpty()) {
         return false;
      }

      ItemStack dropped = stack.copy();
      player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
      this.scatterDroppedItem(player, dropped, origin, force);
      return true;
   }

   private void scatterDroppedItem(Player player, ItemStack dropped, Vec3 origin, double force) {
      ItemEntity itemEntity = player.drop(dropped, false, true);
      if (itemEntity == null) {
         return;
      }

      double angle = this.random.nextDouble() * 6.283185307179586D;
      double speed = force + this.random.nextDouble() * 0.16D;
      double x = Math.cos(angle);
      double z = Math.sin(angle);
      itemEntity.setPos(origin.x + x * 0.35D, origin.y + this.random.nextDouble() * 0.35D, origin.z + z * 0.35D);
      itemEntity.setDeltaMovement(x * speed, 0.22D + this.random.nextDouble() * 0.18D, z * speed);
      itemEntity.setPickUpDelay(45);
   }

   private void triggerOppressionPulse(Player player) {
      Vec3 pulseOrigin = player.position().add(0.0D, 1.0D, 0.0D);
      this.level().playSound((Player)null, pulseOrigin.x, pulseOrigin.y, pulseOrigin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.9F, 0.54F);
      this.level().playSound((Player)null, pulseOrigin.x, pulseOrigin.y, pulseOrigin.z, SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 0.45F, 0.86F);
      this.triggerScreenShake(pulseOrigin, 6.5F, 0.16F, 6);
      if (this.level() instanceof ServerLevel serverLevel) {
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), pulseOrigin.x, pulseOrigin.y + 0.25D, pulseOrigin.z, 6, 0.34D, 0.24D, 0.34D, 0.018D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, pulseOrigin.x, pulseOrigin.y + 0.1D, pulseOrigin.z, 5, 0.28D, 0.22D, 0.28D, 0.008D);
         serverLevel.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + this.getBbHeight() * 0.55D, this.getZ(), 5, this.getBbWidth() * 0.32D, this.getBbHeight() * 0.18D, this.getBbWidth() * 0.32D, 0.012D);
      }
   }

   private void spawnOppressionParticles(ServerLevel serverLevel, Player player, boolean closeEnough, boolean stareLocked) {
      if (this.random.nextFloat() < (closeEnough ? 0.42F : 0.22F)) {
         double angle = this.random.nextDouble() * 6.283185307179586D;
         double radius = closeEnough ? 0.55D + this.random.nextDouble() * 0.85D : 0.85D + this.random.nextDouble() * 1.25D;
         double x = player.getX() + Math.cos(angle) * radius;
         double y = player.getY() + 0.35D + this.random.nextDouble() * 1.55D;
         double z = player.getZ() + Math.sin(angle) * radius;
         if (this.random.nextBoolean()) {
            serverLevel.sendParticles(ModParticles.DREAD_MOTE.get(), x, y, z, 1, 0.04D, 0.06D, 0.04D, 0.005D);
         } else {
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 1, 0.03D, 0.05D, 0.03D, 0.004D);
         }
      }

      if ((closeEnough || stareLocked) && this.random.nextFloat() < 0.2F) {
         Vec3 eye = player.getEyePosition();
         Vec3 towardTyrant = this.getEyePosition().subtract(eye).normalize();
         Vec3 origin = eye.add(towardTyrant.scale(0.85D));
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), origin.x, origin.y, origin.z, 1, 0.14D, 0.08D, 0.14D, 0.008D);
      }

      if (this.random.nextFloat() < 0.2F) {
         double x = this.getX() + (this.random.nextDouble() - 0.5D) * this.getBbWidth() * 1.25D;
         double y = this.getY() + 0.5D + this.random.nextDouble() * this.getBbHeight() * 0.72D;
         double z = this.getZ() + (this.random.nextDouble() - 0.5D) * this.getBbWidth() * 1.25D;
         serverLevel.sendParticles(ParticleTypes.SOUL, x, y, z, 1, 0.02D, 0.05D, 0.02D, 0.0D);
      }
   }

   private TyrantCombatContext buildCombatContext(LivingEntity target, double horizontalDistanceSqr, double verticalGap, boolean hasSight) {
      double forwardAlignment = this.getTargetForwardAlignment(target);
      return new TyrantCombatContext(
         horizontalDistanceSqr,
         verticalGap,
         hasSight,
         Math.max(0.0D, forwardAlignment),
         Math.abs(this.getTargetSideAlignment(target)),
         Math.max(0.0D, -forwardAlignment),
         this.phaseTwoActive,
         this.lastAction,
         this.repeatedActionCount
      );
   }

   private void startAction(TyrantAction action) {
      this.entityData.set(ACTION, action.id());
      this.actionTick = 0;
      this.actionStep = 0;
      this.actionImpactTick = -1;
      this.resetLeapSlamState();
      this.attackCooldown = TyrantCombatDirector.getCooldownTicks(action, this.random, this.phaseTwoActive);
      if (isLeapSlamAction(action)) {
         this.leapSlamCooldown = LEAP_SLAM_FAMILY_COOLDOWN_TICKS;
      }

      if (action == this.lastAction) {
         ++this.repeatedActionCount;
      } else {
         this.repeatedActionCount = 0;
      }

      this.lastAction = action;
      this.getNavigation().stop();
      if (action == TyrantAction.INTRO_ROAR) {
         this.introPlayed = true;
      } else if (action == TyrantAction.PHASE_SHIFT) {
         this.phaseShiftPlayed = true;
      }

      this.combatDisplayTicks = Math.max(this.combatDisplayTicks, action == TyrantAction.PHASE_SHIFT ? 180 : 90);
   }

   private static boolean isLeapSlamAction(TyrantAction action) {
      return action == TyrantAction.LEAP_SLAM_FORWARD || action == TyrantAction.LEAP_SLAM_BACKWARD;
   }

   private boolean isUninterruptibleCeremonyAction(TyrantAction action) {
      return action == TyrantAction.DEATH || action == TyrantAction.INTRO_ROAR || action == TyrantAction.PHASE_SHIFT || action == TyrantAction.COMMAND_EXECUTION;
   }

   private void finishAction() {
      TyrantAction action = this.getCurrentAction();
      this.entityData.set(ACTION, TyrantAction.NONE.id());
      this.actionTick = 0;
      this.actionStep = 0;
      this.actionImpactTick = -1;
      if (isLeapSlamAction(action)) {
         this.resetLeapSlamState();
      }
   }

   private void tickCurrentAction() {
      TyrantAction action = this.getCurrentAction();
      LivingEntity target = this.getTarget();
      ++this.actionTick;
      this.getNavigation().stop();
      if (target != null && target.isAlive() && (!isLeapSlamAction(action) || this.actionTick <= 18)) {
         this.getLookControl().setLookAt(target, 30.0F, 30.0F);
      }

      switch (action) {
         case ATTACK_RIGHT:
            this.tickSingleArmAttack(target, true);
            break;
         case ATTACK_LEFT:
            this.tickSingleArmAttack(target, false);
            break;
         case DOUBLE_SLAM_TAIL:
            this.tickDoubleSlamTail(target);
            break;
         case ROAR_WAVE_SHORT:
            this.tickRoarWave(5.0F, 12.0F, 1.1D);
            break;
         case ROAR_WAVE_LONG:
            this.tickRoarWave(6.5F, 14.0F, 1.4D);
            break;
         case LEAP_SLAM_FORWARD:
            this.tickLeapSlam(target, true);
            break;
         case LEAP_SLAM_BACKWARD:
            this.tickLeapSlam(target, false);
            break;
         case INTRO_ROAR:
            this.tickIntroRoar(target);
            break;
         case PHASE_SHIFT:
            this.tickPhaseShift(target);
            break;
         case COMMAND_EXECUTION:
            this.tickCommandExecution(target);
      }

      if (this.actionTick >= action.durationTicks()) {
         this.finishAction();
      }

   }

   private void tickIntroRoar(LivingEntity target) {
      if (this.actionTick == 1) {
         this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.25F, 0.68F);
         this.triggerScreenShake(this.position().add(0.0D, 1.4D, 0.0D), 16.0F, 0.32F, 8);
      }

      if (this.actionTick >= 10 && this.actionTick <= 34 && this.actionTick % 4 == 0) {
         this.spawnChestChargeParticles();
      }

      if (this.actionTick == 16) {
         this.spawnRoarTelegraph(3.5F);
         this.triggerScreenShake(this.position().add(0.0D, 1.5D, 0.0D), 16.0F, 0.36F, 6);
      }

      if (this.actionTick == 30) {
         this.spawnIntroRoarSurge(false);
         this.playSound(SoundEvents.RAVAGER_ROAR, 1.1F, 0.72F);
         this.triggerScreenShake(this.position().add(0.0D, 1.2D, 0.0D), 18.0F, 0.65F, 10);
      }

      if (this.actionTick == 52) {
         this.spawnRoarTelegraph(6.0F);
         this.spawnIntroRoarSurge(true);
         this.level().playSound((Player)null, this.getX(), this.getY() + 1.1D, this.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.85F, 0.95F);
         this.triggerScreenShake(this.position().add(0.0D, 1.2D, 0.0D), 22.0F, 1.1F, 14);
      }

   }

   private void tickPhaseShift(LivingEntity target) {
      if (this.actionTick == 1) {
         this.playSound(SoundEvents.WITHER_SPAWN, 0.95F, 0.72F);
         this.spawnGroundPressureRing(this.position().add(0.0D, 0.1D, 0.0D), 3.2F, true);
         this.triggerScreenShake(this.position().add(0.0D, 1.2D, 0.0D), 20.0F, 0.6F, 10);
      }

      if (this.actionTick >= 8 && this.actionTick <= 44 && this.actionTick % 2 == 0) {
         this.spawnChestChargeParticles();
      }

      if (this.actionTick == 16 || this.actionTick == 28 || this.actionTick == 40) {
         float radius = this.actionTick == 16 ? 4.2F : (this.actionTick == 28 ? 5.8F : 7.0F);
         this.spawnRoarTelegraph(radius);
         this.spawnChestCollapseTelegraph(true);
         this.triggerScreenShake(this.position().add(0.0D, 1.7D, 0.0D), 22.0F, this.actionTick == 40 ? 0.52F : 0.34F, 7);
      }

      if (this.actionStep == 0 && this.actionTick >= 48) {
         this.actionStep = 1;
         this.phaseTwoActive = true;
         this.spawnPhaseShiftSurge(false);
         this.level().playSound((Player)null, this.getX(), this.getY() + 1.0D, this.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0F, 0.78F);
         this.triggerScreenShake(this.position().add(0.0D, 1.2D, 0.0D), 26.0F, 1.5F, 18);
      }

      if (this.actionStep == 1 && this.actionTick == 64) {
         this.actionStep = 2;
         this.spawnPhaseShiftSurge(true);
         this.level().playSound((Player)null, this.getX(), this.getY() + 0.8D, this.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2F, 0.58F);
         this.triggerScreenShake(this.position().add(0.0D, 1.0D, 0.0D), 30.0F, 2.05F, 22);
      }

   }

   private void tickCommandExecution(LivingEntity target) {
      if (target != null && target.isAlive()) {
         this.faceTargetForStrike(target, 196.0D);
      }

      Vec3 origin = this.position().add(0.0D, 0.2D, 0.0D);
      if (this.actionTick == 1) {
         this.playSound(SoundEvents.WITHER_AMBIENT, 1.2F, 0.42F);
         this.level().playSound((Player)null, this.getX(), this.getY() + 1.1D, this.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.36F);
         this.spawnGroundPressureRing(origin, 2.8F, true);
         this.triggerScreenShake(origin.add(0.0D, 1.1D, 0.0D), 20.0F, 0.72F, 10);
      }

      if (this.actionTick >= 6 && this.actionTick <= 30 && this.actionTick % 4 == 0) {
         this.spawnChestChargeParticles();
         this.spawnExecutionChains(target);
      }

      if (this.actionTick == 18) {
         this.spawnChestCollapseTelegraph(true);
         this.spawnExecutionCrown(target);
         this.level().playSound((Player)null, this.getX(), this.getY() + 1.2D, this.getZ(), SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 1.0F, 0.48F);
         this.triggerScreenShake(origin.add(0.0D, 1.2D, 0.0D), 22.0F, 1.05F, 12);
      }

      if (target != null && target.isAlive() && this.actionTick == 28) {
         Vec3 pull = this.position().subtract(target.position());
         pull = new Vec3(pull.x, 0.0D, pull.z);
         if (pull.lengthSqr() > 1.0E-4D) {
            pull = pull.normalize();
            target.setDeltaMovement(pull.x * 0.42D, 0.12D, pull.z * 0.42D);
            target.hasImpulse = true;
         }

         target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 55, 0, true, false, true), this);
         target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 3, true, true, true), this);
      }

      if (this.actionTick == 42) {
         Vec3 strike = target != null && target.isAlive() ? target.position() : origin;
         this.spawnExecutionImpact(strike);
         this.level().playSound((Player)null, strike.x, strike.y + 0.6D, strike.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.05F, 0.58F);
         this.level().playSound((Player)null, strike.x, strike.y + 0.2D, strike.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.82F, 0.7F);
         this.triggerScreenShake(strike.add(0.0D, 0.8D, 0.0D), 26.0F, 1.85F, 18);
      }

      if (this.actionTick == 58) {
         this.spawnGroundPressureRing(origin, 4.2F, true);
         this.spawnWaveRingGround(origin, this.getFacingVector(), 5.0F, true);
         this.triggerScreenShake(origin.add(0.0D, 0.8D, 0.0D), 24.0F, 1.1F, 12);
      }
   }

   private void tickSingleArmAttack(LivingEntity target, boolean rightArm) {
      if (this.actionTick == 1) {
         this.playSound(rightArm ? SoundEvents.RAVAGER_ROAR : SoundEvents.WARDEN_ATTACK_IMPACT, rightArm ? 1.08F : 1.35F, rightArm ? 0.8F : 0.48F);
      }

      if (this.actionTick >= 4 && this.actionTick <= 10 && this.actionTick % 2 == 0) {
         this.spawnHeavyPunchCharge(rightArm, this.actionTick);
      }

      if (this.actionTick == 6) {
         this.spawnGroundPressureRing(this.position().add(0.0D, 0.1D, 0.0D), rightArm ? 1.9F : 2.5F, false);
         this.triggerScreenShake(this.position().add(0.0D, 0.8D, 0.0D), rightArm ? 8.0F : 12.0F, rightArm ? 0.14F : 0.24F, rightArm ? 3 : 4);
      }

      if (this.actionTick == 8) {
         this.spawnArmTelegraph(rightArm);
         this.triggerScreenShake(this.position().add(0.0D, 1.3D, 0.0D), 10.0F, 0.22F, 4);
      }

      if (this.actionTick == 11) {
         this.spawnArmTelegraph(rightArm);
         this.spawnHeavyPunchWarning(rightArm, false);
         this.spawnChestChargeParticles();
      }

      if (!rightArm && this.actionTick == 13) {
         Vec3 center = this.position().add(0.0D, 0.12D, 0.0D);
         this.spawnGroundPressureRing(center, 3.35F, true);
         this.spawnHeavyPunchWarning(false, true);
         this.spawnBoneBreakerTelegraph(center);
         this.triggerScreenShake(this.position().add(0.0D, 0.9D, 0.0D), 16.0F, 0.52F, 7);
      }

      if (rightArm && this.actionTick == 16) {
         this.faceTargetForStrike(target, 49.0D);
         this.spawnPursuitTelegraph(target);
         this.spawnHeavyPunchWarning(true, true);
         this.surgeTowardTarget(target, this.phaseTwoActive ? 0.72D : 0.58D);
         this.triggerScreenShake(this.position().add(0.0D, 1.0D, 0.0D), 14.0F, 0.42F, 5);
      }

      if (this.actionStep == 0 && this.actionTick >= (rightArm ? 12 : 15)) {
         this.actionStep = 1;
         this.faceTargetForStrike(target, rightArm ? 49.0D : 36.0D);
         this.performArmSlam(rightArm, rightArm ? 24.0F : 50.0F, rightArm ? 1.16D : 1.22D);
      }

      if (this.actionStep == 1 && this.actionTick == (rightArm ? 15 : 18)) {
         this.spawnArmAftershock(rightArm);
         this.performHeavyPunchAftershock(rightArm, 1);
         this.triggerScreenShake(this.position().add(0.0D, 1.0D, 0.0D), rightArm ? 12.0F : 17.0F, rightArm ? 0.4F : 0.72F, rightArm ? 5 : 8);
      }

      if (rightArm && this.actionStep == 1 && this.actionTick == 17) {
         this.actionStep = 2;
         this.spawnPursuitTelegraph(target);
         this.spawnArmTelegraph(true);
         this.triggerScreenShake(this.position().add(0.0D, 1.0D, 0.0D), 15.0F, 0.62F, 6);
      }

      if (rightArm && this.actionStep == 2 && this.actionTick == 18) {
         this.performArmWave(true, this.phaseTwoActive ? 4.6F : 3.8F, this.phaseTwoActive ? 9.0F : 4.5F, this.phaseTwoActive ? 0.95D : 0.56D);
      }

      if (rightArm && this.actionStep == 2 && this.actionTick >= 20) {
         this.actionStep = 3;
         this.faceTargetForStrike(target, 64.0D);
         this.performRightPursuitSlam(target, this.phaseTwoActive ? 31.0F : 27.0F, this.phaseTwoActive ? 1.52D : 1.34D);
      } else if (!rightArm && this.actionStep == 1 && this.actionTick == 21) {
         this.faceTargetForStrike(target, 36.0D);
         this.spawnBoneBreakerAftershock(this.position().add(0.0D, 0.5D, 0.0D));
         this.performArmWave(false, this.phaseTwoActive ? 4.9F : 4.25F, this.phaseTwoActive ? 9.0F : 6.75F, this.phaseTwoActive ? 1.08D : 0.88D);
      }

      if (rightArm && this.actionStep == 3 && this.actionTick == 22) {
         this.performHeavyPunchAftershock(true, 2);
      } else if (!rightArm && this.actionStep == 1 && this.actionTick == 23) {
         this.performHeavyPunchAftershock(false, 2);
      }

   }

   private void tickDoubleSlamTail(LivingEntity target) {
      if (this.actionTick == 1) {
         this.playSound(SoundEvents.WITHER_AMBIENT, 1.18F, 0.52F);
      }

      Vec3 origin = this.position().add(0.0D, 0.4D, 0.0D);
      if (this.actionTick == 8) {
         this.spawnGroundPressureRing(origin, 2.75F, false);
         this.triggerScreenShake(origin, 16.0F, 0.36F, 5);
      }

      if (this.actionTick == 15 || this.actionTick == 27) {
         this.spawnComboTelegraph(this.actionTick == 15);
      }

      if (this.actionTick == 36 || this.actionTick == 40) {
         this.spawnThroneStompTelegraph(origin, this.actionTick == 40);
         this.triggerScreenShake(origin, this.actionTick == 40 ? 27.0F : 20.0F, this.actionTick == 40 ? 0.96F : 0.54F, this.actionTick == 40 ? 11 : 7);
      }

      if (this.actionTick == 40) {
         Vec3 sweepOrigin = origin.add(0.0D, 0.55D, 0.0D);
         double sweepRadius = this.phaseTwoActive ? 7.1D : 6.25D;
         this.rendSweepTerrain(sweepOrigin, sweepRadius, true);
         this.spawnSweepArc(sweepOrigin, this.getFacingVector(), sweepRadius);
      }

      if (this.actionStep == 0 && this.actionTick >= 18) {
         this.actionStep = 1;
         this.faceTargetForStrike(target, 42.25D);
         this.performArmSlam(true, 10.5F, 0.85D);
      }

      if (this.actionStep == 1 && this.actionTick >= 31) {
         this.actionStep = 2;
         this.faceTargetForStrike(target, 42.25D);
         this.performArmSlam(false, 12.5F, 0.92D);
      }

      if (this.actionStep == 2 && this.actionTick >= 45) {
         this.actionStep = 3;
         this.performThroneStomp(5.9F, this.phaseTwoActive ? 25.0F : 21.0F, this.phaseTwoActive ? 1.95D : 1.72D);
      }

      if (this.actionStep == 3 && this.actionTick == 52) {
         this.actionStep = 4;
         this.performThroneStompAftershock(7.0F, this.phaseTwoActive ? 10.0F : 7.0F, 1.05D);
      }

      if (this.phaseTwoActive && this.actionStep == 4 && this.actionTick == 58) {
         this.performThroneStompAftershock(8.0F, 6.0F, 0.82D);
      }

   }

   private void tickRoarWave(float radius, float damage, double knockback) {
      boolean longWave = radius > 6.0F;
      if (this.actionTick == 1) {
         this.playSound(SoundEvents.ENDER_DRAGON_GROWL, longWave ? 1.35F : 1.15F, longWave ? 0.62F : 0.7F);
      }

      if (this.actionTick >= 14 && this.actionTick <= 36 && this.actionTick % 3 == 0) {
         this.spawnChestChargeParticles();
      }

      if (this.actionTick == 22 || this.actionTick == 30 || this.actionTick == 36) {
         float telegraphRadius = this.actionTick == 22 ? radius * 0.45F : (this.actionTick == 30 ? radius * 0.72F : radius * 1.02F);
         this.spawnRoarTelegraph(telegraphRadius);
         this.triggerScreenShake(this.position().add(0.0D, 1.6D, 0.0D), 14.0F, this.actionTick == 36 ? 0.34F : (this.actionTick == 30 ? 0.26F : 0.16F), 5);
      }

      if (this.actionTick == 38) {
         this.spawnChestCollapseTelegraph(longWave);
         this.triggerScreenShake(this.position().add(0.0D, 1.8D, 0.0D), 16.0F, longWave ? 0.48F : 0.36F, 6);
      }

      if (this.actionStep == 0 && this.actionTick >= 39) {
         this.actionStep = 1;
         this.faceTargetForStrike(this.getTarget(), 100.0D);
         this.performGroundWave(radius, damage, knockback, longWave);
      }

      if (this.actionStep == 1 && this.actionTick == 45) {
         this.spawnWaveAfterglow(radius, longWave);
      }

      if (this.phaseTwoActive && this.actionStep == 1 && this.actionTick == 50) {
         this.performWaveEcho(radius, damage * 0.55F, knockback * 0.65D, longWave);
      }

   }

   private void tickLeapSlam(LivingEntity target, boolean forward) {
      if (this.actionTick <= 18) {
         this.refreshLeapSlamAim(target, forward, false);
      }

      if (this.actionTick == 8) {
         this.playSound(SoundEvents.RAVAGER_STUNNED, 1.0F, forward ? 0.72F : 0.62F);
         this.spawnGroundPressureRing(this.position().add(0.0D, 0.1D, 0.0D), forward ? 2.8F : 2.1F, true);
         this.triggerScreenShake(this.position().add(0.0D, 0.8D, 0.0D), 12.0F, forward ? 0.22F : 0.18F, 4);
      }

      if (this.actionTick == 16) {
         this.spawnLeapTelegraph(forward);
         this.triggerScreenShake(this.position().add(0.0D, 1.0D, 0.0D), 11.0F, forward ? 0.3F : 0.24F, 5);
      }

      if (this.actionTick == 20) {
         this.refreshLeapSlamAim(target, forward, true);
      }

      if (this.actionStep == 0 && this.actionTick >= 22) {
         this.actionStep = 1;
         this.refreshLeapSlamAim(target, forward, true);
         Vec3 travel = this.getLeapTravelVector(target, forward);
         this.spawnTakeoffBurst(forward);
         this.setDeltaMovement(travel.x, forward ? 0.68D : 0.58D, travel.z);
         this.hasImpulse = true;
         this.level().playSound((Player)null, this.getX(), this.getY(), this.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, forward ? 1.1F : 0.95F, forward ? 0.56F : 0.48F);
         this.triggerScreenShake(this.position().add(0.0D, 0.6D, 0.0D), 15.0F, forward ? 0.5F : 0.36F, 6);
      }

      if (this.actionStep == 1) {
         this.stabilizeLeapTrajectory(forward);
      }

      if (this.actionStep == 1 && this.actionTick >= 38 && (this.horizontalCollision || this.actionTick >= 42 || this.onGround() && this.getDeltaMovement().y <= 0.12D)) {
         this.actionStep = 2;
         this.actionImpactTick = this.actionTick;
         this.setDeltaMovement(0.0D, Math.min(this.getDeltaMovement().y, 0.0D), 0.0D);
         this.hasImpulse = true;
         this.performLeapImpact(forward ? 6.8F : 5.9F, forward ? 16.0F : 14.5F, forward ? 1.65D : 1.4D, forward);
      }

      if (this.actionStep == 2 && this.actionImpactTick > 0 && this.actionTick >= this.actionImpactTick + 4) {
         this.actionStep = 3;
         this.performLeapAftershock(forward ? 5.2F : 4.6F, forward ? 8.5F : 7.2F, forward ? 1.0D : 0.82D, forward, 1);
      }

      if (this.actionStep == 3 && this.actionImpactTick > 0 && this.actionTick >= this.actionImpactTick + 9) {
         this.actionStep = 4;
         this.performLeapAftershock(forward ? 6.0F : 5.2F, forward ? 6.5F : 5.4F, forward ? 0.82D : 0.68D, forward, 2);
      }

   }

   private Vec3 getLeapTravelVector(LivingEntity target, boolean forward) {
      if (this.leapSlamTravelDirection.lengthSqr() < 1.0E-4D) {
         this.refreshLeapSlamAim(target, forward, true);
      }

      Vec3 direction = this.leapSlamTravelDirection.lengthSqr() < 1.0E-4D ? this.getFacingVector() : this.leapSlamTravelDirection.normalize();
      double horizontalDistance = target != null && target.isAlive()
         ? Math.sqrt(new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ()).lengthSqr())
         : 4.5D;
      double speed = forward
         ? Mth.clamp(horizontalDistance * 0.16D + 0.32D, 1.02D, this.phaseTwoActive ? 1.58D : 1.42D)
         : Mth.clamp(horizontalDistance * 0.11D + 0.28D, 0.78D, this.phaseTwoActive ? 1.12D : 0.98D);
      return direction.scale(speed);
   }

   private void performArmSlam(boolean rightArm, float damage, double knockback) {
      Vec3 facing = this.getFacingVector();
      Vec3 strikeCenter = this.getArmStrikeCenter(rightArm);
      this.lastHeavyPunchCenter = strikeCenter;
      float finalDamage = this.phaseTwoActive ? damage * 1.12F : damage;
      double finalKnockback = this.phaseTwoActive ? knockback * 1.08D : knockback;
      if (rightArm) {
         TyrantDamageHelper.damageOrientedBox(this, strikeCenter, facing, 2.4D, 1.18D, 1.85D, finalDamage, finalKnockback, 0.3D);
         this.spawnDirectionalImpact(strikeCenter, facing, 1, 5.1D, 38);
         this.spawnGroundFractureLine(strikeCenter, facing, 1, 4.6D, 0.82D, 2);
         this.spawnBlockShockwave(strikeCenter, 3.15F, 30, 0.24D, true);
         this.spawnEpicImpactBurst(strikeCenter, 2.7F, false);
         TyrantTerrainHelper.tearTerrain(this, strikeCenter, 2.1F, 2.4F, 0.48F);
         this.triggerScreenShake(strikeCenter, 20.0F, 1.45F, 12);
         this.playImpactSound(strikeCenter, 1.32F, 0.58F);
      } else {
         TyrantDamageHelper.damageOrientedBox(this, strikeCenter, facing, 2.05D, 1.72D, 2.0D, finalDamage, finalKnockback, 0.24D);
         this.spawnDirectionalImpact(strikeCenter, facing, -1, 4.3D, 34);
         this.spawnGroundFractureLine(strikeCenter, facing, -1, 5.0D, 1.38D, 4);
         this.spawnBlockShockwave(strikeCenter, 3.9F, 42, 0.28D, true);
         this.spawnEpicImpactBurst(strikeCenter, 3.45F, true);
         TyrantTerrainHelper.tearTerrain(this, strikeCenter, 2.8F, 4.2F, 0.68F);
         this.triggerScreenShake(strikeCenter, 24.0F, 1.9F, 16);
         this.playImpactSound(strikeCenter, 1.56F, 0.46F);
         this.level().playSound((Player)null, strikeCenter.x, strikeCenter.y + 0.4D, strikeCenter.z, SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 1.45F, 0.42F);
         this.spawnGroundPressureRing(strikeCenter, 2.9F, true);
      }

   }

   private Vec3 getArmStrikeCenter(boolean rightArm) {
      Vec3 facing = this.getFacingVector();
      Vec3 side = this.getSideVector(facing);
      return this.position().add(0.0D, 1.25D, 0.0D).add(facing.scale(rightArm ? 2.35D : 2.08D)).add(side.scale(rightArm ? 1.05D : -1.25D));
   }

   private void spawnHeavyPunchCharge(boolean rightArm, int chargeTick) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         Vec3 fist = this.getArmStrikeCenter(rightArm).add(0.0D, 0.42D, 0.0D);
         float progress = Mth.clamp((float)(chargeTick - 4) / 6.0F, 0.0F, 1.0F);
         double spread = Mth.lerp((double)progress, 0.68D, 0.18D);
         int count = rightArm ? 7 : 10;
         serverLevel.sendParticles(ModParticles.DREAD_MOTE.get(), fist.x, fist.y, fist.z, count, spread, spread * 0.6D, spread, 0.012D + (double)progress * 0.008D);
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), fist.x, fist.y + 0.05D, fist.z, rightArm ? 3 : 5, spread * 0.5D, spread * 0.36D, spread * 0.5D, 0.012D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, fist.x, fist.y - 0.18D, fist.z, rightArm ? 5 : 7, spread * 0.44D, 0.08D, spread * 0.44D, 0.01D);
         if (chargeTick % 4 == 0) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, fist.x, fist.y - 0.08D, fist.z, rightArm ? 3 : 4, spread * 0.32D, 0.08D, spread * 0.32D, 0.01D);
         }

         Vec3 ground = fist.add(0.0D, -1.15D, 0.0D);
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, ground).getY() + 1.04D;
         serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, TyrantTerrainHelper.findImpactState(this, ground)), fist.x, y, fist.z, rightArm ? 3 : 5, 0.18D + spread * 0.25D, 0.08D, 0.18D + spread * 0.25D, 0.05D);
         if (chargeTick == 4 || chargeTick == 8) {
            serverLevel.playSound((Player)null, fist.x, fist.y, fist.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, rightArm ? 0.45F : 0.62F, rightArm ? 0.58F : 0.42F);
         }
      }
   }

   private void spawnHeavyPunchWarning(boolean rightArm, boolean climax) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         Vec3 facing = this.getFacingVector();
         Vec3 side = this.getSideVector(facing);
         Vec3 center = this.getArmStrikeCenter(rightArm).add(0.0D, -1.0D, 0.0D);
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, center).getY() + 1.045D;
         int segments = climax ? 9 : 7;
         int lanes = climax ? 5 : 3;
         double length = rightArm ? 4.4D : 3.8D;
         double width = rightArm ? 1.35D : 1.75D;

         for(int lane = 0; lane < lanes; ++lane) {
            double laneProgress = lanes <= 1 ? 0.5D : (double)lane / (double)(lanes - 1);
            double lateral = (laneProgress - 0.5D) * width * 2.0D;

            for(int i = 0; i < segments; ++i) {
               double progress = segments <= 1 ? 0.0D : (double)i / (double)(segments - 1);
               Vec3 point = center.add(facing.scale(progress * length - 0.65D)).add(side.scale(lateral));
               double pointY = (double)TyrantTerrainHelper.findImpactSurface(this, point).getY() + 1.05D;
               if (climax || (i + lane) % 2 == 0) {
                  serverLevel.sendParticles(ModParticles.DREAD_MOTE.get(), point.x, pointY + 0.08D, point.z, 1, 0.025D, 0.03D, 0.025D, climax ? 0.016D : 0.009D);
               }

               if (i % 2 == 0) {
                  serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, pointY + 0.04D, point.z, 1, 0.02D, 0.025D, 0.02D, climax ? 0.012D : 0.007D);
               }
            }
         }

         this.spawnGroundFractureLine(center.add(facing.scale(0.3D)), facing, rightArm ? 1 : -1, length, width * 0.55D, climax ? 3 : 2);
         this.spawnGroundPressureRing(center, climax ? 2.6F : 1.85F, climax);
      }
   }

   private void performHeavyPunchAftershock(boolean rightArm, int waveIndex) {
      Vec3 origin = this.lastHeavyPunchCenter == Vec3.ZERO ? this.getArmStrikeCenter(rightArm) : this.lastHeavyPunchCenter;
      float radius = (rightArm ? 2.75F : 3.15F) + (float)waveIndex * (rightArm ? 0.82F : 1.05F);
      float damage = (rightArm ? 3.5F : 4.5F) + (float)waveIndex * (rightArm ? 1.5F : 2.0F);
      double knockback = (rightArm ? 0.48D : 0.58D) + (double)waveIndex * 0.18D;
      double minRadius = waveIndex == 1 ? 0.75D : 1.85D;
      TyrantDamageHelper.damageRadial(this, origin, (double)radius, 1.45D, minRadius, this.phaseTwoActive ? damage * 1.12F : damage, this.phaseTwoActive ? knockback * 1.1D : knockback, waveIndex == 1 ? 0.16D : 0.22D, true);
      this.spawnGroundPressureRing(origin, radius, waveIndex == 2 || !rightArm);
      this.spawnBlockShockwave(origin, radius + 0.35F, waveIndex == 1 ? 30 : 42, waveIndex == 1 ? 0.16D : 0.22D, true);
      this.spawnEpicImpactBurst(origin, radius * 0.65F, waveIndex == 2);
      this.triggerScreenShake(origin, waveIndex == 1 ? 14.0F : 18.0F, waveIndex == 1 ? 0.5F : 0.78F, waveIndex == 1 ? 6 : 8);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, waveIndex == 1 ? 0.58F : 0.74F, waveIndex == 1 ? 0.92F : 0.78F);
   }

   private void performTailSweep(double radius, float damage, double knockback) {
      Vec3 origin = this.position().add(0.0D, 0.95D, 0.0D);
      Vec3 facing = this.getFacingVector();
      TyrantDamageHelper.damageTailSweep(this, origin, facing, radius, 1.35D, 2.15D, damage, knockback, 0.24D);
      this.spawnBlockShockwave(origin, (float)radius + 0.55F, 56, 0.24D, true);
      this.spawnSweepArc(origin, facing, radius + 0.45D);
      this.spawnTailRendColumns(origin, facing, radius + 0.15D);
      this.spawnEpicImpactBurst(origin, (float)radius + 0.3F, false);
      this.rendSweepTerrain(origin, radius, true);
      this.triggerScreenShake(origin, 27.0F, 1.75F, 16);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.35F, 0.46F);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.25F, 0.56F);
      this.level().playSound((Player)null, origin.x, origin.y + 0.8D, origin.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.72F, 0.62F);
   }

   private void performThroneStomp(float radius, float damage, double knockback) {
      Vec3 origin = this.position().add(0.0D, 0.88D, 0.0D);
      TyrantDamageHelper.damageRadial(this, origin, (double)radius, 2.05D, 0.35D, damage, knockback, 0.5D, true);
      this.spawnBlockShockwave(origin, radius + 1.2F, 86, 0.45D, true);
      this.spawnGroundPressureRing(origin, radius * 0.62F, true);
      this.spawnLandingColumns(origin, (double)radius + 0.75D);
      this.spawnEpicImpactBurst(origin, radius + 1.25F, true);
      TyrantTerrainHelper.tearTerrain(this, origin, 3.8F, 4.7F, 0.78F);
      this.triggerScreenShake(origin, 34.0F, 2.65F, 24);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.18F, 0.62F);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.65F, 0.42F);
      this.level().playSound((Player)null, origin.x, origin.y + 1.0D, origin.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.95F, 0.46F);
   }

   private void performThroneStompAftershock(float radius, float damage, double knockback) {
      Vec3 origin = this.position().add(0.0D, 0.8D, 0.0D);
      TyrantDamageHelper.damageRadial(this, origin, (double)radius, 1.7D, 2.25D, damage, knockback, 0.24D, true);
      this.spawnBlockShockwave(origin, radius + 0.65F, 58, 0.24D, true);
      this.spawnGroundPressureRing(origin, radius * 0.48F, false);
      this.spawnEpicImpactBurst(origin, radius * 0.74F, false);
      this.triggerScreenShake(origin, 24.0F, 1.15F, 12);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.62F, 0.9F);
   }

   private void performGroundWave(float radius, float damage, double knockback, boolean longWave) {
      Vec3 origin = this.position().add(0.0D, 1.05D, 0.0D);
      Vec3 facing = this.getFacingVector();
      TyrantDamageHelper.damageForwardWave(this, origin.add(facing.scale(0.95D)), facing, (double)radius + (longWave ? 1.2D : 0.55D), 2.25D, longWave ? 1.85D : 1.35D, damage, knockback, 0.35D);
      this.spawnBlockShockwave(origin, radius + 1.05F, longWave ? 68 : 44, longWave ? 0.36D : 0.24D, true);
      this.spawnWaveRingGround(origin, facing, radius + (longWave ? 0.65F : 0.2F), longWave);
      this.spawnPressureWavefront(origin, facing, (double)(radius + (longWave ? 1.1F : 0.55F)), longWave);
      this.spawnEpicImpactBurst(origin.add(facing.scale((double)radius * 0.24D)), radius + (longWave ? 1.45F : 0.72F), longWave);
      TyrantTerrainHelper.tearTerrain(this, origin.add(facing.scale(longWave ? 2.1D : 1.35D)), longWave ? 4.5F : 3.1F, longWave ? 3.8F : 2.8F, longWave ? 0.44F : 0.3F);
      this.triggerScreenShake(origin, longWave ? 34.0F : 26.0F, longWave ? 2.55F : 1.72F, longWave ? 24 : 15);
      this.level().playSound((Player)null, origin.x, origin.y + 1.0D, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, longWave ? 1.18F : 0.96F, longWave ? 0.64F : 0.74F);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, longWave ? 0.92F : 0.58F, 0.82F);
      this.level().playSound((Player)null, origin.x, origin.y + 0.9D, origin.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, longWave ? 0.9F : 0.68F, longWave ? 0.6F : 0.74F);
   }

   private void performLeapImpact(float radius, float damage, double knockback, boolean forward) {
      Vec3 origin = this.getLeapArmImpactCenter(forward);
      Vec3 facing = this.getLeapFacingVector();
      this.leapSlamImpactCenter = origin;
      TyrantDamageHelper.damageOrientedBox(this, origin, facing, forward ? 3.15D : 2.7D, 1.85D, 2.2D, damage, knockback * 1.06D, 0.46D);
      TyrantDamageHelper.damageRadial(this, origin, (double)radius, 1.95D, 0.5D, damage, knockback, 0.42D, true);
      this.spawnBlockShockwave(origin, radius + 1.15F, 72, 0.42D, true);
      this.spawnDirectionalImpact(origin, facing, 0, 5.0D, 36);
      this.spawnGroundFractureLine(origin, facing, 0, 4.6D, 1.35D, 4);
      this.spawnLandingColumns(origin, (double)radius + 0.95D);
      this.spawnEpicImpactBurst(origin, radius + 1.2F, true);
      float craterRadius = (forward ? 6.35F : 5.45F) + (this.phaseTwoActive ? 0.45F : 0.0F);
      int craterDepth = forward || this.phaseTwoActive ? 3 : 2;
      TyrantTerrainHelper.carveImpactCrater(this, origin, craterRadius, craterDepth, forward ? 5.4F : IMPACT_BREAK_SPEED, forward ? 0.86F : 0.72F);
      this.spawnCraterRubble(origin, craterRadius, true);
      this.triggerScreenShake(origin, 34.0F, forward ? 3.15F : 2.35F, forward ? 26 : 20);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.22F, forward ? 0.7F : 0.82F);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.45F, 0.54F);
      this.level().playSound((Player)null, origin.x, origin.y + 0.8D, origin.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, forward ? 0.85F : 0.62F, forward ? 0.62F : 0.7F);
   }

   private void performLeapAftershock(float radius, float damage, double knockback, boolean forward, int pulseIndex) {
      Vec3 facing = this.getLeapFacingVector();
      Vec3 base = this.leapSlamImpactCenter.lengthSqr() > 1.0E-4D ? this.leapSlamImpactCenter : this.getLeapArmImpactCenter(forward);
      Vec3 origin = base.add(facing.scale(pulseIndex == 1 ? 0.2D : 0.45D));
      float scaledRadius = radius + (this.phaseTwoActive ? 0.45F : 0.0F);
      float scaledDamage = this.phaseTwoActive ? damage * 1.12F : damage;
      double scaledKnockback = this.phaseTwoActive ? knockback * 1.1D : knockback;
      TyrantDamageHelper.damageRadial(this, origin, (double)scaledRadius, 1.8D, pulseIndex == 1 ? 1.25D : 2.25D, scaledDamage, scaledKnockback, pulseIndex == 1 ? 0.24D : 0.18D, true);
      this.spawnBlockShockwave(origin, scaledRadius + (pulseIndex == 1 ? 0.65F : 0.9F), pulseIndex == 1 ? 32 : 40, pulseIndex == 1 ? 0.22D : 0.26D, true);
      this.spawnGroundPressureRing(origin, pulseIndex == 1 ? 2.6F : 3.0F, pulseIndex == 2);
      this.spawnPressureWavefront(origin, facing, (double)scaledRadius * (forward ? 0.82D : 0.68D), pulseIndex == 2 || this.phaseTwoActive);
      this.triggerScreenShake(origin, pulseIndex == 1 ? 18.0F : 22.0F, pulseIndex == 1 ? 0.88F : 1.05F, pulseIndex == 1 ? 9 : 11);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, pulseIndex == 1 ? 0.58F : 0.72F, pulseIndex == 1 ? 1.05F : 0.92F);
   }

   private Vec3 getLeapArmImpactCenter(boolean forward) {
      Vec3 facing = this.getLeapFacingVector();
      Vec3 side = this.getSideVector(facing);
      double forwardOffset = forward ? 2.85D : 2.35D;
      return this.position().add(0.0D, 0.98D, 0.0D).add(facing.scale(forwardOffset)).add(side.scale(-1.72D));
   }

   private void performArmWave(boolean rightArm, float radius, float damage, double knockback) {
      Vec3 facing = this.getFacingVector();
      Vec3 side = this.getSideVector(facing);
      Vec3 origin = this.position().add(0.0D, 1.0D, 0.0D).add(facing.scale(1.9D)).add(side.scale(rightArm ? 0.85D : -0.85D));
      TyrantDamageHelper.damageForwardWave(this, origin, facing, (double)radius, 1.7D, 0.95D, damage, knockback, 0.22D);
      this.spawnGroundFractureLine(origin, facing, rightArm ? 1 : -1, (double)radius * 0.95D, 0.58D, 2);
      this.spawnPressureWavefront(origin, facing, (double)radius * 0.78D, false);
      this.spawnEpicImpactBurst(origin, 1.9F, false);
      this.triggerScreenShake(origin, 12.0F, 0.6F, 6);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 0.88F, 0.94F);
   }

   private void performRightPursuitSlam(LivingEntity target, float damage, double knockback) {
      Vec3 facing = target != null ? new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ()) : this.getFacingVector();
      if (facing.lengthSqr() < 1.0E-4D) {
         facing = this.getFacingVector();
      } else {
         facing = facing.normalize();
      }

      Vec3 side = this.getSideVector(facing);
      Vec3 strikeCenter = this.position().add(0.0D, 1.12D, 0.0D).add(facing.scale(2.8D)).add(side.scale(0.92D));
      this.lastHeavyPunchCenter = strikeCenter;
      float finalDamage = this.phaseTwoActive ? damage * 1.18F : damage;
      double finalKnockback = this.phaseTwoActive ? knockback * 1.1D : knockback;
      TyrantDamageHelper.damageOrientedBox(this, strikeCenter, facing, 2.85D, 1.12D, 1.8D, finalDamage, finalKnockback, 0.34D);
      this.spawnDirectionalImpact(strikeCenter, facing, 1, 5.8D, 42);
      this.spawnGroundFractureLine(strikeCenter, facing, 1, 5.4D, 0.74D, 3);
      this.spawnPressureWavefront(strikeCenter, facing, 4.2D, false);
      this.spawnBlockShockwave(strikeCenter, 3.45F, 36, 0.24D, true);
      this.spawnEpicImpactBurst(strikeCenter, 3.05F, true);
      TyrantTerrainHelper.tearTerrain(this, strikeCenter, 2.65F, 3.6F, 0.58F);
      this.triggerScreenShake(strikeCenter, 22.0F, 1.78F, 14);
      this.level().playSound((Player)null, strikeCenter.x, strikeCenter.y, strikeCenter.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 1.36F, 0.58F);
      this.level().playSound((Player)null, strikeCenter.x, strikeCenter.y, strikeCenter.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.82F, 0.66F);
   }

   private void performTailEchoSweep(double radius, float damage, double knockback) {
      Vec3 origin = this.position().add(0.0D, 0.96D, 0.0D);
      Vec3 facing = this.getFacingVector();
      TyrantDamageHelper.damageTailSweep(this, origin, facing, radius, 2.5D, 1.8D, damage, knockback, 0.16D);
      this.spawnTailRendColumns(origin, facing, radius - 0.35D);
      this.spawnSweepArc(origin, facing, radius - 0.55D);
      this.spawnGroundPressureRing(origin, 2.8F, true);
      this.rendSweepTerrain(origin, radius - 0.35D, false);
      this.triggerScreenShake(origin, 18.0F, 0.95F, 9);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.3F, 0.45F);
   }

   private void performWaveEcho(float radius, float damage, double knockback, boolean longWave) {
      Vec3 origin = this.position().add(0.0D, 1.02D, 0.0D);
      Vec3 facing = this.getFacingVector();
      float echoRadius = radius * (longWave ? 0.84F : 0.74F);
      TyrantDamageHelper.damageForwardWave(this, origin.add(facing.scale(1.1D)), facing, (double)echoRadius, 1.75D, longWave ? 1.2D : 0.92D, damage, knockback, 0.22D);
      this.spawnPressureWavefront(origin, facing, (double)echoRadius * 0.88D, longWave);
      this.spawnWaveRingGround(origin, facing, echoRadius * 0.82F, false);
      this.triggerScreenShake(origin, 20.0F, longWave ? 0.88F : 0.72F, 10);
      this.level().playSound((Player)null, origin.x, origin.y + 0.8D, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, longWave ? 0.7F : 0.58F, longWave ? 1.08F : 1.15F);
   }

   private boolean canTargetEntity(LivingEntity living) {
      return living != this && living.isAlive() && !living.isSpectator() && living.getType() != this.getType() && !this.isAlliedTo(living);
   }

   private Vec3 getFacingVector() {
      return Vec3.directionFromRotation(0.0F, this.getYRot()).normalize();
   }

   private Vec3 getSideVector(Vec3 facing) {
      return new Vec3(-facing.z, 0.0D, facing.x);
   }

   private void resetLeapSlamState() {
      this.leapSlamFacing = Vec3.ZERO;
      this.leapSlamTravelDirection = Vec3.ZERO;
      this.leapSlamImpactCenter = Vec3.ZERO;
   }

   private void refreshLeapSlamAim(LivingEntity target, boolean forward, boolean committed) {
      Vec3 facing = this.getFacingVector();
      if (target != null && target.isAlive()) {
         double leadTicks = committed ? (this.phaseTwoActive ? 5.0D : 4.0D) : (this.phaseTwoActive ? 3.4D : 2.6D);
         Vec3 targetMotion = target.getDeltaMovement();
         Vec3 predictedTarget = target.position().add(targetMotion.x * leadTicks, 0.0D, targetMotion.z * leadTicks);
         Vec3 toTarget = new Vec3(predictedTarget.x - this.getX(), 0.0D, predictedTarget.z - this.getZ());
         double distanceSqr = toTarget.lengthSqr();
         if (distanceSqr > 1.0E-4D) {
            facing = toTarget.normalize();
         }
      }

      this.leapSlamFacing = facing;
      this.leapSlamTravelDirection = forward ? facing : facing.scale(-1.0D);
      this.faceDirection(facing);
   }

   private void stabilizeLeapTrajectory(boolean forward) {
      Vec3 direction = this.leapSlamTravelDirection.lengthSqr() > 1.0E-4D
         ? this.leapSlamTravelDirection.normalize()
         : (forward ? this.getFacingVector() : this.getFacingVector().scale(-1.0D));
      Vec3 motion = this.getDeltaMovement();
      double horizontalSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
      double minSpeed = forward ? 0.74D : 0.54D;
      double maxSpeed = forward ? (this.phaseTwoActive ? 1.62D : 1.46D) : (this.phaseTwoActive ? 1.14D : 1.0D);
      horizontalSpeed = Mth.clamp(horizontalSpeed, minSpeed, maxSpeed);
      this.setDeltaMovement(direction.x * horizontalSpeed, motion.y, direction.z * horizontalSpeed);
      this.faceDirection(this.getLeapFacingVector());
      this.hasImpulse = true;
   }

   private Vec3 getLeapFacingVector() {
      return this.leapSlamFacing.lengthSqr() > 1.0E-4D ? this.leapSlamFacing.normalize() : this.getFacingVector();
   }

   private void faceTargetForStrike(LivingEntity target, double maxDistanceSqr) {
      if (target == null || !target.isAlive()) {
         return;
      }

      Vec3 toTarget = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
      double distanceSqr = toTarget.lengthSqr();
      if (distanceSqr > 1.0E-4D && distanceSqr <= maxDistanceSqr) {
         this.faceDirection(toTarget.normalize());
      }
   }

   private void faceDirection(Vec3 direction) {
      Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
      if (horizontal.lengthSqr() < 1.0E-4D) {
         return;
      }

      horizontal = horizontal.normalize();
      float yaw = (float)(Mth.atan2(horizontal.z, horizontal.x) * 57.2957763671875D) - 90.0F;
      this.setYRot(yaw);
      this.yBodyRot = yaw;
      this.yHeadRot = yaw;
   }

   private void surgeTowardTarget(LivingEntity target, double strength) {
      Vec3 forward = target != null ? new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ()) : this.getFacingVector();
      if (forward.lengthSqr() < 1.0E-4D) {
         forward = this.getFacingVector();
      } else {
         forward = forward.normalize();
      }

      this.setDeltaMovement(forward.x * strength, 0.08D, forward.z * strength);
      this.hasImpulse = true;
   }

   private double getTargetForwardAlignment(LivingEntity target) {
      Vec3 horizontal = target.position().subtract(this.position());
      horizontal = new Vec3(horizontal.x, 0.0D, horizontal.z);
      return horizontal.lengthSqr() < 1.0E-4D ? 1.0D : horizontal.normalize().dot(this.getFacingVector());
   }

   private double getTargetSideAlignment(LivingEntity target) {
      Vec3 horizontal = target.position().subtract(this.position());
      horizontal = new Vec3(horizontal.x, 0.0D, horizontal.z);
      return horizontal.lengthSqr() < 1.0E-4D ? 0.0D : horizontal.normalize().dot(this.getSideVector(this.getFacingVector()));
   }

   private void spawnGroundPressureRing(Vec3 center, float radius, boolean heavy) {
      Level impactPos = this.level();
      if (impactPos instanceof ServerLevel serverLevel) {
         BlockPos var20 = TyrantTerrainHelper.findImpactSurface(this, center);
         BlockState impactState = TyrantTerrainHelper.findImpactState(this, center);
         BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, impactState);
         double y = (double)var20.getY() + 1.02D;
         int count = Math.max(16, Mth.ceil(radius * 10.0F));

         for(int i = 0; i < count; ++i) {
            double angle = (Math.PI * 2D) * (double)i / (double)count;
            double ringRadius = (double)radius * (0.88D + ((i + 1 & 1) == 0 ? 0.12D : -0.05D));
            double x = center.x + Math.cos(angle) * ringRadius;
            double z = center.z + Math.sin(angle) * ringRadius;
            serverLevel.sendParticles(debris, x, y, z, heavy ? 2 : 1, 0.06D, heavy ? 0.18D : 0.1D, 0.06D, 0.08D);
            if (i % 2 == 0) {
               serverLevel.sendParticles(ParticleTypes.CLOUD, x, y + 0.06D, z, 1, 0.04D, 0.06D, 0.04D, 0.0D);
            }

            if (heavy && i % 3 == 0) {
               serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.08D, z, 1, 0.02D, 0.03D, 0.02D, 0.006D);
            }
         }

         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, y + 0.12D, center.z, heavy ? 10 : 6, (double)radius * 0.16D, heavy ? 0.12D : 0.08D, (double)radius * 0.16D, 0.02D);
      }
   }

   private void spawnPursuitTelegraph(LivingEntity target) {
      Vec3 origin = this.position().add(0.0D, 0.15D, 0.0D);
      Vec3 facing = target != null ? new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ()) : this.getFacingVector();
      if (facing.lengthSqr() < 1.0E-4D) {
         facing = this.getFacingVector();
      } else {
         facing = facing.normalize();
      }

      this.spawnGroundPressureRing(origin, 2.3F, true);
      this.spawnPressureWavefront(origin, facing, 3.4D, false);
   }

   private void spawnBoneBreakerTelegraph(Vec3 center) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         Vec3 facing = this.getFacingVector();
         Vec3 side = this.getSideVector(facing);
         Vec3 strikeCenter = this.position().add(0.0D, 1.28D, 0.0D).add(facing.scale(2.15D)).add(side.scale(-1.32D));
         double groundY = (double)TyrantTerrainHelper.findImpactSurface(this, strikeCenter).getY() + 1.04D;
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), strikeCenter.x, strikeCenter.y, strikeCenter.z, 7, 0.28D, 0.32D, 0.28D, 0.016D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, strikeCenter.x, strikeCenter.y + 0.08D, strikeCenter.z, 9, 0.36D, 0.24D, 0.36D, 0.012D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, strikeCenter.x, groundY + 0.08D, strikeCenter.z, 8, 0.22D, 0.08D, 0.22D, 0.01D);
         serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, TyrantTerrainHelper.findImpactState(this, strikeCenter)), strikeCenter.x, groundY, strikeCenter.z, 7, 0.28D, 0.1D, 0.28D, 0.07D);
         this.spawnGroundFractureLine(strikeCenter, facing, -1, 3.2D, 1.25D, 3);
         this.spawnGroundPressureRing(center, 2.55F, true);
      }
   }

   private void spawnBoneBreakerAftershock(Vec3 center) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         Vec3 facing = this.getFacingVector();
         Vec3 side = this.getSideVector(facing);
         Vec3 strikeCenter = this.position().add(0.0D, 0.92D, 0.0D).add(facing.scale(2.35D)).add(side.scale(-1.42D));
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, strikeCenter).getY() + 1.04D;
         serverLevel.sendParticles(ParticleTypes.CRIT, strikeCenter.x, y + 0.42D, strikeCenter.z, 8, 0.38D, 0.26D, 0.38D, 0.18D);
         serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), strikeCenter.x, y + 0.35D, strikeCenter.z, 8, 0.46D, 0.16D, 0.46D, 0.018D);
         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, strikeCenter.x, y + 0.14D, strikeCenter.z, 7, 0.36D, 0.14D, 0.36D, 0.018D);
         this.spawnGroundPressureRing(center, 3.2F, true);
      }
   }

   private void spawnArmAftershock(boolean rightArm) {
      Vec3 facing = this.getFacingVector();
      Vec3 side = this.getSideVector(facing);
      Vec3 strikeCenter = this.position().add(0.0D, 1.05D, 0.0D).add(facing.scale(2.05D)).add(side.scale(rightArm ? 1.0D : -1.0D));
      this.spawnGroundPressureRing(strikeCenter, 2.1F, false);
      this.spawnGroundFractureLine(strikeCenter, facing, rightArm ? 1 : -1, 2.3D, 0.7D, 2);
   }

   private void spawnThroneStompTelegraph(Vec3 origin, boolean climax) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, origin).getY() + 1.04D;
         int count = climax ? 72 : 48;
         double radius = climax ? 5.35D : 4.0D;

         for(int i = 0; i < count; ++i) {
            double angle = Math.PI * 2D * (double)i / (double)count;
            double wobble = ((i * 31) % 9 - 4) * 0.055D;
            double ringRadius = radius + wobble;
            double x = origin.x + Math.cos(angle) * ringRadius;
            double z = origin.z + Math.sin(angle) * ringRadius;
            serverLevel.sendParticles(ModParticles.DREAD_MOTE.get(), x, y + 0.14D, z, 1, 0.03D, 0.04D, 0.03D, climax ? 0.012D : 0.008D);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.08D, z, 1, 0.02D, 0.04D, 0.02D, climax ? 0.012D : 0.008D);
            if (i % 4 == 0) {
               serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, TyrantTerrainHelper.findImpactState(this, new Vec3(x, y, z))), x, y, z, 1, 0.08D, 0.1D, 0.08D, 0.06D);
            }
         }

          serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), origin.x, y + 0.42D, origin.z, climax ? 12 : 7, climax ? 1.0D : 0.7D, climax ? 0.28D : 0.2D, climax ? 1.0D : 0.7D, 0.016D);
          serverLevel.sendParticles(ParticleTypes.SMOKE, origin.x, y + 0.18D, origin.z, climax ? 8 : 4, climax ? 0.8D : 0.55D, 0.12D, climax ? 0.8D : 0.55D, 0.01D);
         this.spawnGroundPressureRing(origin, climax ? 4.5F : 3.15F, climax);
      }
   }

   private void spawnTailTelegraph(double radius, boolean climax) {
      Level center = this.level();
      if (center instanceof ServerLevel serverLevel) {
         Vec3 var22 = this.position().add(0.0D, 0.2D, 0.0D);
         Vec3 facing = this.getFacingVector();
         Vec3 side = this.getSideVector(facing);
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, var22).getY() + 1.04D;
         int count = climax ? 42 : 28;

         for(int i = 0; i < count; ++i) {
            double progress = count <= 1 ? 0.5D : (double)i / (double)(count - 1);
            double spread = (progress - 0.5D) * Math.toRadians(250.0D);
            Vec3 direction = facing.scale(-Math.cos(spread)).add(side.scale(Math.sin(spread)));
            double x = var22.x + direction.x * radius * (climax ? 0.92D : 0.8D);
            double z = var22.z + direction.z * radius * (climax ? 0.92D : 0.8D);
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.08D, z, 1, 0.02D, 0.03D, 0.02D, climax ? 0.012D : 0.008D);
            if (i % 2 == 0) {
               serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.05D, z, 1, 0.02D, 0.04D, 0.02D, climax ? 0.012D : 0.007D);
            }

            if (i % 3 == 0) {
               BlockState state = TyrantTerrainHelper.findImpactState(this, new Vec3(x, y, z));
               serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 1, 0.05D, 0.08D, 0.05D, 0.04D);
            }
         }

         this.spawnGroundPressureRing(var22, climax ? 2.8F : 2.2F, climax);
      }
   }

   private void spawnTailAftershock(double radius) {
      Vec3 origin = this.position().add(0.0D, 0.95D, 0.0D);
      this.spawnGroundPressureRing(origin, 2.4F, false);
      this.spawnSweepArc(origin, this.getFacingVector(), radius - 0.75D);
   }

   private void spawnChestCollapseTelegraph(boolean longWave) {
      Level chest = this.level();
      if (chest instanceof ServerLevel serverLevel) {
         Vec3 var14 = this.position().add(0.0D, 2.15D, 0.0D);
         double radius = longWave ? 1.15D : 0.9D;
         int count = longWave ? 18 : 12;

         for(int i = 0; i < count; ++i) {
            double angle = (Math.PI * 2D) * (double)i / (double)count;
            double x = var14.x + Math.cos(angle) * radius;
            double z = var14.z + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, var14.y, z, 1, 0.02D, 0.02D, 0.02D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, var14.y + 0.04D, z, 1, 0.02D, 0.02D, 0.02D, 0.008D);
         }

         serverLevel.sendParticles(ParticleTypes.FLASH, var14.x, var14.y + 0.04D, var14.z, longWave ? 2 : 1, 0.0D, 0.0D, 0.0D, 0.0D);
         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, var14.x, var14.y - 0.05D, var14.z, longWave ? 8 : 5, 0.28D, 0.22D, 0.28D, 0.02D);
         this.spawnGroundPressureRing(this.position().add(0.0D, 0.1D, 0.0D), longWave ? 2.8F : 2.0F, false);
      }
   }

   private void spawnWaveAfterglow(float radius, boolean longWave) {
      Vec3 origin = this.position().add(0.0D, 1.0D, 0.0D);
      Vec3 facing = this.getFacingVector();
      this.spawnPressureWavefront(origin.add(facing.scale((double)radius * 0.25D)), facing, (double)(radius * (longWave ? 0.9F : 0.7F)), false);
      this.spawnRoarTelegraph(radius * (longWave ? 0.85F : 0.65F));
   }

   private void spawnTakeoffBurst(boolean forward) {
      Vec3 origin = this.position().add(0.0D, 0.12D, 0.0D);
      this.spawnGroundPressureRing(origin, forward ? 2.35F : 1.8F, true);
      this.spawnBlockShockwave(origin, forward ? 2.6F : 2.0F, forward ? 18 : 14, 0.18D, false);
   }

   private void spawnGroundFractureLine(Vec3 center, Vec3 facing, int sideSign, double length, double halfWidth, int rows) {
      Level side = this.level();
      if (side instanceof ServerLevel serverLevel) {
         Vec3 var24 = this.getSideVector(facing);
         int segments = Math.max(12, Mth.ceil(length * 6.0D));

         for(int row = 0; row < rows; ++row) {
            double rowProgress = rows <= 1 ? 0.5D : (double)row / (double)(rows - 1);
            double lateral = (rowProgress - 0.5D) * halfWidth * 2.0D + (double)sideSign * 0.28D;

            for(int i = 0; i < segments; ++i) {
               double progress = segments <= 1 ? 0.0D : (double)i / (double)(segments - 1);
               Vec3 point = center.add(facing.scale(progress * length - length * 0.18D)).add(var24.scale(lateral));
               double y = (double)TyrantTerrainHelper.findImpactSurface(this, point).getY() + 1.02D;
               BlockState state = TyrantTerrainHelper.findImpactState(this, point);
               serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), point.x, y, point.z, 1, 0.06D, 0.1D, 0.06D, 0.08D);
               if ((row + i & 1) == 0) {
                  serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, y + 0.08D, point.z, 1, 0.04D, 0.06D, 0.04D, 0.0D);
               }

               if (i % 3 == 0) {
                  serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, y + 0.05D, point.z, 1, 0.03D, 0.04D, 0.03D, 0.008D);
               }
            }
         }

      }
   }

   private void spawnPressureWavefront(Vec3 center, Vec3 facing, double radius, boolean longWave) {
      Level side = this.level();
      if (side instanceof ServerLevel serverLevel) {
         Vec3 var24 = this.getSideVector(facing);
         int depthLayers = longWave ? 4 : 3;

         for(int layer = 0; layer < depthLayers; ++layer) {
            double forwardDistance = radius * (0.4D + (double)layer * 0.18D);
            double width = 1.4D + forwardDistance * (longWave ? 0.9D : 0.72D);
            int samples = Math.max(16, Mth.ceil(width * 6.0D));

            for(int i = 0; i < samples; ++i) {
               double progress = samples <= 1 ? 0.5D : (double)i / (double)(samples - 1);
               double lateral = (progress - 0.5D) * width * 2.0D;
               Vec3 point = center.add(facing.scale(forwardDistance)).add(var24.scale(lateral));
               double y = (double)TyrantTerrainHelper.findImpactSurface(this, point).getY() + 1.04D;
               BlockState state = TyrantTerrainHelper.findImpactState(this, point);
               serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), point.x, y, point.z, 1, 0.05D, 0.12D + (double)layer * 0.03D, 0.05D, 0.08D);
               serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, y + 0.06D, point.z, 1, 0.03D, 0.04D, 0.03D, longWave ? 0.015D : 0.01D);
               if ((layer + i & 1) == 0) {
                  serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, point.x, y + 0.1D, point.z, 1, 0.03D, 0.04D, 0.03D, 0.008D);
               }

               if (i % 4 == 0) {
                  serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, y + 0.12D, point.z, 1, 0.04D, 0.06D, 0.04D, 0.0D);
               }
            }
         }

      }
   }

   private void spawnTailRendColumns(Vec3 center, Vec3 facing, double radius) {
      Level side = this.level();
      if (side instanceof ServerLevel serverLevel) {
         Vec3 var21 = this.getSideVector(facing);
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, center).getY() + 1.05D;
         byte columns = 10;

         for(int i = 0; i < columns; ++i) {
            double progress = columns <= 1 ? 0.5D : (double)i / (double)(columns - 1);
            double spread = (progress - 0.5D) * Math.toRadians(220.0D);
            Vec3 direction = facing.scale(-Math.cos(spread)).add(var21.scale(Math.sin(spread)));
            double x = center.x + direction.x * radius;
            double z = center.z + direction.z * radius;
            BlockState state = TyrantTerrainHelper.findImpactState(this, new Vec3(x, y, z));
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y + 0.08D, z, 2, 0.08D, 0.26D, 0.08D, 0.06D);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.22D, z, 3, 0.06D, 0.28D, 0.06D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.14D, z, 2, 0.04D, 0.22D, 0.04D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.2D, z, 2, 0.04D, 0.2D, 0.04D, 0.012D);
         }

      }
   }

   private void spawnArmTelegraph(boolean rightArm) {
      Level facing = this.level();
      if (facing instanceof ServerLevel serverLevel) {
         Vec3 var8 = this.getFacingVector();
         Vec3 side = this.getSideVector(var8);
         Vec3 point = this.position().add(0.0D, 1.7D, 0.0D).add(var8.scale(1.6D)).add(side.scale(rightArm ? 1.0D : -1.0D));
         double groundY = (double)TyrantTerrainHelper.findImpactSurface(this, point).getY() + 1.04D;
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, point.x, point.y, point.z, 24, 0.42D, 0.3D, 0.42D, 0.02D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y - 0.12D, point.z, 16, 0.32D, 0.1D, 0.32D, 0.008D);
         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y - 0.25D, point.z, 10, 0.28D, 0.14D, 0.28D, 0.01D);
         serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, TyrantTerrainHelper.findImpactState(this, point)), point.x, groundY, point.z, 4, 0.22D, 0.08D, 0.22D, 0.05D);
      }
   }

   private void spawnComboTelegraph(boolean firstHit) {
      Level origin = this.level();
      if (origin instanceof ServerLevel serverLevel) {
         Vec3 var4 = this.position().add(0.0D, 1.35D, 0.0D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, var4.x, var4.y, var4.z, firstHit ? 16 : 24, 0.65D, 0.35D, 0.65D, 0.025D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, var4.x, var4.y - 0.1D, var4.z, firstHit ? 10 : 16, 0.44D, 0.1D, 0.44D, 0.01D);
         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, var4.x, var4.y - 0.3D, var4.z, firstHit ? 8 : 12, 0.36D, 0.18D, 0.36D, 0.02D);
         this.spawnGroundPressureRing(this.position().add(0.0D, 0.1D, 0.0D), firstHit ? 1.8F : 2.35F, false);
      }
   }

   private void spawnLeapTelegraph(boolean forward) {
      Level origin = this.level();
      if (origin instanceof ServerLevel serverLevel) {
         Vec3 var4 = this.position().add(0.0D, 0.15D, 0.0D);
         serverLevel.sendParticles(ParticleTypes.POOF, var4.x, var4.y, var4.z, forward ? 28 : 18, 0.78D, 0.14D, 0.78D, 0.03D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, var4.x, var4.y + 0.25D, var4.z, forward ? 18 : 12, 0.62D, 0.22D, 0.62D, 0.015D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, var4.x, var4.y + 0.18D, var4.z, forward ? 16 : 10, 0.48D, 0.08D, 0.48D, 0.012D);
         this.spawnGroundPressureRing(var4, forward ? 2.8F : 2.1F, true);
      }
   }

   private void spawnDirectionalImpact(Vec3 center, Vec3 facing, int sideSign, double length, int count) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         Vec3 sideOffset = this.getSideVector(facing).scale(sideSign == 0 ? 0.0D : (double)sideSign * 0.6D);

         for(int i = 0; i < count; ++i) {
            double progress = (double)i / (double)Math.max(1, count - 1);
            Vec3 point = center.add(facing.scale(progress * length - 0.3D)).add(sideOffset);
            BlockState state = TyrantTerrainHelper.findImpactState(this, point);
            ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, state);
            double y = (double)TyrantTerrainHelper.findImpactSurface(this, point).getY() + 1.02D;
            serverLevel.sendParticles(debris, point.x, y, point.z, 3, 0.12D, 0.08D, 0.12D, 0.12D);
            if (i % 2 == 0) {
               serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, y + 0.12D, point.z, 1, 0.08D, 0.05D, 0.08D, 0.01D);
               serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, y + 0.08D, point.z, 1, 0.04D, 0.06D, 0.04D, 0.008D);
            }

            if (i == count / 2) {
               serverLevel.sendParticles(ParticleTypes.FLASH, point.x, y + 0.16D, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
         }

      }
   }

   private void spawnBlockShockwave(Vec3 center, float radius, int count, double verticalSpread, boolean souls) {
      Level impactPos = this.level();
      if (impactPos instanceof ServerLevel serverLevel) {
         BlockPos var26 = TyrantTerrainHelper.findImpactSurface(this, center);
         BlockState impactState = TyrantTerrainHelper.findImpactState(this, center);
         BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, impactState);
         double y = (double)var26.getY() + 1.02D;
         int effectiveCount = Math.max(8, Math.min(count, souls ? 42 : 28));

         for(int i = 0; i < effectiveCount; ++i) {
            double angle = (Math.PI * 2D) * (double)i / (double)effectiveCount;
            double ringRadius = (double)radius * (0.75D + 0.25D * ((i & 1) == 0 ? 1.0D : 0.0D));
            double x = center.x + Math.cos(angle) * ringRadius;
            double z = center.z + Math.sin(angle) * ringRadius;
            double dx = Math.cos(angle) * 0.12D;
            double dz = Math.sin(angle) * 0.12D;
            serverLevel.sendParticles(debris, x, y, z, 1, dx, verticalSpread, dz, 0.1D);
            if (souls && i % 4 == 0) {
               serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.12D, z, 1, 0.03D, 0.03D, 0.03D, 0.01D);
            } else if (souls && i % 4 == 2) {
               serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.1D, z, 1, 0.03D, 0.05D, 0.03D, 0.008D);
            }

            if (i % 6 == 0) {
               serverLevel.sendParticles(ParticleTypes.CLOUD, x, y + 0.05D, z, 1, 0.04D, 0.03D, 0.04D, 0.0D);
            }
         }

         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, y + 0.16D, center.z, Math.max(4, effectiveCount / 5), (double)radius * 0.16D, 0.16D, (double)radius * 0.16D, 0.026D);
         serverLevel.sendParticles(ParticleTypes.EXPLOSION, center.x, y + 0.12D, center.z, Math.max(1, effectiveCount / 18), (double)radius * 0.06D, 0.03D, (double)radius * 0.06D, 0.0D);
      }
   }

   private void spawnWaveRingGround(Vec3 center, Vec3 facing, float radius, boolean longWave) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         BlockPos surfacePos = TyrantTerrainHelper.findImpactSurface(this, center);
         double y = (double)surfacePos.getY() + 1.08D;
         int count = Math.max(24, Mth.ceil(radius * 9.0F));
         Vec3 side = this.getSideVector(facing);

         for(int layer = 0; layer < 3; ++layer) {
            double layerRadius = (double)radius - (double)layer * 0.65D;

            for(int i = 0; i < count; ++i) {
               double progress = count <= 1 ? 0.5D : (double)i / (double)(count - 1);
               double spread = (progress - 0.5D) * Math.toRadians(longWave ? 185.0D : 156.0D);
               Vec3 direction = facing.scale(Math.cos(spread)).add(side.scale(Math.sin(spread)));
               double x = center.x + direction.x * layerRadius;
               double z = center.z + direction.z * layerRadius;
               serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + (double)layer * 0.05D, z, 1, 0.02D, 0.06D, 0.02D, longWave ? 0.024D : 0.012D);
               if (i % 2 == 0) {
                  serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.08D + (double)layer * 0.05D, z, 1, 0.02D, 0.02D, 0.02D, 0.01D);
               }

               if (i % 4 == 0) {
                  serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.12D, z, 1, 0.03D, 0.04D, 0.03D, 0.0D);
               }

               if (i % 3 == 0) {
                  BlockState state = TyrantTerrainHelper.findImpactState(this, new Vec3(x, y, z));
                  serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 1, 0.04D, 0.08D, 0.04D, 0.04D);
               }
            }
         }

      }
   }

   private void spawnRoarTelegraph(float radius) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         BlockPos surfacePos = TyrantTerrainHelper.findImpactSurface(this, this.position());
         double y = (double)surfacePos.getY() + 1.05D;
         int count = Math.max(18, Mth.ceil(radius * 6.0F));
         Vec3 facing = this.getFacingVector();
         Vec3 side = this.getSideVector(facing);

         for(int i = 0; i < count; ++i) {
            double progress = count <= 1 ? 0.5D : (double)i / (double)(count - 1);
            double spread = (progress - 0.5D) * Math.toRadians(140.0D);
            Vec3 direction = facing.scale(Math.cos(spread)).add(side.scale(Math.sin(spread)));
            double x = this.getX() + direction.x * (double)radius;
            double z = this.getZ() + direction.z * (double)radius;
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 1, 0.01D, 0.03D, 0.01D, 0.005D);
            if (i % 3 == 0) {
               serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.05D, z, 1, 0.01D, 0.03D, 0.01D, 0.005D);
            }

            if (i % 4 == 0) {
               BlockState state = TyrantTerrainHelper.findImpactState(this, new Vec3(x, y, z));
               serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 1, 0.03D, 0.06D, 0.03D, 0.03D);
            }
         }

      }
   }

   private void spawnExecutionChains(LivingEntity target) {
      if (!(this.level() instanceof ServerLevel serverLevel) || target == null || !target.isAlive()) {
         return;
      }

      Vec3 start = this.position().add(0.0D, 2.05D, 0.0D);
      Vec3 end = target.position().add(0.0D, 0.95D, 0.0D);
      for(int i = 0; i < 9; ++i) {
         double t = (double)i / 8.0D;
         Vec3 point = start.lerp(end, t);
         serverLevel.sendParticles(i % 2 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y, point.z, 1, 0.035D, 0.035D, 0.035D, 0.004D);
         if (i % 3 == 0) {
            serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), point.x, point.y, point.z, 1, 0.025D, 0.025D, 0.025D, 0.006D);
         }
      }
   }

   private void spawnExecutionCrown(LivingEntity target) {
      if (!(this.level() instanceof ServerLevel serverLevel) || target == null || !target.isAlive()) {
         return;
      }

      Vec3 center = target.position().add(0.0D, target.getBbHeight() + 0.35D, 0.0D);
      for(int i = 0; i < 18; ++i) {
         double angle = (Math.PI * 2D) * (double)i / 18.0D;
         double radius = 0.72D + (i % 2 == 0 ? 0.08D : -0.04D);
         double x = center.x + Math.cos(angle) * radius;
         double z = center.z + Math.sin(angle) * radius;
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, center.y, z, 1, 0.015D, 0.04D, 0.015D, 0.004D);
         if (i % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, center.y + 0.06D, z, 1, 0.02D, 0.04D, 0.02D, 0.006D);
         }
      }

      serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), center.x, center.y - 0.18D, center.z, 8, 0.28D, 0.1D, 0.28D, 0.012D);
   }

   private void spawnExecutionImpact(Vec3 center) {
      if (!(this.level() instanceof ServerLevel serverLevel)) {
         return;
      }

      double y = (double)TyrantTerrainHelper.findImpactSurface(this, center).getY() + 1.06D;
      this.spawnGroundPressureRing(center, this.phaseTwoActive ? 4.4F : 3.6F, true);
      this.spawnBlockShockwave(center, this.phaseTwoActive ? 4.8F : 4.0F, this.phaseTwoActive ? 34 : 26, 0.22D, true);
      serverLevel.sendParticles(ParticleTypes.FLASH, center.x, y + 0.2D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
      serverLevel.sendParticles(ParticleTypes.EXPLOSION, center.x, y + 0.1D, center.z, 4, 0.16D, 0.04D, 0.16D, 0.0D);
      serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, y + 0.18D, center.z, 22, 0.5D, 0.18D, 0.5D, 0.026D);
      serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, center.x, y + 0.35D, center.z, 16, 0.38D, 0.24D, 0.38D, 0.018D);
      serverLevel.sendParticles(ModParticles.FEAR_STATIC.get(), center.x, y + 0.52D, center.z, 14, 0.4D, 0.2D, 0.4D, 0.02D);
      serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, TyrantTerrainHelper.findImpactState(this, center)), center.x, y, center.z, 20, 0.42D, 0.12D, 0.42D, 0.09D);
   }

   private void triggerScreenShake(Vec3 origin, float radius, float strength, int durationTicks) {
      Level var6 = this.level();
      if (var6 instanceof ServerLevel serverLevel) {
         PacketDistributor.sendToPlayersNear(serverLevel, (ServerPlayer)null, origin.x, origin.y, origin.z, (double)radius + 12.0D, new TyrantScreenShakePayload(origin.x, origin.y, origin.z, radius, strength, durationTicks), new CustomPacketPayload[0]);
      }

   }

   private void playImpactSound(Vec3 origin, float volume, float pitch) {
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, volume, pitch);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, volume * 0.72F, pitch + 0.06F);
      this.level().playSound((Player)null, origin.x, origin.y + 0.4D, origin.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, volume * 0.56F, Math.max(0.18F, pitch * 0.78F));
      this.level().playSound((Player)null, origin.x, origin.y + 0.2D, origin.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, volume * 0.42F, Math.max(0.2F, pitch * 0.72F));
   }

   private void spawnChestChargeParticles() {
      Level chest = this.level();
      if (chest instanceof ServerLevel serverLevel) {
         Vec3 var3 = this.position().add(0.0D, 2.15D, 0.0D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, var3.x, var3.y, var3.z, 9, 0.46D, 0.34D, 0.46D, 0.014D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, var3.x, var3.y, var3.z, 6, 0.3D, 0.22D, 0.3D, 0.008D);
         serverLevel.sendParticles(ParticleTypes.SOUL, var3.x, var3.y + 0.1D, var3.z, 3, 0.18D, 0.14D, 0.18D, 0.0D);
         serverLevel.sendParticles(ParticleTypes.SMOKE, var3.x, var3.y - 0.15D, var3.z, 4, 0.24D, 0.12D, 0.24D, 0.01D);
      }
   }

   private void spawnDeathLeakParticles() {
      Level chest = this.level();
      if (chest instanceof ServerLevel serverLevel) {
         Vec3 var3 = this.position().add(0.0D, 2.15D, 0.0D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, var3.x, var3.y, var3.z, 12, 0.55D, 0.5D, 0.55D, 0.015D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, var3.x, var3.y, var3.z, 8, 0.3D, 0.3D, 0.3D, 0.01D);
         serverLevel.sendParticles(ParticleTypes.SMOKE, var3.x, var3.y - 0.35D, var3.z, 6, 0.4D, 0.2D, 0.4D, 0.02D);
      }
   }

   private void spawnDeathCollapse() {
      Level chest = this.level();
      if (chest instanceof ServerLevel serverLevel) {
         Vec3 var3 = this.position().add(0.0D, 1.45D, 0.0D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, var3.x, var3.y, var3.z, 18, 0.48D, 0.34D, 0.48D, -0.008D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, var3.x, var3.y, var3.z, 10, 0.26D, 0.22D, 0.26D, -0.004D);
         serverLevel.sendParticles(ParticleTypes.SMOKE, var3.x, var3.y - 0.12D, var3.z, 8, 0.3D, 0.18D, 0.3D, 0.01D);
         this.triggerScreenShake(var3, 16.0F, 0.42F, 8);
         this.level().playSound((Player)null, var3.x, var3.y, var3.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.85F, 0.65F);
      }
   }

   private void spawnDeathBurst(boolean openingBurst) {
      Level chest = this.level();
      if (chest instanceof ServerLevel serverLevel) {
         Vec3 var8 = this.position().add(0.0D, 1.15D, 0.0D);
         double radius = openingBurst ? 3.2D : 4.0D;
         int soulCount = openingBurst ? 22 : 36;
         int flameCount = openingBurst ? 12 : 20;
         this.spawnBlockShockwave(var8, (float)radius, openingBurst ? 26 : 38, openingBurst ? 0.18D : 0.24D, true);
         this.spawnEpicImpactBurst(var8, (float)radius + 0.6F, true);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, var8.x, var8.y + 1.0D, var8.z, soulCount, 0.75D, 0.6D, 0.75D, 0.03D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, var8.x, var8.y + 1.0D, var8.z, flameCount, 0.45D, 0.45D, 0.45D, 0.02D);
         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, var8.x, var8.y + 0.55D, var8.z, openingBurst ? 12 : 22, 0.65D, 0.35D, 0.65D, 0.03D);
         this.triggerScreenShake(var8, openingBurst ? 18.0F : 24.0F, openingBurst ? 0.95F : 1.25F, openingBurst ? 10 : 16);
         this.level().playSound((Player)null, var8.x, var8.y, var8.z, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, openingBurst ? 0.7F : 1.0F, openingBurst ? 0.85F : 0.72F);
      }
   }

   private void spawnEpicImpactBurst(Vec3 center, float radius, boolean heavy) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         BlockPos surfacePos = TyrantTerrainHelper.findImpactSurface(this, center);
         double y = (double)surfacePos.getY() + 1.05D;
         serverLevel.sendParticles(ParticleTypes.FLASH, center.x, y + 0.18D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
         serverLevel.sendParticles(ParticleTypes.EXPLOSION, center.x, y + 0.12D, center.z, heavy ? 5 : 2, (double)radius * 0.12D, 0.06D, (double)radius * 0.12D, 0.0D);
         if (heavy) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, y + 0.22D, center.z, 1, (double)radius * 0.06D, 0.03D, (double)radius * 0.06D, 0.0D);
         }

         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, y + 0.2D, center.z, heavy ? 12 : 7, (double)radius * 0.18D, heavy ? 0.26D : 0.16D, (double)radius * 0.18D, 0.026D);
         serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, y + 0.1D, center.z, heavy ? 13 : 7, (double)radius * 0.14D, heavy ? 0.22D : 0.12D, (double)radius * 0.14D, 0.014D);
         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, center.x, y + 0.16D, center.z, heavy ? 10 : 5, (double)radius * 0.16D, heavy ? 0.24D : 0.14D, (double)radius * 0.16D, 0.018D);
      }
   }

   private void spawnCraterRubble(Vec3 center, float radius, boolean heavy) {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         int samples = heavy ? 46 : 30;

         for(int i = 0; i < samples; ++i) {
            double angle = this.random.nextDouble() * (Math.PI * 2D);
            double distance = Math.sqrt(this.random.nextDouble()) * (double)radius;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            Vec3 point = new Vec3(x, center.y, z);
            BlockPos surfacePos = TyrantTerrainHelper.findImpactSurface(this, point);
            double y = (double)surfacePos.getY() + 1.08D;
            BlockState state = TyrantTerrainHelper.findImpactState(this, point);
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, heavy ? 2 : 1, 0.12D, 0.14D, 0.12D, 0.07D);
            if (i % 2 == 0) {
               serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.1D, z, 1, 0.1D, 0.18D, 0.1D, 0.018D);
            }

            if (i % 3 == 0) {
               serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.08D, z, 1, 0.05D, 0.12D, 0.05D, 0.012D);
            }
         }

         serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, (double)TyrantTerrainHelper.findImpactSurface(this, center).getY() + 1.18D, center.z, heavy ? 16 : 10, (double)radius * 0.32D, 0.18D, (double)radius * 0.32D, 0.024D);
      }
   }

   private void rendSweepTerrain(Vec3 origin, double radius, boolean heavy) {
      float outerRadius = (float)radius + (heavy ? 0.95F : 0.65F);
      float innerRadius = heavy ? 1.2F : 1.45F;
      TyrantTerrainHelper.tearTerrainRing(this, origin, innerRadius, outerRadius, heavy ? 3.6F : 2.9F, heavy ? 0.66F : 0.48F);
      this.spawnGroundPressureRing(origin, outerRadius * 0.82F, heavy);
   }

   private void spawnSweepArc(Vec3 center, Vec3 facing, double radius) {
      Level side = this.level();
      if (side instanceof ServerLevel serverLevel) {
         Vec3 var24 = this.getSideVector(facing);
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, center).getY() + 1.08D;
         byte count = 54;

         for(int layer = 0; layer < 3; ++layer) {
            double layerRadius = radius - (double)layer * 0.55D;

            for(int i = 0; i < count; ++i) {
               double progress = count <= 1 ? 0.5D : (double)i / (double)(count - 1);
               double spread = (progress - 0.5D) * Math.toRadians(252.0D);
               Vec3 direction = facing.scale(-Math.cos(spread)).add(var24.scale(Math.sin(spread)));
               double x = center.x + direction.x * layerRadius;
               double z = center.z + direction.z * layerRadius;
               BlockState state = TyrantTerrainHelper.findImpactState(this, new Vec3(x, y, z));
               serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y + (double)layer * 0.02D, z, 1, 0.05D, 0.1D + (double)layer * 0.04D, 0.05D, 0.06D);
               serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.02D + (double)layer * 0.05D, z, 1, 0.03D, 0.05D, 0.03D, 0.01D);
               if ((i + layer & 1) == 0) {
                  serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.08D + (double)layer * 0.05D, z, 1, 0.03D, 0.04D, 0.03D, 0.01D);
               }

               if (i % 5 == 0) {
                  serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.12D + (double)layer * 0.03D, z, 1, 0.04D, 0.08D, 0.04D, 0.0D);
               }
            }
         }

      }
   }

   private void spawnLandingColumns(Vec3 center, double radius) {
      Level y = this.level();
      if (y instanceof ServerLevel serverLevel) {
         double var16 = (double)TyrantTerrainHelper.findImpactSurface(this, center).getY() + 1.05D;
         byte columns = 12;

         for(int i = 0; i < columns; ++i) {
            double angle = (Math.PI * 2D) * (double)i / (double)columns;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            BlockState state = TyrantTerrainHelper.findImpactState(this, new Vec3(x, var16, z));
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, var16 + 0.08D, z, 2, 0.08D, 0.24D, 0.08D, 0.06D);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, var16 + 0.25D, z, 4, 0.08D, 0.42D, 0.08D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, var16 + 0.18D, z, 5, 0.05D, 0.34D, 0.05D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, var16 + 0.2D, z, 4, 0.05D, 0.28D, 0.05D, 0.015D);
         }

      }
   }

   private void spawnIntroRoarSurge(boolean climax) {
      Vec3 origin = this.position().add(0.0D, 1.05D, 0.0D);
      Vec3 facing = this.getFacingVector();
      this.spawnBlockShockwave(origin, climax ? 5.4F : 3.8F, climax ? 42 : 26, climax ? 0.22D : 0.15D, true);
      this.spawnWaveRingGround(origin, facing, climax ? 6.0F : 4.0F, climax);
      this.spawnEpicImpactBurst(origin.add(0.0D, 0.15D, 0.0D), climax ? 4.2F : 2.8F, climax);
   }

   private void spawnPhaseShiftSurge(boolean climax) {
      Vec3 origin = this.position().add(0.0D, 1.05D, 0.0D);
      Vec3 facing = this.getFacingVector();
      float radius = climax ? 8.5F : 6.2F;
      this.spawnBlockShockwave(origin, radius, climax ? 84 : 62, climax ? 0.42D : 0.32D, true);
      this.spawnWaveRingGround(origin, facing, radius + 0.7F, true);
      this.spawnPressureWavefront(origin, facing, (double)(radius + 1.6F), true);
      this.spawnLandingColumns(origin, (double)radius * 0.9D);
      this.spawnEpicImpactBurst(origin, radius + 1.2F, true);
      TyrantTerrainHelper.tearTerrain(this, origin, climax ? 5.6F : 3.9F, climax ? IMPACT_BREAK_SPEED : 3.4F, climax ? 0.86F : 0.52F);
      this.playImpactSound(origin, climax ? 1.95F : 1.36F, climax ? 0.38F : 0.54F);
   }

   private void tickPhaseTwoDomain() {
      Level level = this.level();
      if (level instanceof ServerLevel serverLevel) {
         if (this.combatDisplayTicks > 0) {
            LivingEntity target = this.getTarget();
            Vec3 origin = this.position().add(0.0D, 0.2D, 0.0D);
            if (this.tickCount % 8 == 0) {
               this.spawnDomainAmbience(serverLevel, origin, target);
            }

            if (this.tickCount % 32 == 0) {
               this.emitDomainPulse(serverLevel, origin);
            }

            return;
         }
      }

   }

   private void spawnDomainAmbience(ServerLevel serverLevel, Vec3 origin, LivingEntity target) {
      double radius = 7.5D;
      int count = 8;

      for(int i = 0; i < count; ++i) {
         double angle = (Math.PI * 2D) * (double)i / (double)count + (double)this.tickCount * 0.045D;
         double ringRadius = radius * (0.72D + (double)(i % 3) * 0.09D);
         double x = origin.x + Math.cos(angle) * ringRadius;
         double z = origin.z + Math.sin(angle) * ringRadius;
         double y = (double)TyrantTerrainHelper.findImpactSurface(this, new Vec3(x, origin.y, z)).getY() + 1.03D;
         BlockState state = TyrantTerrainHelper.findImpactState(this, new Vec3(x, origin.y, z));
         if (i % 2 == 0) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, 1, 0.04D, 0.05D, 0.04D, 0.03D);
         }

         serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.06D, z, 1, 0.03D, 0.04D, 0.03D, 0.006D);
         if (i % 2 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.08D, z, 1, 0.03D, 0.06D, 0.03D, 0.005D);
         }

         if (i % 4 == 0) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, x, y + 0.1D, z, 1, 0.04D, 0.06D, 0.04D, 0.0D);
         }
      }

      if (target != null && target.isAlive()) {
         Vec3 toTarget = target.position().subtract(this.position());
         Vec3 flat = new Vec3(toTarget.x, 0.0D, toTarget.z);
         if (flat.lengthSqr() > 1.0E-4D) {
            flat = flat.normalize();

            for(int i = 0; i < 3; ++i) {
               double step = 1.9D + (double)i * 1.65D;
               Vec3 point = origin.add(flat.scale(step));
               double y = (double)TyrantTerrainHelper.findImpactSurface(this, point).getY() + 1.05D;
               serverLevel.sendParticles(i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SCULK_SOUL, point.x, y + 0.04D, point.z, 1, 0.03D, 0.05D, 0.03D, 0.006D);
            }
         }
      }

   }

   private void emitDomainPulse(ServerLevel serverLevel, Vec3 origin) {
      double radius = 9.0D;
      this.spawnGroundPressureRing(origin, 3.4F, true);
      this.spawnBlockShockwave(origin, 5.8F, 26, 0.2D, true);
      this.spawnWaveRingGround(origin, this.getFacingVector(), 5.4F, false);
      this.triggerScreenShake(origin.add(0.0D, 0.9D, 0.0D), 18.0F, 0.62F, 8);
      this.level().playSound((Player)null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.HOSTILE, 0.85F, 0.58F);

      for(LivingEntity victim : this.level().getEntitiesOfClass(LivingEntity.class, (new AABB(origin, origin)).inflate(radius, 2.6D, radius), living -> TyrantDamageHelper.canDamage(this, living))) {
         victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 36, 0, true, true, true));
         victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 24, 0, true, false, true));
      }

   }

   private void updateBossEvent() {
      this.bossEvent.setName(Component.translatable("bossbar.tyrant.tyrant", this.getDisplayName()));
      this.bossEvent.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
      float healthRatio = this.getMaxHealth() <= 0.0F ? 0.0F : this.getHealth() / this.getMaxHealth();
      if (this.phaseTwoActive) {
         this.bossEvent.setColor(healthRatio > 0.18F ? BossBarColor.RED : BossBarColor.PURPLE);
      } else if (healthRatio > 0.66F) {
         this.bossEvent.setColor(BossBarColor.BLUE);
      } else if (healthRatio > 0.33F) {
         this.bossEvent.setColor(BossBarColor.PURPLE);
      } else {
         this.bossEvent.setColor(BossBarColor.RED);
      }

      boolean shouldShow = this.isAlive()
            && (this.getCurrentAction().isActive() || this.getTarget() != null || this.combatDisplayTicks > 0 || this.fearDomainDisplayTicks > 0);
      this.bossEvent.setVisible(shouldShow);
   }

   public boolean doHurtTarget(Entity entity) {
      return false;
   }

   public boolean hurt(DamageSource source, float amount) {
      if (!this.level().isClientSide() && source.getEntity() instanceof Player player) {
         if (this.commandController.onTyrantAttackedByPlayer(this, player)) {
            return false;
         }
      }

      return !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_FALL) ? super.hurt(source, amount) : false;
   }

   public boolean hurtPart(TyrantPart part, DamageSource source, float amount) {
      return this.hurt(source, amount);
   }

   protected void tickDeath() {
      this.getNavigation().stop();
      if (this.deathAnchorPos == null) {
         BlockPos surface = TyrantTerrainHelper.findImpactSurface(this, this.position());
         this.deathAnchorPos = new Vec3(this.getX(), (double)surface.getY() - 1.99D, this.getZ());
         this.deathLockedYaw = this.getYRot();
         this.deathLockedPitch = this.getXRot();
      }

      float lockedYaw = this.deathLockedYaw != null ? this.deathLockedYaw : this.getYRot();
      float lockedPitch = this.deathLockedPitch != null ? this.deathLockedPitch : this.getXRot();
      this.setNoGravity(true);
      this.setDeltaMovement(Vec3.ZERO);
      this.setPos(this.deathAnchorPos.x, this.deathAnchorPos.y, this.deathAnchorPos.z);
      this.xo = this.deathAnchorPos.x;
      this.yo = this.deathAnchorPos.y;
      this.zo = this.deathAnchorPos.z;
      this.setYRot(lockedYaw);
      this.setXRot(lockedPitch);
      this.yRotO = lockedYaw;
      this.xRotO = lockedPitch;
      this.yHeadRot = lockedYaw;
      this.yHeadRotO = lockedYaw;
      this.yBodyRot = lockedYaw;
      this.yBodyRotO = lockedYaw;
      this.zza = 0.0F;
      this.xxa = 0.0F;
      this.yya = 0.0F;
      this.setTarget((LivingEntity)null);
      this.hurtTime = 0;
      this.hurtDuration = 0;
      this.bossEvent.setVisible(false);
      if (this.getCurrentAction() != TyrantAction.DEATH) {
         this.entityData.set(ACTION, TyrantAction.DEATH.id());
         this.actionTick = 0;
         this.actionStep = 0;
      }

      ++this.deathTime;
      if (this.deathTime == 1) {
         this.level().playSound((Player)null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0F, 0.7F);
         this.spawnDeathCollapse();
      }

      if (this.deathTime == 54) {
         this.spawnDeathBurst(true);
      }

      if (this.deathTime >= 12 && this.deathTime % 6 == 0) {
         this.spawnDeathLeakParticles();
      }

      if (this.deathTime == TyrantAction.DEATH.durationTicks() - 4) {
         this.spawnDeathBurst(false);
      }

      if (this.deathTime >= TyrantAction.DEATH.durationTicks()) {
         this.bossEvent.removeAllPlayers();
         this.level().broadcastEntityEvent(this, (byte)60);
         this.remove(RemovalReason.KILLED);
      }

   }

   public void startSeenByPlayer(ServerPlayer serverPlayer) {
      super.startSeenByPlayer(serverPlayer);
      this.bossEvent.addPlayer(serverPlayer);
   }

   public void stopSeenByPlayer(ServerPlayer serverPlayer) {
      super.stopSeenByPlayer(serverPlayer);
      this.bossEvent.removePlayer(serverPlayer);
   }

   public void addAdditionalSaveData(CompoundTag compound) {
      super.addAdditionalSaveData(compound);
      compound.putBoolean("IntroPlayed", this.introPlayed);
      compound.putBoolean("PhaseShiftPlayed", this.phaseShiftPlayed);
      compound.putBoolean("PhaseTwoActive", this.phaseTwoActive);
      compound.putInt("LeapSlamCooldown", this.leapSlamCooldown);
      this.commandController.addAdditionalSaveData(compound);
   }

   public void readAdditionalSaveData(CompoundTag compound) {
      super.readAdditionalSaveData(compound);
      this.introPlayed = compound.getBoolean("IntroPlayed");
      this.phaseShiftPlayed = compound.getBoolean("PhaseShiftPlayed");
      this.phaseTwoActive = compound.getBoolean("PhaseTwoActive");
      this.leapSlamCooldown = compound.getInt("LeapSlamCooldown");
      this.commandController.readAdditionalSaveData(compound);
      this.bossEvent.setName(Component.translatable("bossbar.tyrant.tyrant", this.getDisplayName()));
   }

   public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
      return false;
   }

   public float maxUpStep() {
      return 1.25F;
   }

   protected SoundEvent getAmbientSound() {
      return SoundEvents.WARDEN_AMBIENT;
   }

   protected SoundEvent getHurtSound(DamageSource damageSource) {
      return SoundEvents.WARDEN_HURT;
   }

   protected SoundEvent getDeathSound() {
      return SoundEvents.WARDEN_DEATH;
   }

   protected void playStepSound(BlockPos pos, BlockState blockState) {
      if (this.tickCount - this.lastStepSoundTick >= (this.phaseTwoActive ? 8 : 10)) {
         this.lastStepSoundTick = this.tickCount;
         this.playSound(SoundEvents.RAVAGER_STEP, this.phaseTwoActive ? 1.42F : 1.2F, this.phaseTwoActive ? 0.34F : 0.42F);
         this.level().playSound((Player)null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, this.phaseTwoActive ? 1.08F : 0.92F, this.phaseTwoActive ? 0.44F : 0.52F);
         this.level().playSound((Player)null, this.getX(), this.getY() + 0.2D, this.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, this.phaseTwoActive ? 0.42F : 0.3F, this.phaseTwoActive ? 0.46F : 0.54F);
         if (!this.level().isClientSide() && this.onGround()) {
            Vec3 stepOrigin = this.position().add(0.0D, 0.15D, 0.0D);
            this.spawnGroundPressureRing(stepOrigin, this.phaseTwoActive ? 1.8F : 1.4F, this.phaseTwoActive);
            this.spawnBlockShockwave(stepOrigin, this.phaseTwoActive ? 2.05F : 1.65F, this.phaseTwoActive ? 14 : 10, this.phaseTwoActive ? 0.12D : 0.08D, false);
            TyrantTerrainHelper.crushStepTerrain(this, stepOrigin, STEP_BREAK_SPEED, this.phaseTwoActive);
            this.triggerScreenShake(stepOrigin, this.phaseTwoActive ? 14.0F : 11.0F, this.phaseTwoActive ? 0.34F : 0.22F, this.phaseTwoActive ? 5 : 4);
         }

      }
   }

   public TyrantAction getCurrentAction() {
      return TyrantAction.byId(this.entityData.get(ACTION));
   }

   public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
      controllers.add(new AnimationController<>(this, "main_controller", 2, this::mainAnimationController));
   }

   private PlayState mainAnimationController(AnimationState<TyrantEntity> state) {
      TyrantAction action = this.getCurrentAction();
      if (action == TyrantAction.DEATH) {
         return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(action.animationName()));
      } else if (action.isActive()) {
         return state.setAndContinue(RawAnimation.begin().thenPlay(action.animationName()));
      } else {
         return state.isMoving() ? state.setAndContinue(WALK_ANIMATION) : state.setAndContinue(IDLE_ANIMATION);
      }
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.animationCache;
   }

   public double getTick(Object object) {
      return (double)this.tickCount;
   }

   public boolean removeWhenFarAway(double distanceToClosestPlayer) {
      return false;
   }

   public static boolean canSpawn(EntityType<TyrantEntity> entityType, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
      return Monster.checkMonsterSpawnRules(entityType, level, spawnType, pos, random);
   }
}
