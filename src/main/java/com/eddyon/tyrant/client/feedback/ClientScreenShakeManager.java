package com.eddyon.tyrant.client.feedback;

import com.eddyon.tyrant.common.config.TyrantConfig;
import com.eddyon.tyrant.common.feedback.ScreenShakeBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class ClientScreenShakeManager {
   private static final List<ClientScreenShakeManager.ShakeInstance> SHAKES = new ArrayList<>();

   private ClientScreenShakeManager() {
   }

   public static void tick() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level != null && minecraft.player != null) {
         for(ScreenShakeBuffer.ShakeEntry entry : ScreenShakeBuffer.drainPending()) {
            SHAKES.add(new ClientScreenShakeManager.ShakeInstance(entry.origin(), entry.radius(), entry.strength(), entry.durationTicks(), (float)(entry.origin().x * 0.173D + entry.origin().y * 0.117D + entry.origin().z * 0.091D)));
         }

         Iterator<ClientScreenShakeManager.ShakeInstance> iterator = SHAKES.iterator();

         while(iterator.hasNext()) {
            ClientScreenShakeManager.ShakeInstance shake = (ClientScreenShakeManager.ShakeInstance)iterator.next();
            ++shake.age;
            if (shake.age >= shake.durationTicks) {
               iterator.remove();
            }
         }

      } else {
         SHAKES.clear();
      }
   }

   public static void apply(ViewportEvent.ComputeCameraAngles event) {
      if (!SHAKES.isEmpty()) {
         float intensity = TyrantConfig.screenShakeIntensity();
         if (intensity <= 0.0F) {
            return;
         }

         Vec3 cameraPos = event.getCamera().getPosition();
         float time = (float)event.getPartialTick() + (float)Minecraft.getInstance().level.getGameTime();
         float yawOffset = 0.0F;
         float pitchOffset = 0.0F;
         float rollOffset = 0.0F;

         for(ClientScreenShakeManager.ShakeInstance shake : SHAKES) {
            float sampled = shake.sample(cameraPos) * intensity;
            if (!(sampled <= 0.0F)) {
               yawOffset += Mth.cos(time * 0.37F + shake.phase) * sampled * 0.9F;
               pitchOffset += Mth.sin(time * 0.61F + shake.phase * 1.7F) * sampled * 1.35F;
               rollOffset += Mth.sin(time * 0.94F + shake.phase * 2.3F) * sampled * 1.8F;
            }
         }

         event.setYaw(event.getYaw() + yawOffset);
         event.setPitch(event.getPitch() + pitchOffset);
         event.setRoll(event.getRoll() + rollOffset);
      }
   }

   private static final class ShakeInstance {
      private final Vec3 origin;
      private final float radius;
      private final float strength;
      private final int durationTicks;
      private final float phase;
      private int age;

      private ShakeInstance(Vec3 origin, float radius, float strength, int durationTicks, float phase) {
         this.origin = origin;
         this.radius = radius;
         this.strength = strength;
         this.durationTicks = durationTicks;
         this.phase = phase;
      }

      private float sample(Vec3 cameraPos) {
         double distance = cameraPos.distanceTo(this.origin);
         if (distance >= (double)this.radius) {
            return 0.0F;
         } else {
            float distanceFactor = 1.0F - (float)(distance / (double)this.radius);
            float ageFactor = 1.0F - (float)this.age / (float)this.durationTicks;
            ageFactor *= ageFactor;
            return this.strength * 1.18F * distanceFactor * ageFactor;
         }
      }
   }
}
