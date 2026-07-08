package com.eddyon.tyrant.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class FearStaticParticle extends TextureSheetParticle {
   private final SpriteSet sprites;
   private final float baseSize;

   protected FearStaticParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
      super(level, x, y, z, xSpeed, ySpeed, zSpeed);
      this.sprites = sprites;
      this.friction = 0.78F;
      this.gravity = 0.0F;
      this.xd = xSpeed;
      this.yd = ySpeed;
      this.zd = zSpeed;
      this.baseSize = 0.12F + this.random.nextFloat() * 0.15F;
      this.quadSize = this.baseSize;
      this.lifetime = 8 + this.random.nextInt(6);
      this.alpha = 0.0F;
      this.rCol = 0.58F + this.random.nextFloat() * 0.22F;
      this.gCol = 0.08F + this.random.nextFloat() * 0.08F;
      this.bCol = 0.12F + this.random.nextFloat() * 0.08F;
      this.setSpriteFromAge(sprites);
   }

   public void tick() {
      super.tick();
      this.setSpriteFromAge(this.sprites);
      float progress = (float)this.age / (float)this.lifetime;
      float pulse = progress < 0.45F ? 1.0F : 1.0F - (progress - 0.45F) / 0.55F;
      this.alpha = Mth.clamp(pulse * (this.random.nextBoolean() ? 0.82F : 0.58F), 0.0F, 0.82F);
   }

   public float getQuadSize(float partialTick) {
      float progress = ((float)this.age + partialTick) / (float)this.lifetime;
      return this.baseSize * (1.45F - progress * 0.72F);
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   public static final class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet sprites;

      public Provider(SpriteSet sprites) {
         this.sprites = sprites;
      }

      public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
         return new FearStaticParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
      }
   }
}
