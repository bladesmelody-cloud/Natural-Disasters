package com.bladesmelody.naturaldisasters.client;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.registry.ModRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import com.bladesmelody.naturaldisasters.entity.HurricaneEntity;
import com.bladesmelody.naturaldisasters.entity.TornadoEntity;

/** Client-only registration: renderers, the cloud particle, storm effects. */
@EventBusSubscriber(modid = NaturalDisasters.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistry.FUNNEL_CLOUD.get(), sprites -> {
            ClientStorms.cloudSprites = sprites;
            TornadoEntity.clientEffects = ClientStorms::tornado;
            HurricaneEntity.clientEffects = ClientStorms::hurricane;
            // For /particle and the like: a lone puff drifting with its velocity.
            return (type, level, x, y, z, xd, yd, zd, random) -> null;
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.TORNADO.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModRegistry.HURRICANE.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModRegistry.METEOR.get(), MeteorRenderer::new);
    }
}
