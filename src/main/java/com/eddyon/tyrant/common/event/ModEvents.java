package com.eddyon.tyrant.common.event;

import com.eddyon.tyrant.TyrantMod;
import com.eddyon.tyrant.common.entity.TyrantEntity;
import com.eddyon.tyrant.common.registry.ModEntities;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation;

@EventBusSubscriber(
   modid = TyrantMod.MODID,
   bus = Bus.MOD
)
public final class ModEvents {
   private ModEvents() {
   }

   @SubscribeEvent
   public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
      event.put(ModEntities.TYRANT.get(), TyrantEntity.createAttributes().build());
   }

   @SubscribeEvent
   public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
      event.register(ModEntities.TYRANT.get(), SpawnPlacementTypes.ON_GROUND, Types.MOTION_BLOCKING_NO_LEAVES, TyrantEntity::canSpawn, Operation.REPLACE);
   }
}
