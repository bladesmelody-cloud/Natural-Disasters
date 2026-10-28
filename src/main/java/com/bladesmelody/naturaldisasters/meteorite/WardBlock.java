package com.bladesmelody.naturaldisasters.meteorite;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Disaster ward: disasters can't change blocks within its radius (bedrock to
 * sky); creatures there are still hurt. Right-click cycles the radius 4, 8,
 * 12, 16 and shows the edge. Wards are tracked in {@link WardData}.
 */
public class WardBlock extends Block {
    /** Radius = 4 × size. */
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 1, 4);

    public WardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIZE, 2));
    }

    public static int radius(BlockState state) {
        return 4 * state.getValue(SIZE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        BlockState next = state.setValue(SIZE, state.getValue(SIZE) % 4 + 1);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        WardData.get(server).put(pos, radius(next));
        player.sendOverlayMessage(Component.translatable("block.naturaldisasters.disaster_ward.radius", radius(next)));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.6F + 0.2F * next.getValue(SIZE));
        showEdge(server, pos, radius(next));
        return InteractionResult.SUCCESS;
    }

    /** A ring of particles where the protection ends. */
    static void showEdge(ServerLevel level, BlockPos pos, int radius) {
        int points = radius * 8;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = pos.getX() + 0.5 + Math.cos(a) * radius, z = pos.getZ() + 0.5 + Math.sin(a) * radius;
            level.sendParticles(ParticleTypes.END_ROD, x, pos.getY() + 0.6, z, 1, 0, 0.3, 0, 0.0);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            WardData.get(server).put(pos, radius(state));
            showEdge(server, pos, radius(state));
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) WardData.get(level).remove(pos);
    }
}
