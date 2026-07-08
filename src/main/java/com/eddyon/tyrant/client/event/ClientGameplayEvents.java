package com.eddyon.tyrant.client.event;

import com.eddyon.tyrant.TyrantMod;
import com.eddyon.tyrant.client.feedback.ClientScreenShakeManager;
import com.eddyon.tyrant.client.feedback.TyrantCommandHudState;
import com.eddyon.tyrant.client.feedback.TyrantExecutionState;
import com.eddyon.tyrant.client.feedback.TyrantFearFeedbackManager;
import com.eddyon.tyrant.client.gui.TyrantCommandOverlay;
import com.eddyon.tyrant.client.gui.TyrantExecutionOverlay;
import com.eddyon.tyrant.client.gui.TyrantFearOverlay;
import com.eddyon.tyrant.client.gui.TyrantBossOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(
   modid = TyrantMod.MODID,
   value = {Dist.CLIENT}
)
public final class ClientGameplayEvents {
   private ClientGameplayEvents() {
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent.Post event) {
      ClientScreenShakeManager.tick();
      TyrantCommandHudState.tick();
      TyrantExecutionState.tick();
      TyrantFearFeedbackManager.tick();
   }

   @SubscribeEvent
   public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      ClientScreenShakeManager.apply(event);
      TyrantFearFeedbackManager.apply(event);
   }

   @SubscribeEvent
   public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
      TyrantFearFeedbackManager.applyFogColor(event);
   }

   @SubscribeEvent
   public static void onBossEventProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
      TyrantBossOverlay.onBossEventProgress(event);
   }

   @SubscribeEvent
   public static void onRenderGui(RenderGuiEvent.Post event) {
      TyrantFearOverlay.render(event);
      TyrantCommandOverlay.render(event);
      TyrantExecutionOverlay.render(event);
   }

   @SubscribeEvent
   public static void onPlaySound(PlaySoundEvent event) {
      event.setSound(TyrantFearFeedbackManager.filterSound(event.getSound()));
   }
}
