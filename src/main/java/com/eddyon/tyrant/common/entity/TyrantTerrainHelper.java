package com.eddyon.tyrant.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;

final class TyrantTerrainHelper {
   private TyrantTerrainHelper() {
   }

   static BlockPos findImpactSurface(TyrantEntity tyrant, Vec3 origin) {
      BlockPos.MutableBlockPos cursor = BlockPos.containing(origin).mutable();

      for(int i = 0; i < 6; ++i) {
         BlockState state = tyrant.level().getBlockState(cursor);
         if (!state.isAir() && state.getFluidState().isEmpty()) {
            return cursor.immutable();
         }

         cursor.move(Direction.DOWN);
      }

      return BlockPos.containing(origin).below();
   }

   static BlockState findImpactState(TyrantEntity tyrant, Vec3 origin) {
      BlockState state = tyrant.level().getBlockState(findImpactSurface(tyrant, origin));
      return state.isAir() ? Blocks.SCULK.defaultBlockState() : state;
   }

   static void tearTerrain(TyrantEntity tyrant, Vec3 origin, float radius, float maxDestroySpeed, float density) {
      Level level = tyrant.level();
      if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         return;
      }

      int gridRadius = Mth.ceil(radius);
      float radiusSqr = radius * radius;

      for(int x = -gridRadius; x <= gridRadius; ++x) {
         for(int z = -gridRadius; z <= gridRadius; ++z) {
            float horizontalSqr = (float)(x * x + z * z);
            if (!(horizontalSqr > radiusSqr)) {
               float edgeFactor = 1.0F - Mth.clamp(Mth.sqrt(horizontalSqr) / radius, 0.0F, 1.0F);
               float breakChance = density * (0.45F + edgeFactor * 0.9F);
               if (!(tyrant.getRandom().nextFloat() > breakChance)) {
                  Vec3 probe = origin.add((double)x, 0.35D, (double)z);
                  BlockPos surfacePos = findImpactSurface(tyrant, probe);
                  tryTearBlock(serverLevel, tyrant, surfacePos, maxDestroySpeed, 1);
               }
            }
         }
      }
   }

   static void carveImpactCrater(TyrantEntity tyrant, Vec3 origin, float radius, int maxDepth, float maxDestroySpeed, float density) {
      Level level = tyrant.level();
      if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         return;
      }

      int gridRadius = Mth.ceil(radius);
      float radiusSqr = radius * radius;
      int clampedDepth = Mth.clamp(maxDepth, 1, 5);

      for(int x = -gridRadius; x <= gridRadius; ++x) {
         for(int z = -gridRadius; z <= gridRadius; ++z) {
            float horizontalSqr = (float)(x * x + z * z);
            if (!(horizontalSqr > radiusSqr)) {
               float distance = Mth.sqrt(horizontalSqr);
               float edgeFactor = 1.0F - Mth.clamp(distance / radius, 0.0F, 1.0F);
               float breakChance = density * (0.5F + edgeFactor * 0.85F);
               if (!(tyrant.getRandom().nextFloat() > breakChance)) {
                  Vec3 probe = origin.add((double)x, 0.45D, (double)z);
                  BlockPos surfacePos = findImpactSurface(tyrant, probe);
                  int columnDepth = Mth.clamp(1 + Mth.floor(edgeFactor * (float)clampedDepth), 1, clampedDepth);

                  for(int depth = 0; depth < columnDepth; ++depth) {
                     float depthFactor = 1.0F - (float)depth / (float)(clampedDepth + 1);
                     if (depth == 0 || !(tyrant.getRandom().nextFloat() > breakChance * depthFactor)) {
                        tryTearBlock(serverLevel, tyrant, surfacePos.below(depth), maxDestroySpeed, depth == 0 ? 3 : 2);
                     }
                  }
               }
            }
         }
      }
   }

   static void tearTerrainRing(TyrantEntity tyrant, Vec3 origin, float innerRadius, float outerRadius, float maxDestroySpeed, float density) {
      Level level = tyrant.level();
      if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         return;
      }

      float clampedInner = Math.max(0.0F, Math.min(innerRadius, outerRadius - 0.1F));
      int gridRadius = Mth.ceil(outerRadius);
      float innerSqr = clampedInner * clampedInner;
      float outerSqr = outerRadius * outerRadius;
      float bandCenter = (clampedInner + outerRadius) * 0.5F;
      float halfBandWidth = Math.max(0.1F, (outerRadius - clampedInner) * 0.5F);

      for(int x = -gridRadius; x <= gridRadius; ++x) {
         for(int z = -gridRadius; z <= gridRadius; ++z) {
            float horizontalSqr = (float)(x * x + z * z);
            if (!(horizontalSqr < innerSqr) && !(horizontalSqr > outerSqr)) {
               float distance = Mth.sqrt(horizontalSqr);
               float bandFactor = 1.0F - Mth.clamp(Math.abs(distance - bandCenter) / halfBandWidth, 0.0F, 1.0F);
               float breakChance = density * (0.4F + bandFactor * 0.75F);
               if (!(tyrant.getRandom().nextFloat() > breakChance)) {
                  Vec3 probe = origin.add((double)x, 0.35D, (double)z);
                  BlockPos surfacePos = findImpactSurface(tyrant, probe);
                  tryTearBlock(serverLevel, tyrant, surfacePos, maxDestroySpeed, 2);
               }
            }
         }
      }
   }

   static void crushStepTerrain(TyrantEntity tyrant, Vec3 origin, float stepBreakSpeed, boolean phaseTwoActive) {
      tearTerrain(tyrant, origin, phaseTwoActive ? 1.45F : 1.1F, stepBreakSpeed, phaseTwoActive ? 0.35F : 0.22F);
   }

   private static boolean tryTearBlock(ServerLevel serverLevel, TyrantEntity tyrant, BlockPos pos, float maxDestroySpeed, int particleCount) {
      BlockState state = serverLevel.getBlockState(pos);
      if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
         return false;
      }

      float destroySpeed = state.getDestroySpeed(serverLevel, pos);
      if (destroySpeed < 0.0F || destroySpeed > maxDestroySpeed) {
         return false;
      }

      serverLevel.levelEvent(2001, pos, Block.getId(state));
      serverLevel.sendParticles(
            new BlockParticleOption(ParticleTypes.BLOCK, state),
            (double)pos.getX() + 0.5D,
            (double)pos.getY() + 0.72D,
            (double)pos.getZ() + 0.5D,
            Math.max(1, particleCount),
            0.24D,
            0.16D,
            0.24D,
            0.075D);
      if (particleCount > 1 && tyrant.getRandom().nextFloat() < 0.35F) {
         serverLevel.sendParticles(
               ParticleTypes.LARGE_SMOKE,
               (double)pos.getX() + 0.5D,
               (double)pos.getY() + 0.85D,
               (double)pos.getZ() + 0.5D,
               1,
               0.18D,
               0.1D,
               0.18D,
               0.01D);
      }

      serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
      return true;
   }
}
