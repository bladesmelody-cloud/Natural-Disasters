package com.bladesmelody.naturaldisasters.engine;

import com.bladesmelody.naturaldisasters.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * One running disaster. It first spends {@code warningTicks} warning nearby
 * players, then runs its effects through a {@link BlockWriter} each tick until
 * it reports {@link #isFinished()}.
 *
 * <p>Everything a disaster needs to resume after a restart must be saved in
 * {@link #saveExtra}; generated shapes (fissure paths etc.) should be derived
 * from {@link #seed} so they can be rebuilt instead of stored.
 */
public abstract class Disaster {
    private final DisasterType<?> type;
    protected int id;
    protected BlockPos center = BlockPos.ZERO;
    protected int magnitude;
    protected long seed;
    protected int warningTicks;
    protected int age;
    private boolean stopped;

    protected Disaster(DisasterType<?> type) {
        this.type = type;
    }

    /** Called once when a new disaster is created (not on load). */
    protected void init(BlockPos center, int magnitude, long seed, int warningTicks) {
        this.center = center.immutable();
        this.magnitude = magnitude;
        this.seed = seed;
        this.warningTicks = warningTicks;
    }

    public final void tick(ServerLevel level, BlockWriter writer) {
        if (age < warningTicks) {
            tickWarning(level, warningTicks - age);
        } else {
            tickActive(level, writer, age - warningTicks);
            if ((age - warningTicks) % 20 == 0) {
                for (ServerPlayer p : level.players()) {
                    if (!p.isSpectator() && p.isAlive() && caughtIn(p)) witnesses.add(p.getUUID());
                }
            }
        }
        age++;
    }

    /** Players who were in it while it raged (and haven't died since): they survive it when it ends. */
    private final java.util.Set<java.util.UUID> witnesses = new java.util.HashSet<>();

    /** Whether a player is in the disaster's area right now (moving ones override this). */
    protected boolean caughtIn(ServerPlayer p) {
        double dx = p.getX() - (center.getX() + 0.5), dz = p.getZ() - (center.getZ() + 0.5);
        return dx * dx + dz * dz <= (double) radius() * radius();
    }

    public java.util.Set<java.util.UUID> witnesses() {
        return witnesses;
    }

    /** Runs the disaster's effects; {@code activeTicks} counts from 0 once the warning is over. */
    protected abstract void tickActive(ServerLevel level, BlockWriter writer, int activeTicks);

    /** Default warning: a distant rumble every eight seconds (the sounds last 6–19 s). */
    protected void tickWarning(ServerLevel level, int ticksLeft) {
        if (ticksLeft % 160 != 0) return;
        for (ServerPlayer p : playersNear(level, radius() * 1.5)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.WARNING.get(), SoundSource.AMBIENT, 0.6F, 1.0F);
        }
    }

    public abstract boolean isDone();

    /** Called once when the disaster is removed (finished or stopped), to undo lasting side effects. */
    protected void onEnd(ServerLevel level) {}

    final void end(ServerLevel level) {
        onEnd(level);
        com.bladesmelody.naturaldisasters.undo.Undo.finished(this);
    }

    /** Effect radius in blocks, for warnings and /disaster list. */
    public abstract int radius();

    public final boolean isFinished() {
        return stopped || isDone();
    }

    public final void stop() {
        stopped = true;
    }

    public boolean inWarning() {
        return age < warningTicks;
    }

    public int warningTicksLeft() {
        return Math.max(0, warningTicks - age);
    }

    /**
     * Call after a disaster moves an entity. A player carried through the air
     * (a tornado's lift, a hurricane, a wave) would otherwise be kicked by the
     * server for "floating too long" after 4 s where flight isn't allowed.
     */
    protected static void carried(net.minecraft.world.entity.Entity e) {
        if (e instanceof ServerPlayer p) p.connection.resetFlyingTicks();
    }

    /** Damage from a disaster (meteoric armor resists it). */
    protected static boolean hurt(ServerLevel level, net.minecraft.world.entity.Entity e, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return com.bladesmelody.naturaldisasters.meteorite.DisasterResistance.during(() -> e.hurtServer(level, source, amount));
    }

    protected java.util.List<ServerPlayer> playersNear(ServerLevel level, double range) {
        double r2 = range * range;
        return level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(center.getX() + 0.5, p.getY(), center.getZ() + 0.5) <= r2);
    }

    public String describe() {
        return type.name() + " M" + magnitude + " (radius " + radius() + ")";
    }

    /**
     * Keeps the weather stormy for at least {@code ticks} more (rain, and
     * thunder if asked). Weather is shared by the whole server, so storms set
     * it rather than owning it; it clears up on its own afterwards.
     */
    protected void keepWeather(ServerLevel level, int ticks, boolean thunder) {
        if (level.dimensionType().hasCeiling()) return;
        if (!level.isRaining() || (thunder && !level.isThundering()) || age % 200 == 0) {
            level.getServer().setWeatherParameters(0, Math.max(ticks, 200), true, thunder || level.isThundering());
        }
    }

    public DisasterType<?> type() {
        return type;
    }

    public int id() {
        return id;
    }

    public BlockPos center() {
        return center;
    }

    public int magnitude() {
        return magnitude;
    }

    public final CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putInt("id", id);
        tag.putLong("center", center.asLong());
        tag.putInt("magnitude", magnitude);
        tag.putLong("seed", seed);
        tag.putInt("warning", warningTicks);
        tag.putInt("age", age);
        if (!witnesses.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            witnesses.stream().sorted().forEach(u -> list.add(new net.minecraft.nbt.IntArrayTag(net.minecraft.core.UUIDUtil.uuidToIntArray(u))));
            tag.put("witnesses", list);
        }
        CompoundTag extra = new CompoundTag();
        saveExtra(extra);
        tag.put("data", extra);
        return tag;
    }

    final void load(CompoundTag tag) {
        id = tag.getIntOr("id", 0);
        center = BlockPos.of(tag.getLongOr("center", 0L));
        magnitude = tag.getIntOr("magnitude", 1);
        seed = tag.getLongOr("seed", 0L);
        witnesses.clear();
        for (net.minecraft.nbt.Tag t : tag.getListOrEmpty("witnesses")) {
            if (t instanceof net.minecraft.nbt.IntArrayTag a && a.getAsIntArray().length == 4) witnesses.add(net.minecraft.core.UUIDUtil.uuidFromIntArray(a.getAsIntArray()));
        }
        warningTicks = tag.getIntOr("warning", 0);
        age = tag.getIntOr("age", 0);
        loadExtra(tag.getCompoundOrEmpty("data"));
    }

    protected abstract void saveExtra(CompoundTag tag);

    protected abstract void loadExtra(CompoundTag tag);
}
