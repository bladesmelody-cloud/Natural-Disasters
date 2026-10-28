package com.bladesmelody.naturaldisasters.gametest;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.disaster.Earthquake;
import com.bladesmelody.naturaldisasters.disaster.Flood;
import com.bladesmelody.naturaldisasters.disaster.Hurricane;
import com.bladesmelody.naturaldisasters.disaster.Meteor;
import com.bladesmelody.naturaldisasters.disaster.Tsunami;
import com.bladesmelody.naturaldisasters.disaster.Volcano;
import com.bladesmelody.naturaldisasters.disaster.Wildfire;
import com.bladesmelody.naturaldisasters.disaster.Tornado;
import com.bladesmelody.naturaldisasters.entity.MeteorEntity;
import com.bladesmelody.naturaldisasters.entity.TornadoEntity;
import com.bladesmelody.naturaldisasters.registry.ModRegistry;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
        FUNCTIONS.register("earthquake_wrecks_house", () -> ModGameTests::earthquakeWrecksHouse);
        FUNCTIONS.register("earthquake_heaves_ground", () -> ModGameTests::earthquakeHeavesGround);
        FUNCTIONS.register("earthquake_save_roundtrip", () -> ModGameTests::earthquakeSaveRoundtrip);
        FUNCTIONS.register("meteor_magnitude_table", () -> ModGameTests::meteorMagnitudeTable);
        FUNCTIONS.register("meteor_makes_crater", () -> ModGameTests::meteorMakesCrater);
        FUNCTIONS.register("meteor_save_roundtrip", () -> ModGameTests::meteorSaveRoundtrip);
        FUNCTIONS.register("meteorite_drops_and_smelts", () -> ModGameTests::meteoriteDropsAndSmelts);
        FUNCTIONS.register("tornado_ef_table", () -> ModGameTests::tornadoEfTable);
        FUNCTIONS.register("tornado_tears_path", () -> ModGameTests::tornadoTearsPath);
        FUNCTIONS.register("tornado_save_roundtrip", () -> ModGameTests::tornadoSaveRoundtrip);
        FUNCTIONS.register("flood_rises_and_drains", () -> ModGameTests::floodRisesAndDrains);
        FUNCTIONS.register("tsunami_wave_smashes_and_drains", () -> ModGameTests::tsunamiWave);
        FUNCTIONS.register("quiet_water_stays_put", () -> ModGameTests::quietWaterStaysPut);
        FUNCTIONS.register("hurricane_wind_and_surge", () -> ModGameTests::hurricaneWindAndSurge);
        FUNCTIONS.register("volcano_builds_and_erupts", () -> ModGameTests::volcanoBuildsAndErupts);
        FUNCTIONS.register("wildfire_burns_forest", () -> ModGameTests::wildfireBurnsForest);
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
        test(event, "earthquake_wrecks_house", arena(env, 500));
        test(event, "earthquake_save_roundtrip", arena(env, 20));
        test(event, "command_start_list_stop", arena(env, 40));
        test(event, "volcano_builds_and_erupts", arena(env, 500));
        test(event, "wildfire_burns_forest", arena(env, 400));
        test(event, "hurricane_wind_and_surge", arena(env, 300));
        test(event, "quiet_water_stays_put", arena(env, 100));
        test(event, "flood_rises_and_drains", arena(env, 400));
        test(event, "tsunami_wave_smashes_and_drains", arena(env, 400));
        test(event, "tornado_ef_table", new TestData<>(env, EMPTY, 20, 0, true));
        test(event, "tornado_tears_path", arena(env, 200));
        test(event, "tornado_save_roundtrip", arena(env, 20));
        test(event, "meteor_magnitude_table", new TestData<>(env, EMPTY, 20, 0, true));
        test(event, "meteor_makes_crater", arena(env, 300));
        test(event, "meteor_save_roundtrip", arena(env, 20));
        test(event, "meteorite_drops_and_smelts", new TestData<>(env, EMPTY, 20, 0, true));
    }

    /** The arena with sky access: without it the test gets a barrier roof, which quakes can't break. */
    private static TestData<Holder<TestEnvironmentDefinition<?>>> arena(Holder<TestEnvironmentDefinition<?>> env, int maxTicks) {
        return new TestData<>(env, ARENA, maxTicks, 0, true, Rotation.NONE, false, 1, 1, true, 0);
    }

    private static void test(RegisterGameTestsEvent event, String name, TestData<Holder<TestEnvironmentDefinition<?>>> data) {
        // -Dnaturaldisasters.tests=flood,tsunami runs only tests whose names contain one of these.
        String only = System.getProperty("naturaldisasters.tests", "");
        if (!only.isEmpty() && java.util.Arrays.stream(only.split(",")).noneMatch(name::contains)) return;
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
        return startQuake(helper, 3, params, seed);
    }

    private static Earthquake startQuake(GameTestHelper helper, int magnitude, Earthquake.Params params, long seed) {
        Earthquake quake = Earthquake.create(helper.absolutePos(CENTER), magnitude, params, seed, 0);
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

    /** An M8 quake knocks planks out of a hollow house and caves its roof in. */
    private static void earthquakeWrecksHouse(GameTestHelper helper) {
        fillFloor(helper);
        int x0 = CENTER.getX() - 4, z0 = CENTER.getZ() - 4, y0 = FLOOR_TOP + 1, roof = FLOOR_TOP + 5;
        int planks = 0;
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                boolean wall = x == 0 || z == 0 || x == 8 || z == 8;
                for (int y = y0; y <= roof; y++) {
                    if (wall || y == roof) {
                        helper.setBlock(new BlockPos(x0 + x, y, z0 + z), Blocks.OAK_PLANKS);
                        planks++;
                    }
                }
            }
        }
        int planksBefore = planks;
        // Long enough that each block gets sampled about as often as in a real M8 quake.
        Earthquake.Params p = params(0, 0.01, 0);
        Earthquake.Params longer = new Earthquake.Params(p.radius(), 300, 0, 0, 0, 0, p.shakeDensity(), 0, 0, 0, 0);
        Earthquake quake = startQuake(helper, 8, longer, 4242L);
        helper.succeedWhen(() -> {
            helper.assertTrue(quake.isFinished(), "quake still running");
            int standing = 0, roofInterior = 0;
            for (int x = 0; x < 9; x++) {
                for (int z = 0; z < 9; z++) {
                    boolean wall = x == 0 || z == 0 || x == 8 || z == 8;
                    for (int y = y0; y <= roof; y++) {
                        // Only where the house was built: rubble on the floor doesn't count.
                        if ((wall || y == roof) && helper.getBlockState(new BlockPos(x0 + x, y, z0 + z)).is(Blocks.OAK_PLANKS)) {
                            standing++;
                            if (y == roof && x > 0 && z > 0 && x < 8 && z < 8) roofInterior++;
                        }
                    }
                }
            }
            helper.assertTrue(standing < planksBefore * 0.8 && roofInterior < 49 - 15,
                    standing + " of " + planksBefore + " planks still in place, " + roofInterior + " of 49 inner roof blocks");
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

    /** Meteor showers grow with magnitude; only M8+ bring a giant impact. */
    private static void meteorMagnitudeTable(GameTestHelper helper) {
        helper.assertTrue(Meteor.Params.forMagnitude(1).count() == 10, "M1 count");
        helper.assertTrue(Meteor.Params.forMagnitude(7).giantRadius() == 0, "no giant at M7");
        helper.assertTrue(Meteor.Params.forMagnitude(8).giantRadius() > 0, "giant at M8");
        helper.assertTrue(Meteor.Params.forMagnitude(10).giantRadius() == 64, "M10 giant radius");
        helper.succeed();
    }

    /**
     * One meteor aimed at the arena centre: a meteor entity flies in, the pig
     * next to the impact gets hurt, and a scorched crater with meteorite ore
     * is left, with nothing changed beyond the crater.
     */
    private static void meteorMakesCrater(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        Pig pig = helper.spawn(EntityType.PIG, CENTER.offset(3, 1, 0));
        float pigHealth = pig.getHealth();
        int radius = 6;
        Meteor meteor = Meteor.create(helper.absolutePos(CENTER), 1, new Meteor.Params(0, 20, 1, radius, radius, 0), 31L, 0);
        DisasterManager.add(level, meteor);
        boolean[] sawMeteor = {false};
        helper.onEachTick(() -> {
            if (!level.getEntitiesOfClass(MeteorEntity.class, new AABB(helper.absolutePos(CENTER)).inflate(300)).isEmpty()) sawMeteor[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(meteor.isFinished(), "meteor still running");
            helper.assertTrue(sawMeteor[0], "no meteor entity was seen in the sky");
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < pigHealth, "pig next to the impact was not hurt");
            helper.assertTrue(helper.getBlockState(CENTER).isAir() && helper.getBlockState(CENTER.below()).isAir(), "no crater at the centre");
            int ore = count(helper, 1, FLOOR_TOP, s -> s.is(ModRegistry.METEORITE_ORE.get()));
            helper.assertTrue(ore >= 1, "no meteorite ore in the crater");
            int scorched = count(helper, 1, FLOOR_TOP, s -> s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.OBSIDIAN));
            helper.assertTrue(scorched >= 10, "only " + scorched + " scorched blocks");
            int outside = 0;
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    int dx = x - CENTER.getX(), dz = z - CENTER.getZ();
                    if (dx * dx + dz * dz <= (radius + 1) * (radius + 1)) continue;
                    for (int y = 1; y <= FLOOR_TOP; y++) if (!helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE)) outside++;
                }
            }
            helper.assertTrue(outside == 0, outside + " blocks changed outside the crater");
        });
    }

    /** A shower with a crater half dug saves and loads back the same. */
    private static void meteorSaveRoundtrip(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        Meteor meteor = Meteor.create(helper.absolutePos(CENTER), 1, new Meteor.Params(0, 1, 1, 8, 8, 0), 77L, 0);
        for (int i = 0; i < 64; i++) meteor.tick(level, new BlockWriter(level, 30, Long.MAX_VALUE));
        CompoundTag first = meteor.save();
        helper.assertTrue(!first.getCompoundOrEmpty("data").getListOrEmpty("craters").isEmpty(), "expected a crater in progress: " + first);
        Disaster loaded = DisasterTypes.load(first).orElseThrow();
        helper.assertTrue(loaded instanceof Meteor, "loaded as " + loaded.getClass().getSimpleName());
        helper.assertTrue(first.equals(loaded.save()), "save differs after reload:\n" + first + "\n" + loaded.save());
        helper.succeed();
    }

    /** Meteorite ore drops a chunk with an iron pickaxe, and the chunk smelts to an ingot. */
    private static void meteoriteDropsAndSmelts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var drops = Block.getDrops(ModRegistry.METEORITE_ORE.get().defaultBlockState(), level, helper.absolutePos(BlockPos.ZERO),
                null, null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.stream().anyMatch(s -> s.is(ModRegistry.METEORITE_CHUNK.get())), "ore dropped " + drops);
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(ModRegistry.METEORITE_CHUNK.get()));
        var recipe = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level);
        helper.assertTrue(recipe.isPresent(), "no smelting recipe for meteorite chunk");
        helper.assertTrue(recipe.get().value().assemble(input).is(ModRegistry.METEORIC_IRON_INGOT.get()), "smelts to the wrong item");
        helper.succeed();
    }

    /** EF rating and size rise with magnitude. */
    private static void tornadoEfTable(GameTestHelper helper) {
        helper.assertTrue(Tornado.Params.forMagnitude(1).ef() == 0, "M1 is EF0");
        helper.assertTrue(Tornado.Params.forMagnitude(10).ef() == 5, "M10 is EF5");
        helper.assertTrue(Tornado.Params.forMagnitude(10).coreRadius() > Tornado.Params.forMagnitude(1).coreRadius(), "bigger core");
        helper.succeed();
    }

    /**
     * An EF2 tornado crossing the arena west to east tears down a plank wall
     * and glass in its path, leaves the stone floor alone, lifts a pig, and
     * doesn't touch rows well outside its reach.
     */
    private static void tornadoTearsPath(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        int cx = CENTER.getX(), cz = CENTER.getZ();
        for (int dz = -3; dz <= 3; dz++) {
            for (int y = FLOOR_TOP + 1; y <= FLOOR_TOP + 3; y++) {
                helper.setBlock(new BlockPos(cx, y, cz + dz), Blocks.OAK_PLANKS);
                helper.setBlock(new BlockPos(cx + 4, y, cz + dz), Blocks.GLASS);
            }
        }
        for (int dx = -14; dx <= 14; dx++) helper.setBlock(new BlockPos(cx + dx, FLOOR_TOP + 1, cz + 12), Blocks.OAK_PLANKS);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(cx - 4, FLOOR_TOP + 1, cz));
        double pigStartY = pig.getY();
        double[] pigMaxY = {pigStartY};
        boolean[] sawFunnel = {false};
        Tornado.Params params = new Tornado.Params(2, 3, 135, 120, 0.2, 3, 20, 40);
        Tornado tornado = Tornado.create(helper.absolutePos(CENTER.offset(-12, 0, 0)), 5, params, 9L, 0, 0.0);
        DisasterManager.add(level, tornado);
        helper.onEachTick(() -> {
            if (pig.isAlive()) pigMaxY[0] = Math.max(pigMaxY[0], pig.getY());
            if (!level.getEntitiesOfClass(TornadoEntity.class, new AABB(helper.absolutePos(CENTER)).inflate(40)).isEmpty()) sawFunnel[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(tornado.isFinished(), "tornado still running");
            helper.assertTrue(sawFunnel[0], "no funnel entity");
            int planks = 0, glass = 0;
            for (int dz = -3; dz <= 3; dz++) {
                for (int y = FLOOR_TOP + 1; y <= FLOOR_TOP + 3; y++) {
                    if (helper.getBlockState(new BlockPos(cx, y, cz + dz)).is(Blocks.OAK_PLANKS)) planks++;
                    if (helper.getBlockState(new BlockPos(cx + 4, y, cz + dz)).is(Blocks.GLASS)) glass++;
                }
            }
            helper.assertTrue(planks < 10 && glass < 5, "wall survived: " + planks + "/21 planks, " + glass + "/21 glass");
            int floorHoles = count(helper, 1, FLOOR_TOP, s -> !s.is(Blocks.STONE));
            helper.assertTrue(floorHoles == 0, floorHoles + " stone floor blocks torn up by an EF2");
            int farRow = 0;
            for (int dx = -14; dx <= 14; dx++) if (helper.getBlockState(new BlockPos(cx + dx, FLOOR_TOP + 1, cz + 12)).is(Blocks.OAK_PLANKS)) farRow++;
            helper.assertTrue(farRow >= 27, "planks 12 blocks off the path were torn: " + farRow + "/29 left");
            helper.assertTrue(pigMaxY[0] > pigStartY + 2, "pig was not lifted (max " + (pigMaxY[0] - pigStartY) + " up)");
        });
    }

    /** A moving tornado saves and loads back the same. */
    private static void tornadoSaveRoundtrip(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        Tornado tornado = Tornado.create(helper.absolutePos(CENTER), 3, new Tornado.Params(1, 3, 110, 100, 0.2, 2, 20, 10), 5L, 0, 1.0);
        for (int i = 0; i < 10; i++) tornado.tick(level, new BlockWriter(level, 50, Long.MAX_VALUE));
        CompoundTag first = tornado.save();
        Disaster loaded = DisasterTypes.load(first).orElseThrow();
        helper.assertTrue(first.equals(loaded.save()), "save differs after reload:\n" + first + "\n" + loaded.save());
        helper.succeed();
    }

    /**
     * A flood in a dug-out basin rises over the floor, keeps a dry island and
     * the wall-free edge, then drains completely, leaving some mud.
     */
    private static void floodRisesAndDrains(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP, FLOOR_TOP, Blocks.GRASS_BLOCK.defaultBlockState());
        // A basin 2 deep in the middle with a pillar sticking out of it.
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int y = FLOOR_TOP - 1; y <= FLOOR_TOP; y++) helper.setBlock(CENTER.offset(dx, y - FLOOR_TOP, dz), Blocks.AIR);
                helper.setBlock(CENTER.offset(dx, -2, dz), Blocks.GRASS_BLOCK);
            }
        }
        for (int y = -2; y <= 4; y++) helper.setBlock(CENTER.offset(0, y, 0), Blocks.STONE);
        ServerLevel level = helper.getLevel();
        Flood flood = Flood.create(helper.absolutePos(CENTER), 3, new Flood.Params(14, 3, 60, 40, 60), 3L, 0);
        DisasterManager.add(level, flood);
        int[] maxWater = {0};
        helper.onEachTick(() -> maxWater[0] = Math.max(maxWater[0],
                count(helper, FLOOR_TOP - 2, FLOOR_TOP + 4, s -> s.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER))));
        helper.succeedWhen(() -> {
            helper.assertTrue(flood.isFinished(), "flood still running (max water " + maxWater[0] + ")");
            helper.assertTrue(maxWater[0] > 300, "flood never got deep: max " + maxWater[0] + " water blocks");
            int left = count(helper, 1, FLOOR_TOP + 6, s -> s.getFluidState().isSource());
            int sources = count(helper, 1, FLOOR_TOP + 6, s -> s.getFluidState().isSource());
            int high = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 6, s -> !s.getFluidState().isEmpty());
            StringBuilder where = new StringBuilder();
            for (int x = 0; x < SIZE && where.length() < 300; x++)
                for (int z = 0; z < SIZE; z++)
                    for (int y = 1; y <= FLOOR_TOP + 6; y++)
                        if (helper.getBlockState(new BlockPos(x, y, z)).getFluidState().isSource()) where.append(x).append(',').append(y).append(',').append(z).append(' ');
            helper.assertTrue(left == 0, left + " water sources left after the flood drained (" + sources + " sources, "
                    + high + " above floor, base " + (flood.base() - helper.absolutePos(BlockPos.ZERO).getY()) + ") sources at " + where);
            helper.assertBlockPresent(Blocks.STONE, CENTER.offset(0, 4, 0));
            int flowing = count(helper, 1, FLOOR_TOP + 6, s -> !s.getFluidState().isEmpty());
            helper.assertTrue(flowing == 0, flowing + " flowing water left (should dry up)");
            int mud = count(helper, 1, FLOOR_TOP, s -> s.is(Blocks.MUD));
            helper.assertTrue(mud > 0, "no mud left behind");
        });
    }

    /**
     * A tsunami runs east across the arena: it reaches over the floor, knocks
     * down a plank wall, and drains away again (a few puddles allowed).
     */
    private static void tsunamiWave(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos center = CENTER.offset(6, 0, 0);
        for (int dz = -3; dz <= 3; dz++) {
            for (int y = 1; y <= 3; y++) helper.setBlock(center.offset(0, y, dz), Blocks.OAK_PLANKS);
        }
        Tsunami tsunami = Tsunami.create(helper.absolutePos(center), 5, new Tsunami.Params(6, 5, 8, 0.5), 21L, 0.0,
                helper.absolutePos(CENTER).getY());
        DisasterManager.add(level, tsunami);
        int[] maxWater = {0};
        helper.onEachTick(() -> maxWater[0] = Math.max(maxWater[0],
                count(helper, FLOOR_TOP + 1, FLOOR_TOP + 6, s -> s.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER))));
        helper.succeedWhen(() -> {
            helper.assertTrue(tsunami.isFinished(), "tsunami still running (max water " + maxWater[0] + ")");
            helper.assertTrue(maxWater[0] > 100, "the wave never came over the floor: max " + maxWater[0]);
            int planks = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 3, s -> s.is(Blocks.OAK_PLANKS));
            helper.assertTrue(planks < 8, planks + "/21 planks survived the wave");
            int left = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 8, s -> !s.getFluidState().isEmpty());
            helper.assertTrue(left < 40, left + " water blocks left after the wave");
        });
    }

    /** Water placed and removed quietly never starts flowing (floods and tsunamis rely on it). */
    private static void quietWaterStaysPut(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockWriter w = new BlockWriter(level, 100, Long.MAX_VALUE);
        w.setQuiet(helper.absolutePos(CENTER.above()), Blocks.WATER.defaultBlockState());
        w.setQuiet(helper.absolutePos(CENTER.above().east()), Blocks.WATER.defaultBlockState());
        helper.runAfterDelay(20, () -> w.setQuiet(helper.absolutePos(CENTER.above().east()), Blocks.AIR.defaultBlockState()));
        helper.runAfterDelay(60, () -> {
            int n = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> !s.getFluidState().isEmpty());
            helper.assertTrue(n == 1, "quiet water spread: " + n);
            helper.succeed();
        });
    }

    /**
     * A small category-4 hurricane parked over the arena: its wind strips
     * leaves and planks off the top, pushes a pig in the open, the surge
     * floods the low land next to a water channel, and it all drains after.
     */
    private static void hurricaneWindAndSurge(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP, FLOOR_TOP, Blocks.GRASS_BLOCK.defaultBlockState());
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z < SIZE; z++) level.setBlock(helper.absolutePos(new BlockPos(x, FLOOR_TOP, z)), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        int leavesBefore = 0;
        for (int dx = -10; dx <= 10; dx += 2) {
            for (int dz = -10; dz <= 10; dz += 2) {
                if (dx * dx + dz * dz < 16 || dx * dx + dz * dz > 120) continue;
                helper.setBlock(CENTER.offset(dx, 1, dz), (dx + dz) % 4 == 0 ? Blocks.OAK_LEAVES : Blocks.OAK_PLANKS);
                leavesBefore++;
            }
        }
        int built = leavesBefore;
        Pig pig = helper.spawn(EntityType.PIG, CENTER.offset(8, 1, 1));
        Vec3 pigStart = pig.position();
        double[] pigMoved = {0};
        int[] surgeMax = {0};
        Hurricane hurricane = Hurricane.create(helper.absolutePos(CENTER), 7,
                new Hurricane.Params(4, 16, 3, 145, 100, 0.0, 2, 0.05), 13L, 0.0, helper.absolutePos(CENTER).getY());
        DisasterManager.add(level, hurricane);
        helper.onEachTick(() -> {
            if (pig.isAlive()) pigMoved[0] = Math.max(pigMoved[0], Math.hypot(pig.getX() - pigStart.x, pig.getZ() - pigStart.z));
            surgeMax[0] = Math.max(surgeMax[0], count(helper, FLOOR_TOP + 1, FLOOR_TOP + 2, s -> s.getFluidState().isSource()));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hurricane.isFinished(), "hurricane still running");
            int standing = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.OAK_LEAVES) || s.is(Blocks.OAK_PLANKS));
            helper.assertTrue(standing < built / 2, standing + "/" + built + " leaves/planks survived");
            helper.assertTrue(pigMoved[0] > 2, "pig barely moved: " + pigMoved[0]);
            helper.assertTrue(surgeMax[0] > 10, "no storm surge (max " + surgeMax[0] + " water blocks above the channel)");
            int left = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 3, s -> s.getFluidState().isSource());
            helper.assertTrue(left == 0, left + " surge water left");
        });
    }

    /**
     * A small volcano: a cone rises, erupts with lava rivers and bombs, and
     * when it's over the rivers have cooled to basalt while the crater keeps
     * its lava lake.
     */
    private static void volcanoBuildsAndErupts(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        Volcano volcano = Volcano.create(helper.absolutePos(CENTER), 3, new Volcano.Params(8, 12, 2, 60, 120, 2, 14, 10), 17L, 0);
        DisasterManager.add(level, volcano);
        boolean[] sawLavaRiver = {false};
        helper.onEachTick(() -> {
            if (count(helper, FLOOR_TOP + 1, FLOOR_TOP + 8, s -> s.is(Blocks.LAVA)) > 20) sawLavaRiver[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(volcano.isFinished(), "volcano still running");
            int peak = helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    helper.absolutePos(CENTER.offset(3, 0, 0)).getX(), helper.absolutePos(CENTER).getZ()) - 1 - helper.absolutePos(BlockPos.ZERO).getY();
            helper.assertTrue(peak >= FLOOR_TOP + 4, "no cone: ground next to the vent is at " + peak);
            helper.assertTrue(sawLavaRiver[0], "never saw lava rivers");
            int basalt = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 9, s -> s.is(Blocks.BASALT) || s.is(Blocks.MAGMA_BLOCK));
            helper.assertTrue(basalt > 20, "only " + basalt + " basalt/magma");
            int lavaOutside = 0;
            for (int x = 0; x < SIZE; x++)
                for (int z = 0; z < SIZE; z++) {
                    int dx = x - CENTER.getX(), dz = z - CENTER.getZ();
                    if (dx * dx + dz * dz <= 9) continue;
                    for (int y = 1; y < 24; y++) if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.LAVA)) lavaOutside++;
                }
            helper.assertTrue(lavaOutside == 0, lavaOutside + " lava blocks outside the crater didn't cool");
        });
    }

    /** A wildfire burns through a little forest and leaves scorched ground. */
    private static void wildfireBurnsForest(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP, FLOOR_TOP, Blocks.GRASS_BLOCK.defaultBlockState());
        ServerLevel level = helper.getLevel();
        int logs = 0, leaves = 0;
        for (int dx = -12; dx <= 12; dx += 4) {
            for (int dz = -12; dz <= 12; dz += 4) {
                if (dx * dx + dz * dz > 150) continue;
                BlockPos base = CENTER.offset(dx, 1, dz);
                for (int y = 0; y < 4; y++, logs++) helper.setBlock(base.above(y), Blocks.OAK_LOG);
                for (int ox = -1; ox <= 1; ox++)
                    for (int oz = -1; oz <= 1; oz++)
                        for (int y = 3; y <= 4; y++) {
                            if (ox == 0 && oz == 0 && y == 3) continue;
                            helper.setBlock(base.offset(ox, y, oz), Blocks.OAK_LEAVES.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                            leaves++;
                        }
            }
        }
        int logsBefore = logs, leavesBefore = leaves;
        Wildfire fire = Wildfire.create(helper.absolutePos(CENTER.offset(2, 1, 0)), 5, new Wildfire.Params(16, 200, 1, 0.6, 300, 3000), 23L, 0);
        DisasterManager.add(level, fire);
        helper.succeedWhen(() -> {
            helper.assertTrue(fire.isFinished(), "fire still burning (" + fire.burning() + " spots)");
            int logsLeft = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 6, s -> s.is(Blocks.OAK_LOG));
            int leavesLeft = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 6, s -> s.is(Blocks.OAK_LEAVES));
            int scorched = count(helper, FLOOR_TOP, FLOOR_TOP, s -> s.is(Blocks.COARSE_DIRT));
            helper.assertTrue(logsLeft < logsBefore * 0.6 && leavesLeft < leavesBefore * 0.5 && scorched > 20,
                    "logs " + logsLeft + "/" + logsBefore + ", leaves " + leavesLeft + "/" + leavesBefore + ", scorched " + scorched);
        });
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
