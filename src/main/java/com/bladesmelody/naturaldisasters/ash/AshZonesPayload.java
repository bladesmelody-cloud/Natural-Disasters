package com.bladesmelody.naturaldisasters.ash;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client: the full list of ash zones. */
public record AshZonesPayload(List<AshZones.Zone> zones) implements CustomPacketPayload {
    public static final Type<AshZonesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "ash_zones"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AshZonesPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.zones.size());
                for (AshZones.Zone z : p.zones) {
                    buf.writeDouble(z.x());
                    buf.writeDouble(z.z());
                    buf.writeVarInt(z.radius());
                    buf.writeVarInt(z.vei());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<AshZones.Zone> zones = new ArrayList<>(n);
                for (int i = 0; i < n; i++) zones.add(new AshZones.Zone(buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt()));
                return new AshZonesPayload(zones);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
