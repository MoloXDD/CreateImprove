package com.molox.createimp.block.redstone_link_router;

import com.mojang.serialization.MapCodec;
import com.molox.createimp.network.OpenRedstoneLinkRouterGuiPacket;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;

public class RedstoneLinkRouterBlock extends Block
        implements IWrenchable, IBE<RedstoneLinkRouterBlockEntity> {

    public static final MapCodec<RedstoneLinkRouterBlock> CODEC =
            simpleCodec(RedstoneLinkRouterBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * 以下坐标（单位：像素，0-16）取自方块的 Blockbench 模型，代表 facing=north（未旋转）
     * 状态下的可视几何体，已合并为若干个包围盒，用于选中框（外观轮廓）。带 -22.5° 倾角的
     * 主控制器部件已按旋转矩阵精确换算为轴对齐包围盒（面板部件的旋转后包围盒完全落在
     * 控制器包围盒内，不再单独列出）；天线部分（含卫星锅）不生成选中框。
     */
    private static final double[][] SHAPE_BOXES_NORTH = {
            {0, 0, 4, 16, 2, 16},
            {0, 2, 6, 2, 9, 16},
            {14, 2, 6, 16, 9, 16},
            {2, 2, 8, 14, 7, 16},
            {2, 7, 14, 14, 9, 16},
            {2, 2.8, 3.7, 14, 10.1, 15.9}
    };

    /**
     * 碰撞箱简化为单个 16(x) * 9(y) * 12(z) 的方形。12 像素的一条边对应模型最底层元素
     * （from [0,0,4] 到 [16,2,16]，即方块的底座）在 z 方向上的跨度：z 从 4 到 16，
     * 换句话说底座并不贴着 z=0 这一侧，靠 z=16 一侧（天线/面板所在的一侧）对齐，
     * z=0 到 z=4 这 4 像素范围内没有碰撞。
     */
    private static final double[][] COLLISION_BOX_NORTH = {
            {0, 0, 4, 16, 9, 16}
    };

    private static final Map<Direction, VoxelShape> SHAPES = buildShapes(SHAPE_BOXES_NORTH);
    private static final Map<Direction, VoxelShape> COLLISION_SHAPES = buildShapes(COLLISION_BOX_NORTH);

    public RedstoneLinkRouterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPES.get(state.getValue(FACING));
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.FAIL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (AllItems.WRENCH.isIn(player.getMainHandItem())) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        PacketDistributor.sendToPlayer((ServerPlayer) player, new OpenRedstoneLinkRouterGuiPacket(pos));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<RedstoneLinkRouterBlockEntity> getBlockEntityClass() {
        return RedstoneLinkRouterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RedstoneLinkRouterBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.REDSTONE_LINK_ROUTER.get();
    }

    private static Map<Direction, VoxelShape> buildShapes(double[][] boxesNorth) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : boxesNorth) {
                shape = Shapes.or(shape, rotatedBox(box[0], box[1], box[2], box[3], box[4], box[5], direction));
            }
            map.put(direction, shape);
        }
        return map;
    }

    private static VoxelShape rotatedBox(double x1, double y1, double z1, double x2, double y2, double z2, Direction facing) {
        double rx1, rz1, rx2, rz2;
        switch (facing) {
            case EAST -> {
                rx1 = 16 - z2;
                rz1 = x1;
                rx2 = 16 - z1;
                rz2 = x2;
            }
            case SOUTH -> {
                rx1 = 16 - x2;
                rz1 = 16 - z2;
                rx2 = 16 - x1;
                rz2 = 16 - z1;
            }
            case WEST -> {
                rx1 = z1;
                rz1 = 16 - x2;
                rx2 = z2;
                rz2 = 16 - x1;
            }
            default -> {
                rx1 = x1;
                rz1 = z1;
                rx2 = x2;
                rz2 = z2;
            }
        }
        return Shapes.box(rx1 / 16.0, y1 / 16.0, rz1 / 16.0, rx2 / 16.0, y2 / 16.0, rz2 / 16.0);
    }
}