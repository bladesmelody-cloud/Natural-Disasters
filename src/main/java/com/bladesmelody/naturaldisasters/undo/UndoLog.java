package com.bladesmelody.naturaldisasters.undo;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Everything one disaster changed: for each spot, the block before the
 * disaster first touched it (with its block entity data, e.g. a chest's
 * contents) and the block the disaster last left there. Also which dropped
 * items came out of which broken container, so an undo can take them back
 * instead of duplicating them.
 */
public final class UndoLog {
    /** Items a broken container dropped: their entity ids and total item count. */
    record Drops(List<UUID> items, int count) {}

    final int disasterId;
    private final List<BlockState> palette = new ArrayList<>();
    private final Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();
    private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
    final LongArrayList positions = new LongArrayList();
    final IntArrayList before = new IntArrayList();
    final IntArrayList after = new IntArrayList();
    final Long2ObjectOpenHashMap<CompoundTag> blockEntities = new Long2ObjectOpenHashMap<>();
    final Long2ObjectOpenHashMap<Drops> drops = new Long2ObjectOpenHashMap<>();
    private final int limit;
    boolean full;
    boolean dirty;
    boolean finished;

    public int disasterId() {
        return disasterId;
    }

    UndoLog(int disasterId, int limit) {
        this.disasterId = disasterId;
        this.limit = limit;
        index.defaultReturnValue(-1);
        paletteIndex.defaultReturnValue(-1);
    }

    /** A spot is about to change from {@code from} to {@code to}. {@code blockEntity}: its data, if it has any. */
    void record(BlockPos pos, BlockState from, BlockState to, @Nullable CompoundTag blockEntity) {
        long key = pos.asLong();
        int i = index.get(key);
        if (i >= 0) {
            after.set(i, state(to));
        } else {
            if (positions.size() >= limit) {
                full = true;
                return;
            }
            index.put(key, positions.size());
            positions.add(key);
            before.add(state(from));
            after.add(state(to));
            if (blockEntity != null) blockEntities.put(key, blockEntity);
        }
        dirty = true;
    }

    void dropped(long pos, UUID item, int count) {
        Drops d = drops.get(pos);
        List<UUID> items = d == null ? new ArrayList<>() : new ArrayList<>(d.items());
        items.add(item);
        drops.put(pos, new Drops(items, (d == null ? 0 : d.count()) + count));
        dirty = true;
    }

    BlockState before(int i) {
        return palette.get(before.getInt(i));
    }

    BlockState after(int i) {
        return palette.get(after.getInt(i));
    }

    /** Spots that actually differ from before the disaster. */
    public int changedBlocks() {
        int n = 0;
        for (int i = 0; i < positions.size(); i++) if (before.getInt(i) != after.getInt(i)) n++;
        return n;
    }

    private int state(BlockState s) {
        int i = paletteIndex.getInt(s);
        if (i < 0) {
            i = palette.size();
            palette.add(s);
            paletteIndex.put(s, i);
        }
        return i;
    }

    // ---- Saving ----

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("disaster", disasterId);
        ListTag states = new ListTag();
        for (BlockState s : palette) states.add(NbtUtils.writeBlockState(s));
        tag.put("palette", states);
        tag.putLongArray("positions", positions.toLongArray());
        tag.putIntArray("before", before.toIntArray());
        tag.putIntArray("after", after.toIntArray());
        ListTag bes = new ListTag();
        for (Long2ObjectOpenHashMap.Entry<CompoundTag> e : blockEntities.long2ObjectEntrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getLongKey());
            t.put("data", e.getValue());
            bes.add(t);
        }
        tag.put("blockEntities", bes);
        ListTag ds = new ListTag();
        for (Long2ObjectOpenHashMap.Entry<Drops> e : drops.long2ObjectEntrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("pos", e.getLongKey());
            t.putInt("count", e.getValue().count());
            ListTag ids = new ListTag();
            for (UUID id : e.getValue().items()) ids.add(new IntArrayTag(UUIDUtil.uuidToIntArray(id)));
            t.put("items", ids);
            ds.add(t);
        }
        tag.put("drops", ds);
        tag.putBoolean("full", full);
        tag.putBoolean("finished", finished);
        return tag;
    }

    static UndoLog load(CompoundTag tag, HolderGetter<Block> blocks, int limit) {
        UndoLog log = new UndoLog(tag.getIntOr("disaster", 0), limit);
        ListTag states = tag.getListOrEmpty("palette");
        for (int i = 0; i < states.size(); i++) log.state(NbtUtils.readBlockState(blocks, states.getCompoundOrEmpty(i)));
        long[] pos = tag.getLongArray("positions").orElse(new long[0]);
        int[] b = tag.getIntArray("before").orElse(new int[0]);
        int[] a = tag.getIntArray("after").orElse(new int[0]);
        int n = Math.min(pos.length, Math.min(b.length, a.length));
        for (int i = 0; i < n; i++) {
            log.index.put(pos[i], i);
            log.positions.add(pos[i]);
            log.before.add(b[i]);
            log.after.add(a[i]);
        }
        ListTag bes = tag.getListOrEmpty("blockEntities");
        for (int i = 0; i < bes.size(); i++) {
            CompoundTag t = bes.getCompoundOrEmpty(i);
            log.blockEntities.put(t.getLongOr("pos", 0L), t.getCompoundOrEmpty("data"));
        }
        ListTag ds = tag.getListOrEmpty("drops");
        for (int i = 0; i < ds.size(); i++) {
            CompoundTag t = ds.getCompoundOrEmpty(i);
            List<UUID> ids = new ArrayList<>();
            ListTag list = t.getListOrEmpty("items");
            for (int j = 0; j < list.size(); j++) {
                if (list.get(j) instanceof IntArrayTag arr && arr.getAsIntArray().length == 4) ids.add(UUIDUtil.uuidFromIntArray(arr.getAsIntArray()));
            }
            log.drops.put(t.getLongOr("pos", 0L), new Drops(ids, t.getIntOr("count", 0)));
        }
        log.full = tag.getBooleanOr("full", false);
        log.finished = tag.getBooleanOr("finished", true);
        return log;
    }
}
