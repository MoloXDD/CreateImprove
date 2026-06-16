package com.molox.createimp.block.labeled_redstone_link;

import com.mojang.serialization.MapCodec;
import com.molox.createimp.CreateImp;
import com.molox.createimp.network.OpenLabeledRedstoneLinkGuiPacket;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.createmod.catnip.data.Iterate;
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

    // 对应原版：isSignalSource 只在 RECEIVER=true 且 POWERED=true 时才是信号源
    @Override
    public boolean isSignalSource(BlockState state) {
        return state.getValue(RECEIVER) && state.getValue(POWERED);
    }

    // 对应原版 getSignal：只有接收端才输出信号
    @Override
    public int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        if (!state.getValue(RECEIVER)) return 0;
        if (!(world.getBlockEntity(pos) instanceof LabeledRedstoneLinkBlockEntity be)) return 0;
        return be.getReceivedSignal();
    }

    // 对应原版 getDirectSignal：朝向附着面方向输出强信号
    @Override
    public int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        if (side != state.getValue(FACING)) return 0;
        return getSignal(state, world, pos, side);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter world, BlockPos pos, Direction side) {
        return true;
    }

    // 对应原版 neighborChanged：只 scheduleTick，不直接处理
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide()) return;
        CreateImp.LOGGER.info("[LRL] neighborChanged from={} block={} time={}",
                fromPos, block.getClass().getSimpleName(), level.getGameTime());

        Direction facing = state.getValue(FACING);
        if (fromPos.equals(pos.relative(facing.getOpposite()))) {
            if (!canSurvive(state, level, pos)) {
                level.destroyBlock(pos, true);
                return;
            }
        }

        // 直接调用，不经过 scheduleTick，和原版终端一致，消除额外1tick延迟
        updateTransmittedSignal(state, level, pos);
    }

    // 对应原版 onPlace：首次放置时触发一次 updateTransmittedSignal
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState oldState, boolean isMoving) {
        if (oldState.getBlock() == state.getBlock()) return;
        if (isMoving) return;
        updateTransmittedSignal(state, level, pos);
    }



    /**
     * 对应原版 updateTransmittedSignal：
     * 只处理发送端，计算本地功率，更新 POWERED 状态，调用 be.transmit()
     */
    public void updateTransmittedSignal(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (state.getValue(RECEIVER)) return;

        final int power = getPower(level, state, pos);

        boolean currentlyPowered = state.getValue(POWERED);
        boolean shouldBePowered = power > 0;
        if (currentlyPowered != shouldBePowered) {
            level.setBlock(pos, state.cycle(POWERED), 2);
        }

        withBlockEntityDo(level, pos, be -> be.transmit(power));
    }

    /**
     * 对应原版 getPower：
     * 遍历6个方向取 level.getSignal，
     * 加上非附着面的下方方块信号（原版逻辑）
     */
    private int getPower(Level level, BlockState state, BlockPos pos) {
        int power = 0;
        Direction facing = state.getValue(FACING);

        // 遍历所有方向取信号
        for (Direction d : Iterate.directions) {
            power = Math.max(power, level.getSignal(pos.relative(d), d));
        }

        // 再遍历非附着面的方向，取那个方向的下方信号
        for (Direction d : Iterate.directions) {
            if (d == facing.getOpposite()) continue;
            power = Math.max(power, level.getSignal(pos.relative(d), Direction.UP));
        }

        return power;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        boolean wasReceiver = state.getValue(RECEIVER);
        BlockState newState = state.setValue(RECEIVER, !wasReceiver).setValue(POWERED, false);
        level.setBlock(pos, newState, 3);

        // 切换后重新初始化网络状态
        withBlockEntityDo(level, pos, be -> {
            if (!wasReceiver) {
                // 从发送切到接收：停止发送，通知网络
                LabeledRedstoneLinkNetworkHandler handler = LabeledRedstoneLinkNetworkHandler.get(level);
                if (handler != null) {
                    be.transmit(0);
                }
            } else {
                // 从接收切到发送：清空接收信号，开始发送
                be.onReceivedSignal(0);
                updateTransmittedSignal(newState, level, pos);
            }
        });

        level.scheduleTick(pos, this, 1);
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
        if (!level.isClientSide() && state.getValue(RECEIVER)) {
            // 接收端移除时通知附着方块
            Direction facing = state.getValue(FACING);
            BlockPos attachedPos = pos.relative(facing.getOpposite());
            level.blockUpdated(attachedPos, level.getBlockState(attachedPos).getBlock());
        }
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