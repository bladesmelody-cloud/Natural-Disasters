package com.bladesmelody.naturaldisasters.engine;

import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.disaster.AcidRain;
import com.bladesmelody.naturaldisasters.disaster.AshCloud;
import com.bladesmelody.naturaldisasters.disaster.Blizzard;
import com.bladesmelody.naturaldisasters.disaster.Drought;
import com.bladesmelody.naturaldisasters.disaster.Earthquake;
import com.bladesmelody.naturaldisasters.disaster.Hailstorm;
import com.bladesmelody.naturaldisasters.disaster.Flood;
import com.bladesmelody.naturaldisasters.disaster.Hurricane;
import com.bladesmelody.naturaldisasters.disaster.Tsunami;
import com.bladesmelody.naturaldisasters.disaster.Volcano;
import com.bladesmelody.naturaldisasters.disaster.Wildfire;
import com.bladesmelody.naturaldisasters.disaster.Meteor;
import com.bladesmelody.naturaldisasters.disaster.Places;
import com.bladesmelody.naturaldisasters.disaster.Tornado;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;

/** All disaster types, by name. */
public final class DisasterTypes {
    private static final Map<String, DisasterType<?>> TYPES = new LinkedHashMap<>();

    // Weights and places are for random disasters only; /disaster start works anywhere.
    public static final DisasterType<Earthquake> EARTHQUAKE = register("earthquake", Earthquake::new).weight(1.0);
    public static final DisasterType<Meteor> METEOR = register("meteor", Meteor::new).weight(0.6);
    public static final DisasterType<Tornado> TORNADO = register("tornado", Tornado::new).weight(1.0)
            .where(Places::openSky);
    public static final DisasterType<Hurricane> HURRICANE = register("hurricane", Hurricane::new).weight(0.6).where(Places::nearOcean);
    public static final DisasterType<Volcano> VOLCANO = register("volcano", Volcano::new).weight(0.3).where(Places::openSky)
            .roll(Volcano::rollRandomMagnitude);
    /** Left behind by big eruptions (or started by command); never picked at random. */
    public static final DisasterType<AshCloud> ASH_CLOUD = register("ash_cloud", AshCloud::new).weight(0).where((l, p) -> false);
    public static final DisasterType<Wildfire> WILDFIRE = register("wildfire", Wildfire::new).weight(0.8)
            .where((level, pos) -> Places.forest(level, pos) || Places.hot(level, pos));
    public static final DisasterType<Blizzard> BLIZZARD = register("blizzard", Blizzard::new).weight(0.8).where(Places::cold);
    public static final DisasterType<Hailstorm> HAILSTORM = register("hailstorm", Hailstorm::new).weight(0.8).where(Places::wet);
    public static final DisasterType<AcidRain> ACID_RAIN = register("acid_rain", AcidRain::new).weight(0.8).where(Places::wet);
    public static final DisasterType<Drought> DROUGHT = register("drought", Drought::new).weight(0.6).where(Places::warm);
    public static final DisasterType<Flood> FLOOD = register("flood", Flood::new).weight(0.8).where(Places::wet);
    public static final DisasterType<Tsunami> TSUNAMI = register("tsunami", Tsunami::new).weight(0.5).where(Places::nearOcean);

    private DisasterTypes() {}

    private static <D extends Disaster> DisasterType<D> register(String name, Function<DisasterType<D>, D> factory) {
        DisasterType<D> type = new DisasterType<>(name, factory, () -> Config.randomEnabled(name));
        TYPES.put(name, type);
        return type;
    }

    public static Optional<DisasterType<?>> get(String name) {
        return Optional.ofNullable(TYPES.get(name));
    }

    public static Collection<DisasterType<?>> all() {
        return TYPES.values();
    }

    /** Recreates a saved disaster, or empty if its type no longer exists. */
    public static Optional<Disaster> load(CompoundTag tag) {
        return get(tag.getStringOr("type", "")).map(type -> {
            Disaster d = type.blank();
            d.load(tag);
            return d;
        });
    }
}
