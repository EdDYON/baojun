package com.eddyon.tyrant.common.network.payload;

import com.eddyon.tyrant.TyrantMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TyrantExecutionPayload(int quoteIndex, int durationTicks) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<TyrantExecutionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "tyrant_execution"));
   public static final StreamCodec<RegistryFriendlyByteBuf, TyrantExecutionPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT,
      TyrantExecutionPayload::quoteIndex,
      ByteBufCodecs.VAR_INT,
      TyrantExecutionPayload::durationTicks,
      TyrantExecutionPayload::new
   );

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
