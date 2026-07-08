package com.eddyon.tyrant;

import com.eddyon.tyrant.common.config.TyrantAttributeBounds;
import com.eddyon.tyrant.common.config.TyrantConfig;
import com.eddyon.tyrant.common.registry.ModEntities;
import com.eddyon.tyrant.common.registry.ModEffects;
import com.eddyon.tyrant.common.registry.ModItems;
import com.eddyon.tyrant.common.registry.ModParticles;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TyrantMod.MODID)
public final class TyrantMod {
   public static final String MODID = "tyrant";
   private static final Logger LOGGER = LogUtils.getLogger();

   public TyrantMod(IEventBus modEventBus, ModContainer modContainer) {
      TyrantAttributeBounds.expandVanillaBounds();
      ModEntities.register(modEventBus);
      ModEffects.register(modEventBus);
      ModItems.register(modEventBus);
      ModParticles.register(modEventBus);
      modContainer.registerConfig(ModConfig.Type.COMMON, TyrantConfig.SPEC, "tyrant-common.toml");
      LOGGER.info("暴君 is ready. Tyrant has been registered.");
   }
}
