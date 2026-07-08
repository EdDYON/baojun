package com.eddyon.tyrant.common.entity.tyrant;

import com.eddyon.tyrant.common.entity.TyrantAction;

public record TyrantCombatContext(
   double horizontalDistanceSqr,
   double verticalGap,
   boolean hasSight,
   double forwardPressure,
   double flankPressure,
   double rearPressure,
   boolean phaseTwoActive,
   TyrantAction lastAction,
   int repeatedActionCount
) {
   public double horizontalDistance() {
      return Math.sqrt(this.horizontalDistanceSqr);
   }
}
