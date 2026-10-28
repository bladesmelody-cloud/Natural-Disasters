package com.bladesmelody.naturaldisasters.client;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.ash.AshZones;
import com.bladesmelody.naturaldisasters.ash.AshZonesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** The look of an ash cloud: near-black fog that hides the sky, and ash drifting down. */
@EventBusSubscriber(modid = NaturalDisasters.MODID, value = Dist.CLIENT)
public final class ClientAsh {
    private ClientAsh() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(AshZonesPayload.TYPE, (payload, context) -> context.enqueueWork(() -> AshZones.setClient(payload.zones())));
    }

    private static AshZones.Zone zoneHere() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        return AshZones.clientZoneAt(p.level(), p.getX(), p.getZ());
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        if (zoneHere() == null) return;
        event.setRed(event.getRed() * 0.15F + 0.06F);
        event.setGreen(event.getGreen() * 0.15F + 0.055F);
        event.setBlue(event.getBlue() * 0.15F + 0.05F);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        AshZones.Zone zone = zoneHere();
        if (zone == null) return;
        // Thicker the bigger the eruption.
        float far = Math.max(24F, 96F - 10F * zone.vei());
        if (event.getFarPlaneDistance() > far) {
            event.setFarPlaneDistance(far);
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1F));
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || zoneHere() == null || Minecraft.getInstance().isPaused()) return;
        RandomSource r = p.getRandom();
        for (int i = 0; i < 6; i++) {
            p.level().addParticle(r.nextInt(4) == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                    p.getX() + (r.nextDouble() - 0.5) * 24, p.getY() + r.nextDouble() * 10, p.getZ() + (r.nextDouble() - 0.5) * 24,
                    0, -0.05, 0);
        }
    }
}
