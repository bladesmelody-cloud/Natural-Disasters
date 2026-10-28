package com.bladesmelody.naturaldisasters.instrument;

import com.bladesmelody.naturaldisasters.registry.ModSounds;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;

/** The three disaster instruments and which disasters each one senses. */
public enum Instrument {
    SEISMOGRAPH("seismograph", ModSounds.INSTRUMENT_SEISMOGRAPH, Set.of("earthquake", "tsunami", "volcano")),
    BAROMETER("barometer", ModSounds.INSTRUMENT_BAROMETER,
            Set.of("tornado", "hurricane", "blizzard", "hailstorm", "flood", "acid_rain", "wildfire", "drought")),
    SKY_WATCHER("sky_watcher", ModSounds.INSTRUMENT_SKY_WATCHER, Set.of("meteor"));

    private final String name;
    private final Supplier<SoundEvent> alarm;
    private final Set<String> senses;

    Instrument(String name, Supplier<SoundEvent> alarm, Set<String> senses) {
        this.name = name;
        this.alarm = alarm;
        this.senses = senses;
    }

    public String id() {
        return name;
    }

    public SoundEvent alarm() {
        return alarm.get();
    }

    public boolean senses(String disasterType) {
        return senses.contains(disasterType);
    }

    /** How far an instrument senses a disaster: twice its radius, at least 128 blocks. */
    public static double range(int disasterRadius) {
        return Math.max(128, 2.0 * disasterRadius);
    }
}
