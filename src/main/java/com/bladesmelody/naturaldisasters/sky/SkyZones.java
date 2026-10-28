package com.bladesmelody.naturaldisasters.sky;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

/**
 * Areas where a disaster changes the sky: volcanic ash, storms, blizzards,
 * acid rain, drought haze. The server keeps one zone per running disaster and
 * sends the list to clients ({@link SkyZonesPayload}). Both sides read it:
 * {@link SkyLayers} recolours sky, fog and light from it, and ash zones also
 * count as night for monsters.
 */
public final class SkyZones {
    public enum Kind {
        ASH, HURRICANE, BLIZZARD, ACID_RAIN, DROUGHT;

        static Kind byId(int id) {
            Kind[] all = values();
            return all[Math.clamp(id, 0, all.length - 1)];
        }
    }

    /** A zone; {@code radius <= 0} means the whole Overworld. {@code strength} is the disaster's magnitude (VEI for ash). */
    /** hole: a clear middle (a hurricane's eye), 0 for none. */
    public record Zone(Kind kind, double x, double z, int radius, int strength, int hole) {
        public Zone(Kind kind, double x, double z, int radius, int strength) {
            this(kind, x, z, radius, strength, 0);
        }

        public boolean covers(double px, double pz) {
            return weight(px, pz) > 0;
        }

        /** How far into the clear hole a point is: 1 in its middle, 0 at its edge and outside. */
        public float inHole(double px, double pz) {
            if (hole <= 0) return 0;
            double dx = px - x, dz = pz - z;
            double d = Math.sqrt(dx * dx + dz * dz);
            return d >= hole ? 0 : (float) Math.min(1, (hole - d) / (hole * 0.4));
        }

        /** 1 inside, fading to 0 over the outer 15 % so a zone's edge isn't a hard line; clear in the hole. */
        public float weight(double px, double pz) {
            if (radius <= 0) return 1;
            double dx = px - x, dz = pz - z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= radius) return 0;
            if (d < hole) return (float) Math.max(0, (d - hole * 0.6) / (hole * 0.4));
            double fadeStart = radius * 0.85;
            return d <= fadeStart ? 1 : (float) ((radius - d) / (radius - fadeStart));
        }
    }

    private static final Map<Integer, Zone> SERVER = new ConcurrentHashMap<>();
    private static volatile List<Zone> client = List.of();
    private static volatile boolean dirty;

    private SkyZones() {}

    /** Called by a disaster each tick while it lasts (id = disaster id). */
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

    /** The zones as seen by this side. */
    public static Iterable<Zone> zones(boolean clientSide) {
        return clientSide ? client : SERVER.values();
    }

    private static boolean overworld(LevelReader level) {
        return level instanceof Level l && l.dimension() == Level.OVERWORLD;
    }

    /** Is this spot under volcanic ash (counts as night)? Server side. */
    public static boolean isDark(LevelReader level, BlockPos pos) {
        if (SERVER.isEmpty() || !overworld(level)) return false;
        for (Zone z : SERVER.values()) {
            if (z.kind == Kind.ASH && z.covers(pos.getX() + 0.5, pos.getZ() + 0.5)) return true;
        }
        return false;
    }

    /** Client side: the strongest zone of a kind covering this spot, or null. */
    public static Zone clientZoneAt(Level level, Kind kind, double x, double z) {
        if (client.isEmpty() || level.dimension() != Level.OVERWORLD) return null;
        Zone best = null;
        for (Zone zone : client) {
            if (zone.kind == kind && zone.covers(x, z) && (best == null || zone.strength > best.strength)) best = zone;
        }
        return best;
    }

    /** Is any world-wide ash darkness active? Server side. */
    public static boolean worldDark() {
        for (Zone z : SERVER.values()) if (z.kind == Kind.ASH && z.radius() <= 0) return true;
        return false;
    }
}
