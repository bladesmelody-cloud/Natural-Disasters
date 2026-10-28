package com.bladesmelody.naturaldisasters.instrument;

import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Runs the instruments once a second: each loaded instrument finds the
 * nearest disaster it senses, sets its redstone power, announces a newly
 * sensed disaster to players nearby and sounds its alarm (every 3 s) while
 * that disaster is still in its warning stage.
 */
public final class Instruments {
    /** Players this close to an instrument hear about what it senses. */
    static final double ANNOUNCE_RANGE = 48;

    private Instruments() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) tick(level);
    }

    public static void tick(ServerLevel level) {
        InstrumentData data = InstrumentData.get(level);
        if (data.instruments.isEmpty()) return;
        LongArrayList gone = new LongArrayList();
        for (Long2ObjectMap.Entry<Instrument> e : data.instruments.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(e.getLongKey());
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof InstrumentBlock block) || block.instrument() != e.getValue()) {
                gone.add(e.getLongKey());
                continue;
            }
            update(level, data, pos, state, e.getValue());
        }
        for (long key : gone) data.remove(BlockPos.of(key));
    }

    private static void update(ServerLevel level, InstrumentData data, BlockPos pos, BlockState state, Instrument instrument) {
        Disaster best = null;
        double bestCloseness = 0;
        for (Disaster d : DisasterData.get(level).active()) {
            double closeness = closeness(instrument, d, pos);
            if (closeness > bestCloseness) {
                best = d;
                bestCloseness = closeness;
            }
        }
        int power = best == null ? 0 : power(bestCloseness, best.magnitude());
        if (state.getValue(InstrumentBlock.POWER) != power) {
            level.setBlock(pos, state.setValue(InstrumentBlock.POWER, power), Block.UPDATE_ALL);
        }
        long key = pos.asLong();
        if (best == null) {
            data.announced.remove(key);
            return;
        }
        if (!data.announced.containsKey(key) || data.announced.get(key) != best.id()) {
            data.announced.put(key, best.id());
            announce(level, pos, instrument, best);
        }
        if (best.inWarning() && level.getGameTime() / 20 % 3 == 0) {
            level.playSound(null, pos, instrument.alarm(), SoundSource.BLOCKS, 1.5F, 1.0F);
        }
    }

    /** 0 when out of range, rising to 1 at the disaster's centre. */
    public static double closeness(Instrument instrument, Disaster d, BlockPos pos) {
        if (!instrument.senses(d.type().name())) return 0;
        double range = Instrument.range(d.radius());
        double dx = d.center().getX() - pos.getX(), dz = d.center().getZ() - pos.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        return dist >= range ? 0 : 1 - dist / range;
    }

    /** 1–15: a big disaster at the instrument gives 15, a small far one 1. */
    public static int power(double closeness, int magnitude) {
        return (int) Math.clamp(Math.ceil(15 * closeness * (0.5 + Math.clamp(magnitude, 1, 10) / 20.0)), 1, 15);
    }

    /** True if an instrument that senses this disaster is in range of it (loaded or not). */
    public static boolean anyInRange(ServerLevel level, Disaster d) {
        for (Long2ObjectMap.Entry<Instrument> e : InstrumentData.get(level).instruments.long2ObjectEntrySet()) {
            if (closeness(e.getValue(), d, BlockPos.of(e.getLongKey())) > 0) return true;
        }
        return false;
    }

    private static void announce(ServerLevel level, BlockPos pos, Instrument instrument, Disaster d) {
        double dx = d.center().getX() - pos.getX(), dz = d.center().getZ() - pos.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        Component what = Component.translatable("disaster.naturaldisasters." + d.type().name());
        Component where = dist < 16
                ? Component.translatable("instrument.naturaldisasters.here")
                : Component.translatable("instrument.naturaldisasters.away", dist,
                        Component.translatable("direction.naturaldisasters." + direction(dx, dz)));
        Component when = d.inWarning()
                ? Component.translatable("instrument.naturaldisasters.soon", Math.max(1, Math.round(d.warningTicksLeft() / 20.0)))
                : Component.translatable("instrument.naturaldisasters.now");
        Component message = Component.translatable("instrument.naturaldisasters.detected",
                Component.translatable("block.naturaldisasters." + instrument.id()), what, d.magnitude(), where, when);
        double r2 = ANNOUNCE_RANGE * ANNOUNCE_RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= r2) {
                p.sendSystemMessage(message);
                com.bladesmelody.naturaldisasters.advancement.ModTriggers.event(p, com.bladesmelody.naturaldisasters.advancement.ModTriggers.INSTRUMENT_WARNING);
            }
        }
    }

    /** Compass direction (8 points) of an offset; +z is south. */
    public static String direction(double dx, double dz) {
        String[] names = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        int i = (int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4));
        return names[Math.floorMod(i, 8)];
    }
}
