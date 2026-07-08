package com.eddyon.tyrant.common.entity;

import com.eddyon.tyrant.common.config.TyrantConfig;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class TyrantDamageHelper {
   private TyrantDamageHelper() {
   }

   static void damageOrientedBox(TyrantEntity tyrant, Vec3 center, Vec3 facing, double halfForward, double halfWidth, double halfHeight, float damage, double knockback, double yBoost) {
      Vec3 side = sideVector(facing);
      AABB area = (new AABB(center, center)).inflate(halfForward + halfWidth, halfHeight, halfForward + halfWidth);

      for(LivingEntity victim : tyrant.level().getEntitiesOfClass(LivingEntity.class, area, living -> canDamage(tyrant, living))) {
         Vec3 sample = closestPointTo(victim, center).subtract(center);
         double forward = sample.dot(facing);
         double sideways = sample.dot(side);
         double vertical = sample.y;
         if (Math.abs(forward) <= halfForward && Math.abs(sideways) <= halfWidth && Math.abs(vertical) <= halfHeight && tyrant.hasLineOfSight(victim)) {
            hurtAndPushFrom(tyrant, victim, center, damage, knockback, yBoost);
         }
      }

   }

   static void damageRadial(TyrantEntity tyrant, Vec3 origin, double radius, double verticalHalf, double minRadius, float damage, double knockback, double yBoost, boolean requireSight) {
      AABB area = (new AABB(origin, origin)).inflate(radius, verticalHalf, radius);
      List<LivingEntity> victims = tyrant.level().getEntitiesOfClass(LivingEntity.class, area, living -> canDamage(tyrant, living));
      double radiusSqr = radius * radius;
      double minRadiusSqr = minRadius * minRadius;

      for(LivingEntity victim : victims) {
         Vec3 delta = closestPointTo(victim, origin).subtract(origin);
         double horizontalSqr = delta.x * delta.x + delta.z * delta.z;
         if (!(horizontalSqr > radiusSqr) && !(horizontalSqr < minRadiusSqr) && !(Math.abs(delta.y) > verticalHalf) && (!requireSight || tyrant.hasLineOfSight(victim))) {
            double distanceFactor = 1.0D - Math.sqrt(horizontalSqr) / radius;
            float scaledDamage = (float)((double)damage * (0.72D + distanceFactor * 0.28D));
            double scaledKnockback = knockback * (0.85D + distanceFactor * 0.35D);
            hurtAndPushFrom(tyrant, victim, origin, scaledDamage, scaledKnockback, yBoost);
         }
      }

   }

   static void damageForwardWave(TyrantEntity tyrant, Vec3 origin, Vec3 facing, double radius, double verticalHalf, double widthBase, float damage, double knockback, double yBoost) {
      Vec3 side = sideVector(facing);
      AABB area = (new AABB(origin, origin)).inflate(radius, verticalHalf, radius);

      for(LivingEntity victim : tyrant.level().getEntitiesOfClass(LivingEntity.class, area, living -> canDamage(tyrant, living))) {
         Vec3 sample = closestPointTo(victim, origin).subtract(origin);
         double forward = sample.dot(facing);
         double sideways = Math.abs(sample.dot(side));
         double vertical = Math.abs(sample.y);
         if (!(forward < 0.8D) && !(forward > radius) && !(vertical > verticalHalf)) {
            double maxSide = widthBase + forward * 0.7D;
            if (!(sideways > maxSide) && tyrant.hasLineOfSight(victim)) {
               double distanceFactor = 1.0D - forward / radius;
               float scaledDamage = (float)((double)damage * (0.78D + distanceFactor * 0.22D));
               double scaledKnockback = knockback * (0.92D + distanceFactor * 0.3D);
               hurtAndPushFrom(tyrant, victim, origin, scaledDamage, scaledKnockback, yBoost);
            }
         }
      }

   }

   static void damageTailSweep(TyrantEntity tyrant, Vec3 origin, Vec3 facing, double radius, double minRadius, double verticalHalf, float damage, double knockback, double yBoost) {
      AABB area = (new AABB(origin, origin)).inflate(radius, verticalHalf, radius);

      for(LivingEntity victim : tyrant.level().getEntitiesOfClass(LivingEntity.class, area, living -> canDamage(tyrant, living))) {
         Vec3 delta = closestPointTo(victim, origin).subtract(origin);
         double horizontalSqr = delta.x * delta.x + delta.z * delta.z;
         if (!(horizontalSqr > radius * radius) && !(horizontalSqr < minRadius * minRadius) && !(Math.abs(delta.y) > verticalHalf)) {
            Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
            if (!(horizontal.lengthSqr() < 1.0E-4D)) {
               double forwardDot = horizontal.normalize().dot(facing);
               if (!(forwardDot > 0.45D) && tyrant.hasLineOfSight(victim)) {
                  double distanceFactor = 1.0D - Math.sqrt(horizontalSqr) / radius;
                  float scaledDamage = (float)((double)damage * (0.8D + distanceFactor * 0.2D));
                  hurtAndPushFrom(tyrant, victim, origin, scaledDamage, knockback, yBoost);
               }
            }
         }
      }

   }

   static boolean canDamage(TyrantEntity tyrant, LivingEntity living) {
      if (!canTargetEntity(tyrant, living)) {
         return false;
      } else if (living instanceof Player player) {
         return !player.isCreative();
      } else {
         return true;
      }
   }

   private static boolean canTargetEntity(TyrantEntity tyrant, LivingEntity living) {
      return living != tyrant && living.isAlive() && !living.isSpectator() && living.getType() != tyrant.getType() && !tyrant.isAlliedTo(living);
   }

   private static Vec3 closestPointTo(LivingEntity victim, Vec3 point) {
      AABB box = victim.getBoundingBox();
      return new Vec3(Mth.clamp(point.x, box.minX, box.maxX), Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
   }

   private static void hurtAndPushFrom(TyrantEntity tyrant, LivingEntity victim, Vec3 origin, float damage, double knockback, double yBoost) {
      float scaledDamage = TyrantConfig.scaleSkillDamage(damage);
      if (scaledDamage <= 0.0F) {
         return;
      }

      if (victim.hurt(tyrant.damageSources().mobAttack(tyrant), scaledDamage)) {
         Vec3 horizontal = victim.position().subtract(origin);
         horizontal = new Vec3(horizontal.x, 0.0D, horizontal.z);
         if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = facingVector(tyrant);
         } else {
            horizontal = horizontal.normalize();
         }

         victim.push(horizontal.x * knockback, yBoost + knockback * 0.09D, horizontal.z * knockback);
         applySkillHitEffects(tyrant, victim);
      }

   }

   private static void applySkillHitEffects(TyrantEntity tyrant, LivingEntity victim) {
      TyrantAction action = tyrant.getCurrentAction();
      if (action == TyrantAction.ATTACK_LEFT) {
         victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1, true, true, true), tyrant);
         victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, true, true, true), tyrant);
      } else if (action == TyrantAction.DOUBLE_SLAM_TAIL) {
         victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 0, true, true, true), tyrant);
      } else if (action == TyrantAction.LEAP_SLAM_FORWARD || action == TyrantAction.LEAP_SLAM_BACKWARD) {
         victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 55, 1, true, true, true), tyrant);
         victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 35, 0, true, false, true), tyrant);
      } else if (action == TyrantAction.ROAR_WAVE_SHORT || action == TyrantAction.ROAR_WAVE_LONG) {
         victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 42, 0, true, true, true), tyrant);
      }
   }

   private static Vec3 facingVector(TyrantEntity tyrant) {
      return Vec3.directionFromRotation(0.0F, tyrant.getYRot()).normalize();
   }

   private static Vec3 sideVector(Vec3 facing) {
      return new Vec3(-facing.z, 0.0D, facing.x);
   }
}
