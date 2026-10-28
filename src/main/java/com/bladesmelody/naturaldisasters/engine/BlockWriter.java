package com.bladesmelody.naturaldisasters.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A disaster's share of this tick's block budget. Every world change a disaster
 * makes goes through here, so the engine can cap them.
 *
 * <p>Changes skip neighbor updates (the main lag source in huge edits); only
 * nearby fluids and falling blocks are woken up so water still pours into
 * holes and sand still drops. Unloaded chunks and unbreakable blocks are never
 * touched.
 */
public final class BlockWriter {
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel level;
    private final long deadlineNanos;
    private int remaining;
    private int changed;

    public BlockWriter(ServerLevel level, int budget, long deadlineNanos) {
        this.level = level;
        this.remaining = budget;
        this.deadlineNanos = deadlineNanos;
    }

    public ServerLevel level() {
        return level;
    }

    /** Changes every disaster may make each tick even when the time cap is used up, so none stalls. */
    public static final int GUARANTEED = 16;

    /** Whether there is budget (block count and time) left this tick. */
    public boolean hasBudget() {
        if (remaining <= 0) return false;
        if (changed < GUARANTEED) return true;
        if ((changed & 31) == 0 && System.nanoTime() > deadlineNanos) {
            remaining = 0;
            return false;
        }
        return true;
    }

    /** Block changes made through this writer. */
    public int changed() {
        return changed;
    }

    /** Uses up budget for work that isn't a plain block change (e.g. spawning a falling block). */
    public void spend(int amount) {
        remaining -= amount;
        changed += amount;
    }

    /** True if disasters may change this position: loaded chunk, in build height, breakable. */
    public boolean canEdit(BlockPos pos) {
        return canEdit(pos, level.getBlockState(pos));
    }

    private boolean canEdit(BlockPos pos, BlockState current) {
        return isLoaded(level, pos) && current.getDestroySpeed(level, pos) >= 0;
    }

    public static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.isLoaded(pos);
    }

    /**
     * Sets a block if allowed. Costs one unit of budget when the block actually
     * changes. Returns false if the position may not be edited.
     */
    public boolean set(BlockPos pos, BlockState state) {
        if (!isLoaded(level, pos)) return false;
        BlockState current = level.getBlockState(pos);
        if (current == state) return true;
        if (current.getDestroySpeed(level, pos) < 0) return false;
        level.setBlock(pos, state, FLAGS);
        remaining--;
        changed++;
        // Only a hole wakes fluids (fire and plants are "replaceable" too, but waking
        // quiet lava or flood water next to them would set it flowing for good).
        if (state.isAir()) wakeNeighbors(pos);
        if (state.getBlock() instanceof FallingBlock) level.scheduleTick(pos, state.getBlock(), 2);
        return true;
    }

    /**
     * Sets a block without any side effects at all: no onPlace logic (so
     * placed water doesn't start flowing) and no waking of neighbours. Used for
     * water a disaster places and later drains exactly.
     */
    public boolean setQuiet(BlockPos pos, BlockState state) {
        if (!isLoaded(level, pos)) return false;
        BlockState current = level.getBlockState(pos);
        if (current == state) return true;
        if (current.getDestroySpeed(level, pos) < 0) return false;
        level.setBlock(pos, state, FLAGS | Block.UPDATE_SKIP_ON_PLACE);
        remaining--;
        changed++;
        return true;
    }

    private void wakeNeighbors(BlockPos pos) {
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            n.setWithOffset(pos, d);
            if (!level.isLoaded(n)) continue;
            FluidState fluid = level.getFluidState(n);
            if (!fluid.isEmpty()) {
                level.scheduleTick(n.immutable(), fluid.getType(), fluid.getType().getTickDelay(level));
            } else if (d == Direction.UP) {
                BlockState above = level.getBlockState(n);
                if (above.getBlock() instanceof FallingBlock) level.scheduleTick(n.immutable(), above.getBlock(), 2);
            }
        }
    }
}
