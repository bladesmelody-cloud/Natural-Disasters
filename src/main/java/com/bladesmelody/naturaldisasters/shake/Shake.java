package com.bladesmelody.naturaldisasters.shake;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends camera shake to players near something violent: stronger the closer they are. */
public final class Shake {
    private Shake() {}

    public static void near(ServerLevel level, double x, double z, double radius, float intensity, int ticks) {
        double r2 = radius * radius;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - x, dz = p.getZ() - z;
            double d2 = dx * dx + dz * dz;
            if (d2 > r2) continue;
            float falloff = (float) (1 - Math.sqrt(d2) / radius);
            float strength = intensity * (0.3F + 0.7F * falloff);
            if (strength > 0.02F) PacketDistributor.sendToPlayer(p, new ShakePayload(strength, ticks));
        }
    }
}
