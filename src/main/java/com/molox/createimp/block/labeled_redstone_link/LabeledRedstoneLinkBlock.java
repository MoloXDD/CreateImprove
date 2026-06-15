package com.molox.createimp.block.labeled_redstone_link;

import com.mojang.serialization.MapCodec;
import com.molox.createimp.network.OpenLabeledRedstoneLinkGuiPacket;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

public class LabeledRedstoneLinkBlock extends WrenchableDirectionalBlock
        implements IBE<LabeledRedstoneLinkBlockEntity> {

    public static final MapCodec<LabeledRedstoneLinkBlock> CODEC =
            simpleCodec(LabeledRedstoneLinkBlock::new);

    public static final BooleanProperty RECEIVER = BooleanProperty.create("receiver");
    public static final BooleanProperty POWERED  = BlockStateProperties.POWERED;

    public LabeledRedstoneLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(RECEIVER, false)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends WrenchableDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RECEIVER, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos neighbourPos = pos.relative(facing.getOpposite());
        return !world.getBlockState(neighbourPos).canBeReplaced();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return AllShapes.REDSTONE_BRIDGE.get(state.getValue(FACING));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        return true;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        level.setBlock(pos, state.cycle(RECEIVER), 3);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (AllItems.WRENCH.isIn(player.getMainHandItem())) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LabeledRedstoneLinkBlockEntity be))
            return InteractionResult.PASS;
        PacketDistributor.sendToPlayer((ServerPlayer) player,
                new OpenLabeledRedstoneLinkGuiPacket(pos, be.getFrequencyText()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<LabeledRedstoneLinkBlockEntity> getBlockEntityClass() {
        return LabeledRedstoneLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LabeledRedstoneLinkBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.LABELED_REDSTONE_LINK.get();
    }
}