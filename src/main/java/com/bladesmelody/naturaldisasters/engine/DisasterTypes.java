package com.bladesmelody.naturaldisasters.engine;

import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.disaster.Earthquake;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

/** All disaster types, by name. */
public final class DisasterTypes {
    private static final Map<String, DisasterType<?>> TYPES = new LinkedHashMap<>();

    public static final DisasterType<Earthquake> EARTHQUAKE =
            register(new DisasterType<>("earthquake", Earthquake::new, Config.EARTHQUAKE_RANDOM::getAsBoolean));

    private DisasterTypes() {}

    private static <D extends Disaster> DisasterType<D> register(DisasterType<D> type) {
        TYPES.put(type.name(), type);
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
