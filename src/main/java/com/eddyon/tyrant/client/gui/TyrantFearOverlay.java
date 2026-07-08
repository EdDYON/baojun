package com.eddyon.tyrant.client.gui;

import com.eddyon.tyrant.client.feedback.TyrantFearFeedbackManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class TyrantFearOverlay {
   private static final int VOID_SHADE = 0x0A0711;
   private static final int DEEP_PURPLE_SHADE = 0x1A0D22;
   private static final int BLOOD_SHADE = 0x3F111D;
   private static final int ABYSS_CYAN_SHADE = 0x0A6570;

   private TyrantFearOverlay() {
   }

   public static void render(RenderGuiEvent.Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (!minecraft.options.hideGui && minecraft.player != null) {
         float fear = TyrantFearFeedbackManager.getVisualIntensity();
         if (fear > 0.01F) {
            GuiGraphics gui = event.getGuiGraphics();
            int width = minecraft.getWindow().getGuiScaledWidth();
            int height = minecraft.getWindow().getGuiScaledHeight();
            float pulse = TyrantFearFeedbackManager.getPulseIntensity();
            float proximity = TyrantFearFeedbackManager.getProximityPressure();
            long time = Util.getMillis();
            RenderSystem.enableBlend();
            renderColorGrade(gui, width, height, fear, pulse);
            renderPixelVignette(gui, width, height, fear, pulse, proximity, time);
            renderEdgeStatic(gui, width, height, fear, pulse, time);
            renderCornerGlints(gui, width, height, fear, pulse, time);
            RenderSystem.disableBlend();
         }
      }
   }

   private static void renderColorGrade(GuiGraphics gui, int width, int height, float fear, float pulse) {
      int grayAlpha = Mth.clamp((int)(fear * 13.0F), 0, 22);
      int purpleAlpha = Mth.clamp((int)(fear * 10.0F + pulse * 8.0F), 0, 24);
      int bloodAlpha = Mth.clamp((int)(fear * 5.0F + pulse * 10.0F), 0, 20);
      gui.fill(0, 0, width, height, grayAlpha << 24 | VOID_SHADE);
      gui.fill(0, 0, width, height, purpleAlpha << 24 | DEEP_PURPLE_SHADE);
      gui.fill(0, 0, width, height, bloodAlpha << 24 | BLOOD_SHADE);
   }

   private static void renderPixelVignette(GuiGraphics gui, int width, int height, float fear, float pulse, float proximity, long time) {
      int pulseAlpha = (int)((Mth.sin(time / 155.0F) * 0.5F + 0.5F) * pulse * 14.0F);
      int outerAlpha = Mth.clamp((int)(fear * 42.0F + proximity * 20.0F) + pulseAlpha, 0, 78);
      int midAlpha = outerAlpha * 3 / 5;
      int innerAlpha = outerAlpha / 4;
      int minSide = Math.min(width, height);
      int outer = Math.max(14, minSide / 16);
      int mid = Math.max(8, outer / 2);
      int inner = Math.max(3, outer / 5);
      long frame = time / 170L;
      drawIrregularEdge(gui, width, height, outer, frame, outerAlpha, VOID_SHADE, 0);
      drawIrregularEdge(gui, width, height, mid, frame + 11L, midAlpha, DEEP_PURPLE_SHADE, 1);
      drawIrregularEdge(gui, width, height, inner, frame + 23L, innerAlpha, BLOOD_SHADE, 2);
      drawCornerPixels(gui, width, height, outer, outerAlpha, time);
   }

   private static void drawIrregularEdge(GuiGraphics gui, int width, int height, int depth, long frame, int baseAlpha, int shade, int layer) {
      int segment = Math.max(10, depth + layer * 5);
      for(int x = 0; x < width; x += segment) {
         long topHash = hash(frame, 101L + layer * 977L + x);
         int length = segment + (int)(Math.abs(topHash >> 5) % Math.max(3, segment));
         int topDepth = 1 + (int)(Math.abs(topHash >> 13) % Math.max(2, depth + 1));
         int bottomDepth = 1 + (int)(Math.abs(topHash >> 23) % Math.max(2, depth + 1));
         int topAlpha = Mth.clamp(baseAlpha - 16 + (int)(Math.abs(topHash >> 31) % 28L), 0, 96);
         int bottomAlpha = Mth.clamp(baseAlpha - 18 + (int)(Math.abs(topHash >> 37) % 26L), 0, 92);
         if ((topHash & 3L) != 0L) {
            gui.fill(x, 0, Math.min(width, x + length), topDepth, topAlpha << 24 | shade);
         }
         if ((topHash & 7L) != 1L) {
            gui.fill(x, height - bottomDepth, Math.min(width, x + length), height, bottomAlpha << 24 | shade);
         }
      }

      for(int y = 0; y < height; y += segment) {
         long sideHash = hash(frame, 509L + layer * 577L + y);
         int length = segment + (int)(Math.abs(sideHash >> 7) % Math.max(3, segment));
         int leftDepth = 1 + (int)(Math.abs(sideHash >> 15) % Math.max(2, depth + 1));
         int rightDepth = 1 + (int)(Math.abs(sideHash >> 25) % Math.max(2, depth + 1));
         int leftAlpha = Mth.clamp(baseAlpha - 16 + (int)(Math.abs(sideHash >> 33) % 28L), 0, 96);
         int rightAlpha = Mth.clamp(baseAlpha - 18 + (int)(Math.abs(sideHash >> 41) % 26L), 0, 92);
         if ((sideHash & 3L) != 2L) {
            gui.fill(0, y, leftDepth, Math.min(height, y + length), leftAlpha << 24 | shade);
         }
         if ((sideHash & 7L) != 3L) {
            gui.fill(width - rightDepth, y, width, Math.min(height, y + length), rightAlpha << 24 | shade);
         }
      }
   }

   private static void drawCornerPixels(GuiGraphics gui, int width, int height, int outer, int baseAlpha, long time) {
      int tile = Math.max(4, outer / 4);
      int color = Mth.clamp(baseAlpha + 8, 0, 96) << 24 | VOID_SHADE;
      for(int layer = 0; layer < 4; ++layer) {
         int size = tile * (4 - layer);
         int offset = tile * layer;
         gui.fill(offset, offset, offset + size, offset + tile, color);
         gui.fill(offset, offset, offset + tile, offset + size, color);
         gui.fill(width - offset - size, offset, width - offset, offset + tile, color);
         gui.fill(width - offset - tile, offset, width - offset, offset + size, color);
         gui.fill(offset, height - offset - tile, offset + size, height - offset, color);
         gui.fill(offset, height - offset - size, offset + tile, height - offset, color);
         gui.fill(width - offset - size, height - offset - tile, width - offset, height - offset, color);
         gui.fill(width - offset - tile, height - offset - size, width - offset, height - offset, color);
      }

      long frame = time / 180L;
      for(int i = 0; i < 10; ++i) {
         long hash = hash(frame, 401L + (long)i * 53L);
         int corner = (int)(Math.abs(hash >> 4) % 4L);
         int x = (int)(Math.abs(hash >> 12) % Math.max(1L, (long)(outer * 2)));
         int y = (int)(Math.abs(hash >> 20) % Math.max(1L, (long)(outer * 2)));
         if (corner == 1 || corner == 3) {
            x = width - x - tile;
         }
         if (corner >= 2) {
            y = height - y - tile;
         }
         int alpha = Mth.clamp(baseAlpha / 3 + (int)(Math.abs(hash >> 28) % 24L), 0, 62);
         gui.fill(x, y, x + tile, y + tile, alpha << 24 | DEEP_PURPLE_SHADE);
      }
   }

   private static void renderEdgeStatic(GuiGraphics gui, int width, int height, float fear, float pulse, long time) {
      int count = 6 + Math.round(fear * 18.0F + pulse * 10.0F);
      int edgeBand = Math.max(10, Math.min(width, height) / 12);
      long frame = time / 95L;

      for(int i = 0; i < count; ++i) {
         long hash = hash(frame, i * 31L + 7L);
         int size = 1 + (int)(Math.abs(hash >> 5) % 2L);
         int alpha = 14 + (int)(Math.abs(hash >> 13) % 26L);
         int shade = ((hash & 3L) == 0L) ? ABYSS_CYAN_SHADE : (((hash & 1L) == 0L) ? DEEP_PURPLE_SHADE : BLOOD_SHADE);
         int color = alpha << 24 | shade;
         int side = (int)(Math.abs(hash >> 17) % 4L);
         int x;
         int y;
         if (side == 0) {
            x = (int)(Math.abs(hash >> 21) % (long)width);
            y = (int)(Math.abs(hash >> 29) % (long)edgeBand);
         } else if (side == 1) {
            x = (int)(Math.abs(hash >> 21) % (long)width);
            y = height - edgeBand + (int)(Math.abs(hash >> 29) % (long)edgeBand);
         } else if (side == 2) {
            x = (int)(Math.abs(hash >> 21) % (long)edgeBand);
            y = (int)(Math.abs(hash >> 29) % (long)height);
         } else {
            x = width - edgeBand + (int)(Math.abs(hash >> 21) % (long)edgeBand);
            y = (int)(Math.abs(hash >> 29) % (long)height);
         }

         gui.fill(x, y, x + size, y + size, color);
      }
   }

   private static void renderCornerGlints(GuiGraphics gui, int width, int height, float fear, float pulse, long time) {
      if (fear < 0.35F) {
         return;
      }

      int count = 2 + Math.round(pulse * 3.0F);
      int span = Math.max(28, Math.min(width, height) / 5);
      long frame = time / 260L;
      for(int i = 0; i < count; ++i) {
         long hash = hash(frame, 991L + (long)i * 71L);
         int corner = (int)(Math.abs(hash >> 3) % 4L);
         int length = 8 + (int)(Math.abs(hash >> 11) % 18L);
         int x = (int)(Math.abs(hash >> 19) % (long)span);
         int y = (int)(Math.abs(hash >> 27) % (long)span);
         if (corner == 1 || corner == 3) {
            x = width - x - length;
         }
         if (corner >= 2) {
            y = height - y - 2;
         }

         int alpha = Mth.clamp((int)(fear * 26.0F + pulse * 24.0F), 0, 54);
         gui.fill(x, y, x + length, y + 2, alpha << 24 | ABYSS_CYAN_SHADE);
         gui.fill(x + length / 2, y - 2, x + length / 2 + 2, y + 4, (alpha / 2) << 24 | BLOOD_SHADE);
      }
   }

   private static long hash(long a, long b) {
      long x = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
      x ^= x >>> 33;
      x *= 0xFF51AFD7ED558CCDL;
      x ^= x >>> 33;
      x *= 0xC4CEB9FE1A85EC53L;
      x ^= x >>> 33;
      return x;
   }
}
