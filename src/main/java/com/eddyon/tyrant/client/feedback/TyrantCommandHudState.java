package com.eddyon.tyrant.client.feedback;

import com.eddyon.tyrant.common.entity.tyrant.TyrantCommand;
import com.eddyon.tyrant.common.feedback.TyrantCommandHudBuffer;
import com.eddyon.tyrant.common.network.payload.TyrantCommandHudPayload;
import net.minecraft.client.Minecraft;

public final class TyrantCommandHudState {
   private static final int ANNOUNCEMENT_DURATION_TICKS = 54;
   private static TyrantCommand command = TyrantCommand.NONE;
   private static TyrantCommand announcementCommand = TyrantCommand.NONE;
   private static int elapsedTicks;
   private static int totalTicks;
   private static int graceTicks;
   private static int staleTicks;
   private static int announcementTicks;
   private static int lockedHotbarMask;

   private TyrantCommandHudState() {
   }

   public static void tick() {
      TyrantCommandHudPayload payload = TyrantCommandHudBuffer.consumeLatest();
      if (payload != null) {
         TyrantCommand nextCommand = TyrantCommand.byNetworkId(payload.commandId());
         if (!nextCommand.isActive()) {
            clear();
            return;
         }

         if (nextCommand != command || !command.isActive()) {
            announcementCommand = nextCommand;
            announcementTicks = ANNOUNCEMENT_DURATION_TICKS;
         }

         command = nextCommand;
         elapsedTicks = Math.max(0, payload.elapsedTicks());
         totalTicks = Math.max(0, payload.totalTicks());
         graceTicks = Math.max(0, payload.graceTicks());
         lockedHotbarMask = payload.lockedHotbarMask() & 0x1FF;
         staleTicks = 0;
      } else if (isVisible()) {
         ++elapsedTicks;
         ++staleTicks;
      }

      if (announcementTicks > 0) {
         --announcementTicks;
      }

      enforceClientHotbarLock();

      if (command.isActive() && (elapsedTicks > totalTicks + 8 || staleTicks > 45)) {
         clear();
      }
   }

   public static boolean isVisible() {
      return command.isActive() && totalTicks > 0 && elapsedTicks <= totalTicks + 8;
   }

   public static TyrantCommand command() {
      return command;
   }

   public static boolean isAnnouncementVisible() {
      return announcementCommand.isActive() && announcementTicks > 0;
   }

   public static TyrantCommand announcementCommand() {
      return announcementCommand;
   }

   public static float announcementProgress(float partialTick) {
      if (announcementTicks <= 0) {
         return 0.0F;
      }

      return Math.max(0.0F, Math.min(1.0F, ((float)announcementTicks - partialTick) / (float)ANNOUNCEMENT_DURATION_TICKS));
   }

   public static boolean isPreparing() {
      return elapsedTicks <= graceTicks;
   }

   public static int elapsedTicks() {
      return elapsedTicks;
   }

   public static int totalTicks() {
      return totalTicks;
   }

   public static int graceTicks() {
      return graceTicks;
   }

   public static int remainingTicks() {
      return Math.max(0, totalTicks - elapsedTicks);
   }

   public static int lockedHotbarMask() {
      return lockedHotbarMask;
   }

   public static float remainingProgress(float partialTick) {
      float elapsed = Math.min((float)totalTicks, (float)elapsedTicks + partialTick);
      return totalTicks <= 0 ? 0.0F : Math.max(0.0F, (float)totalTicks - elapsed) / (float)totalTicks;
   }

   public static float graceBoundaryProgress() {
      return totalTicks <= 0 ? 0.0F : Math.max(0.0F, (float)(totalTicks - graceTicks)) / (float)totalTicks;
   }

   private static void clear() {
      command = TyrantCommand.NONE;
      elapsedTicks = 0;
      totalTicks = 0;
      graceTicks = 0;
      staleTicks = 0;
      announcementCommand = TyrantCommand.NONE;
      announcementTicks = 0;
      lockedHotbarMask = 0;
   }

   private static void enforceClientHotbarLock() {
      if (command != TyrantCommand.DISARM || lockedHotbarMask == 0) {
         return;
      }

      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player == null) {
         return;
      }

      int selected = minecraft.player.getInventory().selected;
      if (selected < 0 || selected >= 9 || (lockedHotbarMask & 1 << selected) == 0) {
         return;
      }

      int replacement = findUnlockedHotbarSlot(selected);
      if (replacement >= 0) {
         minecraft.player.getInventory().selected = replacement;
      }
   }

   private static int findUnlockedHotbarSlot(int selected) {
      for(int offset = 1; offset <= 9; ++offset) {
         int slot = (selected + offset) % 9;
         if ((lockedHotbarMask & 1 << slot) == 0) {
            return slot;
         }
      }

      return -1;
   }
}
