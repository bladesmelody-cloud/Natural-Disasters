package com.bladesmelody.naturaldisasters.meteorite;

import com.bladesmelody.naturaldisasters.protect.Protection;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Meteoric armor's perk: each piece takes 15 % off damage from disasters
 * (hail, acid, heat waves, lightning, blasts, waves, thrown debris), so a full
 * set takes 60 % less. Disasters mark their own hits with {@link #during}.
 */
public final class DisasterResistance {
    public static final float PER_PIECE = 0.15F;
    private static int depth;

    private DisasterResistance() {}

    /** Runs a disaster's damage call so the perk can recognise it. */
    public static boolean during(java.util.function.BooleanSupplier hit) {
        depth++;
        try {
            return hit.getAsBoolean();
        } finally {
            depth--;
        }
    }

    public static int pieces(LivingEntity e) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = e.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.is(MeteoriteItems.ARMOR_TAG)) n++;
        }
        return n;
    }

    public static void onDamage(LivingDamageEvent.Pre event) {
        boolean fromDisaster = depth > 0
                || (event.getSource().getDirectEntity() instanceof FallingBlockEntity b && b.entityTags().contains(Protection.DEBRIS_TAG));
        if (!fromDisaster) return;
        int n = pieces(event.getEntity());
        if (n > 0) event.setNewDamage(event.getNewDamage() * (1 - PER_PIECE * n));
    }
}
