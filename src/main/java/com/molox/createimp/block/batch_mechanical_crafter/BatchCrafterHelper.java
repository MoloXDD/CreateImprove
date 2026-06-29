package com.molox.createimp.block.batch_mechanical_crafter;

import com.molox.createimp.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BatchCrafterHelper {

    public static BatchMechanicalCrafterBlockEntity getCrafter(BlockAndTintGetter reader, BlockPos pos) {
        BlockEntity blockEntity = reader.getBlockEntity(pos);
        if (!(blockEntity instanceof BatchMechanicalCrafterBlockEntity))
            return null;
        return (BatchMechanicalCrafterBlockEntity) blockEntity;
    }

    public static BatchConnectedInputHandler.ConnectedInput getInput(BlockAndTintGetter reader, BlockPos pos) {
        BatchMechanicalCrafterBlockEntity crafter = BatchCrafterHelper.getCrafter(reader, pos);
        return crafter == null ? null : crafter.input;
    }

    public static boolean areCraftersConnected(BlockAndTintGetter reader, BlockPos pos, BlockPos otherPos) {
        BatchConnectedInputHandler.ConnectedInput input1 = BatchCrafterHelper.getInput(reader, pos);
        BatchConnectedInputHandler.ConnectedInput input2 = BatchCrafterHelper.getInput(reader, otherPos);
        if (input1 == null || input2 == null)
            return false;
        if (input1.data.isEmpty() || input2.data.isEmpty())
            return false;
        try {
            if (pos.offset((Vec3i) input1.data.get(0))
                    .equals(otherPos.offset((Vec3i) input2.data.get(0))))
                return true;
        } catch (IndexOutOfBoundsException ignored) {
        }
        return false;
    }

    public static boolean isBatchCrafter(net.minecraft.world.level.block.state.BlockState state) {
        return ModBlocks.BATCH_MECHANICAL_CRAFTER.get() == state.getBlock();
    }
}