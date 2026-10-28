package com.bladesmelody.naturaldisasters.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.world.entity.Entity;

/**
 * A big soft cloud puff that orbits a disaster's visual entity, like
 * Weather2's funnel particles: in a tornado it spirals up the funnel cone, in
 * a hurricane it drifts round the eye in the cloud bands overhead. Removes
 * itself when its storm is gone.
 */
public class CloudParticle extends SingleQuadParticle {
    public enum Mode { FUNNEL, BAND }

    private final Entity anchor;
    private final Mode mode;
    private double angle;
    /** FUNNEL: height fraction 0–1. BAND: distance from the eye. */
    private double t;
    private final double spin;
    private final float stormRadius;
    private final float height;
    private final float baseAlpha;

    public CloudParticle(ClientLevel level, Entity anchor, Mode mode, double angle, double t, float stormRadius, float height,
                         float size, float shade, float alpha, int lifetime, SpriteSet sprites) {
        super(level, anchor.getX(), anchor.getY(), anchor.getZ(), sprites.get(level.getRandom()));
        this.anchor = anchor;
        this.mode = mode;
        this.angle = angle;
        this.t = t;
        this.stormRadius = stormRadius;
        this.height = height;
        this.quadSize = size;
        this.rCol = shade;
        this.gCol = shade * 0.98F;
        this.bCol = shade * 0.96F;
        this.baseAlpha = alpha;
        this.alpha = 0;
        this.lifetime = lifetime;
        this.hasPhysics = false;
        this.gravity = 0;
        this.roll = (float) (random.nextDouble() * Math.PI * 2);
        this.oRoll = roll;
        this.spin = mode == Mode.FUNNEL
                ? 0.22 / Math.max(1, Math.sqrt(stormRadius / 6.0))
                : 0.0025 * Math.max(1, 120.0 / Math.max(20, t));
        place();
        xo = x;
        yo = y;
        zo = z;
    }

    private void place() {
        double r, y;
        if (mode == Mode.FUNNEL) {
            r = stormRadius * (0.3 + 1.2 * Math.pow(t, 1.8));
            y = anchor.getY() + t * height;
        } else {
            r = t;
            y = anchor.getY() + height;
        }
        setPos(anchor.getX() + Math.cos(angle) * r, y, anchor.getZ() + Math.sin(angle) * r);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime || anchor.isRemoved()) {
            remove();
            return;
        }
        if (mode == Mode.FUNNEL) {
            angle += spin * (1.2 - 0.6 * t);
            t = Math.min(1.05, t + 0.004);
        } else {
            angle += spin;
            t = Math.max(0, t - 0.03); // slowly spiralling in
        }
        roll += 0.01F;
        place();
        float fadeIn = Math.min(1, age / 12F), fadeOut = Math.min(1, (lifetime - age) / 20F);
        alpha = baseAlpha * Math.min(fadeIn, fadeOut);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
