package com.bladesmelody.naturaldisasters.shake;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client: shake the camera with this strength (degrees) for this many ticks. */
public record ShakePayload(float intensity, int ticks) implements CustomPacketPayload {
    public static final Type<ShakePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "shake"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShakePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ShakePayload::intensity, ByteBufCodecs.VAR_INT, ShakePayload::ticks, ShakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
