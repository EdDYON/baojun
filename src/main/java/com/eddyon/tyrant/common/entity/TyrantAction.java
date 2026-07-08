package com.eddyon.tyrant.common.entity;

public enum TyrantAction {
   NONE(0, "", 0),
   ATTACK_RIGHT(1, "attack", 24),
   ATTACK_LEFT(2, "attack2", 24),
   DOUBLE_SLAM_TAIL(3, "sk02", 63),
   ROAR_WAVE_SHORT(4, "sk03", 69),
   ROAR_WAVE_LONG(5, "sk01", 70),
   LEAP_SLAM_FORWARD(6, "sk04", 60),
   LEAP_SLAM_BACKWARD(7, "sk05", 60),
   DEATH(8, "down", 248),
   INTRO_ROAR(9, "sk03", 78),
   PHASE_SHIFT(10, "sk01", 92),
   COMMAND_EXECUTION(11, "sk01", 76);

   private final int id;
   private final String animationName;
   private final int durationTicks;

   private TyrantAction(int id, String animationName, int durationTicks) {
      this.id = id;
      this.animationName = animationName;
      this.durationTicks = durationTicks;
   }

   public int id() {
      return this.id;
   }

   public String animationName() {
      return this.animationName;
   }

   public int durationTicks() {
      return this.durationTicks;
   }

   public boolean isActive() {
      return this != NONE;
   }

   public static TyrantAction byId(int id) {
      for(TyrantAction action : values()) {
         if (action.id == id) {
            return action;
         }
      }

      return NONE;
   }
}
