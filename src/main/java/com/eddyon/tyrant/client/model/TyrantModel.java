package com.eddyon.tyrant.client.model;

import com.eddyon.tyrant.TyrantMod;
import com.eddyon.tyrant.common.entity.TyrantEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class TyrantModel extends GeoModel<TyrantEntity> {
   private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "geo/tyrant.geo.json");
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "textures/entity/tyrant.png");
   private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "animations/tyrant.animation.json");

   public ResourceLocation getModelResource(TyrantEntity animatable) {
      return MODEL;
   }

   public ResourceLocation getTextureResource(TyrantEntity animatable) {
      return TEXTURE;
   }

   public ResourceLocation getAnimationResource(TyrantEntity animatable) {
      return ANIMATION;
   }
}
