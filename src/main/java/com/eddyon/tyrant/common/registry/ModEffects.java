package com.eddyon.tyrant.common.registry;

import com.eddyon.tyrant.TyrantMod;
import com.eddyon.tyrant.common.effect.KingOppressionEffect;
import com.eddyon.tyrant.common.effect.RoyalDecreeEffect;
import com.eddyon.tyrant.common.effect.TimidityEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
   private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, TyrantMod.MODID);
   public static final DeferredHolder<MobEffect, MobEffect> KING_OPPRESSION = EFFECTS.register("king_oppression", KingOppressionEffect::new);
   public static final DeferredHolder<MobEffect, MobEffect> TIMIDITY = EFFECTS.register("timidity", TimidityEffect::new);
   public static final DeferredHolder<MobEffect, MobEffect> ROYAL_DECREE = EFFECTS.register("royal_decree", RoyalDecreeEffect::new);

   private ModEffects() {
   }

   public static void register(IEventBus modEventBus) {
      EFFECTS.register(modEventBus);
   }
}
