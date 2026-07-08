package com.eddyon.tyrant.common.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

public final class TyrantPart extends PartEntity<TyrantEntity> {
   private final TyrantEntity parentMob;
   private final EntityDimensions size;

   public TyrantPart(TyrantEntity parentMob, float width, float height) {
      super(parentMob);
      this.parentMob = parentMob;
      this.size = EntityDimensions.scalable(width, height);
      this.refreshDimensions();
   }

   protected void defineSynchedData(SynchedEntityData.Builder builder) {
   }

   protected void readAdditionalSaveData(CompoundTag compound) {
   }

   protected void addAdditionalSaveData(CompoundTag compound) {
   }

   public boolean isPickable() {
      return this.parentMob.isAlive();
   }

   public ItemStack getPickResult() {
      return this.parentMob.getPickResult();
   }

   public boolean hurt(DamageSource source, float amount) {
      return this.isInvulnerableTo(source) ? false : this.parentMob.hurtPart(this, source, amount);
   }

   public boolean is(Entity entity) {
      return this == entity || this.parentMob == entity;
   }

   public EntityDimensions getDimensions(Pose pose) {
      return this.size;
   }

   public boolean shouldBeSaved() {
      return false;
   }
}
