package com.eddyon.tyrant.common.entity.tyrant;

import com.eddyon.tyrant.common.config.TyrantConfig;
import com.eddyon.tyrant.common.entity.TyrantEntity;
import com.eddyon.tyrant.common.network.payload.TyrantCommandHudPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TyrantCommandController {
   private static final double RETREAT_FORBIDDEN_RADIUS = 6.8D;
   private static final double RETREAT_SAFE_RADIUS = 8.8D;
   private static final double APPROACH_FORBIDDEN_RADIUS = 16.0D;
   private static final double APPROACH_SAFE_RADIUS = 13.2D;
   private static final double FACE_TYRANT_DOT = 0.38D;
   private static final double PARDON_RADIUS = 8.0D;
   private static final double PARDON_FACE_DOT = 0.24D;
   private static final double DIRECT_GAZE_DOT = 0.48D;
   private static final double BOW_SAFE_GAZE_DOT = 0.24D;
   private static final float BOW_REQUIRED_PITCH = 28.0F;
   private static final float BOW_SOFT_PITCH = 18.0F;
   private static final int KNEEL_THRESHOLD = 6;
   private static final int AUDIENCE_THRESHOLD = 24;
   private static final int RETREAT_THRESHOLD = 12;
   private static final int APPROACH_THRESHOLD = 26;
   private static final int DISARM_LOCK_THRESHOLD = 8;
   private static final int DISARM_DROP_THRESHOLD = 24;
   private static final int DISARM_SEVERE_THRESHOLD = 46;
   private static final int BOW_THRESHOLD = 22;
   private static final int PARDON_THRESHOLD = 18;
   private static final int PARDON_REQUIRED_TICKS = 48;
   private static final String TAG_EXECUTION_MARKS = "TyrantExecutionMarks";
   private static final String TAG_MARK_PLAYER = "Player";
   private static final String TAG_MARKS = "Marks";
   private final RandomSource random = RandomSource.create();
   private final Map<UUID, Integer> violationTicks = new HashMap<>();
   private final Map<UUID, Integer> obedienceTicks = new HashMap<>();
   private final Map<UUID, Integer> punishedCooldowns = new HashMap<>();
   private final Map<UUID, Integer> executionMarks = new HashMap<>();
   private final Map<UUID, Integer> lockedHotbarMasks = new HashMap<>();
   private final Map<UUID, Integer> disarmPenaltyStage = new HashMap<>();
   private final Set<UUID> hudViewers = new HashSet<>();
   private final Set<UUID> pardonGranted = new HashSet<>();
   private final Set<UUID> countedThisCommand = new HashSet<>();
   private final List<TyrantCommand> commandRound = new ArrayList<>();
   private TyrantCommand activeCommand = TyrantCommand.NONE;
   private TyrantCommand previousCommand = TyrantCommand.NONE;
   private int commandTicks;
   private int cooldownTicks = TyrantCommandHub.initialCooldownTicks();

   public void tick(TyrantEntity tyrant) {
      this.tickCooldowns();
      if (!this.activeCommand.isActive()) {
         this.tickInactive(tyrant);
         return;
      }

      if (!tyrant.isAlive()) {
         this.endCommand(tyrant, List.of());
         return;
      }

      ++this.commandTicks;
      List<Player> players = this.collectTargets(tyrant);
      TyrantCommandHub.CommandProfile profile = TyrantCommandHub.profile(this.activeCommand);
      if (!players.isEmpty()) {
         this.refreshRoyalDecreeEffects(tyrant, players);
         if (!profile.isPreparing(this.commandTicks)) {
            this.evaluatePlayers(tyrant, players);
         }

         if (this.activeCommand == TyrantCommand.DISARM) {
            this.enforceDisarmLocks(players);
         }

         this.syncHudIfNeeded(profile, players);
      }

      if (this.commandTicks >= profile.totalTicks()) {
         this.endCommand(tyrant, players);
      }
   }

   public boolean reducesFearFor(Player player) {
      return this.activeCommand == TyrantCommand.KNEEL
         && this.commandTicks > TyrantCommandHub.profile(this.activeCommand).graceTicks()
         && player.isShiftKeyDown()
         && !player.isSprinting()
         && !this.isRisingFast(player);
   }

   public boolean shouldHoldCombatForPardon() {
      return this.activeCommand == TyrantCommand.PARDON && !TyrantCommandHub.profile(this.activeCommand).isPreparing(this.commandTicks);
   }

   public boolean onTyrantAttackedByPlayer(TyrantEntity tyrant, Player player) {
      if (this.activeCommand != TyrantCommand.PARDON || TyrantCommandHub.profile(this.activeCommand).isPreparing(this.commandTicks) || !tyrant.canTyrantCommandAffect(player)) {
         return false;
      }

      UUID uuid = player.getUUID();
      this.violationTicks.put(uuid, this.scalePenaltyTicks(80));
      this.obedienceTicks.put(uuid, Math.max(0, this.obedienceTicks.getOrDefault(uuid, 0) - 20));
      this.tryPunish(tyrant, player, this.violationTicks.get(uuid));
      this.syncHud(List.of(player));
      return true;
   }

   public void addAdditionalSaveData(CompoundTag compound) {
      ListTag marks = new ListTag();
      for(Map.Entry<UUID, Integer> entry : this.executionMarks.entrySet()) {
         int clamped = clampExecutionMarks(entry.getValue());
         if (clamped <= 0) {
            continue;
         }

         CompoundTag markTag = new CompoundTag();
         markTag.putUUID(TAG_MARK_PLAYER, entry.getKey());
         markTag.putInt(TAG_MARKS, clamped);
         marks.add(markTag);
      }

      compound.put(TAG_EXECUTION_MARKS, marks);
   }

   public void readAdditionalSaveData(CompoundTag compound) {
      this.executionMarks.clear();
      ListTag marks = compound.getList(TAG_EXECUTION_MARKS, Tag.TAG_COMPOUND);
      for(int i = 0; i < marks.size(); ++i) {
         CompoundTag markTag = marks.getCompound(i);
         if (!markTag.hasUUID(TAG_MARK_PLAYER)) {
            continue;
         }

         int clamped = clampExecutionMarks(markTag.getInt(TAG_MARKS));
         if (clamped > 0) {
            this.executionMarks.put(markTag.getUUID(TAG_MARK_PLAYER), clamped);
         }
      }
   }

   private void tickInactive(TyrantEntity tyrant) {
      if (this.cooldownTicks > 0) {
         --this.cooldownTicks;
      }

      if (this.cooldownTicks <= 0 && tyrant.canStartTyrantCommand()) {
         this.startCommand(tyrant);
      }
   }

   private void startCommand(TyrantEntity tyrant) {
      this.activeCommand = this.drawNextCommand();
      this.commandTicks = 0;
      this.violationTicks.clear();
      this.obedienceTicks.clear();
      this.punishedCooldowns.clear();
      this.lockedHotbarMasks.clear();
      this.disarmPenaltyStage.clear();
      this.pardonGranted.clear();
      this.countedThisCommand.clear();
      List<Player> players = this.collectTargets(tyrant);
      tyrant.onTyrantCommandStarted(this.activeCommand, players);
      this.syncHud(players);
   }

   private TyrantCommand drawNextCommand() {
      if (this.commandRound.isEmpty()) {
         this.refillCommandRound();
      }

      if (this.commandRound.isEmpty()) {
         return TyrantCommand.KNEEL;
      }

      return this.commandRound.remove(this.commandRound.size() - 1);
   }

   private void refillCommandRound() {
      this.commandRound.clear();
      for(TyrantCommand command : TyrantCommand.values()) {
         if (command.isActive()) {
            this.commandRound.add(command);
         }
      }

      for(int i = this.commandRound.size() - 1; i > 0; --i) {
         Collections.swap(this.commandRound, i, this.random.nextInt(i + 1));
      }

      int firstIndex = this.commandRound.size() - 1;
      if (firstIndex > 0 && this.previousCommand.isActive() && this.commandRound.get(firstIndex) == this.previousCommand) {
         Collections.swap(this.commandRound, firstIndex, this.random.nextInt(firstIndex));
      }
   }

   private void endCommand(TyrantEntity tyrant, List<Player> players) {
      TyrantCommand finishedCommand = this.activeCommand;
      if (finishedCommand.isActive()) {
         for(Player player : players) {
            UUID uuid = player.getUUID();
            int obedience = this.obedienceTicks.getOrDefault(uuid, 0);
            int marks = this.executionMarks.getOrDefault(uuid, 0);
            int requiredObedience = TyrantCommandHub.profile(finishedCommand).obedienceTicksRequired();
            boolean counted = this.countedThisCommand.contains(uuid);
            if (finishedCommand == TyrantCommand.PARDON) {
               if (!counted && obedience >= PARDON_REQUIRED_TICKS && !this.pardonGranted.contains(uuid)) {
                  this.grantPardon(tyrant, player);
               }
            } else if (!counted && obedience >= requiredObedience) {
               if (marks > 0) {
                  this.setExecutionMarks(tyrant, player, marks - 1);
               }

               tyrant.rewardTyrantCommandObedience(player, finishedCommand);
            }
         }
      }

      this.previousCommand = finishedCommand;
      this.activeCommand = TyrantCommand.NONE;
      this.commandTicks = 0;
      this.violationTicks.clear();
      this.obedienceTicks.clear();
      this.punishedCooldowns.clear();
      this.lockedHotbarMasks.clear();
      this.disarmPenaltyStage.clear();
      this.pardonGranted.clear();
      this.countedThisCommand.clear();
      this.clearHud(tyrant, players);
      this.cooldownTicks = TyrantCommandHub.cooldownTicks(tyrant.isTyrantPhaseTwoActive());
   }

   private void evaluatePlayers(TyrantEntity tyrant, List<Player> players) {
      for(Player player : players) {
         UUID uuid = player.getUUID();
         int increment = this.scaleViolationIncrement(this.getViolationIncrement(tyrant, player));
         boolean correctResponse = this.isCorrectResponse(tyrant, player);
         if (increment > 0) {
            int ticks = Math.min(80, this.violationTicks.getOrDefault(uuid, 0) + increment);
            this.violationTicks.put(uuid, ticks);
            this.obedienceTicks.put(uuid, Math.max(0, this.obedienceTicks.getOrDefault(uuid, 0) - 2));
            this.tryPunish(tyrant, player, ticks);
         } else {
            this.violationTicks.put(uuid, Math.max(0, this.violationTicks.getOrDefault(uuid, 0) - 2));
            if (correctResponse) {
               int obedience = this.obedienceTicks.getOrDefault(uuid, 0) + 1;
               this.obedienceTicks.put(uuid, obedience);
               if (this.activeCommand == TyrantCommand.PARDON && obedience >= PARDON_REQUIRED_TICKS && !this.countedThisCommand.contains(uuid) && !this.pardonGranted.contains(uuid)) {
                  this.grantPardon(tyrant, player);
               }
            }
         }
      }
   }

   private void grantPardon(TyrantEntity tyrant, Player player) {
      UUID uuid = player.getUUID();
      int marks = this.executionMarks.getOrDefault(uuid, 0);
      boolean clearedMark = marks > 0;
      if (clearedMark) {
         this.setExecutionMarks(tyrant, player, marks - 1);
      }

      this.pardonGranted.add(uuid);
      tyrant.rewardPardon(player, clearedMark);
   }

   private void tryPunish(TyrantEntity tyrant, Player player, int ticks) {
      UUID uuid = player.getUUID();
      if (this.punishedCooldowns.containsKey(uuid)) {
         return;
      }

      switch (this.activeCommand) {
         case KNEEL -> {
            if (ticks >= KNEEL_THRESHOLD) {
               int marks = this.addExecutionMarkForCurrentCommand(tyrant, player, 1);
               tyrant.punishKneelViolation(player, marks);
               this.violationTicks.put(uuid, KNEEL_THRESHOLD / 2);
               this.punishedCooldowns.put(uuid, 32);
            }
         }
         case AUDIENCE -> {
            if (ticks >= AUDIENCE_THRESHOLD) {
               this.addExecutionMarkForCurrentCommand(tyrant, player, 1);
               tyrant.punishAudienceViolation(player, ticks >= AUDIENCE_THRESHOLD + 16 ? 2 : 1);
               this.violationTicks.put(uuid, AUDIENCE_THRESHOLD / 2);
               this.punishedCooldowns.put(uuid, 38);
            }
         }
         case RETREAT -> {
            if (ticks >= RETREAT_THRESHOLD) {
               this.addExecutionMarkForCurrentCommand(tyrant, player, 1);
               tyrant.punishRetreatViolation(player);
               this.violationTicks.put(uuid, RETREAT_THRESHOLD / 2);
               this.punishedCooldowns.put(uuid, 34);
            }
         }
         case APPROACH -> {
            if (ticks >= APPROACH_THRESHOLD) {
               this.addExecutionMarkForCurrentCommand(tyrant, player, 1);
               tyrant.punishApproachViolation(player);
               this.violationTicks.put(uuid, APPROACH_THRESHOLD / 2);
               this.punishedCooldowns.put(uuid, 44);
            }
         }
         case DISARM -> this.tryPunishDisarm(tyrant, player, ticks);
         case BOW -> {
            if (ticks >= BOW_THRESHOLD) {
               this.addExecutionMarkForCurrentCommand(tyrant, player, 1);
               tyrant.punishBowViolation(player);
               this.violationTicks.put(uuid, BOW_THRESHOLD / 2);
               this.punishedCooldowns.put(uuid, 38);
            }
         }
         case PARDON -> {
            if (ticks >= PARDON_THRESHOLD) {
               int marks = this.addExecutionMarkForCurrentCommand(tyrant, player, 1);
               tyrant.punishPardonViolation(player, marks);
               this.violationTicks.put(uuid, PARDON_THRESHOLD / 2);
               this.punishedCooldowns.put(uuid, 45);
            }
         }
         default -> {
         }
      }
   }

   private void tryPunishDisarm(TyrantEntity tyrant, Player player, int ticks) {
      UUID uuid = player.getUUID();
      int stage = this.disarmPenaltyStage.getOrDefault(uuid, 0);
      if (stage == 0 && ticks >= DISARM_LOCK_THRESHOLD) {
         this.ensureDisarmLocks(player);
         tyrant.punishDisarmLock(player);
         this.disarmPenaltyStage.put(uuid, 1);
         this.violationTicks.put(uuid, 0);
         this.syncHud(List.of(player));
      } else if (stage <= 1 && ticks >= DISARM_DROP_THRESHOLD) {
         this.addExecutionMarkForCurrentCommand(tyrant, player, 1);
         tyrant.punishDisarmItemDrop(player, true);
         this.disarmPenaltyStage.put(uuid, 2);
         this.violationTicks.put(uuid, 0);
         this.punishedCooldowns.put(uuid, 36);
      } else if (stage >= 2 && ticks >= DISARM_SEVERE_THRESHOLD) {
         int marks = this.addExecutionMarkForCurrentCommand(tyrant, player, 1);
         tyrant.punishDisarmSevere(player, this.lockedHotbarMasks.getOrDefault(uuid, 0), marks > 1 ? 2 : 1);
         this.violationTicks.put(uuid, DISARM_SEVERE_THRESHOLD / 2);
         this.punishedCooldowns.put(uuid, 52);
      }
   }

   private int addExecutionMarkForCurrentCommand(TyrantEntity tyrant, Player player, int amount) {
      UUID uuid = player.getUUID();
      if (!this.countedThisCommand.add(uuid)) {
         return this.executionMarks.getOrDefault(uuid, 0);
      }

      return this.addExecutionMark(tyrant, player, amount);
   }

   private int addExecutionMark(TyrantEntity tyrant, Player player, int amount) {
      UUID uuid = player.getUUID();
      int marks = this.setExecutionMarks(tyrant, player, this.executionMarks.getOrDefault(uuid, 0) + amount);
      int threshold = TyrantConfig.executionMarkThreshold();
      tyrant.announceExecutionMark(player, marks, threshold);
      if (marks >= threshold) {
         tyrant.punishExecutionMarked(player);
         this.setExecutionMarks(tyrant, player, 0);
         return 0;
      }

      return marks;
   }

   private int setExecutionMarks(TyrantEntity tyrant, Player player, int marks) {
      UUID uuid = player.getUUID();
      int clamped = clampExecutionMarks(marks);
      if (clamped > 0) {
         this.executionMarks.put(uuid, clamped);
      } else {
         this.executionMarks.remove(uuid);
      }

      tyrant.updateRoyalDecreeEffect(player, clamped, TyrantConfig.executionMarkThreshold());
      return clamped;
   }

   private static int clampExecutionMarks(int marks) {
      return Math.max(0, Math.min(TyrantConfig.executionMarkThreshold(), marks));
   }

   private int scaleViolationIncrement(int increment) {
      return this.scalePenaltyTicks(increment);
   }

   private int scalePenaltyTicks(int ticks) {
      if (ticks <= 0) {
         return 0;
      }

      float multiplier = TyrantConfig.commandPenaltyMultiplier();
      if (multiplier <= 0.0F) {
         return 0;
      }

      return Math.max(1, Math.round((float)ticks * multiplier));
   }

   private int getViolationIncrement(TyrantEntity tyrant, Player player) {
      return switch (this.activeCommand) {
         case KNEEL -> this.getKneelViolation(player);
         case AUDIENCE -> this.getAudienceViolation(tyrant, player);
         case RETREAT -> this.getRetreatViolation(tyrant, player);
         case APPROACH -> this.getApproachViolation(tyrant, player);
         case DISARM -> this.getDisarmViolation(player);
         case BOW -> this.getBowViolation(tyrant, player);
         case PARDON -> this.getPardonViolation(tyrant, player);
         default -> 0;
      };
   }

   private boolean isCorrectResponse(TyrantEntity tyrant, Player player) {
      double distance = player.distanceTo(tyrant);
      return switch (this.activeCommand) {
         case KNEEL -> player.isShiftKeyDown() && !player.isSprinting() && !this.isRisingFast(player);
         case AUDIENCE -> tyrant.isPlayerLookingAtTyrant(player, FACE_TYRANT_DOT);
         case RETREAT -> distance >= RETREAT_SAFE_RADIUS;
         case APPROACH -> distance <= APPROACH_SAFE_RADIUS;
         case DISARM -> this.isFullyDisarmed(player);
         case BOW -> player.getXRot() >= BOW_REQUIRED_PITCH && !tyrant.isPlayerLookingAtTyrant(player, DIRECT_GAZE_DOT);
         case PARDON -> this.isPardonPosture(tyrant, player);
         default -> false;
      };
   }

   private int getKneelViolation(Player player) {
      if (player.isSprinting() || this.isRisingFast(player)) {
         return 8;
      }

      if (this.isMovingHorizontally(player, 0.035D) && !player.isShiftKeyDown()) {
         return 4;
      }

      return 0;
   }

   private int getDisarmViolation(Player player) {
      boolean mainHandFilled = !player.getMainHandItem().isEmpty();
      boolean offhandFilled = !player.getOffhandItem().isEmpty();
      boolean armorEquipped = this.hasArmorEquipped(player);
      int selected = player.getInventory().selected;
      int mask = this.lockedHotbarMasks.getOrDefault(player.getUUID(), 0);
      boolean selectedLockedWithItem = selected >= 0 && selected < 9 && (mask & 1 << selected) != 0 && !player.getInventory().getItem(selected).isEmpty();
      if (!mainHandFilled && !offhandFilled && !armorEquipped && !selectedLockedWithItem) {
         return 0;
      }

      int stage = this.disarmPenaltyStage.getOrDefault(player.getUUID(), 0);
      if (stage <= 0) {
         return DISARM_LOCK_THRESHOLD;
      }

      if (stage == 1) {
         return DISARM_DROP_THRESHOLD;
      }

      int filledGroups = (mainHandFilled ? 1 : 0) + (offhandFilled ? 1 : 0) + (armorEquipped ? 1 : 0) + (selectedLockedWithItem ? 1 : 0);
      return filledGroups >= 2 ? DISARM_SEVERE_THRESHOLD : DISARM_SEVERE_THRESHOLD / 2;
   }

   private boolean isFullyDisarmed(Player player) {
      return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty() && !this.hasArmorEquipped(player);
   }

   private boolean hasArmorEquipped(Player player) {
      for(ItemStack stack : player.getArmorSlots()) {
         if (!stack.isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private int getBowViolation(TyrantEntity tyrant, Player player) {
      double lookDot = this.lookDotToTyrant(tyrant, player);
      if (player.getXRot() >= BOW_REQUIRED_PITCH && lookDot <= BOW_SAFE_GAZE_DOT) {
         return 0;
      }

      if (lookDot > DIRECT_GAZE_DOT) {
         return 5;
      }

      return player.getXRot() < BOW_SOFT_PITCH ? 2 : 1;
   }

   private int getPardonViolation(TyrantEntity tyrant, Player player) {
      if (this.isPardonPosture(tyrant, player)) {
         return 0;
      }

      double distance = player.distanceTo(tyrant);
      boolean marked = this.executionMarks.getOrDefault(player.getUUID(), 0) > 0;
      int increment = marked ? 2 : 0;
      if (distance <= PARDON_RADIUS && !player.isShiftKeyDown()) {
         increment += 4;
      }

      if (distance > PARDON_RADIUS + 5.0D) {
         increment += marked ? 2 : 1;
      } else if (distance > PARDON_RADIUS) {
         increment += 1;
      }

      if (player.isSprinting()) {
         increment += 1;
      }

      if (distance <= PARDON_RADIUS && !this.isHorizontallyFacingTyrant(tyrant, player, PARDON_FACE_DOT)) {
         increment += 1;
      }

      return increment;
   }

   private boolean isPardonPosture(TyrantEntity tyrant, Player player) {
      return player.distanceTo(tyrant) <= PARDON_RADIUS && player.isShiftKeyDown() && this.isHorizontallyFacingTyrant(tyrant, player, PARDON_FACE_DOT);
   }

   private int getAudienceViolation(TyrantEntity tyrant, Player player) {
      double lookDot = this.lookDotToTyrant(tyrant, player);
      if (lookDot > FACE_TYRANT_DOT) {
         return 0;
      }

      boolean movingAway = this.isMovingAwayFromTyrant(tyrant, player);
      if (lookDot < -0.08D) {
         return movingAway || player.isSprinting() ? 3 : 2;
      }

      return movingAway || player.isSprinting() ? 2 : 1;
   }

   private int getRetreatViolation(TyrantEntity tyrant, Player player) {
      double distance = player.distanceTo(tyrant);
      if (distance < RETREAT_FORBIDDEN_RADIUS) {
         return distance < RETREAT_FORBIDDEN_RADIUS - 1.7D ? 5 : 3;
      }

      return 0;
   }

   private int getApproachViolation(TyrantEntity tyrant, Player player) {
      double distance = player.distanceTo(tyrant);
      if (distance > APPROACH_FORBIDDEN_RADIUS) {
         return distance > APPROACH_FORBIDDEN_RADIUS + 6.0D ? 4 : 2;
      }

      return 0;
   }

   private void syncHudIfNeeded(TyrantCommandHub.CommandProfile profile, List<Player> players) {
      if (this.commandTicks <= 1 || this.commandTicks % 5 == 0 || this.commandTicks == profile.graceTicks() + 1 || this.commandTicks >= profile.totalTicks()) {
         this.syncHud(players);
      }
   }

   private void syncHud(List<Player> players) {
      TyrantCommandHub.CommandProfile profile = TyrantCommandHub.profile(this.activeCommand);

      for(Player player : players) {
         TyrantCommandHudPayload payload = new TyrantCommandHudPayload(this.activeCommand.networkId(), this.commandTicks, profile.totalTicks(), profile.graceTicks(), this.getLockedHotbarMask(player));
         this.sendHud(player, payload, true);
      }
   }

   private void clearHud(TyrantEntity tyrant, List<Player> currentPlayers) {
      TyrantCommandHudPayload payload = new TyrantCommandHudPayload(TyrantCommand.NONE.networkId(), 0, 0, 0, 0);
      Set<UUID> recipients = new HashSet<>(this.hudViewers);

      for(Player player : currentPlayers) {
         recipients.add(player.getUUID());
         this.sendHud(player, payload, false);
      }

      if (tyrant.level() instanceof ServerLevel serverLevel) {
         for(UUID uuid : recipients) {
            Player player = serverLevel.getPlayerByUUID(uuid);
            if (player instanceof ServerPlayer serverPlayer) {
               this.sendHud(serverPlayer, payload, false);
            }
         }
      }

      this.hudViewers.clear();
   }

   private void ensureDisarmLocks(Player player) {
      UUID uuid = player.getUUID();
      int mask = this.lockedHotbarMasks.getOrDefault(uuid, 0);
      if (Integer.bitCount(mask) >= 2) {
         return;
      }

      int selected = player.getInventory().selected;
      mask = this.addLockedSlot(mask, selected);

      for(int offset = 1; offset <= 9 && Integer.bitCount(mask) < 2; ++offset) {
         int slot = (selected + offset) % 9;
         if (!player.getInventory().getItem(slot).isEmpty()) {
            mask = this.addLockedSlot(mask, slot);
         }
      }

      for(int slot = 0; slot < 9 && Integer.bitCount(mask) < 2; ++slot) {
         mask = this.addLockedSlot(mask, slot);
      }

      this.lockedHotbarMasks.put(uuid, mask & 0x1FF);
   }

   private int addLockedSlot(int mask, int slot) {
      return slot >= 0 && slot < 9 ? mask | 1 << slot : mask;
   }

   private int getLockedHotbarMask(Player player) {
      return this.activeCommand == TyrantCommand.DISARM ? this.lockedHotbarMasks.getOrDefault(player.getUUID(), 0) : 0;
   }

   private void enforceDisarmLocks(List<Player> players) {
      for(Player player : players) {
         int mask = this.lockedHotbarMasks.getOrDefault(player.getUUID(), 0);
         if (mask == 0) {
            continue;
         }

         int selected = player.getInventory().selected;
         if ((mask & 1 << selected) == 0) {
            continue;
         }

         int replacement = this.findUnlockedHotbarSlot(player, mask);
         if (replacement >= 0) {
            player.stopUsingItem();
            player.getInventory().selected = replacement;
            if (player instanceof ServerPlayer serverPlayer) {
               serverPlayer.containerMenu.broadcastChanges();
            }
         }
      }
   }

   private int findUnlockedHotbarSlot(Player player, int mask) {
      for(int slot = 0; slot < 9; ++slot) {
         if ((mask & 1 << slot) == 0 && player.getInventory().getItem(slot).isEmpty()) {
            return slot;
         }
      }

      for(int slot = 0; slot < 9; ++slot) {
         if ((mask & 1 << slot) == 0) {
            return slot;
         }
      }

      return -1;
   }

   private void sendHud(Player player, TyrantCommandHudPayload payload, boolean trackViewer) {
      if (player instanceof ServerPlayer serverPlayer) {
         PacketDistributor.sendToPlayer(serverPlayer, payload);
         if (trackViewer) {
            this.hudViewers.add(serverPlayer.getUUID());
         }
      }
   }

   private List<Player> collectTargets(TyrantEntity tyrant) {
      double radius = TyrantConfig.commandRadius();
      AABB box = tyrant.getBoundingBox().inflate(radius, 8.0D, radius);
      return tyrant.level().getEntitiesOfClass(Player.class, box, tyrant::canTyrantCommandAffect);
   }

   private void refreshRoyalDecreeEffects(TyrantEntity tyrant, List<Player> players) {
      for(Player player : players) {
         int marks = this.executionMarks.getOrDefault(player.getUUID(), 0);
         if (marks > 0) {
            tyrant.updateRoyalDecreeEffect(player, marks, TyrantConfig.executionMarkThreshold());
         }
      }
   }

   private void tickCooldowns() {
      this.punishedCooldowns.replaceAll((uuid, ticks) -> ticks - 1);
      this.punishedCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
   }

   private boolean isRisingFast(Player player) {
      return !player.onGround() && player.getDeltaMovement().y > 0.075D;
   }

   private boolean isMovingHorizontally(Player player, double threshold) {
      Vec3 motion = player.getDeltaMovement();
      return motion.x * motion.x + motion.z * motion.z > threshold * threshold;
   }

   private boolean isMovingAwayFromTyrant(TyrantEntity tyrant, Player player) {
      Vec3 away = player.position().subtract(tyrant.position());
      away = new Vec3(away.x, 0.0D, away.z);
      Vec3 motion = player.getDeltaMovement();
      motion = new Vec3(motion.x, 0.0D, motion.z);
      if (away.lengthSqr() < 1.0E-4D || motion.lengthSqr() < 0.0025D) {
         return false;
      }

      return motion.normalize().dot(away.normalize()) > 0.35D;
   }

   private double lookDotToTyrant(TyrantEntity tyrant, Player player) {
      Vec3 toTyrant = tyrant.getEyePosition().subtract(player.getEyePosition());
      double length = toTyrant.length();
      if (length < 1.0E-4D) {
         return 1.0D;
      }

      return player.getViewVector(1.0F).normalize().dot(toTyrant.scale(1.0D / length));
   }

   private boolean isHorizontallyFacingTyrant(TyrantEntity tyrant, Player player, double dotThreshold) {
      Vec3 toTyrant = tyrant.position().subtract(player.position());
      toTyrant = new Vec3(toTyrant.x, 0.0D, toTyrant.z);
      if (toTyrant.lengthSqr() < 1.0E-4D) {
         return true;
      }

      Vec3 look = player.getViewVector(1.0F);
      look = new Vec3(look.x, 0.0D, look.z);
      if (look.lengthSqr() < 1.0E-4D) {
         return true;
      }

      return look.normalize().dot(toTyrant.normalize()) > dotThreshold;
   }
}
