package com.molox.createimp.block.batch_mechanical_crafter;

import com.molox.createimp.CreateImp;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BatchCrafterCTBehaviour extends ConnectedTextureBehaviour.Base {

    private static final String NS = CreateImp.MODID;
    private static final String PREFIX = "block/batch_mechanical_crafter/";

    private static final CTSpriteShiftEntry BATCH_CRAFTER_SIDE =
            CTSpriteShifter.getCT(
                    AllCTTypes.VERTICAL,
                    ResourceLocation.fromNamespaceAndPath(NS, PREFIX + "batch_crafter_side"),
                    ResourceLocation.fromNamespaceAndPath(NS, PREFIX + "batch_crafter_side_connected")
            );

    private static final CTSpriteShiftEntry BATCH_CRAFTER_OTHERSIDE =
            CTSpriteShifter.getCT(
                    AllCTTypes.HORIZONTAL,
                    ResourceLocation.fromNamespaceAndPath(NS, PREFIX + "batch_crafter_side"),
                    ResourceLocation.fromNamespaceAndPath(NS, PREFIX + "batch_crafter_side_connected")
            );

    private static final CTSpriteShiftEntry BATCH_BRASS_BLOCK =
            CTSpriteShifter.getCT(
                    AllCTTypes.OMNIDIRECTIONAL,
                    ResourceLocation.fromNamespaceAndPath(NS, PREFIX + "batch_brass_block"),
                    ResourceLocation.fromNamespaceAndPath(NS, PREFIX + "batch_brass_block_connected")
            );

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader,
                              BlockPos pos, BlockPos otherPos, Direction face) {
        if (state.getBlock() != other.getBlock())
            return false;
        if (state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING) !=
                other.getValue(HorizontalKineticBlock.HORIZONTAL_FACING))
            return false;
        return BatchCrafterHelper.areCraftersConnected(reader, pos, otherPos);
    }

    @Override
    protected boolean reverseUVs(BlockState state, Direction direction) {
        if (!direction.getAxis().isVertical())
            return false;
        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        if (facing.getAxis() == direction.getAxis())
            return false;
        boolean isNegative = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
        if (direction == Direction.DOWN && facing.getAxis() == Direction.Axis.Z)
            return !isNegative;
        return isNegative;
    }

    @Override
    public CTSpriteShiftEntry getShift(BlockState state, Direction direction,
                                       @Nullable TextureAtlasSprite sprite) {
        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        boolean isFront = facing.getAxis() == direction.getAxis();
        boolean isVertical = direction.getAxis().isVertical();
        boolean facingX = facing.getAxis() == Direction.Axis.X;
        if (isFront)
            return BATCH_BRASS_BLOCK;
        return (isVertical && !facingX) ? BATCH_CRAFTER_OTHERSIDE : BATCH_CRAFTER_SIDE;
    }
}