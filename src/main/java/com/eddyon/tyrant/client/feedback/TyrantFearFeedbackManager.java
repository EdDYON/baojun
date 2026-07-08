package com.eddyon.tyrant.client.feedback;

import com.eddyon.tyrant.common.config.TyrantConfig;
import com.eddyon.tyrant.common.entity.TyrantAction;
import com.eddyon.tyrant.common.entity.TyrantEntity;
import com.eddyon.tyrant.common.registry.ModEffects;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class TyrantFearFeedbackManager {
   private static float visualIntensity;
   private static float pulseIntensity;
   private static float actionPressure;
   private static float proximityPressure;
   private static float soundPressure;
   private static int heartbeatCooldown;
   private static int breathCooldown;

   private TyrantFearFeedbackManager() {
   }

   public static void tick() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level == null || minecraft.player == null) {
         reset();
      } else if (!minecraft.isPaused()) {
         Player player = minecraft.player;
         TyrantEntity tyrant = findNearestTyrant(player);
         float targetIntensity = 0.0F;
         float targetPulse = 0.0F;
         actionPressure = 0.0F;
         proximityPressure = 0.0F;
         soundPressure = 0.0F;
         if (tyrant == null) {
            reset();
            return;
         }

         float distance = player.distanceTo(tyrant);
         boolean inView = isTyrantInView(player, tyrant);
         if (inView || distance < 40.0F) {
            proximityPressure = Mth.clamp(1.0F - distance / 40.0F, 0.0F, 1.0F);
            soundPressure = Mth.clamp(1.0F - distance / 18.0F, 0.0F, 1.0F);
            targetIntensity += 0.22F + proximityPressure * 0.64F;
            actionPressure = getActionPressure(tyrant.getCurrentAction());
            targetIntensity += actionPressure * 0.5F;
            targetPulse = Math.max(targetPulse, actionPressure);
            if (inView) {
               targetIntensity += 0.12F;
            }
         }

         boolean kingOppression = player.hasEffect(ModEffects.KING_OPPRESSION);
         boolean timidity = player.hasEffect(ModEffects.TIMIDITY);
         if (kingOppression) {
            targetIntensity = Math.max(targetIntensity, 0.52F + proximityPressure * 0.24F);
            targetPulse = Math.max(targetPulse, 0.56F);
         }

         if (timidity) {
            targetIntensity = Math.max(targetIntensity, 0.78F + proximityPressure * 0.16F);
            targetPulse = Math.max(targetPulse, 0.74F + proximityPressure * 0.18F);
         }

         float healthPressure = 1.0F - player.getHealth() / Math.max(1.0F, player.getMaxHealth());
         targetIntensity += healthPressure * 0.18F;
         float screenIntensity = TyrantConfig.fearScreenIntensity();
         targetIntensity = Mth.clamp(targetIntensity * screenIntensity, 0.0F, 1.0F);
         targetPulse = Mth.clamp((targetPulse + healthPressure * 0.12F) * screenIntensity, 0.0F, 1.0F);
         float blend = targetIntensity > visualIntensity ? 0.18F : 0.08F;
         visualIntensity = Mth.lerp(blend, visualIntensity, targetIntensity);
         pulseIntensity = Mth.lerp(0.16F, pulseIntensity, targetPulse);
         if (targetIntensity <= 0.01F) {
            heartbeatCooldown = 0;
            breathCooldown = 0;
         } else {
            if (heartbeatCooldown-- <= 0) {
               float volume = Mth.clamp(0.38F + visualIntensity * 0.58F + soundPressure * 1.42F + pulseIntensity * 0.42F + (timidity ? 0.32F : 0.0F), 0.28F, 2.45F);
               float pitch = Mth.clamp(0.68F - soundPressure * 0.28F - pulseIntensity * 0.05F - (timidity ? 0.05F : 0.0F), 0.36F, 0.76F);
               minecraft.level.playLocalSound(player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, volume, pitch, false);
               heartbeatCooldown = Math.max(5, 28 - Math.round(soundPressure * 18.0F) - Math.round(pulseIntensity * 10.0F) - (timidity ? 5 : 0));
            }

            if (breathCooldown-- <= 0) {
               float volume = Mth.clamp(0.14F + visualIntensity * 0.2F + soundPressure * 0.62F + (timidity ? 0.1F : 0.0F), 0.08F, 1.05F);
               float pitch = Mth.clamp(0.94F - soundPressure * 0.2F - pulseIntensity * 0.12F, 0.64F, 1.0F);
               minecraft.level.playLocalSound(player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.WARDEN_NEARBY_CLOSE, SoundSource.AMBIENT, volume, pitch, false);
               breathCooldown = Math.max(18, 74 - Math.round(soundPressure * 34.0F) - (timidity ? 8 : 0));
            }
         }

      }
   }

   public static void apply(ViewportEvent.ComputeCameraAngles event) {
      if (visualIntensity > 0.01F) {
         Minecraft minecraft = Minecraft.getInstance();
         float time = (float)minecraft.level.getGameTime() + (float)event.getPartialTick();
         float pressure = visualIntensity * (0.12F + pulseIntensity * 0.34F);
         event.setYaw(event.getYaw() + Mth.sin(time * 0.19F) * pressure * 0.75F);
         event.setPitch(event.getPitch() + Mth.cos(time * 0.13F) * pressure * 0.68F);
         event.setRoll(event.getRoll() + Mth.sin(time * 0.08F) * pressure * 1.28F);
      }
   }

   public static void applyFogColor(ViewportEvent.ComputeFogColor event) {
      if (visualIntensity > 0.01F) {
         float gray = (event.getRed() + event.getGreen() + event.getBlue()) / 3.0F;
         float desat = Mth.clamp(visualIntensity * 0.56F, 0.0F, 0.56F);
         float red = Mth.lerp(desat, event.getRed(), gray * 0.96F);
         float green = Mth.lerp(desat, event.getGreen(), gray * 0.86F);
         float blue = Mth.lerp(desat, event.getBlue(), gray * 1.02F);
         float tint = Mth.clamp(visualIntensity * 0.18F + pulseIntensity * 0.1F, 0.0F, 0.28F);
         event.setRed(Mth.lerp(tint, red, 0.34F));
         event.setGreen(Mth.lerp(tint, green, 0.06F));
         event.setBlue(Mth.lerp(tint, blue, 0.18F));
      }
   }

   public static float getVisualIntensity() {
      return visualIntensity;
   }

   public static float getPulseIntensity() {
      return pulseIntensity;
   }

   public static float getActionPressure() {
      return actionPressure;
   }

   public static float getProximityPressure() {
      return proximityPressure;
   }

   public static float getSoundPressure() {
      return soundPressure;
   }

   public static SoundInstance filterSound(SoundInstance sound) {
      if (sound == null || visualIntensity < 0.18F) {
         return sound;
      } else {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.level == null || minecraft.player == null) {
            return sound;
         } else if (minecraft.player.isCreative() || minecraft.player.isSpectator()) {
            return sound;
         } else if (isPlayerHeartbeat(sound, minecraft.player)) {
            return sound;
         } else if (isTyrantThreatSound(sound, minecraft.player)) {
            return sound;
         } else if (isPlayerGeneratedSound(sound, minecraft.player)) {
            float scale = Mth.clamp(0.24F - visualIntensity * 0.1F, 0.1F, 0.22F);
            return new QuietSoundInstance(sound, scale);
         } else {
            return null;
         }
      }
   }

   private static TyrantEntity findNearestTyrant(Player player) {
      List<TyrantEntity> tyrants = player.level().getEntitiesOfClass(TyrantEntity.class, (new AABB(player.blockPosition())).inflate(40.0D, 20.0D, 40.0D));
      return tyrants.stream().filter(TyrantEntity::isAlive).min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
   }

   private static boolean isPlayerHeartbeat(SoundInstance sound, Player player) {
      ResourceLocation location = sound.getLocation();
      if (!"minecraft".equals(location.getNamespace()) || !location.getPath().contains("warden.heartbeat")) {
         return false;
      } else if (sound.getSource() != SoundSource.AMBIENT && sound.getSource() != SoundSource.MASTER) {
         return false;
      } else {
         double dx = sound.getX() - player.getX();
         double dy = sound.getY() - (player.getY() + 1.0D);
         double dz = sound.getZ() - player.getZ();
         return dx * dx + dy * dy + dz * dz < 9.0D;
      }
   }

   private static boolean isTyrantThreatSound(SoundInstance sound, Player player) {
      if (sound.getSource() != SoundSource.HOSTILE) {
         return false;
      } else if (!isThreatSoundName(sound.getLocation())) {
         return false;
      } else {
         Vec3 soundPos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
         List<TyrantEntity> tyrants = player.level().getEntitiesOfClass(TyrantEntity.class, (new AABB(player.blockPosition())).inflate(48.0D, 24.0D, 48.0D));

         for(TyrantEntity tyrant : tyrants) {
            if (tyrant.isAlive() && tyrant.position().distanceToSqr(soundPos) <= 196.0D) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean isPlayerGeneratedSound(SoundInstance sound, Player player) {
      if (sound.getSource() != SoundSource.PLAYERS) {
         return false;
      } else {
         double dx = sound.getX() - player.getX();
         double dy = sound.getY() - (player.getY() + 1.0D);
         double dz = sound.getZ() - player.getZ();
         return dx * dx + dy * dy + dz * dz <= 36.0D;
      }
   }

   private static boolean isThreatSoundName(ResourceLocation location) {
      if (!"minecraft".equals(location.getNamespace())) {
         return false;
      } else {
         String path = location.getPath();
         return path.contains("ravager.roar")
            || path.contains("ravager.stunned")
            || path.contains("warden.attack")
            || path.contains("warden.sonic_boom")
            || path.contains("ender_dragon.growl")
            || path.contains("generic.explode")
            || path.contains("player.attack.sweep")
            || path.contains("wither.spawn")
            || path.contains("warden.death");
      }
   }

   private static boolean isTyrantInView(Player player, TyrantEntity tyrant) {
      Vec3 toTyrant = tyrant.getEyePosition().subtract(player.getEyePosition());
      double length = toTyrant.length();
      if (length < 1.0E-4D) {
         return true;
      } else {
         Vec3 look = player.getViewVector(1.0F).normalize();
         return look.dot(toTyrant.scale(1.0D / length)) > 0.15D;
      }
   }

   private static float getActionPressure(TyrantAction action) {
      return switch (action) {
         case ROAR_WAVE_SHORT -> 0.38F;
         case ROAR_WAVE_LONG, INTRO_ROAR -> 0.56F;
         case COMMAND_EXECUTION -> 0.62F;
         case LEAP_SLAM_FORWARD, LEAP_SLAM_BACKWARD -> 0.44F;
         case DOUBLE_SLAM_TAIL -> 0.46F;
         case ATTACK_RIGHT -> 0.3F;
         case ATTACK_LEFT -> 0.36F;
         case PHASE_SHIFT -> 0.0F;
         case DEATH, NONE -> 0.0F;
      };
   }

   private static void reset() {
      visualIntensity = 0.0F;
      pulseIntensity = 0.0F;
      actionPressure = 0.0F;
      proximityPressure = 0.0F;
      soundPressure = 0.0F;
      heartbeatCooldown = 0;
      breathCooldown = 0;
   }

   private record QuietSoundInstance(SoundInstance delegate, float volumeScale) implements SoundInstance {
      @Override
      public ResourceLocation getLocation() {
         return this.delegate.getLocation();
      }

      @Override
      public WeighedSoundEvents resolve(SoundManager manager) {
         return this.delegate.resolve(manager);
      }

      @Override
      public Sound getSound() {
         return this.delegate.getSound();
      }

      @Override
      public SoundSource getSource() {
         return this.delegate.getSource();
      }

      @Override
      public boolean isLooping() {
         return this.delegate.isLooping();
      }

      @Override
      public boolean isRelative() {
         return this.delegate.isRelative();
      }

      @Override
      public int getDelay() {
         return this.delegate.getDelay();
      }

      @Override
      public float getVolume() {
         return this.delegate.getVolume() * this.volumeScale;
      }

      @Override
      public float getPitch() {
         return this.delegate.getPitch();
      }

      @Override
      public double getX() {
         return this.delegate.getX();
      }

      @Override
      public double getY() {
         return this.delegate.getY();
      }

      @Override
      public double getZ() {
         return this.delegate.getZ();
      }

      @Override
      public Attenuation getAttenuation() {
         return this.delegate.getAttenuation();
      }

      @Override
      public boolean canPlaySound() {
         return this.delegate.canPlaySound();
      }

      @Override
      public boolean canStartSilent() {
         return this.delegate.canStartSilent();
      }
   }
}
