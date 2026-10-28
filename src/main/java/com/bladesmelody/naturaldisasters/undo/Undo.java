package com.bladesmelody.naturaldisasters.undo;

import com.bladesmelody.naturaldisasters.Config;
import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Undo records for the most recent disasters ({@code undoRecords}, owner:
 * last 10). Running disasters' records live in memory and are written to
 * {@code <world>/naturaldisasters/undo/<id>.dat} whenever the world saves;
 * finished ones are only read back for an undo. The list of records is a
 * small SavedData in the Overworld.
 */
public final class Undo extends SavedData {
    /** What the list shows about a record. */
    public record Entry(int id, String type, int magnitude, Identifier dimension, BlockPos center, long startedTick, int radius) {
        /** Whether a player's edit here could touch this disaster's damage. */
        boolean near(Identifier dim, BlockPos pos) {
            if (!dim.equals(dimension)) return false;
            double reach = 2.0 * radius + 32, dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
            return dx * dx + dz * dz <= reach * reach;
        }
    }

    private static final Codec<Undo> CODEC = CompoundTag.CODEC.xmap(Undo::fromTag, Undo::toTag);
    public static final SavedDataType<Undo> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "undo_records"), Undo::new, CODEC);
    /** Persistent-data key on items a broken container dropped: {d: disaster id, p: container position}. */
    static final String DROP_KEY = NaturalDisasters.MODID + ":drop";
    /** Persistent-data key on thrown debris: the disaster that threw it. */
    public static final String DEBRIS_KEY = NaturalDisasters.MODID + ":disaster";

    private final List<Entry> entries = new ArrayList<>();
    /** When players last broke or placed a block near a recorded disaster, per dimension: undo leaves those spots alone. */
    private final java.util.Map<Identifier, Long2LongOpenHashMap> playerEdits = new java.util.HashMap<>();
    private static final Int2ObjectOpenHashMap<UndoLog> OPEN = new Int2ObjectOpenHashMap<>();
    private static final List<UndoTask> TASKS = new ArrayList<>();

    /** Set while a disaster's block change runs, so the items a broken container drops can be tagged. */
    private static @Nullable UndoLog dropLog;
    private static long dropPos;

    public static Undo get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Entry> entries() {
        return entries.stream().sorted(Comparator.comparingInt(Entry::id).reversed()).toList();
    }

    public Optional<Entry> entry(int id) {
        return entries.stream().filter(e -> e.id() == id).findFirst();
    }

    // ---- Recording ----

    /** A disaster was added: start its record (and drop the oldest finished ones beyond the limit). */
    public static void started(ServerLevel level, Disaster d) {
        MinecraftServer server = level.getServer();
        Undo undo = get(server);
        if (undo.entry(d.id()).isEmpty()) {
            undo.entries.add(new Entry(d.id(), d.type().name(), d.magnitude(), level.dimension().identifier(), d.center(),
                    level.getGameTime(), Math.min(d.radius(), 1_000_000)));
            undo.setDirty();
        }
        OPEN.computeIfAbsent(d.id(), id -> new UndoLog(id, Config.MAX_UNDO_BLOCKS.getAsInt()));
        undo.trim(server);
    }

    /** The record a running disaster writes to (after a restart: read back from disk, or a new one). */
    public static @Nullable UndoLog log(ServerLevel level, Disaster d) {
        UndoLog log = OPEN.get(d.id());
        if (log != null) return log;
        Undo undo = get(level.getServer());
        if (undo.entry(d.id()).isEmpty()) {
            started(level, d);
            return OPEN.get(d.id());
        }
        log = read(level.getServer(), d.id()).orElseGet(() -> new UndoLog(d.id(), Config.MAX_UNDO_BLOCKS.getAsInt()));
        log.finished = false;
        OPEN.put(d.id(), log);
        return log;
    }

    public static void finished(Disaster d) {
        UndoLog log = OPEN.get(d.id());
        if (log != null) {
            log.finished = true;
            log.dirty = true;
        }
    }

    /** Called by the block writer before a change: remembers the old block (and its block entity data). */
    public static void record(UndoLog log, ServerLevel level, BlockPos pos, BlockState from, BlockState to) {
        CompoundTag blockEntity = null;
        if (from.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) blockEntity = be.saveWithFullMetadata(level.registryAccess());
        }
        log.record(pos, from, to, blockEntity);
    }

    /** Called by the block writer around a change, so items a broken container drops can be tagged. */
    public static void beginChange(UndoLog log, BlockPos pos) {
        dropLog = log;
        dropPos = pos.asLong();
    }

    public static void endChange() {
        dropLog = null;
    }

    /** Starts undoing a record. Returns a failure message, or empty when started. */
    public static Optional<String> start(MinecraftServer server, int id, CommandSourceStack source) {
        Undo undo = get(server);
        Optional<Entry> entry = undo.entry(id);
        if (entry.isEmpty()) return Optional.of("No undo record for disaster #" + id + " (only the last " + Config.UNDO_RECORDS.getAsInt() + " are kept).");
        if (busy(id)) return Optional.of("Disaster #" + id + " is already being undone.");
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, entry.get().dimension()));
        if (level == null) return Optional.of("Disaster #" + id + " happened in a dimension that isn't loaded.");
        // A disaster still running is stopped first, so it can't keep changing blocks.
        com.bladesmelody.naturaldisasters.engine.DisasterManager.stop(server, id);
        Optional<UndoLog> log = read(server, id);
        if (log.isEmpty()) return Optional.of("The undo record for disaster #" + id + " is missing.");
        log.get().finished = true;
        OPEN.put(id, log.get());
        Entry e = entry.get();
        UndoTask task = new UndoTask(log.get(), level, source, "#" + id + " " + e.type() + " M" + e.magnitude(),
                undo.edits(e.dimension()), e.startedTick());
        addTask(task);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Undoing #" + id + " " + e.type() + " M" + e.magnitude()
                + ": " + task.total() + " blocks to restore" + (TASKS.size() > 1 ? " (after " + (TASKS.size() - 1) + " other undo(s))" : "") + "..."), true);
        return Optional.empty();
    }

    /** For /disaster undo list: how many blocks a record holds (reads it from disk if needed). */
    public static int blocks(MinecraftServer server, int id) {
        return read(server, id).map(UndoLog::changedBlocks).orElse(0);
    }

    public static boolean running(int id) {
        UndoLog log = OPEN.get(id);
        return log != null && !log.finished;
    }

    /** Items spawned while a disaster breaks a container: remember which container they came from. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (dropLog == null || !(event.getEntity() instanceof ItemEntity item) || event.getLevel().isClientSide()) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("d", dropLog.disasterId);
        tag.putLong("p", dropPos);
        item.getPersistentData().put(DROP_KEY, tag);
        dropLog.dropped(dropPos, item.getUUID(), item.getItem().getCount());
    }

    /** Thrown debris that landed: the block it became is part of the disaster's damage. */
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof FallingBlockEntity block) || !(event.getLevel() instanceof ServerLevel level)) return;
        Entity.RemovalReason reason = block.getRemovalReason();
        if (reason == null || reason != Entity.RemovalReason.DISCARDED) return;
        int id = block.getPersistentData().getIntOr(DEBRIS_KEY, -1);
        UndoLog log = id < 0 ? null : OPEN.get(id);
        if (log == null) return;
        BlockPos pos = block.blockPosition();
        BlockState now = level.getBlockState(pos);
        if (now == block.getBlockState()) log.record(pos, Blocks.AIR.defaultBlockState(), now, null);
    }

    Long2LongOpenHashMap edits(Identifier dim) {
        return playerEdits.computeIfAbsent(dim, k -> {
            Long2LongOpenHashMap m = new Long2LongOpenHashMap();
            m.defaultReturnValue(-1);
            return m;
        });
    }

    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.getPlayer() != null) playerEdit(event.getLevel(), event.getPos());
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player) playerEdit(event.getLevel(), event.getPos());
    }

    /** A player broke or placed a block here: an undo will leave this spot alone. */
    public static void playerEdit(net.minecraft.world.level.LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof ServerLevel level)) return;
        Undo undo = get(level.getServer());
        Identifier dim = level.dimension().identifier();
        if (undo.entries.stream().noneMatch(e -> e.near(dim, pos))) return;
        undo.edits(dim).put(pos.asLong(), level.getGameTime());
        undo.setDirty();
    }

    // ---- Saving ----

    public static void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) return;
        flush(level.getServer());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        OPEN.clear();
        TASKS.clear();
        dropLog = null;
    }

    /** Writes changed records; finished ones are then dropped from memory. */
    static void flush(MinecraftServer server) {
        Undo undo = get(server);
        for (var it = OPEN.int2ObjectEntrySet().iterator(); it.hasNext(); ) {
            UndoLog log = it.next().getValue();
            if (undo.entry(log.disasterId).isEmpty()) {
                it.remove();
                continue;
            }
            if (log.dirty) {
                write(server, log);
                log.dirty = false;
            }
            if (log.finished && TASKS.stream().noneMatch(t -> t.log == log)) it.remove();
        }
    }

    private static Path file(MinecraftServer server, int id) {
        return server.getWorldPath(LevelResource.ROOT).resolve(NaturalDisasters.MODID).resolve("undo").resolve(id + ".dat");
    }

    private static void write(MinecraftServer server, UndoLog log) {
        Path path = file(server, log.disasterId);
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(log.disasterId + ".tmp");
            NbtIo.writeCompressed(log.save(), tmp);
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            NaturalDisasters.LOGGER.error("Couldn't save undo record for disaster #{}", log.disasterId, e);
        }
    }

    static Optional<UndoLog> read(MinecraftServer server, int id) {
        UndoLog open = OPEN.get(id);
        if (open != null) return Optional.of(open);
        Path path = file(server, id);
        if (!Files.exists(path)) return Optional.empty();
        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            return Optional.of(UndoLog.load(tag, server.registryAccess().lookupOrThrow(Registries.BLOCK), Config.MAX_UNDO_BLOCKS.getAsInt()));
        } catch (IOException e) {
            NaturalDisasters.LOGGER.error("Couldn't read undo record for disaster #{}", id, e);
            return Optional.empty();
        }
    }

    /** Forgets a record: list entry, memory and file. */
    void forget(MinecraftServer server, int id) {
        entries.removeIf(e -> e.id() == id);
        setDirty();
        // Edits from before the oldest remaining record can't matter any more.
        long oldest = entries.stream().mapToLong(Entry::startedTick).min().orElse(Long.MAX_VALUE);
        for (Long2LongOpenHashMap m : playerEdits.values()) m.values().removeIf(t -> t < oldest);
        OPEN.remove(id);
        try {
            Files.deleteIfExists(file(server, id));
        } catch (IOException e) {
            NaturalDisasters.LOGGER.warn("Couldn't delete undo record for disaster #{}", id, e);
        }
    }

    /** Keeps the newest {@code undoRecords}; never drops the record of a disaster still running. */
    private void trim(MinecraftServer server) {
        int keep = Config.UNDO_RECORDS.getAsInt();
        List<Entry> oldestFirst = entries.stream().sorted(Comparator.comparingInt(Entry::id)).toList();
        int excess = entries.size() - keep;
        for (Entry e : oldestFirst) {
            if (excess <= 0) break;
            UndoLog open = OPEN.get(e.id());
            if (open != null && !open.finished) continue;
            forget(server, e.id());
            excess--;
        }
    }

    // ---- Undoing ----

    static void addTask(UndoTask task) {
        TASKS.add(task);
    }

    public static boolean busy(int id) {
        return TASKS.stream().anyMatch(t -> t.log.disasterId == id);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) return;
        UndoTask task = TASKS.getFirst();
        if (task.tick()) {
            TASKS.removeFirst();
            get(event.getServer()).forget(event.getServer(), task.log.disasterId);
        }
    }

    private static Undo fromTag(CompoundTag tag) {
        Undo undo = new Undo();
        ListTag list = tag.getListOrEmpty("entries");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            Identifier dim = Identifier.tryParse(t.getStringOr("dimension", "minecraft:overworld"));
            undo.entries.add(new Entry(t.getIntOr("id", 0), t.getStringOr("type", "?"), t.getIntOr("magnitude", 0),
                    dim == null ? Identifier.withDefaultNamespace("overworld") : dim,
                    BlockPos.of(t.getLongOr("center", 0L)), t.getLongOr("started", 0L), t.getIntOr("radius", 1_000_000)));
        }
        CompoundTag edits = tag.getCompoundOrEmpty("playerEdits");
        for (String key : edits.keySet()) {
            Identifier dim = Identifier.tryParse(key);
            if (dim == null) continue;
            long[] pos = edits.getCompoundOrEmpty(key).getLongArray("pos").orElse(new long[0]);
            long[] tick = edits.getCompoundOrEmpty(key).getLongArray("tick").orElse(new long[0]);
            Long2LongOpenHashMap map = undo.edits(dim);
            for (int i = 0; i < Math.min(pos.length, tick.length); i++) map.put(pos[i], tick[i]);
        }
        return undo;
    }

    private CompoundTag toTag() {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", e.id());
            t.putString("type", e.type());
            t.putInt("magnitude", e.magnitude());
            t.putString("dimension", e.dimension().toString());
            t.putLong("center", e.center().asLong());
            t.putLong("started", e.startedTick());
            t.putInt("radius", e.radius());
            list.add(t);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("entries", list);
        CompoundTag edits = new CompoundTag();
        for (var e : playerEdits.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLongArray("pos", e.getValue().keySet().toLongArray());
            t.putLongArray("tick", e.getValue().values().toLongArray());
            edits.put(e.getKey().toString(), t);
        }
        tag.put("playerEdits", edits);
        return tag;
    }
}
