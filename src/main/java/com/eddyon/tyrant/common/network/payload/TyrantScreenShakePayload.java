package com.eddyon.tyrant.common.network.payload;

import com.eddyon.tyrant.TyrantMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TyrantScreenShakePayload(double x, double y, double z, float radius, float strength, int durationTicks) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<TyrantScreenShakePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "tyrant_screen_shake"));
   public static final StreamCodec<RegistryFriendlyByteBuf, TyrantScreenShakePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.DOUBLE, TyrantScreenShakePayload::x, ByteBufCodecs.DOUBLE, TyrantScreenShakePayload::y, ByteBufCodecs.DOUBLE, TyrantScreenShakePayload::z, ByteBufCodecs.FLOAT, TyrantScreenShakePayload::radius, ByteBufCodecs.FLOAT, TyrantScreenShakePayload::strength, ByteBufCodecs.VAR_INT, TyrantScreenShakePayload::durationTicks, TyrantScreenShakePayload::new);

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
