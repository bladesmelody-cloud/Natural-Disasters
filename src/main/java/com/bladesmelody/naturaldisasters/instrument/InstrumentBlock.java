package com.bladesmelody.naturaldisasters.instrument;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    private final Instrument instrument;

    public InstrumentBlock(Instrument instrument, Properties properties) {
        super(properties);
        this.instrument = instrument;
        registerDefaultState(stateDefinition.any().setValue(POWER, 0).setValue(FACING, Direction.NORTH));
    }

    /** Roughly the model's outline; the same for every facing (the models are near-symmetric). */
    private VoxelShape shape() {
        return switch (instrument) {
            case SEISMOGRAPH -> SEISMOGRAPH_SHAPE;
            case BAROMETER -> BAROMETER_SHAPE;
            case SKY_WATCHER -> SKY_WATCHER_SHAPE;
        };
    }

    private static final VoxelShape SEISMOGRAPH_SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(2, 3, 4, 14, 13, 13));
    private static final VoxelShape BAROMETER_SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(4, 2, 4, 12, 16, 12));
    private static final VoxelShape SKY_WATCHER_SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 8, 13), Block.box(2, 6, 1, 14, 14, 15));

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The front (dial, lens, paper) faces the player who placed it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public Instrument instrument() {
        return instrument;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, FACING);
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
