package com.eddyon.tyrant.common.network.payload;

import com.eddyon.tyrant.TyrantMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TyrantCommandHudPayload(int commandId, int elapsedTicks, int totalTicks, int graceTicks, int lockedHotbarMask) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<TyrantCommandHudPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TyrantMod.MODID, "tyrant_command_hud"));
   public static final StreamCodec<RegistryFriendlyByteBuf, TyrantCommandHudPayload> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT,
      TyrantCommandHudPayload::commandId,
      ByteBufCodecs.VAR_INT,
      TyrantCommandHudPayload::elapsedTicks,
      ByteBufCodecs.VAR_INT,
      TyrantCommandHudPayload::totalTicks,
      ByteBufCodecs.VAR_INT,
      TyrantCommandHudPayload::graceTicks,
      ByteBufCodecs.VAR_INT,
      TyrantCommandHudPayload::lockedHotbarMask,
      TyrantCommandHudPayload::new
   );

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}
