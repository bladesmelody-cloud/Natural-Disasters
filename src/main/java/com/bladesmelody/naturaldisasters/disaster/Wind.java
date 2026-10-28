package com.bladesmelody.naturaldisasters.disaster;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * How strong a wind (in mph, like the EF / Saffir-Simpson scales) it takes to
 * tear a block loose. Loosely follows the thresholds tornado addons for
 * Weather2 use (leaves 75, planks 95, logs 103, cobble 120, stone 144) but
 * spread out so only the strongest storms scour bare stone.
 */
public final class Wind {
    /** Never torn loose. */
    public static final double NEVER = Double.MAX_VALUE;

    private Wind() {}

    public static double resistance(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return NEVER;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || state.hasBlockEntity()) return NEVER;
        if (state.is(BlockTags.LEAVES) || state.canBeReplaced() || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)
                || state.is(BlockTags.SAPLINGS) || state.getCollisionShape(level, pos).isEmpty()) return 45;
        if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)) return 60;
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(Blocks.HAY_BLOCK)) return 70;
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.ALL_SIGNS)) return 95;
        if (state.is(BlockTags.LOGS)) return 105;
        if (state.is(BlockTags.SNOW) || state.is(BlockTags.ICE)) return 110;
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.FARMLAND)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.CLAY)) return 140;
        if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.BRICKS)
                || state.is(BlockTags.TERRACOTTA)) return 175;
        return 120 + hardness * 60;
    }
}
