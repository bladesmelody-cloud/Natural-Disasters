package com.bladesmelody.naturaldisasters.mixin;

import com.bladesmelody.naturaldisasters.sky.SkyZones;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Under a volcanic ash cloud the sky counts as night for monster spawning. */
@Mixin(Monster.class)
public abstract class MonsterMixin {
    /** Sky darkening at midnight. */
    private static final int NIGHT_DARKEN = 11;

    @Redirect(method = "isDarkEnoughToSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I"))
    private static int naturaldisasters$ashNight(ServerLevelAccessor level, BlockPos pos) {
        if (SkyZones.isDark(level.getLevel(), pos)) return level.getMaxLocalRawBrightness(pos, NIGHT_DARKEN);
        return level.getMaxLocalRawBrightness(pos);
    }
}
