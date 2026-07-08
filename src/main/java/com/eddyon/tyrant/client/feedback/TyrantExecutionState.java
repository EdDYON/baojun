package com.eddyon.tyrant.client.feedback;

import com.eddyon.tyrant.common.feedback.TyrantExecutionBuffer;
import com.eddyon.tyrant.common.network.payload.TyrantExecutionPayload;

public final class TyrantExecutionState {
   public static final int QUOTE_COUNT = 6;
   private static int quoteIndex;
   private static int ticks;
   private static int durationTicks;

   private TyrantExecutionState() {
   }

   public static void tick() {
      TyrantExecutionPayload payload = TyrantExecutionBuffer.consumeLatest();
      if (payload != null) {
         quoteIndex = Math.floorMod(payload.quoteIndex(), QUOTE_COUNT);
         durationTicks = Math.max(1, payload.durationTicks());
         ticks = durationTicks;
      } else if (ticks > 0) {
         --ticks;
      }
   }

   public static boolean isVisible() {
      return ticks > 0;
   }

   public static int quoteIndex() {
      return quoteIndex;
   }

   public static float progress(float partialTick) {
      if (ticks <= 0 || durationTicks <= 0) {
         return 0.0F;
      }

      return Math.max(0.0F, Math.min(1.0F, ((float)ticks - partialTick) / (float)durationTicks));
   }
}
