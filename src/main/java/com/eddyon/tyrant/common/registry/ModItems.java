package com.eddyon.tyrant.common.registry;

import com.eddyon.tyrant.TyrantMod;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
   private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TyrantMod.MODID);
   public static final DeferredItem<Item> TYRANT_SPAWN_EGG = ITEMS.register("tyrant_spawn_egg", () -> new DeferredSpawnEggItem(ModEntities.TYRANT, 2233626, 5147531, new Item.Properties()));

   private ModItems() {
   }

   public static void register(IEventBus modEventBus) {
      ITEMS.register(modEventBus);
   }
}
