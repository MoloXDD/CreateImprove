package com.molox.createimp.block.brass_scrap_bucket;

import com.mojang.serialization.MapCodec;
import com.molox.createimp.registry.ModBlockEntityTypes;
import com.molox.createimp.registry.ModMenuTypes;
import com.molox.createimp.screen.BrassScrapBucketMenu;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BrassScrapBucketBlock extends BaseEntityBlock implements IWrenchable {

    public static final MapCodec<BrassScrapBucketBlock> CODEC = simpleCodec(BrassScrapBucketBlock::new);

    public BrassScrapBucketBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level.isClientSide() || player == null) return InteractionResult.SUCCESS;

        if (level.getBlockEntity(pos) instanceof BrassScrapBucketBlockEntity be) {
            dropFilterIcon(be, level, pos, player);
        }

        level.removeBlock(pos, false);
        ItemStack drop = new ItemStack(this);
        if (!player.getInventory().add(drop)) {
            player.drop(drop, false);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrassScrapBucketBlockEntity(ModBlockEntityTypes.BRASS_SCRAP_BUCKET.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, ModBlockEntityTypes.BRASS_SCRAP_BUCKET.get(),
                (lvl, pos, blockState, be) -> be.tick());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide()) return;
        if (!fromPos.equals(pos.above())) return;
        if (!(level.getBlockEntity(pos) instanceof BrassScrapBucketBlockEntity be)) return;
        if (be.getAttachType() == BrassScrapBucketBlockEntity.ATTACH_NONE) {
            be.resetKeepConfig();
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BrassScrapBucketBlockEntity be) {
            ItemStack filter = be.filterIcon;
            if (!filter.isEmpty() && filter.getItem() instanceof FilterItem) {
                drops.add(filter.copy());
            }
        }
        return drops;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BrassScrapBucketBlockEntity be))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        FilteringBehaviour filtering = be.filtering;
        if (filtering != null && filtering.getSlotPositioning().testHit(level, pos, state, hit.getLocation())) {
            if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
            filtering.onShortInteract(player, hand, hit.getDirection(), hit);
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BrassScrapBucketBlockEntity be)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (be.getNuggetCount() <= 0) return InteractionResult.PASS;
            ItemStack nuggets = be.takeAllNuggets();
            if (!player.getInventory().add(nuggets)) {
                player.drop(nuggets, false);
            }
            return InteractionResult.SUCCESS;
        }

        int attachType = be.getAttachType();
        int maxItems = 0;
        int maxStacks = 0;
        int currentAmount = 0;
        int currentStacks = 0;

        boolean hasDetectionFilter = !be.filterIcon.isEmpty();

        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM) {
            maxItems = be.getAboveMaxItems();
            maxStacks = be.getAboveMaxStacks();
            if (hasDetectionFilter) {
                currentAmount = be.getFilteredCurrentItems();
                currentStacks = be.getFilteredCurrentStacks();
            } else {
                currentAmount = be.getAboveCurrentItems();
                currentStacks = be.getAboveCurrentStacks();
            }
        } else if (attachType == BrassScrapBucketBlockEntity.ATTACH_FLUID) {
            maxItems = be.getAboveMaxFluids();
            if (hasDetectionFilter) {
                currentAmount = be.getFilteredCurrentFluids();
            } else {
                currentAmount = be.getAboveCurrentFluids();
            }
        }

        final int fAttachType = attachType;
        final int fMaxItems = maxItems;
        final int fMaxStacks = maxStacks;
        final int fCurrentAmount = currentAmount;
        final int fCurrentStacks = currentStacks;
        final int fKeepAmount = be.keepAmount;
        final boolean fKeepInStacks = be.keepInStacks;
        final ItemStack fFilterIcon = be.filterIcon.copy();

        ((ServerPlayer) player).openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.createimp.brass_scrap_bucket");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new BrassScrapBucketMenu(ModMenuTypes.BRASS_SCRAP_BUCKET.get(), id, inv,
                        pos, fAttachType, fKeepAmount, fKeepInStacks,
                        fMaxItems, fMaxStacks, fCurrentAmount, fCurrentStacks,
                        fFilterIcon);
            }
        }, buf -> {
            BlockPos.STREAM_CODEC.encode(buf, pos);
            buf.writeInt(fAttachType);
            buf.writeInt(fKeepAmount);
            buf.writeBoolean(fKeepInStacks);
            buf.writeInt(fMaxItems);
            buf.writeInt(fMaxStacks);
            buf.writeInt(fCurrentAmount);
            buf.writeInt(fCurrentStacks);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, fFilterIcon);
        });

        return InteractionResult.SUCCESS;
    }

    private void dropFilterIcon(BrassScrapBucketBlockEntity be, Level level, BlockPos pos, Player player) {
        ItemStack filter = be.filterIcon;
        if (filter.isEmpty() || !(filter.getItem() instanceof FilterItem)) return;
        if (!player.getInventory().add(filter.copy())) {
            Block.popResource(level, pos, filter.copy());
        }
    }
}