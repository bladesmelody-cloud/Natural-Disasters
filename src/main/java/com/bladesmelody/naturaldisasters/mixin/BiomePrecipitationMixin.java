package com.bladesmelody.naturaldisasters.mixin;

import com.bladesmelody.naturaldisasters.sky.SkyZones;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client only: inside a blizzard the weather renders as snow, even in warm biomes. */
@Mixin(Biome.class)
public abstract class BiomePrecipitationMixin {
    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void naturaldisasters$blizzardSnow(BlockPos pos, int seaLevel, CallbackInfoReturnable<Biome.Precipitation> cir) {
        // Rendering only: on an integrated server the same class serves the server thread too.
        if (!"Render thread".equals(Thread.currentThread().getName())) return;
        for (SkyZones.Zone z : SkyZones.zones(true)) {
            if (z.kind() == SkyZones.Kind.BLIZZARD && z.covers(pos.getX() + 0.5, pos.getZ() + 0.5)) {
                cir.setReturnValue(Biome.Precipitation.SNOW);
                return;
            }
        }
    }
}
