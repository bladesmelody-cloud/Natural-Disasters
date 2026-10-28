package com.bladesmelody.naturaldisasters.instrument;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A disaster instrument. It gives a redstone signal (1–15, stronger for a
 * closer or bigger disaster) while it senses one of its disasters; the
 * sensing itself runs in {@link Instruments}.
 */
public class InstrumentBlock extends Block {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    private final Instrument instrument;

    public InstrumentBlock(Instrument instrument, Properties properties) {
        super(properties);
        this.instrument = instrument;
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    public Instrument instrument() {
        return instrument;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) InstrumentData.get(server).add(pos, instrument);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) InstrumentData.get(level).remove(pos);
    }
}
