package com.bladesmelody.naturaldisasters.ash;

import com.bladesmelody.naturaldisasters.disaster.AshCloud;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-side glue for ash clouds: syncing zones, ash on newly loaded chunks, no sleeping through the dark. */
public final class AshEvents {
    private AshEvents() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(AshEvents::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AshEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(AshEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(AshEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(AshEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(AshEvents::onSleepFinished);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The client registers its handler in ClientSetup.
        event.registrar("1").optional().playToClient(AshZonesPayload.TYPE, AshZonesPayload.CODEC);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, new AshZonesPayload(AshZones.serverZones()));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (AshZones.takeDirty()) PacketDistributor.sendToAllPlayers(new AshZonesPayload(AshZones.serverZones()));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        AshZones.clearServer();
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        for (Disaster d : DisasterData.get(level).active()) {
            if (d instanceof AshCloud cloud) cloud.chunkLoaded(chunk);
        }
    }

    /** Under world-wide ash there is no morning to sleep through to. */
    private static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!AshZones.worldDark()) return;
        event.setCanceled(true);
        if (event.getLevel() instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                p.sendOverlayMessage(Component.translatable("message.naturaldisasters.ash_blots_sun"));
            }
        }
    }
}
