package com.bladesmelody.naturaldisasters.gametest;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.disaster.Earthquake;
import com.bladesmelody.naturaldisasters.engine.BlockWriter;
import com.bladesmelody.naturaldisasters.engine.Disaster;
import com.bladesmelody.naturaldisasters.engine.DisasterManager;
import com.bladesmelody.naturaldisasters.engine.DisasterType;
import com.bladesmelody.naturaldisasters.engine.DisasterTypes;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Game tests. Run with {@code ./gradlew runGameTestServer}.
 *
 * <p>Earthquake tests use the 40x24x40 {@code arena} structure: a stone floor
 * is laid at relative y 1–12 and a small quake runs at its centre, so the
 * whole quake stays inside the arena.
 */
public final class ModGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, NaturalDisasters.MODID);

    private static final Identifier EMPTY = Identifier.withDefaultNamespace("empty");
    private static final Identifier ARENA = id("arena");
    private static final int SIZE = 40;
    private static final int FLOOR_TOP = 12;
    private static final BlockPos CENTER = new BlockPos(SIZE / 2, FLOOR_TOP, SIZE / 2);
    private static final int QUAKE_RADIUS = 14;

    static {
        FUNCTIONS.register("smoke", () -> ModGameTests::smoke);
        FUNCTIONS.register("block_budget_caps_changes", () -> ModGameTests::blockBudgetCapsChanges);
        FUNCTIONS.register("magnitude_table", () -> ModGameTests::magnitudeTable);
        FUNCTIONS.register("earthquake_opens_fissures", () -> ModGameTests::earthquakeOpensFissures);
        FUNCTIONS.register("earthquake_shatters_and_cracks", () -> ModGameTests::earthquakeShattersAndCracks);
        FUNCTIONS.register("earthquake_heaves_ground", () -> ModGameTests::earthquakeHeavesGround);
        FUNCTIONS.register("earthquake_save_roundtrip", () -> ModGameTests::earthquakeSaveRoundtrip);
        FUNCTIONS.register("command_start_list_stop", () -> ModGameTests::commandStartListStop);
    }

    private ModGameTests() {}

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(id("default"));
        test(event, "smoke", new TestData<>(env, EMPTY, 20, 0, true));
        test(event, "block_budget_caps_changes", arena(env, 20));
        test(event, "magnitude_table", new TestData<>(env, EMPTY, 20, 0, true));
        test(event, "earthquake_opens_fissures", arena(env, 300));
        test(event, "earthquake_shatters_and_cracks", arena(env, 300));
        test(event, "earthquake_heaves_ground", arena(env, 300));
        test(event, "earthquake_save_roundtrip", arena(env, 20));
        test(event, "command_start_list_stop", arena(env, 40));
    }

    /** The arena with sky access: without it the test gets a barrier roof, which quakes can't break. */
    private static TestData<Holder<TestEnvironmentDefinition<?>>> arena(Holder<TestEnvironmentDefinition<?>> env, int maxTicks) {
        return new TestData<>(env, ARENA, maxTicks, 0, true, Rotation.NONE, false, 1, 1, true, 0);
    }

    private static void test(RegisterGameTestsEvent event, String name, TestData<Holder<TestEnvironmentDefinition<?>>> data) {
        event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)), data));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, path);
    }

    // ---- Helpers ----

    private static void fillFloor(GameTestHelper helper) {
        fill(helper, 1, FLOOR_TOP, Blocks.STONE.defaultBlockState());
    }

    private static void fill(GameTestHelper helper, int fromY, int toY, BlockState state) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = fromY; y <= toY; y++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static int count(GameTestHelper helper, int fromY, int toY, Predicate<BlockState> what) {
        int n = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = fromY; y <= toY; y++) {
                    if (what.test(helper.getBlockState(new BlockPos(x, y, z)))) n++;
                }
            }
        }
        return n;
    }

    /** Blocks matching {@code what} in columns farther than the quake radius from the centre. */
    private static int countOutside(GameTestHelper helper, int fromY, int toY, Predicate<BlockState> what) {
        int n = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int dx = x - CENTER.getX(), dz = z - CENTER.getZ();
                if (dx * dx + dz * dz <= (QUAKE_RADIUS + 1) * (QUAKE_RADIUS + 1)) continue;
                for (int y = fromY; y <= toY; y++) {
                    if (what.test(helper.getBlockState(new BlockPos(x, y, z)))) n++;
                }
            }
        }
        return n;
    }

    private static int outsideColumns() {
        int n = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int dx = x - CENTER.getX(), dz = z - CENTER.getZ();
                if (dx * dx + dz * dz > (QUAKE_RADIUS + 1) * (QUAKE_RADIUS + 1)) n++;
            }
        }
        return n;
    }

    /** A quake with only the given effects switched on. */
    private static Earthquake.Params params(int fissures, double shakeDensity, int heaves) {
        return new Earthquake.Params(QUAKE_RADIUS, 40, fissures, 20, 1, 6, shakeDensity,
                heaves, 5, 2, 0.0);
    }

    private static Earthquake startQuake(GameTestHelper helper, Earthquake.Params params, long seed) {
        Earthquake quake = Earthquake.create(helper.absolutePos(CENTER), 3, params, seed, 0);
        DisasterManager.add(helper.getLevel(), quake);
        return quake;
    }

    // ---- Tests ----

    /** The mod loads and a test can run. */
    private static void smoke(GameTestHelper helper) {
        helper.succeed();
    }

    /** A writer with a budget of 10 makes exactly 10 changes, and never breaks bedrock. */
    private static void blockBudgetCapsChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockWriter writer = new BlockWriter(level, 10, Long.MAX_VALUE);
        for (int i = 0; i < 50 && writer.hasBudget(); i++) {
            writer.set(helper.absolutePos(new BlockPos(i % SIZE, 2, i / SIZE)), Blocks.STONE.defaultBlockState());
        }
        helper.assertTrue(writer.changed() == 10, "expected 10 changes, got " + writer.changed());
        helper.assertTrue(!writer.hasBudget(), "budget should be used up");
        helper.assertTrue(count(helper, 2, 2, s -> s.is(Blocks.STONE)) == 10, "expected 10 stone blocks placed");

        BlockPos bedrock = new BlockPos(5, 5, 5);
        helper.setBlock(bedrock, Blocks.BEDROCK);
        BlockWriter second = new BlockWriter(level, 10, Long.MAX_VALUE);
        helper.assertTrue(!second.set(helper.absolutePos(bedrock), Blocks.AIR.defaultBlockState()), "bedrock must not be editable");
        helper.assertBlockPresent(Blocks.BEDROCK, bedrock);
        helper.succeed();
    }

    /** Magnitude sizes follow the table; small quakes are rolled far more often than huge ones. */
    private static void magnitudeTable(GameTestHelper helper) {
        helper.assertTrue(Earthquake.Params.forMagnitude(1).radius() == 16, "M1 radius");
        helper.assertTrue(Earthquake.Params.forMagnitude(5).radius() == 64, "M5 radius");
        helper.assertTrue(Earthquake.Params.forMagnitude(10).radius() == 256, "M10 radius");
        helper.assertTrue(Earthquake.Params.forMagnitude(10).durationTicks() == 120 * 20, "M10 duration");
        helper.assertTrue(Earthquake.Params.forMagnitude(3).heaves() == 0, "no heave below M7");
        helper.assertTrue(Earthquake.Params.forMagnitude(8).heaves() > 0, "heave at M8");
        int[] counts = new int[11];
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 20000; i++) counts[DisasterType.rollMagnitude(random)]++;
        helper.assertTrue(counts[1] > counts[5] && counts[5] > counts[10], "magnitudes should get rarer: M1="
                + counts[1] + " M5=" + counts[5] + " M10=" + counts[10]);
        helper.succeed();
    }

    /** Fissures carve deep air gaps into the floor, and nothing outside the radius is touched. */
    private static void earthquakeOpensFissures(GameTestHelper helper) {
        fillFloor(helper);
        Earthquake quake = startQuake(helper, params(2, 0, 0), 1234L);
        helper.succeedWhen(() -> {
            helper.assertTrue(quake.isFinished(), "quake still running");
            int carved = count(helper, 1, FLOOR_TOP, BlockState::isAir);
            helper.assertTrue(carved > 60, "expected fissures, only " + carved + " air blocks in floor");
            int deep = count(helper, FLOOR_TOP - 4, FLOOR_TOP - 4, BlockState::isAir);
            helper.assertTrue(deep > 5, "fissures should go deep, only " + deep + " air blocks 4 down");
            int outside = countOutside(helper, 1, FLOOR_TOP, BlockState::isAir);
            helper.assertTrue(outside == 0, outside + " blocks changed outside the quake radius");
        });
    }

    /** Shaking shatters glass and cracks stone into cobblestone. */
    private static void earthquakeShattersAndCracks(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, Blocks.GLASS.defaultBlockState());
        Earthquake quake = startQuake(helper, params(0, 0.05, 0), 99L);
        int glassBefore = SIZE * SIZE;
        helper.succeedWhen(() -> {
            helper.assertTrue(quake.isFinished(), "quake still running");
            int glass = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.GLASS));
            helper.assertTrue(glassBefore - glass > 20, "only " + (glassBefore - glass) + " glass shattered");
            int cobble = count(helper, 1, FLOOR_TOP, s -> s.is(Blocks.COBBLESTONE) || s.is(Blocks.GRAVEL));
            helper.assertTrue(cobble > 20, "only " + cobble + " stone cracked");
            int outsideGlass = countOutside(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.GLASS));
            helper.assertTrue(outsideGlass == outsideColumns(), "glass outside the radius was broken");
        });
    }

    /** A heave patch lifts ground above the old surface or sinks it below. */
    private static void earthquakeHeavesGround(GameTestHelper helper) {
        fillFloor(helper);
        Earthquake quake = startQuake(helper, params(0, 0, 1), 7L);
        helper.succeedWhen(() -> {
            helper.assertTrue(quake.isFinished(), "quake still running");
            int raised = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 4, s -> !s.isAir());
            int sunk = count(helper, FLOOR_TOP, FLOOR_TOP, BlockState::isAir);
            helper.assertTrue(raised + sunk > 10, "ground did not move (raised " + raised + ", sunk " + sunk + ")");
        });
    }

    /** A half-finished quake saves and loads back to the same state. */
    private static void earthquakeSaveRoundtrip(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        Earthquake quake = Earthquake.create(helper.absolutePos(CENTER), 3, params(2, 0.01, 1), 555L, 0);
        for (int i = 0; i < 15; i++) quake.tick(level, new BlockWriter(level, 40, Long.MAX_VALUE));
        CompoundTag first = quake.save();
        Disaster loaded = DisasterTypes.load(first).orElseThrow();
        helper.assertTrue(loaded instanceof Earthquake, "loaded as " + loaded.getClass().getSimpleName());
        helper.assertTrue(first.equals(loaded.save()), "save differs after reload:\n" + first + "\n" + loaded.save());
        helper.succeed();
    }

    /** /disaster start, list and stop work. */
    private static void commandStartListStop(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(CENTER);
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
        int before = DisasterManager.all(level.getServer()).size();
        level.getServer().getCommands().performPrefixedCommand(source,
                "disaster start earthquake 1 " + at.getX() + " " + at.getY() + " " + at.getZ());
        var started = DisasterManager.all(level.getServer()).stream()
                .filter(r -> r.disaster().center().equals(at)).findFirst();
        helper.assertTrue(started.isPresent(), "command did not start a quake (had " + before + ")");
        Disaster d = started.get().disaster();
        helper.assertTrue(d.magnitude() == 1 && d.type() == DisasterTypes.EARTHQUAKE, "wrong quake: " + d.describe());
        level.getServer().getCommands().performPrefixedCommand(source, "disaster list");
        level.getServer().getCommands().performPrefixedCommand(source, "disaster stop " + d.id());
        helper.assertTrue(d.isFinished(), "stop did not stop it");
        helper.assertTrue(DisasterManager.all(level.getServer()).stream().noneMatch(r -> r.disaster() == d), "still listed");
        helper.succeed();
    }
}
