package com.eddyon.tyrant.client.gui;

import com.eddyon.tyrant.client.feedback.TyrantExecutionState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

public final class TyrantExecutionOverlay {
   private TyrantExecutionOverlay() {
   }

   public static void render(RenderGuiEvent.Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.options.hideGui || minecraft.player == null || !TyrantExecutionState.isVisible()) {
         return;
      }

      GuiGraphics gui = event.getGuiGraphics();
      int width = minecraft.getWindow().getGuiScaledWidth();
      int height = minecraft.getWindow().getGuiScaledHeight();
      float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
      float progress = TyrantExecutionState.progress(partialTick);
      float fadeIn = Mth.clamp((1.0F - progress) / 0.18F, 0.0F, 1.0F);
      float fadeOut = Mth.clamp(progress / 0.28F, 0.0F, 1.0F);
      int alpha = Mth.clamp(Math.round(255.0F * fadeIn * fadeOut), 0, 255);
      if (alpha <= 0) {
         return;
      }

      RenderSystem.enableBlend();
      renderText(gui, minecraft.font, width, height, progress, alpha);
      RenderSystem.disableBlend();
   }

   private static void renderText(GuiGraphics gui, Font font, int width, int height, float progress, int alpha) {
      int centerX = width / 2;
      int centerY = height / 2;
      float pulse = 1.0F + (float)Math.sin((double)progress * Math.PI * 9.0D) * 0.025F;
      Component title = Component.translatable("command.tyrant.execution.title");
      Component quote = Component.translatable("command.tyrant.execution.quote." + TyrantExecutionState.quoteIndex());
      int titleColor = withAlpha(0xD5222B, alpha);
      int quoteColor = withAlpha(0xF0C6C6, Math.round((float)alpha * 0.9F));
      int blackShadow = withAlpha(0x050207, Math.round((float)alpha * 0.88F));
      drawCenteredScaled(gui, font, title, centerX + 1, centerY - 18, 2.15F * pulse, blackShadow);
      drawCenteredScaled(gui, font, title, centerX, centerY - 19, 2.15F * pulse, titleColor);
      drawCenteredScaled(gui, font, quote, centerX + 1, centerY + 13, 1.05F, blackShadow);
      drawCenteredScaled(gui, font, quote, centerX, centerY + 12, 1.05F, quoteColor);
   }

   private static void drawCenteredScaled(GuiGraphics gui, Font font, Component text, int centerX, int y, float scale, int color) {
      gui.pose().pushPose();
      gui.pose().translate((float)centerX, (float)y, 0.0F);
      gui.pose().scale(scale, scale, 1.0F);
      gui.drawString(font, text, -font.width(text) / 2, 0, color, false);
      gui.pose().popPose();
   }

   private static int withAlpha(int rgb, int alpha) {
      return Mth.clamp(alpha, 0, 255) << 24 | rgb & 0xFFFFFF;
   }
}
