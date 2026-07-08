package com.eddyon.tyrant.common.entity.tyrant;

public enum TyrantCommand {
   NONE("none"),
   KNEEL("kneel"),
   AUDIENCE("audience"),
   RETREAT("retreat"),
   APPROACH("approach"),
   DISARM("disarm"),
   BOW("bow"),
   PARDON("pardon");

   private final String id;

   TyrantCommand(String id) {
      this.id = id;
   }

   public boolean isActive() {
      return this != NONE;
   }

   public String id() {
      return this.id;
   }

   public int networkId() {
      return this.ordinal();
   }

   public String titleKey() {
      return "command.tyrant." + this.id;
   }

   public String ruleKey() {
      return "command.tyrant." + this.id + ".rule";
   }

   public static TyrantCommand byNetworkId(int networkId) {
      TyrantCommand[] commands = values();
      return networkId >= 0 && networkId < commands.length ? commands[networkId] : NONE;
   }
}
