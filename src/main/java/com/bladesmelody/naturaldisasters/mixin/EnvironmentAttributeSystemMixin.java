package com.bladesmelody.naturaldisasters.mixin;

import com.bladesmelody.naturaldisasters.sky.SkyLayers;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the disaster sky layers after vanilla's (so they apply on top of weather). */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class EnvironmentAttributeSystemMixin {
    @Inject(method = "addDefaultLayers(Lnet/minecraft/world/attribute/EnvironmentAttributeSystem$Builder;Lnet/minecraft/world/level/Level;)V",
            at = @At("TAIL"))
    private static void naturaldisasters$skyLayers(EnvironmentAttributeSystem.Builder builder, Level level, CallbackInfo ci) {
        SkyLayers.add(builder, level);
    }
}
