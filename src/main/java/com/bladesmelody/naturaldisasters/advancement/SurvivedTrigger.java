package com.bladesmelody.naturaldisasters.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code naturaldisasters:survived}: the player was in a disaster's area
 * while it raged and didn't die before it ended. Conditions: {@code type}
 * (e.g. "tornado"), {@code min_magnitude}.
 */
public class SurvivedTrigger extends SimpleCriterionTrigger<SurvivedTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, String type, int magnitude) {
        trigger(player, t -> t.matches(type, magnitude));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<String> type, Optional<Integer> minMagnitude)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("type").forGetter(TriggerInstance::type),
                Codec.INT.optionalFieldOf("min_magnitude").forGetter(TriggerInstance::minMagnitude)
        ).apply(i, TriggerInstance::new));

        boolean matches(String type, int magnitude) {
            return this.type.map(type::equals).orElse(true) && minMagnitude.map(m -> magnitude >= m).orElse(true);
        }
    }
}
