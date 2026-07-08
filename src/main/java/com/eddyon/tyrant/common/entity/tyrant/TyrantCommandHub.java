package com.eddyon.tyrant.common.entity.tyrant;

import com.eddyon.tyrant.common.config.TyrantConfig;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

public final class TyrantCommandHub {
   private static final CommandProfile NONE_PROFILE = new CommandProfile(TyrantCommand.NONE, 0, 0, 0, 20);
   private static final Map<TyrantCommand, CommandProfile> PROFILES = new EnumMap<>(TyrantCommand.class);

   static {
      register(new CommandProfile(TyrantCommand.KNEEL, 280, 60, 4, 10));
      register(new CommandProfile(TyrantCommand.AUDIENCE, 260, 60, 3, 10));
      register(new CommandProfile(TyrantCommand.RETREAT, 240, 40, 3, 10));
      register(new CommandProfile(TyrantCommand.APPROACH, 300, 80, 3, 10));
      register(new CommandProfile(TyrantCommand.DISARM, 280, 60, 3, 10));
      register(new CommandProfile(TyrantCommand.BOW, 260, 50, 3, 10));
      register(new CommandProfile(TyrantCommand.PARDON, 260, 60, 3, 10));
   }

   private TyrantCommandHub() {
   }

   public static CommandProfile profile(TyrantCommand command) {
      return PROFILES.getOrDefault(command, NONE_PROFILE);
   }

   public static int initialCooldownTicks() {
      return TyrantConfig.commandInitialCooldownTicks();
   }

   public static int cooldownTicks(boolean phaseTwo) {
      return TyrantConfig.commandCooldownTicks(phaseTwo);
   }

   public static TyrantCommand choose(RandomSource random, TyrantCommand previousCommand) {
      int totalWeight = 0;

      for(CommandProfile profile : PROFILES.values()) {
         totalWeight += adjustedWeight(profile, previousCommand);
      }

      int roll = random.nextInt(Math.max(1, totalWeight));

      for(CommandProfile profile : PROFILES.values()) {
         roll -= adjustedWeight(profile, previousCommand);
         if (roll < 0) {
            return profile.command();
         }
      }

      return TyrantCommand.KNEEL;
   }

   public static boolean shouldShowCountdown(TyrantCommand command, int elapsedTicks) {
      CommandProfile profile = profile(command);
      if (!command.isActive()) {
         return false;
      }

      if (elapsedTicks == 1 || elapsedTicks == profile.graceTicks() + 1) {
         return true;
      }

      int phaseRemaining = profile.isPreparing(elapsedTicks) ? profile.graceRemainingTicks(elapsedTicks) : profile.remainingTicks(elapsedTicks);
      if (phaseRemaining <= 60) {
         return elapsedTicks % 10 == 0;
      }

      return elapsedTicks % profile.countdownIntervalTicks() == 0;
   }

   public static Component countdownMessage(TyrantCommand command, int elapsedTicks) {
      CommandProfile profile = profile(command);
      Component title = Component.translatable(command.titleKey());
      Component rule = Component.translatable(command.ruleKey());
      if (profile.isPreparing(elapsedTicks)) {
         return Component.translatable("command.tyrant.countdown.prepare", title, formatSeconds(profile.graceRemainingTicks(elapsedTicks)), rule);
      }

      return Component.translatable("command.tyrant.countdown.active", title, formatSeconds(profile.remainingTicks(elapsedTicks)), rule);
   }

   private static void register(CommandProfile profile) {
      PROFILES.put(profile.command(), profile);
   }

   private static int adjustedWeight(CommandProfile profile, TyrantCommand previousCommand) {
      return profile.command() == previousCommand ? Math.max(1, profile.weight() / 2) : profile.weight();
   }

   private static String formatSeconds(int ticks) {
      double seconds = Math.max(0, ticks) / 20.0D;
      if (seconds <= 3.0D) {
         return String.format(java.util.Locale.ROOT, "%.1f", seconds);
      }

      return Integer.toString((int)Math.ceil(seconds));
   }

   public record CommandProfile(TyrantCommand command, int totalTicks, int graceTicks, int weight, int countdownIntervalTicks) {
      public boolean isPreparing(int elapsedTicks) {
         return elapsedTicks <= this.graceTicks;
      }

      public int activeTicks() {
         return Math.max(0, this.totalTicks - this.graceTicks);
      }

      public int obedienceTicksRequired() {
         return Math.max(42, Math.round((float)this.activeTicks() * 0.7F));
      }

      public int remainingTicks(int elapsedTicks) {
         return Math.max(0, this.totalTicks - elapsedTicks);
      }

      public int graceRemainingTicks(int elapsedTicks) {
         return Math.max(0, this.graceTicks - elapsedTicks);
      }
   }
}
