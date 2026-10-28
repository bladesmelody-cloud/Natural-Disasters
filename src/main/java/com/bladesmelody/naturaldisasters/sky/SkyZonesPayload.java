package com.bladesmelody.naturaldisasters.sky;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server → client: the full list of sky zones. */
public record SkyZonesPayload(List<SkyZones.Zone> zones) implements CustomPacketPayload {
    public static final Type<SkyZonesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "sky_zones"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkyZonesPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.zones.size());
                for (SkyZones.Zone z : p.zones) {
                    buf.writeVarInt(z.kind().ordinal());
                    buf.writeDouble(z.x());
                    buf.writeDouble(z.z());
                    buf.writeVarInt(z.radius());
                    buf.writeVarInt(z.strength());
                    buf.writeVarInt(z.hole());
                }
            },
            buf -> {
                int n = buf.readVarInt();
                List<SkyZones.Zone> zones = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    zones.add(new SkyZones.Zone(SkyZones.Kind.byId(buf.readVarInt()), buf.readDouble(), buf.readDouble(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                }
                return new SkyZonesPayload(zones);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
