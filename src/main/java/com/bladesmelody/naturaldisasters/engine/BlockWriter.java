package com.bladesmelody.naturaldisasters.engine;

import com.bladesmelody.naturaldisasters.protect.Protection;
import com.bladesmelody.naturaldisasters.undo.Undo;
import com.bladesmelody.naturaldisasters.undo.UndoLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

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
    private final @Nullable UndoLog undo;
    private int remaining;
    private int changed;

    public BlockWriter(ServerLevel level, int budget, long deadlineNanos) {
        this(level, budget, deadlineNanos, null);
    }

    /** {@code undo}: the disaster's undo record, which every change is written to (null: not recorded). */
    public BlockWriter(ServerLevel level, int budget, long deadlineNanos, @Nullable UndoLog undo) {
        this.level = level;
        this.remaining = budget;
        this.deadlineNanos = deadlineNanos;
        this.undo = undo;
    }

    public ServerLevel level() {
        return level;
    }

    /** Changes every disaster may make each tick even when the time cap is used up, so none stalls. */
    public static final int GUARANTEED = 32;

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

    /** True if disasters may change this position: loaded chunk, in build height, breakable, not protected. */
    public boolean canEdit(BlockPos pos) {
        return isLoaded(level, pos) && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0 && !Protection.isProtected(level, pos);
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
        if (current.getDestroySpeed(level, pos) < 0 || Protection.isProtected(level, pos)) return false;
        change(pos, current, state, FLAGS);
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
        if (current.getDestroySpeed(level, pos) < 0 || Protection.isProtected(level, pos)) return false;
        change(pos, current, state, FLAGS | Block.UPDATE_SKIP_ON_PLACE);
        remaining--;
        changed++;
        return true;
    }

    private void change(BlockPos pos, BlockState current, BlockState state, int flags) {
        if (undo == null) {
            level.setBlock(pos, state, flags);
            return;
        }
        Undo.record(undo, level, pos, current, state);
        Undo.beginChange(undo, pos);
        try {
            level.setBlock(pos, state, flags);
        } finally {
            Undo.endChange();
        }
    }

    /**
     * A disaster turns the block here into a falling block (debris) itself:
     * records the removal, and the debris, so where it lands can be undone too.
     */
    public void recordFall(BlockPos pos, BlockState state, FallingBlockEntity debris) {
        debris.addTag(Protection.DEBRIS_TAG);
        if (undo == null) return;
        Undo.record(undo, level, pos, state, Blocks.AIR.defaultBlockState());
        debris.getPersistentData().putInt(Undo.DEBRIS_KEY, undo.disasterId());
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
