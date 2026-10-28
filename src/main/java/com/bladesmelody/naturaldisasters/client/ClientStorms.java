package com.bladesmelody.naturaldisasters.client;

import com.bladesmelody.naturaldisasters.entity.HurricaneEntity;
import com.bladesmelody.naturaldisasters.entity.TornadoEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Client particle effects for tornadoes and hurricanes. */
public final class ClientStorms {
    static SpriteSet cloudSprites;

    private ClientStorms() {}

    /** A dense dark cone of big cloud puffs, debris kicked up at its foot. */
    static void tornado(TornadoEntity e) {
        if (cloudSprites == null) return;
        ClientLevel level = (ClientLevel) e.level();
        RandomSource r = level.getRandom();
        float radius = e.radius(), height = e.funnelHeight();
        float sizeScale = Math.clamp(radius / 6F, 0.8F, 4F);
        int count = 6 + (int) (radius / 2);
        for (int i = 0; i < count; i++) {
            double t = Math.pow(r.nextDouble(), 1.3) * 0.9;
            float shade = (float) (t < 0.12 ? 0.5 + r.nextDouble() * 0.1 : 0.38 + r.nextDouble() * 0.2);
            float size = (float) ((1.0 + 2.5 * t) * sizeScale * (0.8 + r.nextDouble() * 0.4));
            add(new CloudParticle(level, e, CloudParticle.Mode.FUNNEL, r.nextDouble() * Math.PI * 2, t, radius, height,
                    size, shade, 0.85F, 80 + r.nextInt(60), cloudSprites));
        }
        // The wall cloud: wide, dark, slow puffs at the top.
        if (r.nextInt(2) == 0) {
            add(new CloudParticle(level, e, CloudParticle.Mode.BAND, r.nextDouble() * Math.PI * 2, radius * (1.5 + r.nextDouble() * 2.5),
                    radius, height, (float) (4 * sizeScale + r.nextDouble() * 3), 0.25F, 0.9F, 160, cloudSprites));
        }
        // Dust and debris at the ground.
        BlockPos below = BlockPos.containing(e.getX(), e.getY() - 1, e.getZ());
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, below.getX(), below.getZ()) - 1;
        BlockState ground = level.getBlockState(below.atY(groundY));
        if (!ground.isAir()) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < 6 + radius / 2; i++) {
                double a = r.nextDouble() * Math.PI * 2, w = radius * (0.5 + r.nextDouble());
                level.addAlwaysVisibleParticle(dust, true, e.getX() + Math.cos(a) * w, groundY + 1 + r.nextDouble() * 3,
                        e.getZ() + Math.sin(a) * w, -Math.sin(a) * 0.6, 0.4 + r.nextDouble() * 0.4, Math.cos(a) * 0.6);
            }
        }
    }

    /**
     * Spiral cloud bands overhead around a calm eye, only near the camera
     * (a hurricane is hundreds of blocks wide).
     */
    static void hurricane(HurricaneEntity e) {
        if (cloudSprites == null) return;
        ClientLevel level = (ClientLevel) e.level();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        double eye = e.eyeRadius(), storm = e.stormRadius();
        double camDist = Math.hypot(cam.x - e.getX(), cam.z - e.getZ());
        if (camDist > storm + 160) return;
        RandomSource r = level.getRandom();
        // Low enough to show through the storm's fog (it ends ~96 blocks out).
        float base = (float) (Math.max(cam.y, level.getSeaLevel()) - e.getY()) + 28;
        for (int i = 0; i < 26; i++) {
            double px = cam.x + (r.nextDouble() - 0.5) * 200, pz = cam.z + (r.nextDouble() - 0.5) * 200;
            double dx = px - e.getX(), dz = pz - e.getZ();
            double d = Math.hypot(dx, dz);
            if (d < eye || d > storm) continue; // the eye stays clear
            double a = Math.atan2(dz, dx);
            boolean eyewall = d < eye * 2.2;
            // Two spiral arms; the eyewall is solid.
            double arm = 0.5 + 0.5 * Math.cos(2 * a - d / 25.0);
            if (!eyewall && r.nextDouble() > arm * arm) continue;
            float size = (float) (eyewall ? 12 + r.nextDouble() * 8 : 9 + r.nextDouble() * 7);
            float shade = (float) (eyewall ? 0.16 + r.nextDouble() * 0.08 : 0.22 + r.nextDouble() * 0.12);
            add(new CloudParticle(level, e, CloudParticle.Mode.BAND, a, d, (float) storm, base + (float) (r.nextDouble() * 16),
                    size, shade, 0.95F, 200 + r.nextInt(80), cloudSprites));
        }
    }

    private static void add(CloudParticle p) {
        Minecraft.getInstance().particleEngine.add(p);
    }
}
