package com.bladesmelody.naturaldisasters.engine;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * A kind of disaster. {@code factory} makes a blank instance (used both for new
 * disasters and when loading saved ones).
 */
public final class DisasterType<D extends Disaster> {
    private final String name;
    private final Function<DisasterType<D>, D> factory;
    private final BooleanSupplier randomEnabled;
    private double weight = 1.0;
    private BiPredicate<ServerLevel, BlockPos> where = (level, pos) -> true;
    private ToIntFunction<RandomSource> roll = DisasterType::rollMagnitude;

    public DisasterType(String name, Function<DisasterType<D>, D> factory, BooleanSupplier randomEnabled) {
        this.name = name;
        this.factory = factory;
        this.randomEnabled = randomEnabled;
    }

    /** How likely random picks are to choose this type, relative to others (default 1). */
    public DisasterType<D> weight(double weight) {
        this.weight = weight;
        return this;
    }

    /** Where this type may happen randomly (e.g. blizzards only where it's cold). Commands ignore it. */
    public DisasterType<D> where(BiPredicate<ServerLevel, BlockPos> where) {
        this.where = where;
        return this;
    }

    /** Replaces how random disasters of this type pick their magnitude. */
    public DisasterType<D> roll(ToIntFunction<RandomSource> roll) {
        this.roll = roll;
        return this;
    }

    /** A magnitude for a random disaster of this type. */
    public int randomMagnitude(RandomSource random) {
        return roll.applyAsInt(random);
    }

    public double weight() {
        return weight;
    }

    public boolean canHappenAt(ServerLevel level, BlockPos pos) {
        return where.test(level, pos);
    }

    public String name() {
        return name;
    }

    public boolean randomEnabled() {
        return randomEnabled.getAsBoolean();
    }

    public D create(BlockPos center, int magnitude, long seed, int warningTicks) {
        D d = factory.apply(this);
        d.init(center, magnitude, seed, warningTicks);
        return d;
    }

    D blank() {
        return factory.apply(this);
    }

    /** Magnitude 1–10, small ones common and big ones rare (each step ~0.6x as likely). */
    public static int rollMagnitude(RandomSource random) {
        double total = 0;
        for (int m = 1; m <= 10; m++) total += Math.pow(0.6, m - 1);
        double r = random.nextDouble() * total;
        for (int m = 1; m <= 10; m++) {
            r -= Math.pow(0.6, m - 1);
            if (r <= 0) return m;
        }
        return 10;
    }
}
