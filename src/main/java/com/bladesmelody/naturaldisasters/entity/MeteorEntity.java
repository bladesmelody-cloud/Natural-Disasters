package com.bladesmelody.naturaldisasters.entity;

import com.bladesmelody.naturaldisasters.registry.ModRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The falling meteor players see: flies in a straight line at constant speed
 * and leaves a fire-and-smoke trail. It is only the visual; the
 * {@link com.bladesmelody.naturaldisasters.disaster.Meteor} disaster decides
 * when and where it hits, so it is never saved and can't be hit.
 */
public class MeteorEntity extends Entity {
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);

    private int life;
    private int maxLife = 200;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Sends a meteor from {@code from} to {@code to}, arriving after {@code flightTicks}. */
    public static MeteorEntity launch(ServerLevel level, Vec3 from, Vec3 to, int flightTicks, float size) {
        MeteorEntity e = new MeteorEntity(ModRegistry.METEOR.get(), level);
        e.setPos(from);
        e.setDeltaMovement(to.subtract(from).scale(1.0 / flightTicks));
        e.maxLife = flightTicks;
        e.entityData.set(SIZE, size);
        level.addFreshEntity(e);
        return e;
    }

    public float size() {
        return entityData.get(SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = getDeltaMovement();
        setPos(position().add(motion));
        if (level().isClientSide()) {
            trail();
        } else if (++life > maxLife) {
            discard();
        }
    }

    private void trail() {
        RandomSource r = random;
        float size = size();
        int count = 2 + (int) (size * 1.5F);
        for (int i = 0; i < count; i++) {
            double ox = (r.nextDouble() - 0.5) * size, oy = (r.nextDouble() - 0.5) * size, oz = (r.nextDouble() - 0.5) * size;
            level().addAlwaysVisibleParticle(ParticleTypes.FLAME, true, getX() + ox, getY() + oy, getZ() + oz, 0, 0, 0);
            level().addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, getX() + ox, getY() + oy, getZ() + oz, 0, 0.02, 0);
        }
        if (r.nextInt(3) == 0) level().addAlwaysVisibleParticle(ParticleTypes.LAVA, true, getX(), getY(), getZ(), 0, 0, 0);
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
