package com.eddyon.tyrant.common.entity.tyrant;

import com.eddyon.tyrant.common.entity.TyrantAction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

public final class TyrantCombatDirector {
   private static final float PHASE_TWO_HEALTH_THRESHOLD = 0.5F;
   private static final double CLOSE_RANGE_SQR = 9.0D;
   private static final double MELEE_RANGE_SQR = 20.25D;
   private static final double WAVE_MIN_RANGE_SQR = 9.0D;
   private static final double WAVE_MAX_RANGE_SQR = 81.0D;
   private static final double LEAP_MIN_RANGE_SQR = 9.0D;
   private static final double LEAP_MAX_RANGE_SQR = 144.0D;
   private static final double MAX_MELEE_VERTICAL_GAP = 3.2D;
   private static final double MAX_WAVE_VERTICAL_GAP = 2.5D;
   private static final double MAX_LEAP_VERTICAL_GAP = 5.0D;
   private static final TyrantAction[] COMBAT_ACTIONS = new TyrantAction[]{
      TyrantAction.ATTACK_RIGHT,
      TyrantAction.ATTACK_LEFT,
      TyrantAction.DOUBLE_SLAM_TAIL,
      TyrantAction.ROAR_WAVE_SHORT,
      TyrantAction.ROAR_WAVE_LONG,
      TyrantAction.LEAP_SLAM_FORWARD,
      TyrantAction.LEAP_SLAM_BACKWARD
   };

