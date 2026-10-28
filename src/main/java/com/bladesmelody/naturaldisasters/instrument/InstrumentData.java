package com.bladesmelody.naturaldisasters.instrument;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Where a dimension's instruments are, so a new disaster can tell whether one
 * is in range even when its chunk isn't loaded (that doubles the warning).
 */
public final class InstrumentData extends SavedData {
    private static final Codec<InstrumentData> CODEC = CompoundTag.CODEC.xmap(InstrumentData::fromTag, InstrumentData::toTag);
    public static final SavedDataType<InstrumentData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "instruments"), InstrumentData::new, CODEC);

    final Long2ObjectOpenHashMap<Instrument> instruments = new Long2ObjectOpenHashMap<>();
    /** Not saved: the disaster each instrument last announced (after a restart it announces again once). */
    final Long2IntOpenHashMap announced = new Long2IntOpenHashMap();

    public static InstrumentData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    void add(BlockPos pos, Instrument instrument) {
        if (instruments.put(pos.asLong(), instrument) != instrument) setDirty();
    }

    void remove(BlockPos pos) {
        if (instruments.remove(pos.asLong()) != null) setDirty();
        announced.remove(pos.asLong());
    }

    public Map<Long, Instrument> all() {
        return instruments;
    }

    private static InstrumentData fromTag(CompoundTag tag) {
        InstrumentData data = new InstrumentData();
        ListTag list = tag.getListOrEmpty("instruments");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompoundOrEmpty(i);
            int kind = e.getIntOr("kind", -1);
            if (kind >= 0 && kind < Instrument.values().length) data.instruments.put(e.getLongOr("pos", 0L), Instrument.values()[kind]);
        }
        return data;
    }

    private CompoundTag toTag() {
        ListTag list = new ListTag();
        for (Long2ObjectOpenHashMap.Entry<Instrument> e : instruments.long2ObjectEntrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getLongKey());
            t.putInt("kind", e.getValue().ordinal());
            list.add(t);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("instruments", list);
        return tag;
    }
}
