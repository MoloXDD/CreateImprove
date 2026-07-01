package com.molox.createimp.block.template_panel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.molox.createimp.registry.ModItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.utility.CreateLang;
import io.netty.buffer.ByteBuf;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class TemplatePanelBlock extends FaceAttachedHorizontalDirectionalBlock
        implements ProperWaterloggedBlock, IBE<TemplatePanelBlockEntity>, IWrenchable, SpecialBlockItemRequirement {

    public static final MapCodec<TemplatePanelBlock> CODEC = simpleCodec(TemplatePanelBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public TemplatePanelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(WATERLOGGED, Boolean.FALSE)
                .setValue(POWERED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACE, FACING, WATERLOGGED, POWERED));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canAttachLenient(level, pos, getConnectedDirection(state).getOpposite());
    }

    public static boolean canAttachLenient(LevelReader reader, BlockPos pos, Direction direction) {
        BlockPos relative = pos.relative(direction);
        return !reader.getBlockState(relative).getCollisionShape(reader, relative).isEmpty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState stateForPlacement = super.getStateForPlacement(context);
        if (stateForPlacement == null) {
            return null;
        }
        if (stateForPlacement.getValue(FACE) == AttachFace.FLOOR) {
            stateForPlacement = stateForPlacement.setValue(FACING, stateForPlacement.getValue(FACING).getOpposite());
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState blockState = level.getBlockState(pos);
        TemplatePanelBlockEntity fpbe = getBlockEntity(level, pos);
        Vec3 location = context.getClickLocation();
        if (blockState.is(this) && location != null && fpbe != null) {
            if (!level.isClientSide()) {
                PanelSlot targetedSlot = getTargetedSlot(pos, blockState, location);
                ItemStack panelItem = TemplatePanelBlockItem.fixCtrlCopiedStack(context.getItemInHand());
                UUID networkFromStack = com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem.networkFromStack(panelItem);
                Player player = context.getPlayer();
                if (fpbe.addPanel(targetedSlot, networkFromStack) && player != null) {
                    player.displayClientMessage(CreateLang.translateDirect("logistically_linked.connected"), true);
                    if (!player.isCreative()) {
                        panelItem.shrink(1);
                        if (panelItem.isEmpty()) {
                            player.setItemInHand(context.getHand(), ItemStack.EMPTY);
                        }
                    }
                }
            }
            stateForPlacement = blockState;
        }
        return withWater(stateForPlacement, context);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        PanelSlot slot = getTargetedSlot(pos, state, context.getClickLocation());
        if (!(world instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }
        return onBlockEntityUse(world, pos, be -> {
            TemplatePanelBehaviour behaviour = be.panels.get(slot);
            if (behaviour == null || !behaviour.isActive()) {
                return InteractionResult.SUCCESS;
            }
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, world.getBlockState(pos), player);
            NeoForge.EVENT_BUS.post((Event) event);
            if (event.isCanceled()) {
                return InteractionResult.SUCCESS;
            }
            if (!be.removePanel(slot)) {
                return InteractionResult.SUCCESS;
            }
            if (player != null && !player.isCreative()) {
                player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.TEMPLATE_PANEL.get()));
            }
            IWrenchable.playRemoveSound(world, pos);
            if (be.activePanels() == 0) {
                world.destroyBlock(pos, false);
            }
            return InteractionResult.SUCCESS;
        });
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer == null) {
            return;
        }
        double range = placer.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0;
        HitResult hitResult = placer.pick(range, 1.0f, false);
        Vec3 location = hitResult.getLocation();
        if (location == null) {
            return;
        }
        PanelSlot initialSlot = getTargetedSlot(pos, state, location);
        withBlockEntityDo(level, pos, fpbe -> fpbe.addPanel(initialSlot,
                com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem.networkFromStack(stack)));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(stack.getItem() == ModItems.TEMPLATE_PANEL.get())) {
            return ItemInteractionResult.SUCCESS;
        }
        Vec3 location = hitResult.getLocation();
        if (location == null) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!TemplatePanelBlockItem.isTuned(stack)) {
            com.simibubi.create.AllSoundEvents.DENY.playOnServer(level, (Vec3i) pos);
            player.displayClientMessage(CreateLang.translate("factory_panel.tune_before_placing").component(), true);
            return ItemInteractionResult.FAIL;
        }
        PanelSlot newSlot = getTargetedSlot(pos, state, location);
        withBlockEntityDo(level, pos, fpbe -> {
            ItemStack fixedStack = TemplatePanelBlockItem.fixCtrlCopiedStack(stack);
            if (!fpbe.addPanel(newSlot, com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem.networkFromStack(fixedStack))) {
                return;
            }
            player.displayClientMessage(CreateLang.translateDirect("logistically_linked.connected"), true);
            level.playSound(null, pos, this.soundType.getPlaceSound(), SoundSource.BLOCKS);
            if (player.isCreative()) {
                return;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        });
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (tryDestroySubPanelFirst(state, level, pos, player)) {
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    private boolean tryDestroySubPanelFirst(BlockState state, Level level, BlockPos pos, Player player) {
        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1.0;
        HitResult hitResult = player.pick(range, 1.0f, false);
        Vec3 location = hitResult.getLocation();
        PanelSlot destroyedSlot = getTargetedSlot(pos, state, location);
        return InteractionResult.SUCCESS == onBlockEntityUse(level, pos, fpbe -> {
            if (fpbe.activePanels() < 2) {
                return InteractionResult.FAIL;
            }
            if (!fpbe.removePanel(destroyedSlot)) {
                return InteractionResult.FAIL;
            }
            if (!player.isCreative()) {
                popResource(level, pos, new ItemStack(ModItems.TEMPLATE_PANEL.get()));
            }
            return InteractionResult.SUCCESS;
        });
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) && getConnectedDirection(state) == side ? 15 : 0;
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (!(context.getItemInHand().getItem() == ModItems.TEMPLATE_PANEL.get())) {
            return false;
        }
        Vec3 location = context.getClickLocation();
        BlockPos pos = context.getClickedPos();
        PanelSlot slot = getTargetedSlot(pos, state, location);
        TemplatePanelBlockEntity blockEntity = getBlockEntity(context.getLevel(), pos);
        if (blockEntity == null) {
            return false;
        }
        TemplatePanelBehaviour behaviour = blockEntity.panels.get(slot);
        return behaviour != null && !behaviour.isActive();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext ecc && ecc.getEntity() == null) {
            return getShape(state, level, pos, context);
        }
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        TemplatePanelBlockEntity blockEntity = getBlockEntity(level, pos);
        if (blockEntity != null) {
            return blockEntity.getShape();
        }
        return com.simibubi.create.AllShapes.FACTORY_PANEL_FALLBACK.get(getConnectedDirection(state));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        this.updateWater(level, state, currentPos);
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return this.fluidState(state);
    }

    public static Direction connectedDirection(BlockState state) {
        return getConnectedDirection(state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    public static PanelSlot getTargetedSlot(BlockPos pos, BlockState blockState, Vec3 clickLocation) {
        double bestDistance = Double.MAX_VALUE;
        PanelSlot bestSlot = PanelSlot.BOTTOM_LEFT;
        Vec3 localClick = clickLocation.subtract(Vec3.atLowerCornerOf(pos));
        float xRot = 57.295776f * getXRot(blockState);
        float yRot = 57.295776f * getYRot(blockState);
        for (PanelSlot slot : PanelSlot.values()) {
            Vec3 vec = new Vec3(0.25 + slot.xOffset * 0.5, 0.0, 0.25 + slot.yOffset * 0.5);
            vec = VecHelper.rotateCentered(vec, 180.0, Direction.Axis.Y);
            vec = VecHelper.rotateCentered(vec, xRot + 90.0f, Direction.Axis.X);
            vec = VecHelper.rotateCentered(vec, yRot, Direction.Axis.Y);
            double diff = vec.distanceToSqr(localClick);
            if (diff > bestDistance) continue;
            bestDistance = diff;
            bestSlot = slot;
        }
        return bestSlot;
    }

    @Override
    public Class<TemplatePanelBlockEntity> getBlockEntityClass() {
        return TemplatePanelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TemplatePanelBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.TEMPLATE_PANEL.get();
    }

    public static float getXRot(BlockState state) {
        AttachFace face = state.getOptionalValue(FACE).orElse(AttachFace.FLOOR);
        return face == AttachFace.CEILING ? 1.5707964f : (face == AttachFace.FLOOR ? -1.5707964f : 0.0f);
    }

    public static float getYRot(BlockState state) {
        Direction facing = state.getOptionalValue(FACING).orElse(Direction.SOUTH);
        AttachFace face = state.getOptionalValue(FACE).orElse(AttachFace.FLOOR);
        return (face == AttachFace.CEILING ? (float) Math.PI : 0.0f) + AngleHelper.rad(AngleHelper.horizontalAngle(facing));
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity blockEntity) {
        return ItemRequirement.NONE;
    }

    @NotNull
    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public enum PanelSlot implements StringRepresentable {
        TOP_LEFT(1, 1),
        TOP_RIGHT(0, 1),
        BOTTOM_LEFT(1, 0),
        BOTTOM_RIGHT(0, 0);

        public static final Codec<PanelSlot> CODEC = StringRepresentable.fromValues(PanelSlot::values);
        public static final StreamCodec<ByteBuf, PanelSlot> STREAM_CODEC = CatnipStreamCodecBuilders.ofEnum(PanelSlot.class);

        public final int xOffset;
        public final int yOffset;

        PanelSlot(int xOffset, int yOffset) {
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }

        @NotNull
        @Override
        public String getSerializedName() {
            return Lang.asId(this.name());
        }
    }

    public enum PanelState {
        PASSIVE, ACTIVE
    }
}