   private TyrantCombatDirector() {
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 3333.0D)
         .add(Attributes.ATTACK_DAMAGE, 31.0D)
         .add(Attributes.ARMOR, 20.0D)
         .add(Attributes.ARMOR_TOUGHNESS, 8.0D)
         .add(Attributes.MOVEMENT_SPEED, 0.255D)
         .add(Attributes.FOLLOW_RANGE, 40.0D)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
         .add(Attributes.ATTACK_KNOCKBACK, 1.9D);
   }

   public static boolean shouldEnterPhaseTwo(float health, float maxHealth) {
      return health <= maxHealth * PHASE_TWO_HEALTH_THRESHOLD;
   }

   public static boolean shouldHoldPosition(TyrantCombatContext context) {
      return context.horizontalDistanceSqr() <= CLOSE_RANGE_SQR && context.hasSight() && context.verticalGap() <= 2.4D;
   }

   public static boolean shouldJumpToClose(TyrantCombatContext context) {
      return context.verticalGap() > 1.15D && context.horizontalDistanceSqr() <= 36.0D;
   }

   public static double getChaseSpeed(TyrantCombatContext context) {
      double speed;
      if (!context.hasSight()) {
         speed = 1.24D;
      } else if (context.horizontalDistanceSqr() > LEAP_MAX_RANGE_SQR) {
         speed = 1.2D;
      } else if (context.horizontalDistanceSqr() > 49.0D) {
         speed = 1.12D;
      } else {
         speed = 1.0D;
      }

      if (context.phaseTwoActive()) {
         speed *= 1.18D;
      }

      return speed;
   }

   public static TyrantAction chooseAction(RandomSource random, TyrantCombatContext context) {
      return chooseAction(random, context, true);
   }

   public static TyrantAction chooseAction(RandomSource random, TyrantCombatContext context, boolean leapSlamReady) {
      float totalWeight = 0.0F;
      float[] weights = new float[COMBAT_ACTIONS.length];

      for (int i = 0; i < COMBAT_ACTIONS.length; ++i) {
         TyrantAction action = COMBAT_ACTIONS[i];
         float weight = !leapSlamReady && isLeapAction(action) ? 0.0F : getActionWeight(action, context);
         if (action == context.lastAction()) {
            weight *= context.repeatedActionCount() >= 1 ? 0.18F : 0.45F;
         } else if (isLeapAction(action) && isLeapAction(context.lastAction())) {
            weight *= 0.2F;
         } else if (action == TyrantAction.ATTACK_RIGHT && context.lastAction() == TyrantAction.ATTACK_LEFT
            || action == TyrantAction.ATTACK_LEFT && context.lastAction() == TyrantAction.ATTACK_RIGHT) {
            weight *= 1.2F;
         }

         weights[i] = weight;
         totalWeight += weight;
      }

      if (totalWeight <= 0.0F) {
         return TyrantAction.NONE;
      }

      float pick = random.nextFloat() * totalWeight;

      for (int i = 0; i < COMBAT_ACTIONS.length; ++i) {
         pick -= weights[i];
         if (pick <= 0.0F) {
            return COMBAT_ACTIONS[i];
         }
      }

      return COMBAT_ACTIONS[COMBAT_ACTIONS.length - 1];
   }

   public static int getCooldownTicks(TyrantAction action, RandomSource random, boolean phaseTwoActive) {
      int cooldown;
      switch (action) {
         case ATTACK_RIGHT:
            cooldown = 21 + random.nextInt(4);
            break;
         case ATTACK_LEFT:
            cooldown = 28 + random.nextInt(5);
            break;
         case DOUBLE_SLAM_TAIL:
            cooldown = 40 + random.nextInt(7);
            break;
         case ROAR_WAVE_SHORT:
         case ROAR_WAVE_LONG:
            cooldown = 64 + random.nextInt(8);
            break;
         case LEAP_SLAM_FORWARD:
            cooldown = 86 + random.nextInt(14);
            break;
         case LEAP_SLAM_BACKWARD:
            cooldown = 76 + random.nextInt(12);
            break;
         case INTRO_ROAR:
            cooldown = 30;
            break;
         case PHASE_SHIFT:
            cooldown = 96;
            break;
         case DEATH:
            cooldown = 0;
            break;
         default:
            cooldown = 12;
      }

      if (phaseTwoActive && action != TyrantAction.INTRO_ROAR && action != TyrantAction.PHASE_SHIFT && action != TyrantAction.DEATH) {
         cooldown = Math.max(9, Mth.floor((float)cooldown * 0.76F));
      }

      return cooldown;
   }

   private static float getActionWeight(TyrantAction action, TyrantCombatContext context) {
      double horizontalDistance = context.horizontalDistance();
      float weight;
      switch (action) {
         case ATTACK_RIGHT:
            weight = canUseMelee(context)
               ? (float)((horizontalDistance <= 2.8D ? 2.7D : (horizontalDistance <= 4.8D ? 1.9D : 0.55D))
               * (0.8D + context.forwardPressure() * 0.82D)
               * (context.hasSight() ? 1.0D : 0.28D))
               : 0.0F;
            break;
         case ATTACK_LEFT:
            weight = canUseMelee(context)
               ? (float)((horizontalDistance <= 3.4D ? 2.35D : (horizontalDistance <= 5.0D ? 1.8D : 0.34D))
               * (0.58D + context.forwardPressure() * 0.38D + context.flankPressure() * 0.42D)
               * (context.hasSight() ? 1.0D : 0.24D))
               : 0.0F;
            break;
         case DOUBLE_SLAM_TAIL:
            weight = canUseMelee(context)
               ? (float)((horizontalDistance <= 2.8D ? 2.35D : (horizontalDistance <= 5.0D ? 2.75D : 1.25D))
               * (0.86D + context.flankPressure() * 0.7D + context.rearPressure() * 0.92D))
               : 0.0F;
            break;
         case ROAR_WAVE_SHORT:
            weight = canUseRoarWave(context)
               ? (float)((horizontalDistance >= 3.8D && horizontalDistance <= 6.8D ? 2.5D : 1.0D)
               * (0.7D + context.forwardPressure() * 0.6D))
               : 0.0F;
            break;
         case ROAR_WAVE_LONG:
            weight = canUseRoarWave(context)
               ? (float)((horizontalDistance >= 6.8D ? 2.9D : 0.55D)
               * (0.72D + context.forwardPressure() * 0.65D))
               : 0.0F;
            break;
         case LEAP_SLAM_FORWARD:
            weight = canUseForwardLeap(context)
               ? (float)(((!context.hasSight() ? 1.35D : (horizontalDistance >= 9.0D ? 1.85D : 1.05D))
               + (context.verticalGap() > 1.8D && horizontalDistance >= 5.0D ? 0.65D : 0.0D))
               * (0.62D + context.forwardPressure() * 0.25D))
               : 0.0F;
            break;
         case LEAP_SLAM_BACKWARD:
            weight = canUseBackwardLeap(context)
               ? (float)((horizontalDistance <= 2.1D ? 0.95D : 0.35D)
               * (0.55D + context.forwardPressure() * 0.35D + context.rearPressure() * 0.25D)
               * (context.hasSight() ? 1.0D : 0.15D))
               : 0.0F;
            break;
         default:
            weight = 0.0F;
      }

      if (!context.phaseTwoActive()) {
         return weight;
      }

      float multiplier;
      switch (action) {
         case ATTACK_RIGHT:
            multiplier = 0.95F;
            break;
         case ATTACK_LEFT:
            multiplier = 1.12F;
            break;
         case DOUBLE_SLAM_TAIL:
            multiplier = 1.36F;
            break;
         case ROAR_WAVE_SHORT:
            multiplier = 1.26F;
            break;
         case ROAR_WAVE_LONG:
            multiplier = 1.38F;
            break;
         case LEAP_SLAM_FORWARD:
         case LEAP_SLAM_BACKWARD:
            multiplier = 0.95F;
            break;
         default:
            multiplier = 1.0F;
      }

      return weight * multiplier;
   }

   private static boolean canUseMelee(TyrantCombatContext context) {
      return context.horizontalDistanceSqr() <= MELEE_RANGE_SQR && context.verticalGap() <= MAX_MELEE_VERTICAL_GAP;
   }

   private static boolean canUseRoarWave(TyrantCombatContext context) {
      return context.hasSight()
         && context.horizontalDistanceSqr() >= WAVE_MIN_RANGE_SQR
         && context.horizontalDistanceSqr() <= WAVE_MAX_RANGE_SQR
         && context.verticalGap() <= MAX_WAVE_VERTICAL_GAP;
   }

   private static boolean canUseForwardLeap(TyrantCombatContext context) {
      double distanceSqr = context.horizontalDistanceSqr();
      boolean farGap = distanceSqr >= 42.25D;
      boolean heightGap = context.verticalGap() > 1.65D && distanceSqr >= 20.25D;
      boolean blindGap = !context.hasSight() && distanceSqr >= 49.0D;
      return (farGap || heightGap || blindGap)
         && distanceSqr >= LEAP_MIN_RANGE_SQR
         && distanceSqr <= LEAP_MAX_RANGE_SQR
         && context.verticalGap() <= MAX_LEAP_VERTICAL_GAP;
   }

   private static boolean canUseBackwardLeap(TyrantCombatContext context) {
      return context.hasSight() && context.horizontalDistanceSqr() <= 7.29D && context.verticalGap() <= 1.75D;
   }

   private static boolean isLeapAction(TyrantAction action) {
      return action == TyrantAction.LEAP_SLAM_FORWARD || action == TyrantAction.LEAP_SLAM_BACKWARD;
   }
}
