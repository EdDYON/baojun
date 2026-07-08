package com.eddyon.tyrant.client.gui;

import com.eddyon.tyrant.TyrantMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

public final class TyrantBossOverlay {
   private static final String TYRANT_BOSSBAR_KEY = "bossbar.tyrant.tyrant";
   private static final String TYRANT_ENTITY_KEY = "entity.tyrant.tyrant";
   private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "textures/gui/tyrant_boss_bar_bg.png");
   private static final ResourceLocation FILL = ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "textures/gui/tyrant_boss_bar_fill.png");
   private static final ResourceLocation FOREGROUND = ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "textures/gui/tyrant_boss_bar_fg.png");
   private static final int PANEL_WIDTH = 260;
   private static final int PANEL_HEIGHT = 72;
   private static final int FILL_X = 10;
   private static final int FILL_Y = 29;
   private static final int FILL_WIDTH = 239;
   private static final int FILL_HEIGHT = 21;
   private static final int NAME_Y = 15;
   private static final float NAME_SCALE = 1.0F;
   private static final float DISPLAY_SCALE = 0.55F;
   private static final int[] RAGGED_FILL_EDGE = new int[]{-5, -3, -6, -2, 0, -4, -7, -2, -5, -1, -3, -6, -2, -4, 0, -5, -7, -3, -6, -2, -4};
   private static final int NAME_COLOR = 0xFFF0E5CB;
   private static final int NAME_GLOW = 0xFF26202A;

   private TyrantBossOverlay() {
   }

   static int getCommandOverlayY() {
      return Math.round((float)PANEL_HEIGHT * DISPLAY_SCALE) - 2;
   }

   public static void onBossEventProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
      LerpingBossEvent bossEvent = event.getBossEvent();
      if (!isTyrantBoss(bossEvent)) {
         return;
      }

      event.setCanceled(true);
      int scaledWidth = Math.round((float)PANEL_WIDTH * DISPLAY_SCALE);
      int scaledHeight = Math.round((float)PANEL_HEIGHT * DISPLAY_SCALE);
      event.setIncrement(scaledHeight + 6);
      Minecraft minecraft = Minecraft.getInstance();
      GuiGraphics gui = event.getGuiGraphics();
      int x = event.getWindow().getGuiScaledWidth() / 2 - scaledWidth / 2;
      int y = Math.max(0, event.getY() - 6);
      int progress = Math.max(0, Math.min(FILL_WIDTH, Math.round(bossEvent.getProgress() * (float)FILL_WIDTH)));

      gui.pose().pushPose();
      gui.pose().translate((float)x, (float)y, 0.0F);
      gui.pose().scale(DISPLAY_SCALE, DISPLAY_SCALE, 1.0F);
      gui.blit(BACKGROUND, 0, 0, 0, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
      if (progress > 0) {
         renderRaggedFill(gui, progress);
      }

      gui.blit(FOREGROUND, 0, 0, 0, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
      drawCenteredScaled(gui, minecraft.font, bossEvent.getName(), PANEL_WIDTH / 2 + 1, NAME_Y + 1, NAME_SCALE, NAME_GLOW);
      drawCenteredScaled(gui, minecraft.font, bossEvent.getName(), PANEL_WIDTH / 2, NAME_Y, NAME_SCALE, NAME_COLOR);
      gui.pose().popPose();
   }

   private static void renderRaggedFill(GuiGraphics gui, int progress) {
      if (progress >= FILL_WIDTH - 1) {
         gui.blit(FILL, FILL_X, FILL_Y, 0, 0.0F, 0.0F, FILL_WIDTH, FILL_HEIGHT, FILL_WIDTH, FILL_HEIGHT);
         return;
      }

      for(int row = 0; row < FILL_HEIGHT; ++row) {
         int rowWidth = Math.max(0, Math.min(FILL_WIDTH, progress + RAGGED_FILL_EDGE[row % RAGGED_FILL_EDGE.length]));
         if (rowWidth <= 0) {
            continue;
         }

         gui.blit(FILL, FILL_X, FILL_Y + row, 0, 0.0F, (float)row, rowWidth, 1, FILL_WIDTH, FILL_HEIGHT);
      }
   }

   private static void drawCenteredScaled(GuiGraphics gui, Font font, Component text, int centerX, int y, float scale, int color) {
      gui.pose().pushPose();
      gui.pose().translate((float)centerX, (float)y, 0.0F);
      gui.pose().scale(scale, scale, 1.0F);
      gui.drawString(font, text, -font.width(text) / 2, 0, color, false);
      gui.pose().popPose();
   }

   private static boolean isTyrantBoss(LerpingBossEvent bossEvent) {
      if (bossEvent.getName().getContents() instanceof TranslatableContents contents) {
         String key = contents.getKey();
         if (TYRANT_BOSSBAR_KEY.equals(key) || TYRANT_ENTITY_KEY.equals(key)) {
            return true;
         }
      }

      String text = bossEvent.getName().getString().trim();
      return "Tyrant".equalsIgnoreCase(text);
   }
}
