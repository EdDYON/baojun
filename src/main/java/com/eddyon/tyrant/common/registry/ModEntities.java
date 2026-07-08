package com.eddyon.tyrant.common.registry;

import com.eddyon.tyrant.TyrantMod;
import com.eddyon.tyrant.common.entity.TyrantEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
   private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, TyrantMod.MODID);
   public static final DeferredHolder<EntityType<?>, EntityType<TyrantEntity>> TYRANT = ENTITY_TYPES.register("tyrant", () -> Builder.of(TyrantEntity::new, MobCategory.MONSTER).sized(2.9F, 4.8F).eyeHeight(4.1F).clientTrackingRange(10).updateInterval(1).build("tyrant"));

   private ModEntities() {
   }

   public static void register(IEventBus modEventBus) {
      ENTITY_TYPES.register(modEventBus);
   }
}
