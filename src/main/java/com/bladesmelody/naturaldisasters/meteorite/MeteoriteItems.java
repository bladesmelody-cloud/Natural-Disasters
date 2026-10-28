package com.bladesmelody.naturaldisasters.meteorite;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** What meteoric iron makes: gear, the disaster ward and decorative blocks. */
public final class MeteoriteItems {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NaturalDisasters.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NaturalDisasters.MODID);

    public static final TagKey<Item> ARMOR_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "meteoric_armor"));

    // ---- Gear (like netherite: survives fire and lava) ----
    public static final DeferredItem<Item> SWORD = ITEMS.registerItem("meteoric_sword", Item::new,
            p -> p.sword(MeteoricMaterials.TOOLS, 3.0F, -2.4F).fireResistant());
    public static final DeferredItem<Item> PICKAXE = ITEMS.registerItem("meteoric_pickaxe", Item::new,
            p -> p.pickaxe(MeteoricMaterials.TOOLS, 1.0F, -2.8F).fireResistant());
    public static final DeferredItem<AxeItem> AXE = ITEMS.registerItem("meteoric_axe", p -> new AxeItem(MeteoricMaterials.TOOLS, 5.0F, -3.0F, p),
            p -> p.fireResistant());
    public static final DeferredItem<ShovelItem> SHOVEL = ITEMS.registerItem("meteoric_shovel", p -> new ShovelItem(MeteoricMaterials.TOOLS, 1.5F, -3.0F, p),
            p -> p.fireResistant());
    public static final DeferredItem<HoeItem> HOE = ITEMS.registerItem("meteoric_hoe", p -> new HoeItem(MeteoricMaterials.TOOLS, -5.0F, 0.0F, p),
            p -> p.fireResistant());
    /** Netherite's spear numbers taken one step further along iron → diamond → netherite. */
    public static final DeferredItem<Item> SPEAR = ITEMS.registerItem("meteoric_spear", Item::new,
            p -> p.spear(MeteoricMaterials.TOOLS, 1.2F, 1.25F, 0.35F, 2.5F, 8.5F, 5.25F, 5.1F, 8.25F, 4.6F).fireResistant());
    public static final DeferredItem<Item> HELMET = armor("meteoric_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> CHESTPLATE = armor("meteoric_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> LEGGINGS = armor("meteoric_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> BOOTS = armor("meteoric_boots", ArmorType.BOOTS);

    // ---- Blocks ----
    public static final DeferredBlock<WardBlock> WARD = BLOCKS.registerBlock("disaster_ward", WardBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(25.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.LODESTONE)
                    .lightLevel(s -> 7));
    public static final DeferredBlock<Block> METEORIC_IRON_BLOCK = BLOCKS.registerSimpleBlock("meteoric_iron_block",
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(30.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK));
    public static final DeferredBlock<Block> RAW_METEORITE_BLOCK = BLOCKS.registerSimpleBlock("raw_meteorite_block",
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(10.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.ANCIENT_DEBRIS));
    public static final DeferredBlock<Block> POLISHED_METEORITE = BLOCKS.registerSimpleBlock("polished_meteorite",
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE));
    public static final DeferredBlock<Block> METEORITE_BRICKS = BLOCKS.registerSimpleBlock("meteorite_bricks",
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS));
    public static final DeferredBlock<StairBlock> METEORITE_BRICK_STAIRS = BLOCKS.registerBlock("meteorite_brick_stairs",
            p -> new StairBlock(METEORITE_BRICKS.get().defaultBlockState(), p),
            () -> BlockBehaviour.Properties.ofFullCopy(METEORITE_BRICKS.get()));
    public static final DeferredBlock<SlabBlock> METEORITE_BRICK_SLAB = BLOCKS.registerBlock("meteorite_brick_slab", SlabBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(METEORITE_BRICKS.get()));

    public static final DeferredItem<BlockItem> WARD_ITEM = ITEMS.registerSimpleBlockItem(WARD, p -> p.fireResistant());
    public static final DeferredItem<BlockItem> METEORIC_IRON_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(METEORIC_IRON_BLOCK, p -> p.fireResistant());
    public static final DeferredItem<BlockItem> RAW_METEORITE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(RAW_METEORITE_BLOCK);
    public static final DeferredItem<BlockItem> POLISHED_METEORITE_ITEM = ITEMS.registerSimpleBlockItem(POLISHED_METEORITE);
    public static final DeferredItem<BlockItem> METEORITE_BRICKS_ITEM = ITEMS.registerSimpleBlockItem(METEORITE_BRICKS);
    public static final DeferredItem<BlockItem> METEORITE_BRICK_STAIRS_ITEM = ITEMS.registerSimpleBlockItem(METEORITE_BRICK_STAIRS);
    public static final DeferredItem<BlockItem> METEORITE_BRICK_SLAB_ITEM = ITEMS.registerSimpleBlockItem(METEORITE_BRICK_SLAB);

    private MeteoriteItems() {}

    private static DeferredItem<Item> armor(String name, ArmorType type) {
        return ITEMS.registerItem(name, Item::new, p -> p.humanoidArmor(MeteoricMaterials.ARMOR, type).fireResistant());
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(MeteoriteItems::fillCreativeTabs);
    }

    private static void fillCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (DeferredItem<?> i : java.util.List.of(SHOVEL, PICKAXE, AXE, HOE)) event.accept(i);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (DeferredItem<?> i : java.util.List.of(SWORD, SPEAR, AXE, HELMET, CHESTPLATE, LEGGINGS, BOOTS)) event.accept(i);
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (DeferredItem<?> i : java.util.List.of(METEORIC_IRON_BLOCK_ITEM, POLISHED_METEORITE_ITEM, METEORITE_BRICKS_ITEM,
                    METEORITE_BRICK_STAIRS_ITEM, METEORITE_BRICK_SLAB_ITEM)) event.accept(i);
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) event.accept(RAW_METEORITE_BLOCK_ITEM);
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(WARD_ITEM);
    }
}
