package com.eddyon.tyrant.client.event;

import com.eddyon.tyrant.TyrantMod;
import com.eddyon.tyrant.client.particle.DreadMoteParticle;
import com.eddyon.tyrant.client.particle.FearStaticParticle;
import com.eddyon.tyrant.client.renderer.TyrantRenderer;
import com.eddyon.tyrant.common.registry.ModEntities;
import com.eddyon.tyrant.common.registry.ModParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
   modid = TyrantMod.MODID,
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
public final class ClientModEvents {
   private ClientModEvents() {
   }

   @SubscribeEvent
   public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
      event.registerSpriteSet(ModParticles.DREAD_MOTE.get(), DreadMoteParticle.Provider::new);
      event.registerSpriteSet(ModParticles.FEAR_STATIC.get(), FearStaticParticle.Provider::new);
   }

   @SubscribeEvent
   public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(ModEntities.TYRANT.get(), TyrantRenderer::new);
   }
}
