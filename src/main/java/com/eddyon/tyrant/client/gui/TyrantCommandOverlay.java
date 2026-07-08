package com.eddyon.tyrant.client.gui;

import com.eddyon.tyrant.TyrantMod;
import com.eddyon.tyrant.client.feedback.TyrantCommandHudState;
import com.eddyon.tyrant.common.entity.tyrant.TyrantCommand;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class TyrantCommandOverlay {
   private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "textures/gui/tyrant_command_hud.png");
   private static final int PANEL_WIDTH = 160;
   private static final int PANEL_HEIGHT = 36;
   private static final int BAR_X = 36;
   private static final int BAR_Y = 13;
   private static final int BAR_WIDTH = 84;
   private static final int BAR_HEIGHT = 7;
   private static final int TIMER_CENTER_X = 140;
   private static final int TIMER_Y = 13;
   private static final float DISPLAY_SCALE = 0.50F;
   private static final int[] RAGGED_BAR_EDGE = new int[]{-3, -1, -4, 0, -2, -5, -1};
   private static final int TEXT_GLOW = 0x6E150A12;
   private static final int TEXT_PREPARE = 0xFFBDE8E3;
   private static final int TEXT_ACTIVE = 0xFFD8F7FF;

   private TyrantCommandOverlay() {
   }

   public static void render(RenderGuiEvent.Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.options.hideGui || minecraft.player == null || !TyrantCommandHudState.isVisible()) {
         return;
      }

      GuiGraphics gui = event.getGuiGraphics();
      int x = (minecraft.getWindow().getGuiScaledWidth() - displayWidth()) / 2;
      int y = TyrantBossOverlay.getCommandOverlayY();
      float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
      RenderSystem.enableBlend();
      renderAnnouncement(gui, minecraft.font, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), partialTick);
      gui.pose().pushPose();
      gui.pose().translate((float)x, (float)y, 0.0F);
      gui.pose().scale(DISPLAY_SCALE, DISPLAY_SCALE, 1.0F);
      renderPanel(gui, 0, 0);
      renderBar(gui, BAR_X, BAR_Y, partialTick);
      renderText(gui, minecraft.font, TIMER_CENTER_X, TIMER_Y);
      gui.pose().popPose();
      renderLockedHotbarSlots(gui, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
      RenderSystem.disableBlend();
   }

   private static void renderAnnouncement(GuiGraphics gui, Font font, int width, int height, float partialTick) {
      if (!TyrantCommandHudState.isAnnouncementVisible()) {
         return;
      }

      TyrantCommand command = TyrantCommandHudState.announcementCommand();
      float progress = TyrantCommandHudState.announcementProgress(partialTick);
      float fade = Mth.clamp(progress / 0.38F, 0.0F, 1.0F);
      int alpha = Mth.clamp(Math.round(fade * 255.0F), 0, 255);
      int centerX = width / 2;
      int titleY = Math.max(TyrantBossOverlay.getCommandOverlayY() + displayHeight() + 12, height / 3 - 28);
      float titleScale = 2.35F + (1.0F - progress) * 0.08F;
      Component title = Component.translatable(command.titleKey());
      Component rule = Component.translatable(command.ruleKey());
      int titleColor = withAlpha(0xFFE3A5, alpha);
      int ruleColor = withAlpha(0xE8D8B0, Math.round(alpha * 0.86F));
      int shadowColor = withAlpha(0x12070D, Math.round(alpha * 0.74F));
      drawCenteredScaled(gui, font, title, centerX + 2, titleY + 3, titleScale, shadowColor);
      drawCenteredScaled(gui, font, title, centerX, titleY, titleScale, titleColor);
      drawCenteredScaled(gui, font, rule, centerX + 1, titleY + 34, 1.12F, shadowColor);
      drawCenteredScaled(gui, font, rule, centerX, titleY + 33, 1.12F, ruleColor);
   }

   private static void drawCenteredScaled(GuiGraphics gui, Font font, Component text, int centerX, int y, float scale, int color) {
      gui.pose().pushPose();
      gui.pose().translate((float)centerX, (float)y, 0.0F);
      gui.pose().scale(scale, scale, 1.0F);
      gui.drawString(font, text, -font.width(text) / 2, 0, color, false);
      gui.pose().popPose();
   }

   private static void renderPanel(GuiGraphics gui, int x, int y) {
      gui.blit(PANEL_TEXTURE, x, y, 0, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
   }

   private static void renderText(GuiGraphics gui, Font font, int centerX, int y) {
      String text = formatSeconds(TyrantCommandHudState.remainingTicks()) + "s";
      int x = centerX - font.width(text) / 2;
      gui.drawString(font, text, x + 1, y + 1, TEXT_GLOW, false);
      gui.drawString(font, text, x, y, TyrantCommandHudState.isPreparing() ? TEXT_PREPARE : TEXT_ACTIVE, false);
   }

   private static void renderBar(GuiGraphics gui, int x, int y, float partialTick) {
      float progress = TyrantCommandHudState.remainingProgress(partialTick);
      int fillWidth = Mth.clamp(Math.round(progress * BAR_WIDTH), 0, BAR_WIDTH);
      int seed = TyrantCommandHudState.command().networkId() * 41 + (TyrantCommandHudState.isPreparing() ? 7 : 19);

      if (fillWidth > 0) {
         for(int row = 0; row < BAR_HEIGHT; ++row) {
            int rowWidth = raggedFillWidth(fillWidth, row);
            if (rowWidth <= 0) {
               continue;
            }

            int rowY = y + row;
            for(int px = 0; px < rowWidth; px += 4) {
               int end = Math.min(rowWidth, px + 4);
               int noise = hash(seed + row * 17, px);
               int color = (noise & 3) == 0 ? 0xE86CEBF5 : 0xE236B6C8;
               gui.fill(x + px, rowY, x + end, rowY + 1, color);
            }

            if (row >= BAR_HEIGHT - 2) {
               gui.fill(x, rowY, x + rowWidth, rowY + 1, 0xCA126473);
            }

            if (rowWidth > 3 && rowWidth < BAR_WIDTH && (row & 1) == 0) {
               gui.fill(x + rowWidth - 1, rowY, x + rowWidth, rowY + 1, 0xDDA8FBFF);
            }
         }
      }

      int boundary = x + Math.round(TyrantCommandHudState.graceBoundaryProgress() * BAR_WIDTH);
      if (boundary > x && boundary < x + BAR_WIDTH) {
         gui.fill(boundary, y - 1, boundary + 1, y + BAR_HEIGHT + 1, 0xBDE8D8B0);
      }
   }

   private static int raggedFillWidth(int fillWidth, int row) {
      if (fillWidth >= BAR_WIDTH - 1) {
         return BAR_WIDTH;
      }

      return Mth.clamp(fillWidth + RAGGED_BAR_EDGE[row % RAGGED_BAR_EDGE.length], 0, BAR_WIDTH);
   }

   private static void renderLockedHotbarSlots(GuiGraphics gui, int width, int height) {
      int mask = TyrantCommandHudState.lockedHotbarMask();
      if (mask == 0) {
         return;
      }

      int startX = width / 2 - 91;
      int y = height - 22;

      for(int slot = 0; slot < 9; ++slot) {
         if ((mask & 1 << slot) != 0) {
            int x = startX + slot * 20;
            gui.fill(x, y, x + 20, y + 20, 0x96070308);
            gui.fill(x + 1, y + 1, x + 19, y + 19, 0x8A1B0B16);
            gui.fill(x + 3, y + 4, x + 17, y + 6, 0xE0C08A37);
            gui.fill(x + 5, y + 3, x + 7, y + 5, 0xE0FFD36B);
            gui.fill(x + 9, y + 2, x + 11, y + 5, 0xE0FFD36B);
            gui.fill(x + 13, y + 3, x + 15, y + 5, 0xE0FFD36B);
            gui.fill(x + 6, y + 9, x + 14, y + 15, 0xD03B0B13);
            gui.fill(x + 8, y + 11, x + 12, y + 17, 0xE0E8D8B0);
            gui.fill(x + 4, y + 17, x + 16, y + 18, 0xE0050308);
         }
      }
   }

   private static int withAlpha(int rgb, int alpha) {
      return Mth.clamp(alpha, 0, 255) << 24 | rgb & 0xFFFFFF;
   }

   private static int hash(int seed, int value) {
      int x = seed * 0x45D9F3B ^ value * 0x119DE1F3;
      x ^= x >>> 16;
      x *= 0x45D9F3B;
      x ^= x >>> 16;
      return x;
   }

   private static int displayWidth() {
      return Math.round((float)PANEL_WIDTH * DISPLAY_SCALE);
   }

   private static int displayHeight() {
      return Math.round((float)PANEL_HEIGHT * DISPLAY_SCALE);
   }

   private static String formatSeconds(int ticks) {
      double seconds = Math.max(0, ticks) / 20.0D;
      return seconds <= 3.0D ? String.format(Locale.ROOT, "%.1f", seconds) : Integer.toString((int)Math.ceil(seconds));
   }
}
