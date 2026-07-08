package com.eddyon.tyrant.common.config;

import java.lang.reflect.Field;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class TyrantAttributeBounds {
   private static final double MAX_HEALTH_LIMIT = 1000000.0D;

   private TyrantAttributeBounds() {
   }

   public static void expandVanillaBounds() {
      try {
         Field maxValue = RangedAttribute.class.getDeclaredField("maxValue");
         maxValue.setAccessible(true);
         expand(maxValue, Attributes.MAX_HEALTH.value(), MAX_HEALTH_LIMIT);
      } catch (ReflectiveOperationException exception) {
         throw new IllegalStateException("Unable to expand Tyrant attribute bounds", exception);
      }
   }

   private static void expand(Field maxValue, Attribute attribute, double limit) throws IllegalAccessException {
      if (attribute instanceof RangedAttribute rangedAttribute && rangedAttribute.getMaxValue() < limit) {
         maxValue.setDouble(rangedAttribute, limit);
      }
   }
}
