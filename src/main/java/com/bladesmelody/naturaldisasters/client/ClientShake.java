package com.bladesmelody.naturaldisasters.client;

import com.bladesmelody.naturaldisasters.ClientConfig;
import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.shake.ShakePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Camera shake: jitters yaw, pitch and roll with smooth noise that dies away. */
@EventBusSubscriber(modid = NaturalDisasters.MODID, value = Dist.CLIENT)
public final class ClientShake {
    private static float intensity;
    private static int ticksLeft;
    private static int totalTicks = 1;

    private ClientShake() {}

    @SubscribeEvent
    public static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(ShakePayload.TYPE, (payload, context) -> context.enqueueWork(() -> {
            // A stronger shake takes over; a weaker one just keeps the current one going.
            if (payload.intensity() >= intensity * ticksLeft / (float) totalTicks) {
                intensity = payload.intensity();
                totalTicks = Math.max(1, payload.ticks());
            }
            ticksLeft = Math.max(ticksLeft, payload.ticks());
        }));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (ticksLeft > 0 && !Minecraft.getInstance().isPaused()) ticksLeft--;
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (ticksLeft <= 0 || !ClientConfig.CAMERA_SHAKE.getAsBoolean()) return;
        float t = (float) ((Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + event.getPartialTick());
        float amount = intensity * Math.min(1, ticksLeft / (float) totalTicks + 0.2F) * (float) ClientConfig.CAMERA_SHAKE_STRENGTH.getAsDouble();
        event.setYaw(event.getYaw() + amount * noise(t, 1.7F));
        event.setPitch(event.getPitch() + amount * noise(t, 2.3F));
        event.setRoll(event.getRoll() + amount * 0.6F * noise(t, 3.1F));
    }

    /** Cheap smooth noise in -1..1 from a few sines. */
    private static float noise(float t, float seed) {
        return (float) (Math.sin(t * 1.9 + seed) * 0.5 + Math.sin(t * 3.7 + seed * 2) * 0.3 + Math.sin(t * 7.3 + seed * 3) * 0.2);
    }
}
