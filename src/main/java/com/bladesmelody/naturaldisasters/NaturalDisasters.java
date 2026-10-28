package com.bladesmelody.naturaldisasters;

import com.bladesmelody.naturaldisasters.sky.SkyEvents;
import com.bladesmelody.naturaldisasters.command.DisasterCommand;
import com.bladesmelody.naturaldisasters.engine.DisasterManager;
import com.bladesmelody.naturaldisasters.gametest.ModGameTests;
import com.bladesmelody.naturaldisasters.instrument.Instruments;
import com.bladesmelody.naturaldisasters.protect.Protection;
import com.bladesmelody.naturaldisasters.registry.ModRegistry;
import com.bladesmelody.naturaldisasters.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(NaturalDisasters.MODID)
public class NaturalDisasters {
    public static final String MODID = "naturaldisasters";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NaturalDisasters(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ModRegistry.register(modBus);
        ModSounds.register(modBus);
        SkyEvents.register(modBus);
        ModGameTests.register(modBus);
        NeoForge.EVENT_BUS.addListener(DisasterManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(Instruments::onServerTick);
        NeoForge.EVENT_BUS.addListener(Protection::onEntityTick);
        NeoForge.EVENT_BUS.addListener(DisasterCommand::register);
    }
}
