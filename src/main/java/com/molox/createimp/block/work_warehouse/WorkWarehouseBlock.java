package com.molox.createimp.block.work_warehouse;

import com.mojang.serialization.MapCodec;
import com.molox.createimp.network.OpenWorkWarehouseGuiPacket;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public class WorkWarehouseBlock extends WrenchableDirectionalBlock
        implements IBE<WorkWarehouseBlockEntity> {

    public static final MapCodec<WorkWarehouseBlock> CODEC =
            simpleCodec(WorkWarehouseBlock::new);

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public WorkWarehouseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(AXIS, Direction.Axis.Z)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends WrenchableDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction facing = clickedFace == Direction.DOWN ? Direction.UP : clickedFace;
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        if (facing == Direction.UP) {
            return true;
        }
        BlockPos neighbourPos = pos.relative(facing.getOpposite());
        return !world.getBlockState(neighbourPos).canBeReplaced();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (AllItems.WRENCH.isIn(player.getMainHandItem())) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof WorkWarehouseBlockEntity be))
            return InteractionResult.PASS;
        PacketDistributor.sendToPlayer((ServerPlayer) player,
                new OpenWorkWarehouseGuiPacket(pos, be.getAddress(), be.isWorking()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) || !newState.hasBlockEntity()) {
            if (level.getBlockEntity(pos) instanceof WorkWarehouseBlockEntity be) {
                NonNullList<ItemStack> drops = NonNullList.create();
                for (int i = 0; i < be.storage.getSlots(); i++) {
                    drops.add(be.storage.getStackInSlot(i));
                }
                Containers.dropContents(level, pos, drops);
            }
        }
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public Class<WorkWarehouseBlockEntity> getBlockEntityClass() {
        return WorkWarehouseBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WorkWarehouseBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.WORK_WAREHOUSE.get();
    }
}