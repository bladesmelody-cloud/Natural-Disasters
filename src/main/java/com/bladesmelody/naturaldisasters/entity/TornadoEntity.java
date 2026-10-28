package com.bladesmelody.naturaldisasters.entity;

import com.bladesmelody.naturaldisasters.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The tornado funnel players see, drawn entirely with client-side particles
 * (like Weather2's particle funnels): a spinning cone of cloud, narrow at the
 * ground and wide at the cloud base, with dirt kicked up around its foot.
 *
 * <p>The {@link com.bladesmelody.naturaldisasters.disaster.Tornado} disaster
 * moves it every tick; if the disaster stops touching it, it removes itself.
 */
public class TornadoEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);

    private int untouched;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static TornadoEntity spawn(ServerLevel level, double x, double y, double z, float radius, float height) {
        TornadoEntity e = new TornadoEntity(ModRegistry.TORNADO.get(), level);
        e.setPos(x, y, z);
        e.entityData.set(RADIUS, radius);
        e.entityData.set(HEIGHT, height);
        level.addFreshEntity(e);
        return e;
    }

    /** Called by the disaster each tick to move the funnel and keep it alive. */
    public void follow(double x, double y, double z) {
        setPos(x, y, z);
        untouched = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 4.0F);
        builder.define(HEIGHT, 50.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            funnel();
        } else if (++untouched > 40) {
            discard();
        }
    }

    private void funnel() {
        RandomSource r = random;
        float radius = entityData.get(RADIUS);
        float height = entityData.get(HEIGHT);
        double spin = tickCount * 0.35;
        // Body: dark smoke packed into a cone; slow particles so the shape holds.
        int count = 50 + (int) (radius * 6);
        for (int i = 0; i < count; i++) {
            double t = Math.pow(r.nextDouble(), 0.7);
            double w = radius * (0.3 + 1.2 * Math.pow(t, 1.8)) * (0.85 + r.nextDouble() * 0.3);
            double a = spin + r.nextDouble() * Math.PI * 2;
            double speed = 0.08 + 0.06 * t;
            level().addAlwaysVisibleParticle(r.nextInt(4) == 0 ? ParticleTypes.CLOUD : ParticleTypes.LARGE_SMOKE, true,
                    getX() + Math.cos(a) * w, getY() + t * height, getZ() + Math.sin(a) * w,
                    -Math.sin(a) * speed, 0.03, Math.cos(a) * speed);
        }
        // Wall cloud: a wide dark ring where the funnel meets the storm.
        for (int i = 0; i < 6 + radius; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double w = radius * (1.5 + r.nextDouble() * 2.5);
            level().addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, getX() + Math.cos(a) * w,
                    getY() + height * (0.95 + r.nextDouble() * 0.1), getZ() + Math.sin(a) * w, -Math.sin(a) * 0.1, 0, Math.cos(a) * 0.1);
        }
        BlockPos below = BlockPos.containing(getX(), getY() - 1, getZ());
        int groundY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, below.getX(), below.getZ()) - 1;
        BlockState ground = level().getBlockState(below.atY(groundY));
        if (!ground.isAir()) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < 8 + radius; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double w = radius * (0.5 + r.nextDouble());
                level().addAlwaysVisibleParticle(dust, true, getX() + Math.cos(a) * w, groundY + 1 + r.nextDouble() * 3,
                        getZ() + Math.sin(a) * w, -Math.sin(a) * 0.6, 0.4 + r.nextDouble() * 0.4, Math.cos(a) * 0.6);
            }
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
