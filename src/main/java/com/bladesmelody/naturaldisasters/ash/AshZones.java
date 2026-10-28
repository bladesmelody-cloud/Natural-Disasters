package com.bladesmelody.naturaldisasters.ash;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

/**
 * Where volcanic ash darkens the sky. The server keeps one zone per active ash
 * cloud and sends the list to clients ({@link AshZonesPayload}); both sides
 * use it: the server so mobs spawn and don't burn there as at night, the
 * client for the black ash sky and fog.
 */
public final class AshZones {
    /** An ash-darkened area; {@code radius <= 0} means the whole Overworld. */
    public record Zone(double x, double z, int radius, int vei) {
        public boolean covers(double px, double pz) {
            if (radius <= 0) return true;
            double dx = px - x, dz = pz - z;
            return dx * dx + dz * dz <= (double) radius * radius;
        }
    }

    private static final Map<Integer, Zone> SERVER = new ConcurrentHashMap<>();
    private static volatile List<Zone> client = List.of();
    private static volatile boolean dirty;

    private AshZones() {}

    /** Called by an ash cloud each tick while it lasts. */
    public static void put(int id, Zone zone) {
        if (!zone.equals(SERVER.put(id, zone))) dirty = true;
    }

    public static void remove(int id) {
        if (SERVER.remove(id) != null) dirty = true;
    }

    public static void clearServer() {
        SERVER.clear();
        dirty = false;
    }

    /** True once after the server zones changed (so they get re-sent to clients). */
    public static boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public static List<Zone> serverZones() {
        return List.copyOf(SERVER.values());
    }

    public static void setClient(List<Zone> zones) {
        client = List.copyOf(zones);
    }

    public static List<Zone> clientZones() {
        return client;
    }

    private static boolean overworld(LevelReader level) {
        return level instanceof Level l && l.dimension() == Level.OVERWORLD;
    }

    /** Is this spot under the ash cloud (counts as night)? Server side. */
    public static boolean isDark(LevelReader level, BlockPos pos) {
        if (SERVER.isEmpty() || !overworld(level)) return false;
        for (Zone z : SERVER.values()) {
            if (z.covers(pos.getX() + 0.5, pos.getZ() + 0.5)) return true;
        }
        return false;
    }

    /** Client side: the ash zone covering this spot, or null. */
    public static Zone clientZoneAt(Level level, double x, double z) {
        if (client.isEmpty() || level.dimension() != Level.OVERWORLD) return null;
        for (Zone zone : client) {
            if (zone.covers(x, z)) return zone;
        }
        return null;
    }

    /** Is any world-wide ash darkness active? Server side. */
    public static boolean worldDark() {
        for (Zone z : SERVER.values()) if (z.radius() <= 0) return true;
        return false;
    }
}
