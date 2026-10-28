package com.bladesmelody.naturaldisasters;

import com.bladesmelody.naturaldisasters.gametest.ModGameTests;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NaturalDisasters.MODID)
public class NaturalDisasters {
    public static final String MODID = "naturaldisasters";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NaturalDisasters(IEventBus modBus) {
        ModGameTests.register(modBus);
        LOGGER.info("Natural Disasters loaded");
    }
}
