package com.bladesmelody.naturaldisasters.registry;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.entity.MeteorEntity;
import com.bladesmelody.naturaldisasters.entity.TornadoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks, items and entity types. */
public final class ModRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NaturalDisasters.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NaturalDisasters.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, NaturalDisasters.MODID);

    public static final DeferredBlock<Block> METEORITE_ORE = BLOCKS.registerSimpleBlock("meteorite_ore",
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(5.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.ANCIENT_DEBRIS));
    public static final DeferredItem<BlockItem> METEORITE_ORE_ITEM = ITEMS.registerSimpleBlockItem(METEORITE_ORE);
    public static final DeferredItem<Item> METEORITE_CHUNK = ITEMS.registerSimpleItem("meteorite_chunk");
    public static final DeferredItem<Item> METEORIC_IRON_INGOT = ITEMS.registerSimpleItem("meteoric_iron_ingot");

    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR = ENTITY_TYPES.register("meteor",
            id -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().noSummon().noSave()
                    .clientTrackingRange(16).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<TornadoEntity>> TORNADO = ENTITY_TYPES.register("tornado",
            id -> EntityType.Builder.<TornadoEntity>of(TornadoEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().noSummon().noSave()
                    .clientTrackingRange(16).updateInterval(2)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private ModRegistry() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(ModRegistry::fillCreativeTabs);
    }

    private static void fillCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) event.accept(METEORITE_ORE_ITEM);
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(METEORITE_CHUNK);
            event.accept(METEORIC_IRON_INGOT);
        }
    }
}
