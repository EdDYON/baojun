package com.eddyon.tyrant.common.network;

import com.eddyon.tyrant.TyrantMod;
import com.eddyon.tyrant.common.feedback.ScreenShakeBuffer;
import com.eddyon.tyrant.common.feedback.TyrantCommandHudBuffer;
import com.eddyon.tyrant.common.feedback.TyrantExecutionBuffer;
import com.eddyon.tyrant.common.network.payload.TyrantCommandHudPayload;
import com.eddyon.tyrant.common.network.payload.TyrantExecutionPayload;
import com.eddyon.tyrant.common.network.payload.TyrantScreenShakePayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(
   modid = TyrantMod.MODID,
   bus = Bus.MOD
)
public final class ModNetworking {
   private ModNetworking() {
   }

   @SubscribeEvent
   public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar("1");
      registrar.playToClient(TyrantScreenShakePayload.TYPE, TyrantScreenShakePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> ScreenShakeBuffer.queue(payload)));
      registrar.playToClient(TyrantCommandHudPayload.TYPE, TyrantCommandHudPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> TyrantCommandHudBuffer.queue(payload)));
      registrar.playToClient(TyrantExecutionPayload.TYPE, TyrantExecutionPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> TyrantExecutionBuffer.queue(payload)));
   }
}
