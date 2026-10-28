package com.bladesmelody.naturaldisasters.registry;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import com.bladesmelody.naturaldisasters.entity.HurricaneEntity;
import com.bladesmelody.naturaldisasters.entity.MeteorEntity;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.util.ColorRGBA;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import com.bladesmelody.naturaldisasters.ash.AshLayerBlock;
import com.bladesmelody.naturaldisasters.instrument.Instrument;
import com.bladesmelody.naturaldisasters.instrument.InstrumentBlock;
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
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, NaturalDisasters.MODID);
    /** Big soft cloud puff: tornado funnels and hurricane cloud bands. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FUNNEL_CLOUD =
            PARTICLE_TYPES.register("funnel_cloud", () -> new SimpleParticleType(true));

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, NaturalDisasters.MODID);

    public static final DeferredBlock<Block> METEORITE_ORE = BLOCKS.registerSimpleBlock("meteorite_ore",
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(5.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.ANCIENT_DEBRIS));
    public static final DeferredItem<BlockItem> METEORITE_ORE_ITEM = ITEMS.registerSimpleBlockItem(METEORITE_ORE);
    public static final DeferredBlock<AshLayerBlock> VOLCANIC_ASH = BLOCKS.registerBlock("volcanic_ash", AshLayerBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).replaceable().forceSolidOff().strength(0.1F).requiresCorrectToolForDrops()
                    .sound(SoundType.SAND).isViewBlocking((state, level, pos) -> state.getValue(SnowLayerBlock.LAYERS) >= 8)
                    .pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<BlockItem> VOLCANIC_ASH_ITEM = ITEMS.registerSimpleBlockItem(VOLCANIC_ASH);
    public static final DeferredBlock<ColoredFallingBlock> VOLCANIC_ASH_BLOCK = BLOCKS.registerBlock("volcanic_ash_block",
            p -> new ColoredFallingBlock(new ColorRGBA(0xFF6B6662), p),
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(0.5F).sound(SoundType.SAND));
    public static final DeferredItem<BlockItem> VOLCANIC_ASH_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(VOLCANIC_ASH_BLOCK);
    public static final DeferredBlock<InstrumentBlock> SEISMOGRAPH = instrument(Instrument.SEISMOGRAPH, MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<InstrumentBlock> BAROMETER = instrument(Instrument.BAROMETER, MapColor.COLOR_ORANGE, SoundType.COPPER);
    public static final DeferredBlock<InstrumentBlock> SKY_WATCHER = instrument(Instrument.SKY_WATCHER, MapColor.METAL, SoundType.METAL);
    public static final DeferredItem<BlockItem> SEISMOGRAPH_ITEM = ITEMS.registerSimpleBlockItem(SEISMOGRAPH);
    public static final DeferredItem<BlockItem> BAROMETER_ITEM = ITEMS.registerSimpleBlockItem(BAROMETER);
    public static final DeferredItem<BlockItem> SKY_WATCHER_ITEM = ITEMS.registerSimpleBlockItem(SKY_WATCHER);
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

    public static final DeferredHolder<EntityType<?>, EntityType<HurricaneEntity>> HURRICANE = ENTITY_TYPES.register("hurricane",
            id -> EntityType.Builder.<HurricaneEntity>of(HurricaneEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().noSummon().noSave()
                    .clientTrackingRange(32).updateInterval(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    private ModRegistry() {}

    private static DeferredBlock<InstrumentBlock> instrument(Instrument instrument, MapColor color, SoundType sound) {
        return BLOCKS.registerBlock(instrument.id(), p -> new InstrumentBlock(instrument, p),
                p -> p.mapColor(color).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(sound).noOcclusion());
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        PARTICLE_TYPES.register(modBus);
        modBus.addListener(ModRegistry::fillCreativeTabs);
    }

    private static void fillCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(METEORITE_ORE_ITEM);
            event.accept(VOLCANIC_ASH_ITEM);
            event.accept(VOLCANIC_ASH_BLOCK_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS || event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SEISMOGRAPH_ITEM);
            event.accept(BAROMETER_ITEM);
            event.accept(SKY_WATCHER_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(METEORITE_CHUNK);
            event.accept(METEORIC_IRON_INGOT);
        }
    }
}
