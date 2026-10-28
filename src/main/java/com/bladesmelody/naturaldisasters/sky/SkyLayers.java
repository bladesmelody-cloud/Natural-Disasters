package com.bladesmelody.naturaldisasters.sky;

import java.util.function.ToDoubleFunction;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Adds positional layers to the 26.1 environment-attribute system (the same
 * mechanism vanilla uses to grey the sky in rain), so inside a sky zone the
 * sky, fog, clouds and light take on the disaster's look and blend out at
 * the zone's edge. Added to every level by {@code EnvironmentAttributeSystemMixin}.
 */
public final class SkyLayers {
    /** How a kind of zone looks at full strength. fogEnd/skyFogEnd: how far you can see (blocks). */
    private record Look(int sky, int fog, int cloud, float fogEnd, float skyFogEnd, float lightFactor, float sunset) {}

    private static Look look(SkyZones.Zone z) {
        return switch (z.kind()) {
            // Black ash; thicker for bigger eruptions.
            case ASH -> new Look(0x141210, 0x211e1b, 0x1a1816, Math.max(24, 96 - 10 * z.strength()), 16, 0.25F, 0);
            case HURRICANE -> new Look(0x3a3f46, 0x4d535a, 0x2c3035, 96, 64, 0.6F, 0.2F);
            // Whiteout.
            case BLIZZARD -> new Look(0xc9ced6, 0xdde2e9, 0xe6eaef, Math.max(32, 96 - 6 * z.strength()), 40, 0.8F, 0.3F);
            // Sickly yellow-green.
            case ACID_RAIN -> new Look(0x8f9a52, 0x86924a, 0x737d3c, 128, 96, 0.75F, 0.4F);
            // Hot, hazy, washed out.
            case DROUGHT -> new Look(0xd9c9a2, 0xe2cf9a, 0xf0e6cc, 192, 160, 1.0F, 1.0F);
        };
    }

    private SkyLayers() {}

    public static void add(EnvironmentAttributeSystem.Builder builder, Level level) {
        color(builder, level, EnvironmentAttributes.SKY_COLOR, Look::sky);
        color(builder, level, EnvironmentAttributes.FOG_COLOR, Look::fog);
        color(builder, level, EnvironmentAttributes.CLOUD_COLOR, Look::cloud);
        lessOf(builder, level, EnvironmentAttributes.FOG_END_DISTANCE, Look::fogEnd);
        lessOf(builder, level, EnvironmentAttributes.SKY_FOG_END_DISTANCE, Look::skyFogEnd);
        lessOf(builder, level, EnvironmentAttributes.CLOUD_FOG_END_DISTANCE, l -> l.skyFogEnd() * 2);
        scaled(builder, level, EnvironmentAttributes.SKY_LIGHT_FACTOR, Look::lightFactor);
        scaled(builder, level, EnvironmentAttributes.STAR_BRIGHTNESS, l -> l.sunset());
        builder.addPositionalLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (base, pos, interp) -> {
            float keep = 1;
            for (SkyZones.Zone z : zonesHere(level)) keep *= 1 - z.weight(pos.x, pos.z) * (1 - look(z).sunset());
            return ARGB.color(Math.round(ARGB.alpha(base) * keep), base & 0xFFFFFF);
        });
        // A hurricane's eye: the sky opens up, brighter and bluer than the storm around it.
        eye(builder, level, EnvironmentAttributes.SKY_COLOR, 0x8fb3d9, 0.75F);
        eye(builder, level, EnvironmentAttributes.FOG_COLOR, 0xa9bccf, 0.6F);
        eye(builder, level, EnvironmentAttributes.CLOUD_COLOR, 0xd2d6dc, 0.5F);
        builder.addPositionalLayer(EnvironmentAttributes.SKY_LIGHT_FACTOR, (base, pos, interp) -> {
            float e = eyeAt(level, pos);
            return e > 0 ? base + (1 - base) * 0.6F * e : base;
        });
        // Monsters don't burn in the sun under ash (the gameplay side of "real darkness").
        builder.addPositionalLayer(EnvironmentAttributes.MONSTERS_BURN, (base, pos, interp) -> base && ashWeight(level, pos) < 0.5F);
    }

    private static Iterable<SkyZones.Zone> zonesHere(Level level) {
        return level.dimension() == Level.OVERWORLD ? SkyZones.zones(level.isClientSide()) : java.util.List.of();
    }

    private static float ashWeight(Level level, Vec3 pos) {
        float w = 0;
        for (SkyZones.Zone z : zonesHere(level)) if (z.kind() == SkyZones.Kind.ASH) w = Math.max(w, z.weight(pos.x, pos.z));
        return w;
    }

    private static float eyeAt(Level level, Vec3 pos) {
        float e = 0;
        for (SkyZones.Zone z : zonesHere(level)) if (z.kind() == SkyZones.Kind.HURRICANE) e = Math.max(e, z.inHole(pos.x, pos.z));
        return e;
    }

    private static void eye(EnvironmentAttributeSystem.Builder builder, Level level, EnvironmentAttribute<Integer> attribute,
                            int color, float amount) {
        builder.addPositionalLayer(attribute, (base, pos, interp) -> {
            float e = eyeAt(level, pos);
            return e > 0 ? ARGB.srgbLerp(e * amount, base, 0xFF000000 | color) : base;
        });
    }

    private static void color(EnvironmentAttributeSystem.Builder builder, Level level, EnvironmentAttribute<Integer> attribute,
                              java.util.function.ToIntFunction<Look> target) {
        builder.addPositionalLayer(attribute, (base, pos, interp) -> {
            int c = base;
            for (SkyZones.Zone z : zonesHere(level)) {
                float w = z.weight(pos.x, pos.z);
                if (w > 0) c = ARGB.srgbLerp(w, c, 0xFF000000 | target.applyAsInt(look(z)));
            }
            return c;
        });
    }

    /** Blends toward a smaller value (fog distances): you never see further because of a zone. */
    private static void lessOf(EnvironmentAttributeSystem.Builder builder, Level level, EnvironmentAttribute<Float> attribute,
                               ToDoubleFunction<Look> target) {
        builder.addPositionalLayer(attribute, (base, pos, interp) -> {
            float v = base;
            for (SkyZones.Zone z : zonesHere(level)) {
                float w = z.weight(pos.x, pos.z);
                float t = (float) target.applyAsDouble(look(z));
                if (w > 0 && t < v) v = v + (t - v) * w;
            }
            return v;
        });
    }

    /** Multiplies by a factor (light, stars), blended by the zone weight. */
    private static void scaled(EnvironmentAttributeSystem.Builder builder, Level level, EnvironmentAttribute<Float> attribute,
                               ToDoubleFunction<Look> factor) {
        builder.addPositionalLayer(attribute, (base, pos, interp) -> {
            float v = base;
            for (SkyZones.Zone z : zonesHere(level)) {
                float w = z.weight(pos.x, pos.z);
                if (w > 0) v *= 1 - w * (1 - (float) factor.applyAsDouble(look(z)));
            }
            return v;
        });
    }
}
