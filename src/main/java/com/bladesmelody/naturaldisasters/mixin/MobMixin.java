package com.bladesmelody.naturaldisasters.mixin;

import com.bladesmelody.naturaldisasters.ash.AshZones;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zombies and skeletons don't burn in the sun under a volcanic ash cloud. */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void naturaldisasters$noSunUnderAsh(CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (!self.level().isClientSide() && AshZones.isDark(self.level(), self.blockPosition())) cir.setReturnValue(false);
    }
}
