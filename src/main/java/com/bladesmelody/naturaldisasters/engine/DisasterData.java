package com.bladesmelody.naturaldisasters.engine;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Saved per dimension: that dimension's active disasters. The overworld's copy
 * also holds the random-disaster timer and the id counter.
 */
public final class DisasterData extends SavedData {
    private static final Codec<DisasterData> CODEC = CompoundTag.CODEC.xmap(DisasterData::fromTag, DisasterData::toTag);
    public static final SavedDataType<DisasterData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "disasters"), DisasterData::new, CODEC);

    final List<Disaster> active = new ArrayList<>();
    /** Ticks (with players online) until the next random disaster; -1 = not scheduled yet. */
    long ticksUntilRandom = -1;
    int nextId = 1;

    public static DisasterData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static DisasterData fromTag(CompoundTag tag) {
        DisasterData data = new DisasterData();
        data.ticksUntilRandom = tag.getLongOr("ticksUntilRandom", -1L);
        data.nextId = tag.getIntOr("nextId", 1);
        ListTag list = tag.getListOrEmpty("active");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            DisasterTypes.load(entry).ifPresentOrElse(data.active::add,
                    () -> NaturalDisasters.LOGGER.warn("Dropping saved disaster of unknown type {}", entry.getStringOr("type", "?")));
        }
        return data;
    }

    private CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ticksUntilRandom", ticksUntilRandom);
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        for (Disaster d : active) list.add(d.save());
        tag.put("active", list);
        return tag;
    }

    public List<Disaster> active() {
        return active;
    }
}
