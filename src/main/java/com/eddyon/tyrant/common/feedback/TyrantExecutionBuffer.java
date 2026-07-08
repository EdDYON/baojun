package com.eddyon.tyrant.common.feedback;

import com.eddyon.tyrant.common.network.payload.TyrantExecutionPayload;

public final class TyrantExecutionBuffer {
   private static TyrantExecutionPayload latestPayload;

   private TyrantExecutionBuffer() {
   }

   public static synchronized void queue(TyrantExecutionPayload payload) {
      latestPayload = payload;
   }

   public static synchronized TyrantExecutionPayload consumeLatest() {
      TyrantExecutionPayload payload = latestPayload;
      latestPayload = null;
      return payload;
   }
}
