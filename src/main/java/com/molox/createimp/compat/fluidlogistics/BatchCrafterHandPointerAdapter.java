package com.molox.createimp.compat.fluidlogistics;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.batch_mechanical_crafter.BatchConnectedInputHandler;
import com.molox.createimp.block.batch_mechanical_crafter.BatchCrafterHelper;
import com.molox.createimp.block.batch_mechanical_crafter.BatchMechanicalCrafterBlock;
import com.molox.createimp.block.batch_mechanical_crafter.BatchMechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.yision.fluidlogistics.api.handpointer.crafter.HandPointerCrafterAdapter;
import com.yision.fluidlogistics.api.handpointer.crafter.HandPointerCrafterAdapters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 让流体包裹指读棒（Hand Pointer）"自动连接动力合成器"这一功能，对本模组
 * 的批量动力合成器同样生效。
 * <p>
 * 流体包裹自己给这个功能开了一个公开的适配器扩展点
 * （{@code api.handpointer.crafter.HandPointerCrafterAdapter} /
 * {@code HandPointerCrafterAdapters}），指读棒的连接逻辑完全通过这套接口
 * 操作目标方块，不关心目标具体是不是原版机械合成器。本类按反编译确认的
 * 流包官方适配器（{@code CreateMechanicalCrafterAdapter}）实现方式，把
 * 每一步操作原样换成批量动力合成器自己对应的方法——两者的朝向属性
 * （{@code HORIZONTAL_FACING}/{@code POINTING}）、连接判断
 * （{@code shouldConnect}/{@code areCraftersConnected}/
 * {@code toggleConnection}）、朝向切换（{@code pointingFromFacing} +
 * {@code KineticBlockEntity.switchToBlockState}）本来就是照抄原版机械
 * 合成器实现的，方法签名和语义完全对应，不需要做任何额外转换。
 * <p>
 * 本类只在 {@link FluidLogisticsCompat#isLoaded()} 为真之后才会被真正
 * 引用到（见 {@link #register()} 的调用方 {@code CreateImp#commonSetup}），
 * 未安装流体包裹时不会触发本类加载。
 */
public final class BatchCrafterHandPointerAdapter implements HandPointerCrafterAdapter {

    private static final BatchCrafterHandPointerAdapter INSTANCE = new BatchCrafterHandPointerAdapter();

    private BatchCrafterHandPointerAdapter() {
    }

    /**
     * 注册进指读棒的机械合成器适配器体系。调用前必须已经确认
     * {@link FluidLogisticsCompat#isLoaded()} 为真，且只应该调用一次
     * （{@code HandPointerCrafterAdapters.register} 对同一个 id 重复注册会
     * 直接抛异常）。这个功能不受任何配置项控制，只要装了流体包裹就一直
     * 生效。
     */
    public static void register() {
        HandPointerCrafterAdapters.register(
                ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "batch_mechanical_crafter"),
                INSTANCE);
    }

    @Override
    public boolean matches(Level level, BlockPos pos, BlockState state) {
        return BatchCrafterHelper.isBatchCrafter(state)
                && level.getBlockEntity(pos) instanceof BatchMechanicalCrafterBlockEntity;
    }

    @Override
    public Direction getFacing(Level level, BlockPos pos, BlockState state) {
        return state.getValue(BatchMechanicalCrafterBlock.HORIZONTAL_FACING);
    }

    @Override
    public Direction getTargetDirection(Level level, BlockPos pos, BlockState state) {
        return BatchMechanicalCrafterBlock.getTargetDirection(state);
    }

    @Override
    public boolean canConnect(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        Direction facing = getFacing(level, pos, state);
        return BatchConnectedInputHandler.shouldConnect(level, pos, facing.getOpposite(), direction);
    }

    @Override
    public boolean areConnected(Level level, BlockPos first, BlockPos second) {
        return BatchCrafterHelper.areCraftersConnected((BlockAndTintGetter) level, first, second);
    }

    @Override
    public void toggleConnection(Level level, BlockPos first, BlockPos second) {
        BatchConnectedInputHandler.toggleConnection(level, first, second);
    }

    @Override
    public void setTargetDirection(Level level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        Direction facing = getFacing(level, pos, state);
        BlockState updated = state.setValue(BatchMechanicalCrafterBlock.POINTING,
                BatchMechanicalCrafterBlock.pointingFromFacing(direction.getOpposite(), facing));
        if (updated != state) {
            KineticBlockEntity.switchToBlockState(level, pos, updated);
        }
    }
}