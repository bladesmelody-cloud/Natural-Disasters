package com.bladesmelody.naturaldisasters.registry;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Disaster sounds. Each is built in {@code sounds.json} from vanilla sound
 * files, layered and re-pitched; real recordings can replace them there later.
 */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, NaturalDisasters.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WARNING = sound("warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> EARTHQUAKE_RUMBLE = sound("earthquake.rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> EARTHQUAKE_CRACK = sound("earthquake.crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_WHOOSH = sound("meteor.whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_IMPACT = sound("meteor.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> METEOR_SONIC_BOOM = sound("meteor.sonic_boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> TORNADO_ROAR = sound("tornado.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURRICANE_HOWL = sound("hurricane.howl");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLIZZARD_WIND = sound("blizzard.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAIL_HIT = sound("hail.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAIL_SHATTER = sound("hail.shatter");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLCANO_RUMBLE = sound("volcano.rumble");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLCANO_BLAST = sound("volcano.blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLCANO_LAVA = sound("volcano.lava");
    public static final DeferredHolder<SoundEvent, SoundEvent> TSUNAMI_ROAR = sound("tsunami.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOOD_RUSH = sound("flood.rush");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACID_SIZZLE = sound("acid.sizzle");
    public static final DeferredHolder<SoundEvent, SoundEvent> WILDFIRE_ROAR = sound("wildfire.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> INSTRUMENT_SEISMOGRAPH = sound("instrument.seismograph");
    public static final DeferredHolder<SoundEvent, SoundEvent> INSTRUMENT_BAROMETER = sound("instrument.barometer");
    public static final DeferredHolder<SoundEvent, SoundEvent> INSTRUMENT_SKY_WATCHER = sound("instrument.sky_watcher");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
