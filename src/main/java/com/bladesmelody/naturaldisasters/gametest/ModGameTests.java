package com.bladesmelody.naturaldisasters.gametest;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the mod's game tests. Run with {@code ./gradlew runGameTestServer}. */
public final class ModGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, NaturalDisasters.MODID);

    private static final Identifier EMPTY = Identifier.withDefaultNamespace("empty");

    static {
        FUNCTIONS.register("smoke", () -> ModGameTests::smoke);
    }

    private ModGameTests() {}

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(id("default"));
        test(event, "smoke", new TestData<>(env, EMPTY, 20, 0, true));
    }

    private static void test(RegisterGameTestsEvent event, String name, TestData<Holder<TestEnvironmentDefinition<?>>> data) {
        event.registerTest(id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, id(name)), data));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, path);
    }

    /** The mod loads and a test can run. */
    private static void smoke(GameTestHelper helper) {
        helper.succeed();
    }
}
