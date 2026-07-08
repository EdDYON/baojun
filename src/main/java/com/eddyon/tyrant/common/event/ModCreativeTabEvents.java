package com.eddyon.tyrant.common.event;

import com.eddyon.tyrant.TyrantMod;
import com.eddyon.tyrant.common.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(
   modid = TyrantMod.MODID,
   bus = Bus.MOD
)
public final class ModCreativeTabEvents {
   private ModCreativeTabEvents() {
   }

   @SubscribeEvent
   public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
         event.accept((ItemLike)ModItems.TYRANT_SPAWN_EGG.get());
      }

   }
}
