package com.eddyon.tyrant.common.feedback;

import com.eddyon.tyrant.common.network.payload.TyrantCommandHudPayload;

public final class TyrantCommandHudBuffer {
   private static TyrantCommandHudPayload latestPayload;

   private TyrantCommandHudBuffer() {
   }

   public static synchronized void queue(TyrantCommandHudPayload payload) {
      latestPayload = payload;
   }

   public static synchronized TyrantCommandHudPayload consumeLatest() {
      TyrantCommandHudPayload payload = latestPayload;
      latestPayload = null;
      return payload;
   }
}
