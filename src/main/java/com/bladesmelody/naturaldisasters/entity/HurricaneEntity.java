package com.bladesmelody.naturaldisasters.entity;

import com.bladesmelody.naturaldisasters.registry.ModRegistry;
import java.util.function.Consumer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Marks a hurricane's eye for clients, which draw the spiral cloud bands
 * around it. Moved and kept alive by the
 * {@link com.bladesmelody.naturaldisasters.disaster.Hurricane} disaster; never saved.
 */
public class HurricaneEntity extends Entity {
    private static final EntityDataAccessor<Float> EYE = SynchedEntityData.defineId(HurricaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(HurricaneEntity.class, EntityDataSerializers.FLOAT);

    /** Set by the client setup: draws the cloud bands each client tick. */
    public static Consumer<HurricaneEntity> clientEffects;

    private int untouched;

    public HurricaneEntity(EntityType<? extends HurricaneEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static HurricaneEntity spawn(ServerLevel level, double x, double y, double z, float eye, float radius) {
        HurricaneEntity e = new HurricaneEntity(ModRegistry.HURRICANE.get(), level);
        e.setPos(x, y, z);
        e.entityData.set(EYE, eye);
        e.entityData.set(RADIUS, radius);
        level.addFreshEntity(e);
        return e;
    }

    public void follow(double x, double y, double z) {
        setPos(x, y, z);
        untouched = 0;
    }

    public float eyeRadius() {
        return entityData.get(EYE);
    }

    public float stormRadius() {
        return entityData.get(RADIUS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(EYE, 16.0F);
        builder.define(RADIUS, 200.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (clientEffects != null) clientEffects.accept(this);
        } else if (++untouched > 60) {
            discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
