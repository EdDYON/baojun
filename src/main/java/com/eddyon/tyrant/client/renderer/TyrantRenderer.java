package com.eddyon.tyrant.client.renderer;

import com.eddyon.tyrant.client.model.TyrantModel;
import com.eddyon.tyrant.common.entity.TyrantAction;
import com.eddyon.tyrant.common.entity.TyrantEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class TyrantRenderer extends GeoEntityRenderer<TyrantEntity> {
   public TyrantRenderer(EntityRendererProvider.Context context) {
      super(context, new TyrantModel());
      this.shadowRadius = 1.8F;
   }

   protected float getDeathMaxRotation(TyrantEntity animatable) {
      return 0.0F;
   }

   public int getPackedOverlay(TyrantEntity animatable, float u, float partialTick) {
      return animatable.deathTime <= 0 && animatable.getCurrentAction() != TyrantAction.DEATH ? super.getPackedOverlay(animatable, u, partialTick) : super.getPackedOverlay(animatable, 0.0F, partialTick);
   }

   public void render(TyrantEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
      int adjustedPackedLight = entity.isDeadOrDying() ? LightTexture.pack(15, 15) : packedLight;
      super.render(entity, entityYaw, partialTick, poseStack, bufferSource, adjustedPackedLight);
   }
}
