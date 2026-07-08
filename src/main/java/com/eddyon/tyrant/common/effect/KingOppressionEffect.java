package com.eddyon.tyrant.common.effect;

import com.eddyon.tyrant.TyrantMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class KingOppressionEffect extends MobEffect {
   public KingOppressionEffect() {
      super(MobEffectCategory.HARMFUL, 4592691);
      this.addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "king_oppression_speed"), -0.08D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
      this.addAttributeModifier(Attributes.ATTACK_SPEED, ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "king_oppression_attack_speed"), -0.12D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
   }
}
