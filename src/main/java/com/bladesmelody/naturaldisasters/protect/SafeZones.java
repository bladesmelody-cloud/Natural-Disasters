package com.bladesmelody.naturaldisasters.protect;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Areas an admin protected with /disaster safezone: circles, all the way
 * from bedrock to the sky. Saved with the Overworld, for every dimension.
 */
public final class SafeZones extends SavedData {
    public record Zone(String name, Identifier dimension, int x, int z, int radius) {}

    private static final Codec<SafeZones> CODEC = CompoundTag.CODEC.xmap(SafeZones::fromTag, SafeZones::toTag);
    public static final SavedDataType<SafeZones> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "safe_zones"), SafeZones::new, CODEC);

    private final List<Zone> zones = new ArrayList<>();

    public static SafeZones get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Zone> zones() {
        return List.copyOf(zones);
    }

    public Optional<Zone> find(String name) {
        return zones.stream().filter(z -> z.name().equalsIgnoreCase(name)).findFirst();
    }

    /** Adds or replaces (same name) a zone. */
    public void put(Zone zone) {
        zones.removeIf(z -> z.name().equalsIgnoreCase(zone.name()));
        zones.add(zone);
        setDirty();
        Protection.invalidate();
    }

    public boolean remove(String name) {
        boolean removed = zones.removeIf(z -> z.name().equalsIgnoreCase(name));
        if (removed) {
            setDirty();
            Protection.invalidate();
        }
        return removed;
    }

    boolean contains(ServerLevel level, int x, int z) {
        Identifier dim = level.dimension().identifier();
        for (Zone zone : zones) {
            if (!zone.dimension().equals(dim)) continue;
            long dx = x - zone.x(), dz = z - zone.z();
            if (dx * dx + dz * dz <= (long) zone.radius() * zone.radius()) return true;
        }
        return false;
    }

    /** Whether a chunk lies wholly, partly or not at all in a zone. */
    byte chunkState(ServerLevel level, int cx, int cz) {
        Identifier dim = level.dimension().identifier();
        byte state = Protection.FREE;
        for (Zone zone : zones) {
            if (!zone.dimension().equals(dim)) continue;
            // Nearest and farthest chunk corners from the zone's centre.
            double nx = Math.clamp(zone.x(), cx * 16, cx * 16 + 16) - zone.x(), nz = Math.clamp(zone.z(), cz * 16, cz * 16 + 16) - zone.z();
            if (nx * nx + nz * nz > (double) zone.radius() * zone.radius()) continue;
            double fx = Math.max(Math.abs(cx * 16 - zone.x()), Math.abs(cx * 16 + 16 - zone.x()));
            double fz = Math.max(Math.abs(cz * 16 - zone.z()), Math.abs(cz * 16 + 16 - zone.z()));
            if (fx * fx + fz * fz <= (double) zone.radius() * zone.radius()) return Protection.ALL;
            state = Protection.PARTLY;
        }
        return state;
    }

    private static SafeZones fromTag(CompoundTag tag) {
        SafeZones data = new SafeZones();
        ListTag list = tag.getListOrEmpty("zones");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            Identifier dim = Identifier.tryParse(t.getStringOr("dimension", "minecraft:overworld"));
            if (dim == null) continue;
            data.zones.add(new Zone(t.getStringOr("name", "zone" + i), dim, t.getIntOr("x", 0), t.getIntOr("z", 0), t.getIntOr("radius", 16)));
        }
        return data;
    }

    private CompoundTag toTag() {
        ListTag list = new ListTag();
        for (Zone z : zones) {
            CompoundTag t = new CompoundTag();
            t.putString("name", z.name());
            t.putString("dimension", z.dimension().toString());
            t.putInt("x", z.x());
            t.putInt("z", z.z());
            t.putInt("radius", z.radius());
            list.add(t);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("zones", list);
        return tag;
    }
}
