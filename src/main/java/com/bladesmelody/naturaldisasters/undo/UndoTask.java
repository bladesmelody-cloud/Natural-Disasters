package com.bladesmelody.naturaldisasters.undo;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Puts one disaster's damage back, chunk by chunk over several ticks. Spots
 * a player broke or placed a block at since the disaster started are left
 * alone, so repairs and new builds are never overwritten; anything else
 * (water that flowed in, sand that fell, grass that spread) is put back. Containers come
 * back with their contents only if every item they dropped is still lying
 * around (those items are taken back); otherwise they come back empty.
 */
final class UndoTask {
    /** Blocks restored per tick, and chunks it may load per tick. */
    private static final int BLOCKS_PER_TICK = 4096, LOADS_PER_TICK = 8;
    /** Restoring must not set water flowing, drop items or run placement logic. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    final UndoLog log;
    private final ServerLevel level;
    private final CommandSourceStack source;
    private final String label;
    private final Long2LongOpenHashMap playerEdits;
    private final long startedTick;
    private final List<IntArrayList> chunks = new ArrayList<>();
    private int chunk, cursor;
    int restored, skipped, refilled, emptied;

    UndoTask(UndoLog log, ServerLevel level, CommandSourceStack source, String label, Long2LongOpenHashMap playerEdits, long startedTick) {
        this.log = log;
        this.playerEdits = playerEdits;
        this.startedTick = startedTick;
        this.level = level;
        this.source = source;
        this.label = label;
        Long2ObjectLinkedOpenHashMap<IntArrayList> byChunk = new Long2ObjectLinkedOpenHashMap<>();
        LongArrayList positions = log.positions;
        for (int i = 0; i < positions.size(); i++) {
            if (log.before.getInt(i) == log.after.getInt(i)) continue; // changed and changed back (drained water)
            BlockPos p = BlockPos.of(positions.getLong(i));
            byChunk.computeIfAbsent(ChunkPos.pack(p.getX() >> 4, p.getZ() >> 4), k -> new IntArrayList()).add(i);
        }
        chunks.addAll(byChunk.values());
    }

    int total() {
        return chunks.stream().mapToInt(IntArrayList::size).sum();
    }

    /** Works for one tick; true when done. */
    boolean tick() {
        int blocks = 0, loads = 0;
        while (chunk < chunks.size()) {
            IntArrayList list = chunks.get(chunk);
            if (cursor == 0) {
                BlockPos first = BlockPos.of(log.positions.getLong(list.getInt(0)));
                if (!level.isLoaded(first)) {
                    if (loads++ >= LOADS_PER_TICK) return false;
                    level.getChunk(first.getX() >> 4, first.getZ() >> 4);
                }
            }
            while (cursor < list.size()) {
                if (blocks++ >= BLOCKS_PER_TICK) return false;
                restore(list.getInt(cursor++));
            }
            chunk++;
            cursor = 0;
        }
        report();
        return true;
    }

    private void restore(int i) {
        BlockPos pos = BlockPos.of(log.positions.getLong(i));
        BlockState was = log.before(i);
        // Only player changes count: water flowing in, sand falling or grass spreading is overwritten.
        if (playerEdits.get(pos.asLong()) >= startedTick) {
            skipped++;
            return;
        }
        if (level.getBlockState(pos) == was) return;
        level.setBlock(pos, was, FLAGS);
        wakeFluids(pos);
        restored++;
        CompoundTag data = log.blockEntities.get(pos.asLong());
        if (data == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        CompoundTag tag = data.copy();
        UndoLog.Drops drops = log.drops.get(pos.asLong());
        if (drops != null) {
            List<ItemEntity> found = new ArrayList<>();
            int count = 0;
            for (var id : drops.items()) {
                Entity e = level.getEntity(id);
                if (e instanceof ItemEntity item && item.isAlive()) {
                    found.add(item);
                    count += item.getItem().getCount();
                }
            }
            if (count == drops.count()) {
                found.forEach(Entity::discard);
                refilled++;
            } else {
                tag.remove("Items"); // some were picked up or are out of reach: no duplicates
                emptied++;
            }
        }
        be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        be.setChanged();
        level.sendBlockUpdated(pos, was, was, Block.UPDATE_CLIENTS);
    }

    /** Flowing water or lava next to a restored block gets a tick, so what lost its source drains away. */
    private void wakeFluids(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!level.isLoaded(n)) continue;
            FluidState fluid = level.getFluidState(n);
            if (!fluid.isEmpty() && !fluid.isSource()) level.scheduleTick(n, fluid.getType(), fluid.getType().getTickDelay(level));
        }
    }

    private void report() {
        String msg = "Undid " + label + ": " + restored + " blocks restored"
                + (skipped > 0 ? ", " + skipped + " left alone (changed by players since)" : "")
                + (refilled + emptied > 0 ? ", " + refilled + " containers refilled" : "")
                + (emptied > 0 ? ", " + emptied + " came back empty (their items were picked up or are out of reach)" : "")
                + (log.full ? ". The record was full, so the disaster's last changes weren't recorded" : "") + ".";
        source.sendSuccess(() -> Component.literal(msg), true);
    }
}
