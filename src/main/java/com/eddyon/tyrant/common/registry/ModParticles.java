package com.eddyon.tyrant.common.registry;

import com.eddyon.tyrant.TyrantMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
   private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, TyrantMod.MODID);
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAD_MOTE = PARTICLES.register("dread_mote", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FEAR_STATIC = PARTICLES.register("fear_static", () -> new SimpleParticleType(false));

   private ModParticles() {
   }

   public static void register(IEventBus modEventBus) {
      PARTICLES.register(modEventBus);
   }
}
