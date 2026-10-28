package com.bladesmelody.naturaldisasters.meteorite;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.protect.Protection;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** A dimension's disaster wards and their radii (saved, so unloaded wards still count). */
public final class WardData extends SavedData {
    private static final Codec<WardData> CODEC = CompoundTag.CODEC.xmap(WardData::fromTag, WardData::toTag);
    public static final SavedDataType<WardData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "wards"), WardData::new, CODEC);

    private final Long2IntOpenHashMap wards = new Long2IntOpenHashMap();

    public static WardData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    void put(BlockPos pos, int radius) {
        if (wards.put(pos.asLong(), radius) != radius) {
            setDirty();
            Protection.invalidate();
        }
    }

    void remove(BlockPos pos) {
        if (wards.containsKey(pos.asLong())) {
            wards.remove(pos.asLong());
            setDirty();
            Protection.invalidate();
        }
    }

    public boolean isEmpty() {
        return wards.isEmpty();
    }

    public Iterable<Long2IntMap.Entry> entries() {
        return wards.long2IntEntrySet();
    }

    private static WardData fromTag(CompoundTag tag) {
        WardData data = new WardData();
        long[] pos = tag.getLongArray("pos").orElse(new long[0]);
        int[] radius = tag.getIntArray("radius").orElse(new int[0]);
        for (int i = 0; i < Math.min(pos.length, radius.length); i++) data.wards.put(pos[i], radius[i]);
        return data;
    }

    private CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putLongArray("pos", wards.keySet().toLongArray());
        tag.putIntArray("radius", wards.values().toIntArray());
        return tag;
    }
}
