package com.bladesmelody.naturaldisasters.client;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.sky.SkyZones;
import com.bladesmelody.naturaldisasters.sky.SkyZonesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Receives the sky zones from the server, and drifts ash down around the
 * player under volcanic ash. Sky, fog and light colours come from
 * {@link com.bladesmelody.naturaldisasters.sky.SkyLayers}.
 */
@EventBusSubscriber(modid = NaturalDisasters.MODID, value = Dist.CLIENT)
public final class ClientAsh {
    private ClientAsh() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(SkyZonesPayload.TYPE, (payload, context) -> context.enqueueWork(() -> SkyZones.setClient(payload.zones())));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || Minecraft.getInstance().isPaused()) return;
        if (SkyZones.clientZoneAt(p.level(), SkyZones.Kind.ASH, p.getX(), p.getZ()) == null) return;
        RandomSource r = p.getRandom();
        for (int i = 0; i < 6; i++) {
            p.level().addParticle(r.nextInt(4) == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                    p.getX() + (r.nextDouble() - 0.5) * 24, p.getY() + r.nextDouble() * 10, p.getZ() + (r.nextDouble() - 0.5) * 24,
                    0, -0.05, 0);
        }
    }
}
