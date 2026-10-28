package com.bladesmelody.naturaldisasters.meteorite;

import com.bladesmelody.naturaldisasters.NaturalDisasters;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Meteoric iron gear: a step above netherite (owner: "since meteorite is so
 * rare"). Netherite for comparison: tools 2031 uses, speed 9, +4 damage,
 * enchantability 15; armor 37 × durability, 3/6/8/3, toughness 3,
 * knockback resistance 0.1.
 */
public final class MeteoricMaterials {
    public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "meteoric_tool_materials"));

    public static final ToolMaterial TOOLS = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2600, 10.0F, 5.0F, 18, REPAIRS);

    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(NaturalDisasters.MODID, "meteoric"));

    public static final ArmorMaterial ARMOR = new ArmorMaterial(42,
            Map.of(ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 7, ArmorType.CHESTPLATE, 9, ArmorType.HELMET, 3, ArmorType.BODY, 20),
            18, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.15F, REPAIRS, ASSET);

    private MeteoricMaterials() {}
}
