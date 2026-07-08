package com.eddyon.tyrant.common.feedback;

import com.eddyon.tyrant.common.network.payload.TyrantScreenShakePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class ScreenShakeBuffer {
   private static final List<ScreenShakeBuffer.ShakeEntry> PENDING = new ArrayList<>();

   private ScreenShakeBuffer() {
   }

   public static synchronized void queue(TyrantScreenShakePayload payload) {
      PENDING.add(new ScreenShakeBuffer.ShakeEntry(new Vec3(payload.x(), payload.y(), payload.z()), payload.radius(), payload.strength(), payload.durationTicks()));
   }

   public static synchronized List<ScreenShakeBuffer.ShakeEntry> drainPending() {
      if (PENDING.isEmpty()) {
         return List.of();
      } else {
         List<ScreenShakeBuffer.ShakeEntry> queued = List.copyOf(PENDING);
         PENDING.clear();
         return queued;
      }
   }

   public static record ShakeEntry(Vec3 origin, float radius, float strength, int durationTicks) {
   }
}
