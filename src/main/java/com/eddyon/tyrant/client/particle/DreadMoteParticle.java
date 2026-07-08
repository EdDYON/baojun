package com.eddyon.tyrant.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class DreadMoteParticle extends TextureSheetParticle {
   private final SpriteSet sprites;
   private final float baseSize;

   protected DreadMoteParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
      super(level, x, y, z, xSpeed, ySpeed, zSpeed);
      this.sprites = sprites;
      this.friction = 0.92F;
      this.gravity = -0.005F;
      this.xd = xSpeed;
      this.yd = ySpeed;
      this.zd = zSpeed;
      this.baseSize = 0.18F + this.random.nextFloat() * 0.22F;
      this.quadSize = this.baseSize;
      this.lifetime = 18 + this.random.nextInt(12);
      this.alpha = 0.0F;
      this.rCol = 0.22F + this.random.nextFloat() * 0.08F;
      this.gCol = 0.10F + this.random.nextFloat() * 0.06F;
      this.bCol = 0.18F + this.random.nextFloat() * 0.1F;
      this.setSpriteFromAge(sprites);
   }

   public void tick() {
      super.tick();
      this.setSpriteFromAge(this.sprites);
      float progress = (float)this.age / (float)this.lifetime;
      float fadeIn = Mth.clamp(progress / 0.18F, 0.0F, 1.0F);
      float fadeOut = 1.0F - Mth.clamp((progress - 0.58F) / 0.42F, 0.0F, 1.0F);
      this.alpha = fadeIn * fadeOut * 0.74F;
   }

   public float getQuadSize(float partialTick) {
      float progress = ((float)this.age + partialTick) / (float)this.lifetime;
      return this.baseSize * (0.75F + Mth.sin(progress * 3.1415927F) * 0.9F);
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
         return new DreadMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
      }
   }
}
