package com.bladesmelody.naturaldisasters.gametest;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.disaster.AcidRain;
import com.bladesmelody.naturaldisasters.disaster.AshCloud;
import com.bladesmelody.naturaldisasters.disaster.Blizzard;
import com.bladesmelody.naturaldisasters.disaster.Drought;
import com.bladesmelody.naturaldisasters.disaster.Earthquake;
import com.bladesmelody.naturaldisasters.disaster.Hailstorm;
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
import java.util.List;
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
        FUNCTIONS.register("woken_flood_leaves_no_water", () -> ModGameTests::wokenFloodLeavesNoWater);
        FUNCTIONS.register("tsunami_wave_smashes_and_drains", () -> ModGameTests::tsunamiWave);
        FUNCTIONS.register("quiet_water_stays_put", () -> ModGameTests::quietWaterStaysPut);
        FUNCTIONS.register("hurricane_wind_and_surge", () -> ModGameTests::hurricaneWindAndSurge);
        FUNCTIONS.register("volcano_builds_and_erupts", () -> ModGameTests::volcanoBuildsAndErupts);
        FUNCTIONS.register("woken_lava_cools", () -> ModGameTests::wokenLavaCools);
        FUNCTIONS.register("wildfire_burns_forest", () -> ModGameTests::wildfireBurnsForest);
        FUNCTIONS.register("blizzard_buries_and_freezes", () -> ModGameTests::blizzardBuriesAndFreezes);
        FUNCTIONS.register("hailstorm_pelts", () -> ModGameTests::hailstormPelts);
        FUNCTIONS.register("acid_rain_corrodes", () -> ModGameTests::acidRainCorrodes);
        FUNCTIONS.register("drought_dries_out", () -> ModGameTests::droughtDriesOut);
        FUNCTIONS.register("all_types_save_roundtrip", () -> ModGameTests::allTypesSaveRoundtrip);
        FUNCTIONS.register("builds_are_detected", () -> ModGameTests::buildsAreDetected);
        FUNCTIONS.register("instruments_sense_and_warn", () -> ModGameTests::instrumentsSenseAndWarn);
        FUNCTIONS.register("safe_zone_stops_quake", () -> ModGameTests::safeZoneStopsQuake);
        FUNCTIONS.register("undo_restores_without_dupes", () -> ModGameTests::undoRestoresWithoutDupes);
        FUNCTIONS.register("survivor_advancement", () -> ModGameTests::survivorAdvancement);
        FUNCTIONS.register("meteoric_gear_beats_netherite", () -> ModGameTests::meteoricGearBeatsNetherite);
        FUNCTIONS.register("meteoric_armor_resists_disasters", () -> ModGameTests::meteoricArmorResistsDisasters);
        FUNCTIONS.register("ward_protects_its_radius", () -> ModGameTests::wardProtectsItsRadius);
        FUNCTIONS.register("ward_radius_cycles", () -> ModGameTests::wardRadiusCycles);
        FUNCTIONS.register("safe_zone_spares_and_crumbles", () -> ModGameTests::safeZoneSparesAndCrumbles);
        if (net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")) {
            FUNCTIONS.register("opac_claim_is_protected", () -> ClaimTests::opacClaimIsProtected);
        }
        if (net.neoforged.fml.ModList.get().isLoaded("flan")) {
            FUNCTIONS.register("flan_claim_is_protected", () -> ClaimTests::flanClaimIsProtected);
        }
        FUNCTIONS.register("volcano_vei_table", () -> ModGameTests::volcanoVeiTable);
        FUNCTIONS.register("volcano_heat_wave_and_ash", () -> ModGameTests::volcanoHeatWaveAndAsh);
        FUNCTIONS.register("ash_cloud_stops_the_clock", () -> ModGameTests::ashCloudStopsTheClock);
        FUNCTIONS.register("ash_zone_lets_monsters_spawn", () -> ModGameTests::ashZoneLetsMonstersSpawn);
        FUNCTIONS.register("command_start_list_stop", () -> ModGameTests::commandStartListStop);
    }

    private ModGameTests() {}

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(id("default"));
        // Tests with (mock) players run in their own batch, after the rest: disasters react to players
        // (the world-wide ash cloud ashes the chunks around them), which would slow down the other tests.
        Holder<TestEnvironmentDefinition<?>> withPlayers = event.registerEnvironment(id("with_players"));
        test(event, "smoke", new TestData<>(env, EMPTY, 20, 0, true));
        test(event, "block_budget_caps_changes", arena(env, 20));
        test(event, "magnitude_table", new TestData<>(env, EMPTY, 20, 0, true));
        test(event, "earthquake_opens_fissures", arena(env, 300));
        test(event, "earthquake_shatters_and_cracks", arena(env, 300));
        test(event, "earthquake_heaves_ground", arena(env, 300));
        test(event, "earthquake_wrecks_house", arena(env, 650));
        test(event, "earthquake_save_roundtrip", arena(env, 20));
        test(event, "command_start_list_stop", arena(env, 40));
        test(event, "volcano_vei_table", new TestData<>(env, EMPTY, 20, 0, true));
        test(event, "volcano_heat_wave_and_ash", arena(env, 500));
        test(event, "ash_cloud_stops_the_clock", arena(env, 200));
        test(event, "ash_zone_lets_monsters_spawn", arena(env, 40));
        test(event, "builds_are_detected", arena(env, 20));
        test(event, "instruments_sense_and_warn", arena(env, 20));
        test(event, "safe_zone_stops_quake", arena(env, 300));
        test(event, "undo_restores_without_dupes", arena(env, 100));
        test(event, "survivor_advancement", arena(withPlayers, 400));
        test(event, "meteoric_gear_beats_netherite", new TestData<>(env, EMPTY, 20, 0, true));
        test(event, "meteoric_armor_resists_disasters", arena(env, 20));
        test(event, "ward_protects_its_radius", arena(env, 20));
        test(event, "ward_radius_cycles", arena(withPlayers, 20));
        test(event, "safe_zone_spares_and_crumbles", arena(env, 60));
        if (net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")) test(event, "opac_claim_is_protected", arena(env, 20));
        if (net.neoforged.fml.ModList.get().isLoaded("flan")) test(event, "flan_claim_is_protected", arena(env, 20));
        test(event, "all_types_save_roundtrip", arena(env, 20));
        test(event, "acid_rain_corrodes", arena(env, 300));
        test(event, "drought_dries_out", arena(env, 550));
        test(event, "blizzard_buries_and_freezes", arena(env, 300));
        test(event, "hailstorm_pelts", arena(env, 300));
        test(event, "volcano_builds_and_erupts", arena(env, 500));
        test(event, "woken_lava_cools", arena(env, 1500));
        test(event, "wildfire_burns_forest", arena(env, 400));
        test(event, "hurricane_wind_and_surge", arena(env, 300));
        test(event, "quiet_water_stays_put", arena(env, 100));
        test(event, "flood_rises_and_drains", arena(env, 400));
        test(event, "woken_flood_leaves_no_water", arena(env, 400));
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

    /**
     * A disaster's record breaks two full chests, smashes glass and pours
     * water; then a player builds on one spot, water flows into another, and
     * one chest's dropped items go missing. Undo restores everything but the
     * player's spot (the water is overwritten), refills the chest whose items
     * are all still on the ground (taking them back) and brings the other
     * back empty.
     */
    private static void undoRestoresWithoutDupes(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos chestA = CENTER.above(), chestB = CENTER.above().east(3);
        for (BlockPos p : List.of(chestA, chestB)) {
            helper.setBlock(p, Blocks.CHEST);
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(helper.absolutePos(p));
            chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 5));
        }
        List<BlockPos> glass = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            glass.add(CENTER.above().north(3).west(i));
            helper.setBlock(glass.get(i), Blocks.GLASS);
        }
        // A disaster that stays in its warning stage: it only gets an id and an undo record.
        Earthquake quake = Earthquake.create(helper.absolutePos(CENTER), 1, params(0, 0, 0), 5L, 1_000_000);
        DisasterManager.add(level, quake);
        var log = com.bladesmelody.naturaldisasters.undo.Undo.log(level, quake);
        BlockWriter w = new BlockWriter(level, 1000, Long.MAX_VALUE, log);
        w.set(helper.absolutePos(chestA), Blocks.AIR.defaultBlockState());
        w.set(helper.absolutePos(chestB), Blocks.AIR.defaultBlockState());
        for (BlockPos p : glass) w.set(helper.absolutePos(p), Blocks.AIR.defaultBlockState());
        BlockPos wet = CENTER.above().south(3);
        w.setQuiet(helper.absolutePos(wet), Blocks.WATER.defaultBlockState());
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(CENTER)).inflate(8));
        helper.assertTrue(drops.size() == 2, "expected 2 dropped stacks, got " + drops.size());
        // Chest B's diamonds get "picked up".
        drops.stream().filter(e -> e.blockPosition().closerThan(helper.absolutePos(chestB), 1.5)).forEach(net.minecraft.world.entity.Entity::discard);
        helper.setBlock(glass.get(1), Blocks.DIRT); // built over by a player...
        com.bladesmelody.naturaldisasters.undo.Undo.playerEdit(level, helper.absolutePos(glass.get(1)));
        helper.setBlock(glass.get(0), Blocks.WATER); // ...but water flowing in is nature: overwritten
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        var failure = com.bladesmelody.naturaldisasters.undo.Undo.start(level.getServer(), quake.id(), source);
        helper.assertTrue(failure.isEmpty(), "undo didn't start: " + failure.orElse(""));
        helper.succeedWhen(() -> {
            helper.assertTrue(!com.bladesmelody.naturaldisasters.undo.Undo.busy(quake.id()), "undo still running");
            helper.assertBlockPresent(Blocks.DIRT, glass.get(1));
            helper.assertBlockPresent(Blocks.GLASS, glass.get(0));
            for (int i = 2; i < 5; i++) helper.assertBlockPresent(Blocks.GLASS, glass.get(i));
            helper.assertBlockPresent(Blocks.AIR, wet);
            var a = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(helper.absolutePos(chestA));
            var b = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(helper.absolutePos(chestB));
            helper.assertTrue(a != null && a.getItem(0).getCount() == 5, "chest A not refilled");
            helper.assertTrue(b != null && b.isEmpty(), "chest B should come back empty");
            int left = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(CENTER)).inflate(8)).size();
            helper.assertTrue(left == 0, left + " dropped stacks still lying around (duplicates)");
            helper.assertTrue(DisasterManager.all(level.getServer()).stream().noneMatch(r -> r.disaster() == quake), "disaster not stopped");
        });
    }

    /**
     * A player caught in a small quake earns "Still Standing" when it ends;
     * one who dies during it doesn't. All 16 advancements load.
     */
    private static void survivorAdvancement(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        var advancements = level.getServer().getAdvancements();
        for (String name : List.of("root", "survivor", "seen_it_all", "the_big_one", "not_in_kansas", "category_five",
                "year_without_summer", "eye_of_the_storm", "ride_the_wind", "no_rest", "seismograph", "barometer",
                "sky_watcher", "forewarned", "fallen_star", "star_metal", "forged_from_the_stars", "warded")) {
            helper.assertTrue(advancements.get(net.minecraft.resources.Identifier.fromNamespaceAndPath("naturaldisasters", name)) != null,
                    "advancement " + name + " didn't load");
        }
        var survivor = advancements.get(net.minecraft.resources.Identifier.fromNamespaceAndPath("naturaldisasters", "survivor"));
        var lives = helper.makeMockServerPlayerInLevel();
        var dies = helper.makeMockServerPlayerInLevel();
        lives.snapTo(helper.absolutePos(CENTER.above()).getCenter());
        dies.snapTo(helper.absolutePos(CENTER.above().east(2)).getCenter());
        Earthquake quake = startQuake(helper, 1, params(0, 0, 0), 9L);
        // Freshly joined players are briefly invulnerable, so die directly (same death event as a real death).
        helper.runAfterDelay(10, () -> {
            dies.setHealth(0);
            dies.die(level.damageSources().genericKill());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(quake.isFinished() && DisasterManager.all(level.getServer()).stream().noneMatch(r -> r.disaster() == quake), "quake still running");
            helper.assertTrue(lives.getAdvancements().getOrStartProgress(survivor).isDone(), "survivor didn't get Still Standing");
            helper.assertTrue(!dies.getAdvancements().getOrStartProgress(survivor).isDone(), "a player who died got Still Standing");
            // Mock players stay in the world otherwise, and disasters in later tests react to them.
            level.getServer().getPlayerList().remove(lives);
            level.getServer().getPlayerList().remove(dies);
        });
    }

    /** Meteoric gear is a step above netherite, and every new recipe loads. */
    private static void meteoricGearBeatsNetherite(GameTestHelper helper) {
        var mat = com.bladesmelody.naturaldisasters.meteorite.MeteoricMaterials.TOOLS;
        var neth = net.minecraft.world.item.ToolMaterial.NETHERITE;
        helper.assertTrue(mat.durability() > neth.durability() && mat.speed() > neth.speed() && mat.attackDamageBonus() > neth.attackDamageBonus(),
                "meteoric tools not above netherite");
        var armor = com.bladesmelody.naturaldisasters.meteorite.MeteoricMaterials.ARMOR;
        var nArmor = net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE;
        int ours = armor.defense().values().stream().mapToInt(Integer::intValue).sum(), theirs = nArmor.defense().values().stream().mapToInt(Integer::intValue).sum();
        helper.assertTrue(ours > theirs && armor.toughness() > nArmor.toughness() && armor.durability() > nArmor.durability(), "meteoric armor not above netherite");
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String r : List.of("meteoric_sword", "meteoric_pickaxe", "meteoric_axe", "meteoric_shovel", "meteoric_hoe", "meteoric_spear",
                "meteoric_helmet", "meteoric_chestplate", "meteoric_leggings", "meteoric_boots", "disaster_ward", "meteoric_iron_block",
                "meteoric_iron_ingot_from_block", "raw_meteorite_block", "meteorite_chunk_from_raw_block", "polished_meteorite",
                "meteorite_bricks", "meteorite_brick_stairs", "meteorite_brick_slab", "meteorite_brick_slab_stonecutting")) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("naturaldisasters", r));
            helper.assertTrue(recipes.byKey(key).isPresent(), "recipe " + r + " didn't load");
        }
        helper.succeed();
    }

    /** The same hail hit: a full meteoric set takes 60 % less when it comes from a disaster. */
    private static void meteoricArmorResistsDisasters(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        var zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, CENTER.above());
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(com.bladesmelody.naturaldisasters.meteorite.MeteoriteItems.HELMET.get()));
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(com.bladesmelody.naturaldisasters.meteorite.MeteoriteItems.CHESTPLATE.get()));
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(com.bladesmelody.naturaldisasters.meteorite.MeteoriteItems.LEGGINGS.get()));
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(com.bladesmelody.naturaldisasters.meteorite.MeteoriteItems.BOOTS.get()));
        helper.assertTrue(com.bladesmelody.naturaldisasters.meteorite.DisasterResistance.pieces(zombie) == 4, "armor not recognised");
        var hail = level.damageSources().source(com.bladesmelody.naturaldisasters.disaster.Hailstorm.HAIL);
        float before = zombie.getHealth();
        zombie.hurtServer(level, hail, 10.0F);
        float plain = before - zombie.getHealth();
        zombie.setHealth(zombie.getMaxHealth());
        zombie.invulnerableTime = 0;
        before = zombie.getHealth();
        com.bladesmelody.naturaldisasters.meteorite.DisasterResistance.during(() -> zombie.hurtServer(level, hail, 10.0F));
        float resisted = before - zombie.getHealth();
        helper.assertTrue(plain > 0 && Math.abs(resisted - plain * 0.4F) < 0.05F, "damage " + plain + " plain vs " + resisted + " from a disaster (expected 40 %)");
        helper.succeed();
    }

    /** A ward (radius 8 by default) protects blocks within its radius only, and stops when broken. */
    private static void wardProtectsItsRadius(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos ward = CENTER.above();
        helper.setBlock(ward, com.bladesmelody.naturaldisasters.meteorite.MeteoriteItems.WARD.get());
        BlockWriter w = new BlockWriter(level, 100, Long.MAX_VALUE);
        helper.assertTrue(!w.set(helper.absolutePos(ward.east(6)), Blocks.GLASS.defaultBlockState()), "block 6 from a ward changed");
        helper.assertTrue(w.set(helper.absolutePos(ward.east(10)), Blocks.GLASS.defaultBlockState()), "block 10 from a ward not changeable");
        helper.setBlock(ward, Blocks.AIR);
        helper.assertTrue(w.set(helper.absolutePos(ward.east(6)), Blocks.GLASS.defaultBlockState()), "still protected after the ward was broken");
        helper.succeed();
    }

    /** Right-clicking a ward cycles its radius 8 → 12 → 16 → 4, and protection follows. */
    private static void wardRadiusCycles(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos ward = CENTER.above();
        helper.setBlock(ward, com.bladesmelody.naturaldisasters.meteorite.MeteoriteItems.WARD.get());
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos at10 = helper.absolutePos(ward.east(10));
        try {
            helper.assertTrue(!com.bladesmelody.naturaldisasters.protect.Protection.isProtected(level, at10), "radius 8 reaches 10");
            helper.useBlock(ward, player);
            int radius = com.bladesmelody.naturaldisasters.meteorite.WardBlock.radius(level.getBlockState(helper.absolutePos(ward)));
            helper.assertTrue(radius == 12, "radius after one click: " + radius);
            helper.assertTrue(com.bladesmelody.naturaldisasters.protect.Protection.isProtected(level, at10), "radius 12 doesn't reach 10");
            helper.useBlock(ward, player);
            helper.useBlock(ward, player);
            radius = com.bladesmelody.naturaldisasters.meteorite.WardBlock.radius(level.getBlockState(helper.absolutePos(ward)));
            helper.assertTrue(radius == 4, "radius after three clicks: " + radius);
            helper.assertTrue(!com.bladesmelody.naturaldisasters.protect.Protection.isProtected(level, helper.absolutePos(ward.east(6))), "radius 4 reaches 6");
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The shattering quake above, but the arena is a safe zone: not one block changes. */
    private static void safeZoneStopsQuake(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, Blocks.GLASS.defaultBlockState());
        BlockPos c = helper.absolutePos(CENTER);
        String zone = "test_quake_" + c.getX() + "_" + c.getZ();
        com.bladesmelody.naturaldisasters.protect.SafeZones.get(helper.getLevel().getServer()).put(
                new com.bladesmelody.naturaldisasters.protect.SafeZones.Zone(zone, helper.getLevel().dimension().identifier(), c.getX(), c.getZ(), SIZE / 2));
        Earthquake quake = startQuake(helper, params(2, 0.05, 1), 99L);
        helper.succeedWhen(() -> {
            helper.assertTrue(quake.isFinished(), "quake still running");
            com.bladesmelody.naturaldisasters.protect.SafeZones.get(helper.getLevel().getServer()).remove(zone);
            int glass = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.GLASS));
            helper.assertTrue(glass == SIZE * SIZE, (SIZE * SIZE - glass) + " glass broken in a safe zone");
            int stone = count(helper, 1, FLOOR_TOP, s -> s.is(Blocks.STONE));
            helper.assertTrue(stone == SIZE * SIZE * FLOOR_TOP, (SIZE * SIZE * FLOOR_TOP - stone) + " stone changed in a safe zone");
        });
    }

    /**
     * In a safe zone: blocks can't be set, armor stands are spared but pigs
     * aren't, and thrown debris crumbles instead of landing.
     */
    private static void safeZoneSparesAndCrumbles(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos c = helper.absolutePos(CENTER.above());
        String zone = "test_spare_" + c.getX() + "_" + c.getZ();
        var zones = com.bladesmelody.naturaldisasters.protect.SafeZones.get(level.getServer());
        int scoreBefore = com.bladesmelody.naturaldisasters.disaster.Builds.score(level, c, 16);
        zones.put(new com.bladesmelody.naturaldisasters.protect.SafeZones.Zone(zone, level.dimension().identifier(), c.getX(), c.getZ(), 6));
        try {
            BlockWriter w = new BlockWriter(level, 100, Long.MAX_VALUE);
            helper.assertTrue(!w.set(c, Blocks.GLASS.defaultBlockState()) && level.getBlockState(c).isAir(), "set a block in a safe zone");
            helper.assertTrue(w.set(c.east(10), Blocks.GLASS.defaultBlockState()), "couldn't set a block outside the zone");
            helper.assertTrue(com.bladesmelody.naturaldisasters.disaster.Builds.score(level, c, 16) > scoreBefore + 20, "zone didn't count as built-up");
            var stand = helper.spawn(EntityType.ARMOR_STAND, CENTER.above());
            var pig = helper.spawn(EntityType.PIG, CENTER.above().north());
            var outside = helper.spawn(EntityType.ARMOR_STAND, CENTER.above().east(10));
            helper.assertTrue(com.bladesmelody.naturaldisasters.protect.Protection.spares(level, stand), "armor stand in the zone not spared");
            helper.assertTrue(!com.bladesmelody.naturaldisasters.protect.Protection.spares(level, pig), "pig in the zone spared");
            helper.assertTrue(!com.bladesmelody.naturaldisasters.protect.Protection.spares(level, outside), "armor stand outside spared");
        } catch (RuntimeException e) {
            zones.remove(zone);
            throw e;
        }
        var debris = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, c.above(4), Blocks.SAND.defaultBlockState());
        debris.addTag(com.bladesmelody.naturaldisasters.protect.Protection.DEBRIS_TAG);
        helper.runAfterDelay(20, () -> {
            zones.remove(zone);
            helper.assertTrue(debris.isRemoved(), "debris still flying");
            helper.assertTrue(!level.getBlockState(c).is(Blocks.SAND), "debris landed in the safe zone");
            helper.succeed();
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
        Earthquake.Params longer = new Earthquake.Params(p.radius(), 450, 0, 0, 0, 0, p.shakeDensity(), 0, 0, 0, 0);
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
        // Short throws and no flying debris, so nothing lands in the neighbouring test arenas.
        Tornado.Params params = new Tornado.Params(2, 3, 135, 120, 0.2, 3, 20, 40, 4, 0);
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
     * Something wakes the flood's quiet water while it's high (here: fluid
     * ticks on a line of it), so it flows and makes new sources as the flood
     * drains. The settle sweeps must still leave no water behind.
     */
    private static void wokenFloodLeavesNoWater(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        Flood flood = Flood.create(helper.absolutePos(CENTER), 3, new Flood.Params(12, 3, 40, 40, 60), 4L, 0);
        DisasterManager.add(level, flood);
        helper.runAfterDelay(60, () -> {
            for (int dx = -10; dx <= 10; dx += 2) {
                for (int y = 1; y <= 3; y++) {
                    BlockPos p = helper.absolutePos(CENTER.offset(dx, y, 3));
                    if (level.getFluidState(p).isSource()) level.scheduleTick(p, net.minecraft.world.level.material.Fluids.WATER, 1);
                }
            }
        });
        boolean[] checkedAfter = {false};
        helper.succeedWhen(() -> {
            if (checkedAfter[0]) {
                // Once the flood is over, infinite water works there again as normal.
                helper.assertTrue(helper.getBlockState(CENTER.offset(0, 1, 0)).getFluidState().isSource(), "no new source formed after the flood");
                return;
            }
            helper.assertTrue(flood.isFinished(), "flood still running");
            int left = count(helper, 1, FLOOR_TOP + 6, s -> !s.getFluidState().isEmpty());
            int sources = count(helper, 1, FLOOR_TOP + 6, s -> s.getFluidState().isSource());
            int outside = countOutside(helper, 1, FLOOR_TOP + 6, s -> !s.getFluidState().isEmpty());
            StringBuilder where = new StringBuilder();
            for (int x = 0; x < SIZE; x++)
                for (int z = 0; z < SIZE; z++)
                    for (int y = 1; y <= FLOOR_TOP + 6; y++) {
                        var f = helper.getBlockState(new BlockPos(x, y, z)).getFluidState();
                        if (f.isSource()) where.append(x - CENTER.getX()).append(',').append(y).append(',').append(z - CENTER.getZ()).append(' ');
                    }
            helper.assertTrue(left == 0, left + " water blocks left after a woken flood drained (" + sources + " sources, "
                    + outside + " outside the flood radius); sources at " + where);
            checkedAfter[0] = true;
            helper.setBlock(CENTER.offset(-1, 1, 0), Blocks.WATER);
            helper.setBlock(CENTER.offset(1, 1, 0), Blocks.WATER);
            helper.fail("placed two sources, waiting for the infinite-water check");
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
        // An armor stand: physics like a mob but no wandering, so only the wind moves it.
        net.minecraft.world.entity.decoration.ArmorStand pig = helper.spawn(EntityType.ARMOR_STAND, CENTER.offset(6, 1, 1));
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
            helper.assertTrue(pigMoved[0] > 2, "armor stand barely moved: " + pigMoved[0]);
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

    /**
     * The volcano above, but its lava (rivers and crater lake) gets woken
     * mid-eruption, as a fire burning out next to it does, so it flows off the
     * rivers. Once the rivers cool, no lava may be left outside the crater
     * (the cone's rim holds the woken lake in).
     */
    private static void wokenLavaCools(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        Volcano volcano = Volcano.create(helper.absolutePos(CENTER), 3, new Volcano.Params(8, 12, 2, 60, 120, 2, 14, 10), 17L, 0);
        DisasterManager.add(level, volcano);
        int[] woken = {0};
        helper.onEachTick(() -> {
            if (woken[0] > 0 || count(helper, FLOOR_TOP + 1, FLOOR_TOP + 8, s -> s.is(Blocks.LAVA)) < 40) return;
            for (int x = 0; x < SIZE; x++)
                for (int z = 0; z < SIZE; z++) {
                    for (int y = FLOOR_TOP + 1; y < 30; y++) {
                        BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
                        if (level.getFluidState(p).isSource()) {
                            level.scheduleTick(p, level.getFluidState(p).getType(), 1);
                            woken[0]++;
                        }
                    }
                }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(volcano.isFinished(), "volcano still running");
            helper.assertTrue(woken[0] > 0, "never woke any lava");
            int lavaOutside = 0;
            for (int x = 0; x < SIZE; x++)
                for (int z = 0; z < SIZE; z++) {
                    int dx = x - CENTER.getX(), dz = z - CENTER.getZ();
                    if (dx * dx + dz * dz <= 9) continue;
                    for (int y = 1; y < 24; y++) if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.LAVA)) lavaOutside++;
                }
            StringBuilder where = new StringBuilder();
            int sources = 0;
            for (int x = 0; x < SIZE; x++)
                for (int z = 0; z < SIZE; z++)
                    for (int y = 1; y < 24; y++) {
                        var f = helper.getBlockState(new BlockPos(x, y, z)).getFluidState();
                        int dx = x - CENTER.getX(), dz = z - CENTER.getZ();
                        if (!f.isEmpty() && dx * dx + dz * dz > 9 && where.length() < 300) where.append(dx).append(',').append(y).append(',').append(dz).append(' ');
                        if (f.isSource() && dx * dx + dz * dz > 9) {
                            sources++;
                            if (where.length() < 200) where.append(dx).append(',').append(y).append(',').append(dz).append(' ');
                        }
                    }
            helper.assertTrue(lavaOutside == 0, lavaOutside + " lava blocks outside the crater after " + woken[0]
                    + " river cells were woken (" + sources + " sources) at " + where);
        });
    }

    /** A wildfire burns through a little forest and leaves scorched ground. */
    private static void wildfireBurnsForest(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP, FLOOR_TOP, Blocks.GRASS_BLOCK.defaultBlockState());
        ServerLevel level = helper.getLevel();
        int logs = 0, leaves = 0;
        // Canopies touch (trees 3 apart), so the fire can cross the forest without the slow grass.
        for (int dx = -12; dx <= 12; dx += 3) {
            for (int dz = -12; dz <= 12; dz += 3) {
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

    /** A blizzard snows the arena in (to its depth limit), freezes a pond and chills a pig. */
    private static void blizzardBuriesAndFreezes(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP, FLOOR_TOP, Blocks.GRASS_BLOCK.defaultBlockState());
        ServerLevel level = helper.getLevel();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                level.setBlock(helper.absolutePos(CENTER.offset(dx - 6, 0, dz)), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        Pig pig = helper.spawn(EntityType.PIG, CENTER.offset(5, 1, 5));
        int[] frozen = {0};
        Blizzard blizzard = Blizzard.create(helper.absolutePos(CENTER), 5, new Blizzard.Params(14, 120, 4, 4), 41L, 0);
        DisasterManager.add(level, blizzard);
        helper.onEachTick(() -> {
            if (pig.isAlive()) frozen[0] = Math.max(frozen[0], pig.getTicksFrozen());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(blizzard.isFinished(), "blizzard still running");
            int snowy = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK));
            helper.assertTrue(snowy > 300, "only " + snowy + " snowy columns");
            int tooDeep = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.SNOW_BLOCK));
            helper.assertTrue(tooDeep == 0, tooDeep + " columns deeper than the 4-layer limit");
            int ice = count(helper, FLOOR_TOP, FLOOR_TOP, s -> s.is(Blocks.ICE));
            helper.assertTrue(ice > 10, "pond barely froze: " + ice + "/25 ice");
            helper.assertTrue(frozen[0] > 60, "pig never got cold (frozen ticks " + frozen[0] + ")");
        });
    }

    /** Hail breaks exposed glass, flattens crops and hurts a pig in the open. */
    private static void hailstormPelts(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        int glass = 0, wheat = 0;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                if (dx * dx + dz * dz > 100) continue;
                if (dx < 0) {
                    helper.setBlock(CENTER.offset(dx, 1, dz), Blocks.GLASS);
                    glass++;
                } else if (dx > 1) {
                    helper.setBlock(CENTER.offset(dx, 0, dz), Blocks.FARMLAND);
                    helper.setBlock(CENTER.offset(dx, 1, dz), Blocks.WHEAT);
                    wheat++;
                }
            }
        }
        int glassBefore = glass, wheatBefore = wheat;
        Pig pig = helper.spawn(EntityType.PIG, CENTER.offset(1, 1, 0));
        float pigHealth = pig.getHealth();
        Hailstorm hail = Hailstorm.create(helper.absolutePos(CENTER), 6, new Hailstorm.Params(14, 120, 3, 0.02), 43L, 0);
        DisasterManager.add(level, hail);
        helper.succeedWhen(() -> {
            helper.assertTrue(hail.isFinished(), "hailstorm still running");
            int glassLeft = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.GLASS));
            int wheatLeft = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.WHEAT));
            helper.assertTrue(glassLeft < glassBefore * 0.7, glassLeft + "/" + glassBefore + " glass intact");
            helper.assertTrue(wheatLeft < wheatBefore * 0.7, wheatLeft + "/" + wheatBefore + " wheat intact");
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < pigHealth, "pig in the open was not hurt");
        });
    }

    /** Acid rain kills grass and leaves, greens copper and hurts a pig in the open. */
    private static void acidRainCorrodes(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP, FLOOR_TOP, Blocks.GRASS_BLOCK.defaultBlockState());
        ServerLevel level = helper.getLevel();
        int leaves = 0;
        for (int dx = -9; dx <= 9; dx++)
            for (int dz = -9; dz <= 9; dz++) {
                if (dx * dx + dz * dz > 81 || (dx + dz) % 3 != 0) continue;
                BlockPos at = CENTER.offset(dx, 1, dz);
                if (dx > 3) helper.setBlock(at, Blocks.COPPER_BLOCK);
                else {
                    helper.setBlock(at, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                    leaves++;
                }
            }
        int leavesBefore = leaves;
        Pig pig = helper.spawn(EntityType.PIG, CENTER.offset(-1, 1, 1));
        float pigHealth = pig.getHealth();
        AcidRain acid = AcidRain.create(helper.absolutePos(CENTER), 6, new AcidRain.Params(14, 120, 6, 0.05), 47L, 0);
        DisasterManager.add(level, acid);
        helper.succeedWhen(() -> {
            helper.assertTrue(acid.isFinished(), "acid rain still falling");
            int leavesLeft = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.OAK_LEAVES));
            int dirt = count(helper, FLOOR_TOP, FLOOR_TOP, s -> s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT));
            int green = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.EXPOSED_COPPER) || s.is(Blocks.WEATHERED_COPPER) || s.is(Blocks.OXIDIZED_COPPER));
            helper.assertTrue(leavesLeft < leavesBefore * 0.7, leavesLeft + "/" + leavesBefore + " leaves survived");
            helper.assertTrue(dirt > 50, "grass barely died: " + dirt);
            helper.assertTrue(green > 0, "copper didn't weather");
            helper.assertTrue(!pig.isAlive() || pig.getHealth() < pigHealth, "pig in the acid rain was not hurt");
        });
    }

    /** A drought dries a shallow pond from the shore, kills wheat and browns the grass. */
    private static void droughtDriesOut(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP, FLOOR_TOP, Blocks.GRASS_BLOCK.defaultBlockState());
        ServerLevel level = helper.getLevel();
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                level.setBlock(helper.absolutePos(CENTER.offset(dx - 5, 0, dz)), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        int wheat = 0;
        for (int dx = 3; dx <= 9; dx++)
            for (int dz = -3; dz <= 3; dz++, wheat++) {
                helper.setBlock(CENTER.offset(dx, 0, dz), Blocks.FARMLAND);
                helper.setBlock(CENTER.offset(dx, 1, dz), Blocks.WHEAT);
            }
        int wheatBefore = wheat;
        // Potency 5: below the grass-fire threshold. Vanilla fire burning next to the pond
        // would wake it and let it flow back into the dried spots.
        Drought drought = Drought.create(helper.absolutePos(CENTER), 5, new Drought.Params(14, 450, 5, 0.04), 53L, 0);
        DisasterManager.add(level, drought);
        helper.succeedWhen(() -> {
            helper.assertTrue(drought.isFinished(), "drought still on");
            int water = count(helper, FLOOR_TOP, FLOOR_TOP, s -> s.is(Blocks.WATER));
            int wheatLeft = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 1, s -> s.is(Blocks.WHEAT));
            int dirt = count(helper, FLOOR_TOP, FLOOR_TOP, s -> s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT));
            StringBuilder what = new StringBuilder();
            for (int dx = -3; dx <= 3 && what.length() < 400; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = CENTER.offset(dx - 5, 0, dz);
                    if (!helper.getBlockState(p).is(Blocks.WATER)) continue;
                    what.append(dx).append(',').append(dz).append(':')
                            .append(helper.getBlockState(p.above()).getBlock().getDescriptionId().replace("block.minecraft.", ""))
                            .append(helper.getBlockState(p).getFluidState().isSource() ? "" : "(flowing)").append(' ');
                }
            helper.assertTrue(water < 49 * 0.5, water + "/49 pond water left; above them: " + what);
            helper.assertTrue(wheatLeft < wheatBefore * 0.5, wheatLeft + "/" + wheatBefore + " wheat survived");
            helper.assertTrue(dirt > 50, "grass barely browned: " + dirt);
        });
    }

    /**
     * Every disaster type, at every magnitude, describes itself and saves and
     * loads back the same while in its warning (no world changes, so big ones
     * can't spill into other tests).
     */
    private static void allTypesSaveRoundtrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (DisasterType<?> type : DisasterTypes.all()) {
            for (int m = 1; m <= 10; m++) {
                Disaster d = type.create(helper.absolutePos(CENTER), m, 1000L + m, 1000);
                d.tick(level, new BlockWriter(level, 0, Long.MAX_VALUE));
                helper.assertTrue(d.radius() > 0 && !d.describe().isEmpty(), type.name() + " M" + m + " has no size");
                CompoundTag first = d.save();
                Disaster loaded = DisasterTypes.load(first).orElseThrow();
                helper.assertTrue(loaded.type() == type && first.equals(loaded.save()),
                        type.name() + " M" + m + " save differs:\n" + first + "\n" + loaded.save());
            }
        }
        helper.assertTrue(DisasterTypes.all().size() == 13, "expected 12 disaster types + ash cloud, have " + DisasterTypes.all().size());
        helper.succeed();
    }

    /** Bare ground scores 0 for buildings; a little hut with a chest scores more. */
    /**
     * A seismograph senses an earthquake: redstone power, and twice the
     * warning. A sky watcher ignores it.
     */
    private static void instrumentsSenseAndWarn(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos seismo = helper.absolutePos(CENTER.above());
        helper.setBlock(CENTER.above(), com.bladesmelody.naturaldisasters.registry.ModRegistry.SEISMOGRAPH.get());
        Earthquake quake = Earthquake.create(helper.absolutePos(CENTER.east(10)), 6, params(2, 0.01, 1), 77L, 100);
        DisasterManager.add(level, quake);
        try {
            helper.assertTrue(quake.warningTicksLeft() == 200, "warning with a seismograph in range: " + quake.warningTicksLeft());
            com.bladesmelody.naturaldisasters.instrument.Instruments.tick(level);
            int power = level.getBlockState(seismo).getValue(com.bladesmelody.naturaldisasters.instrument.InstrumentBlock.POWER);
            helper.assertTrue(power >= 10, "seismograph power " + power);
            helper.assertTrue(level.getBestNeighborSignal(seismo.north()) == power, "redstone signal " + level.getBestNeighborSignal(seismo.north()));
            helper.assertTrue(com.bladesmelody.naturaldisasters.instrument.Instruments.closeness(
                    com.bladesmelody.naturaldisasters.instrument.Instrument.SKY_WATCHER, quake, seismo) == 0, "sky watcher ignores quakes");
            helper.assertTrue(com.bladesmelody.naturaldisasters.instrument.Instruments.direction(0, -10).equals("north")
                    && com.bladesmelody.naturaldisasters.instrument.Instruments.direction(10, 10).equals("southeast"), "directions");
        } finally {
            DisasterManager.stop(level.getServer(), quake.id());
        }
        helper.succeed();
    }

    private static void buildsAreDetected(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(CENTER);
        int empty = com.bladesmelody.naturaldisasters.disaster.Builds.score(level, at, 12);
        helper.assertTrue(empty == 0, "bare stone scored " + empty);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int y = 1; y <= 3; y++)
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3 || y == 3) helper.setBlock(CENTER.offset(dx, y, dz), Blocks.OAK_PLANKS);
        helper.setBlock(CENTER.offset(0, 1, 0), Blocks.CHEST);
        int hut = com.bladesmelody.naturaldisasters.disaster.Builds.score(level, at, 12);
        helper.assertTrue(hut > empty + 2, "hut only scored " + hut);
        helper.succeed();
    }

    /** Magnitude ↔ VEI, the owner's VEI table, and the two rarity rolls. */
    private static void volcanoVeiTable(GameTestHelper helper) {
        helper.assertTrue(Volcano.vei(1) == 0 && Volcano.vei(5) == 3 && Volcano.vei(7) == 5 && Volcano.vei(10) == 8, "VEI mapping");
        Volcano.Params v3 = Volcano.Params.forMagnitude(5);
        helper.assertTrue(v3.heatReach() == 64 && v3.ashRadius() == 1000 && v3.ashLayers() == 1 && v3.darkTicks() == 2 * 24000, "VEI 3 row: " + v3);
        Volcano.Params v5 = Volcano.Params.forMagnitude(7);
        helper.assertTrue(v5.ashRadius() == 0 && v5.darkTicks() == 28 * 24000, "VEI 5 is world-wide for 4 weeks: " + v5);
        helper.assertTrue(Volcano.Params.forMagnitude(4).heatReach() == 0, "VEI 2 has no heat wave");
        RandomSource random = RandomSource.create(7);
        int[] normal = new int[9], great = new int[9];
        for (int i = 0; i < 20000; i++) {
            normal[Volcano.vei(Volcano.rollRandomMagnitude(random))]++;
            great[Volcano.vei(Volcano.rollGreatMagnitude(random))]++;
        }
        helper.assertTrue(normal[5] + normal[6] + normal[7] + normal[8] == 0, "normal volcanoes rolled VEI 5+");
        helper.assertTrue(normal[4] > 0 && normal[4] * 5 < normal[3], "VEI 4 should be ~10x rarer than VEI 3: " + normal[3] + " vs " + normal[4]);
        helper.assertTrue(great[0] + great[1] + great[2] + great[3] + great[4] == 0 && great[5] > great[6] * 5 && great[6] > great[7],
                "great eruptions should be VEI 5+ with each step rarer: " + java.util.Arrays.toString(great));
        helper.succeed();
    }

    /**
     * A small VEI 3 volcano: the heat wave kills a pig in the open but only
     * hurts an iron golem in a stone hut, chars the trees and scorches the
     * grass; ash falls and the area is dark until the ash cloud passes.
     */
    private static void volcanoHeatWaveAndAsh(GameTestHelper helper) {
        fillFloor(helper);
        fill(helper, FLOOR_TOP, FLOOR_TOP, Blocks.GRASS_BLOCK.defaultBlockState());
        ServerLevel level = helper.getLevel();
        for (int dz = -6; dz <= 6; dz += 3) {
            BlockPos base = CENTER.offset(15, 1, dz);
            for (int y = 0; y < 3; y++) helper.setBlock(base.above(y), Blocks.OAK_LOG);
            helper.setBlock(base.above(3), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        }
        BlockPos hut = CENTER.offset(-14, 1, 0);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int y = 0; y <= 4; y++)
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == 4) helper.setBlock(hut.offset(dx, y, dz), Blocks.STONE);
        net.minecraft.world.entity.animal.golem.IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, hut);
        Pig pig = helper.spawn(EntityType.PIG, CENTER.offset(13, 1, 8));
        pig.setNoAi(true); // stays in the open (it could wander under a tree)
        Volcano volcano = Volcano.create(helper.absolutePos(CENTER), 5,
                // No lava rivers or bombs: their fires and splashes would muddle what the heat wave did.
                new Volcano.Params(8, 12, 2, 40, 80, 0, 6, 100_000, 3, 17, 18, 2, 120, 0), 61L, 0);
        DisasterManager.add(level, volcano);
        boolean[] dark = {false};
        helper.onEachTick(() -> {
            if (com.bladesmelody.naturaldisasters.sky.SkyZones.isDark(level, helper.absolutePos(CENTER.offset(16, 1, 0)))) dark[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(volcano.isFinished(), "volcano still running");
            helper.assertTrue(DisasterManager.all(level.getServer()).stream().noneMatch(r -> r.disaster() instanceof AshCloud
                    && r.disaster().center().equals(helper.absolutePos(CENTER))), "ash cloud still there");
            helper.assertTrue(dark[0], "the area never went dark");
            helper.assertTrue(!pig.isAlive(), "pig in the open survived the heat wave");
            helper.assertTrue(golem.isAlive() && golem.getHealth() < golem.getMaxHealth(), "sheltered golem should be hurt but alive");
            int charred = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 4, st -> st.is(Blocks.STRIPPED_DARK_OAK_LOG));
            int leaves = count(helper, FLOOR_TOP + 1, FLOOR_TOP + 6, st -> st.is(Blocks.OAK_LEAVES));
            helper.assertTrue(charred >= 10 && leaves == 0, "trees not burnt: " + charred + " charred, " + leaves + " leaves");
            int scorched = count(helper, FLOOR_TOP, FLOOR_TOP, st -> st.is(Blocks.COARSE_DIRT));
            helper.assertTrue(scorched > 100, "grass barely scorched: " + scorched);
            int ash = count(helper, 1, 23, st -> st.is(ModRegistry.VOLCANIC_ASH.get()));
            helper.assertTrue(ash > 100, "only " + ash + " ash layers");
            helper.assertTrue(!com.bladesmelody.naturaldisasters.sky.SkyZones.isDark(level, helper.absolutePos(CENTER.offset(16, 1, 0))),
                    "still dark after the cloud ended");
        });
    }

    /** A world-wide ash cloud (no ash, short) pauses the day clock and lets it run again after. */
    private static void ashCloudStopsTheClock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var clock = level.getServer().overworld().dimensionType().defaultClock().orElseThrow();
        var clocks = level.getServer().clockManager();
        AshCloud cloud = AshCloud.create(helper.absolutePos(CENTER), 7, new AshCloud.Params(0, 16, 0, 0, 60), 3L);
        DisasterManager.add(level, cloud);
        long[] at = new long[2];
        helper.runAfterDelay(10, () -> at[0] = clocks.getTotalTicks(clock));
        helper.runAfterDelay(30, () -> {
            at[1] = clocks.getTotalTicks(clock);
            helper.assertTrue(at[0] == at[1], "clock kept running under world-wide ash: " + at[0] + " -> " + at[1]);
            helper.assertTrue(com.bladesmelody.naturaldisasters.sky.SkyZones.worldDark(), "not world-dark");
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(cloud.isFinished(), "cloud should be over");
            helper.assertTrue(!com.bladesmelody.naturaldisasters.sky.SkyZones.worldDark(), "still world-dark");
            long before = clocks.getTotalTicks(clock);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(clocks.getTotalTicks(clock) > before, "clock didn't start again");
                helper.succeed();
            });
        });
    }

    /** Under an ash zone the daytime sky counts as night for monster spawning (mixin). */
    private static void ashZoneLetsMonstersSpawn(GameTestHelper helper) {
        fillFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CENTER.above());
        if (!level.isBrightOutside()) {
            helper.succeed(); // only meaningful by day
            return;
        }
        RandomSource random = RandomSource.create(5);
        int before = 0;
        for (int i = 0; i < 200; i++) if (net.minecraft.world.entity.monster.Monster.isDarkEnoughToSpawn(level, pos, random)) before++;
        com.bladesmelody.naturaldisasters.sky.SkyZones.put(-42, new com.bladesmelody.naturaldisasters.sky.SkyZones.Zone(com.bladesmelody.naturaldisasters.sky.SkyZones.Kind.ASH, pos.getX(), pos.getZ(), 8, 3));
        int after = 0;
        try {
            for (int i = 0; i < 200; i++) if (net.minecraft.world.entity.monster.Monster.isDarkEnoughToSpawn(level, pos, random)) after++;
        } finally {
            com.bladesmelody.naturaldisasters.sky.SkyZones.remove(-42);
        }
        helper.assertTrue(before == 0 && after > 10, "daylight spawn checks: " + before + " without ash, " + after + " under ash");
        // Monsters stop burning in the sun under ash. Sampled at noon on day 2: on day 1 the "day" timeline
        // has no earlier keyframe, so monsters never burn before the first dawn.
        var clock = level.dimensionType().defaultClock().orElseThrow();
        var clocks = level.getServer().clockManager();
        long savedTime = clocks.getTotalTicks(clock);
        net.minecraft.world.phys.Vec3 at = pos.getCenter();
        boolean burnBefore, burnAfter;
        try {
            clocks.setTotalTicks(clock, 24000 + 6000);
            burnBefore = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.MONSTERS_BURN, at);
            com.bladesmelody.naturaldisasters.sky.SkyZones.put(-42, new com.bladesmelody.naturaldisasters.sky.SkyZones.Zone(com.bladesmelody.naturaldisasters.sky.SkyZones.Kind.ASH, pos.getX(), pos.getZ(), 8, 3));
            burnAfter = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.MONSTERS_BURN, at);
        } finally {
            com.bladesmelody.naturaldisasters.sky.SkyZones.remove(-42);
            clocks.setTotalTicks(clock, savedTime);
        }
        helper.assertTrue(burnBefore && !burnAfter, "monsters burn at noon: " + burnBefore + " without ash, " + burnAfter + " under ash");
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
