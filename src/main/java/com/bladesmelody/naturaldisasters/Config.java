package com.bladesmelody.naturaldisasters;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (per world, in serverconfig/naturaldisasters-server.toml). */
public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue TARGET_TICK_MS;
    public static final ModConfigSpec.IntValue MAX_WORK_MS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_FALLING_BLOCKS_PER_SECOND;

    public static final ModConfigSpec.BooleanValue RANDOM_ENABLED;
    public static final ModConfigSpec.DoubleValue RANDOM_INTERVAL_DAYS;
    public static final ModConfigSpec.IntValue WARNING_SECONDS;
    public static final ModConfigSpec.IntValue STRIKE_MIN_DISTANCE;
    public static final ModConfigSpec.IntValue STRIKE_MAX_DISTANCE;

    public static final ModConfigSpec.BooleanValue EARTHQUAKE_RANDOM;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Lag control: every disaster's block changes go through one shared budget.").push("performance");
        MAX_BLOCKS_PER_TICK = b.comment("Most block changes applied per server tick, across all disasters.")
                .defineInRange("maxBlocksPerTick", 2000, 1, 100_000);
        TARGET_TICK_MS = b.comment("When the server's average tick time goes over this, the budget shrinks automatically.")
                .defineInRange("targetTickMs", 40, 5, 1000);
        MAX_WORK_MS_PER_TICK = b.comment("Hard cap on milliseconds disasters may spend changing blocks in one tick.")
                .defineInRange("maxWorkMsPerTick", 15, 1, 1000);
        MAX_FALLING_BLOCKS_PER_SECOND = b.comment("Collapsing blocks shown as falling-block entities, per disaster per second; the rest move instantly.")
                .defineInRange("maxFallingBlocksPerSecond", 8, 0, 1000);
        b.pop();

        b.push("random");
        RANDOM_ENABLED = b.comment("Disasters happen on their own (also toggled by /disaster random on|off).")
                .define("enabled", true);
        RANDOM_INTERVAL_DAYS = b.comment("Average in-game days between random disasters (only counts while players are online).")
                .defineInRange("intervalDays", 7.0, 0.01, 1000.0);
        WARNING_SECONDS = b.comment("Seconds of rumbling and hints before a random disaster hits.")
                .defineInRange("warningSeconds", 20, 0, 600);
        STRIKE_MIN_DISTANCE = b.comment("Random disasters strike at least this many blocks from the chosen player.")
                .defineInRange("strikeMinDistance", 16, 0, 512);
        STRIKE_MAX_DISTANCE = b.comment("...and at most this many.")
                .defineInRange("strikeMaxDistance", 64, 0, 512);
        b.pop();

        b.push("earthquake");
        EARTHQUAKE_RANDOM = b.comment("Earthquakes can happen randomly.").define("random", true);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
