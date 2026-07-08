package com.eddyon.tyrant.common.effect;

import com.eddyon.tyrant.TyrantMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class TimidityEffect extends MobEffect {
   public TimidityEffect() {
      super(MobEffectCategory.HARMFUL, 8075316);
      this.addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "timidity_speed"), -0.14D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      this.addAttributeModifier(Attributes.ATTACK_SPEED, ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "timidity_attack_speed"), -0.24D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
   }
}
