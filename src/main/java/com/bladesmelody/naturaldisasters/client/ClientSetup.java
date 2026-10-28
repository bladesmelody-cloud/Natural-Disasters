package com.bladesmelody.naturaldisasters.client;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.registry.ModRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registration. Meteor and tornado are drawn by their particles for now (models later). */
@EventBusSubscriber(modid = NaturalDisasters.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.METEOR.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModRegistry.TORNADO.get(), NoopRenderer::new);
    }
}
