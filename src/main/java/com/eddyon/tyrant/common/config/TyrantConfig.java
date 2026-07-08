package com.eddyon.tyrant.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TyrantConfig {
   public static final ModConfigSpec SPEC;
   private static final ModConfigSpec.DoubleValue BOSS_MAX_HEALTH;
   private static final ModConfigSpec.DoubleValue BOSS_ATTACK_DAMAGE;
   private static final ModConfigSpec.DoubleValue BOSS_ARMOR;
   private static final ModConfigSpec.DoubleValue SKILL_DAMAGE_MULTIPLIER;
   private static final ModConfigSpec.DoubleValue COMMAND_RADIUS;
   private static final ModConfigSpec.IntValue COMMAND_INITIAL_COOLDOWN_TICKS;
   private static final ModConfigSpec.IntValue COMMAND_COOLDOWN_TICKS;
   private static final ModConfigSpec.IntValue COMMAND_PHASE_TWO_COOLDOWN_TICKS;
   private static final ModConfigSpec.DoubleValue COMMAND_PENALTY_MULTIPLIER;
   private static final ModConfigSpec.IntValue EXECUTION_MARK_THRESHOLD;
   private static final ModConfigSpec.DoubleValue FEAR_SCREEN_INTENSITY;
   private static final ModConfigSpec.DoubleValue SCREEN_SHAKE_INTENSITY;

   static {
      ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
      builder.comment("Tyrant boss combat tuning. Values are loaded from config/tyrant-common.toml.").push("boss");
      BOSS_MAX_HEALTH = builder.comment("Base max health for newly spawned Tyrants.").defineInRange("max_health", 3333.0D, 1.0D, 1000000.0D);
      BOSS_ATTACK_DAMAGE = builder.comment("Base attack damage attribute for the Tyrant.").defineInRange("attack_damage", 31.0D, 0.0D, 100000.0D);
      BOSS_ARMOR = builder.comment("Base armor attribute for the Tyrant.").defineInRange("armor", 20.0D, 0.0D, 100000.0D);
      SKILL_DAMAGE_MULTIPLIER = builder.comment("Multiplier for Tyrant skill damage. 1.0 keeps the default balance.").defineInRange("skill_damage_multiplier", 1.0D, 0.0D, 100.0D);
      builder.pop();

      builder.comment("Royal decree command tuning. All cooldown values are in ticks.").push("royal_decree");
      COMMAND_RADIUS = builder.comment("Horizontal radius where players are affected by royal decrees.").defineInRange("radius", 30.0D, 1.0D, 256.0D);
      COMMAND_INITIAL_COOLDOWN_TICKS = builder.comment("Delay before the first royal decree after combat starts.").defineInRange("initial_cooldown_ticks", 120, 0, 72000);
      COMMAND_COOLDOWN_TICKS = builder.comment("Cooldown between royal decrees before phase two.").defineInRange("cooldown_ticks", 220, 0, 72000);
      COMMAND_PHASE_TWO_COOLDOWN_TICKS = builder.comment("Cooldown between royal decrees during phase two.").defineInRange("phase_two_cooldown_ticks", 170, 0, 72000);
      COMMAND_PENALTY_MULTIPLIER = builder.comment("Scales royal decree violation buildup and punishment damage.").defineInRange("penalty_multiplier", 1.0D, 0.0D, 100.0D);
      EXECUTION_MARK_THRESHOLD = builder.comment("Number of execution marks required before judgement is triggered.").defineInRange("execution_mark_threshold", 4, 1, 100);
      builder.pop();

      builder.comment("Client-side feedback intensity. Players can lower these if the screen effects are too strong.").push("client_feedback");
      FEAR_SCREEN_INTENSITY = builder.comment("Multiplier for fear overlay, fog tint, muffled sound pressure, and fear camera sway.").defineInRange("fear_screen_intensity", 1.0D, 0.0D, 2.0D);
      SCREEN_SHAKE_INTENSITY = builder.comment("Multiplier for Tyrant screen shake.").defineInRange("screen_shake_intensity", 1.0D, 0.0D, 2.0D);
      builder.pop();
      SPEC = builder.build();
   }

   private TyrantConfig() {
   }

   public static double bossMaxHealth() {
      return BOSS_MAX_HEALTH.get();
   }

   public static double bossAttackDamage() {
      return BOSS_ATTACK_DAMAGE.get();
   }

   public static double bossArmor() {
      return BOSS_ARMOR.get();
   }

   public static float scaleSkillDamage(float damage) {
      return scale(damage, SKILL_DAMAGE_MULTIPLIER.get());
   }

   public static double commandRadius() {
      return COMMAND_RADIUS.get();
   }

   public static int commandInitialCooldownTicks() {
      return COMMAND_INITIAL_COOLDOWN_TICKS.get();
   }

   public static int commandCooldownTicks(boolean phaseTwo) {
      return phaseTwo ? COMMAND_PHASE_TWO_COOLDOWN_TICKS.get() : COMMAND_COOLDOWN_TICKS.get();
   }

   public static float commandPenaltyMultiplier() {
      return COMMAND_PENALTY_MULTIPLIER.get().floatValue();
   }

   public static float scaleCommandPenaltyDamage(float damage) {
      return scale(damage, COMMAND_PENALTY_MULTIPLIER.get());
   }

   public static int executionMarkThreshold() {
      return EXECUTION_MARK_THRESHOLD.get();
   }

   public static float fearScreenIntensity() {
      return FEAR_SCREEN_INTENSITY.get().floatValue();
   }

   public static float screenShakeIntensity() {
      return SCREEN_SHAKE_INTENSITY.get().floatValue();
   }

   private static float scale(float value, double multiplier) {
      return (float)Math.max(0.0D, (double)value * multiplier);
   }
}
