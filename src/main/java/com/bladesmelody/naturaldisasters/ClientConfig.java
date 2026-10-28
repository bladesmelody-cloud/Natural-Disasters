package com.bladesmelody.naturaldisasters;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player client settings (config/naturaldisasters-client.toml). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE_STRENGTH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        CAMERA_SHAKE = b.comment("Shake the camera during earthquakes, meteor impacts and eruptions.").define("cameraShake", true);
        CAMERA_SHAKE_STRENGTH = b.comment("Multiplier for camera shake.").defineInRange("cameraShakeStrength", 1.0, 0.0, 3.0);
        SPEC = b.build();
    }

    private ClientConfig() {}
}
