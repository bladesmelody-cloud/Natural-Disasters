package com.bladesmelody.naturaldisasters.advancement;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Advancement triggers (the advancements are data: data/naturaldisasters/advancement). */
public final class ModTriggers {
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, NaturalDisasters.MODID);
    public static final DeferredHolder<CriterionTrigger<?>, SurvivedTrigger> SURVIVED = TRIGGERS.register("survived", SurvivedTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, EventTrigger> EVENT = TRIGGERS.register("event", EventTrigger::new);

    /** Event names for {@link EventTrigger}. */
    public static final String HURRICANE_EYE = "hurricane_eye", TORNADO_RIDE = "tornado_ride",
            SLEEP_IN_ASH = "sleep_in_ash", INSTRUMENT_WARNING = "instrument_warning";

    private ModTriggers() {}

    public static void register(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }

    /** A disaster ran its course: everyone it caught who is still alive survived it. */
    public static void disasterSurvived(net.minecraft.server.MinecraftServer server, com.bladesmelody.naturaldisasters.engine.Disaster d) {
        for (java.util.UUID id : d.witnesses()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.isAlive()) SURVIVED.get().trigger(p, d.type().name(), d.magnitude());
        }
    }

    /** A player died: they didn't survive the disasters they were caught in. */
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        for (var r : com.bladesmelody.naturaldisasters.engine.DisasterManager.all(p.level().getServer())) r.disaster().witnesses().remove(p.getUUID());
    }

    public static void event(ServerPlayer player, String event) {
        EVENT.get().trigger(player, event);
    }
}
