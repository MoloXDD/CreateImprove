package com.molox.createimp.block.brass_scrap_bucket;

import com.mojang.serialization.MapCodec;
import com.molox.createimp.network.OpenBrassScrapBucketGuiPacket;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

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
        return InteractionResult.FAIL;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrassScrapBucketBlockEntity(pos, state);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BrassScrapBucketBlockEntity be)) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            if (be.getNuggetCount() <= 0) return InteractionResult.PASS;
            ItemStack nuggets = be.takeAllNuggets();
            Inventory inventory = player.getInventory();
            if (!inventory.add(nuggets)) {
                player.drop(nuggets, false);
            }
            return InteractionResult.SUCCESS;
        }

        int attachType = be.getAttachType();
        int maxItems = 0;
        int maxStacks = 0;
        int currentAmount = 0;
        int currentStacks = 0;
        if (attachType == BrassScrapBucketBlockEntity.ATTACH_ITEM) {
            maxItems = be.getAboveMaxItems();
            maxStacks = be.getAboveMaxStacks();
            currentAmount = be.getAboveCurrentItems();
            currentStacks = be.getAboveCurrentStacks();
        } else if (attachType == BrassScrapBucketBlockEntity.ATTACH_FLUID) {
            maxItems = be.getAboveMaxFluids();
            currentAmount = be.getAboveCurrentFluids();
        }

        PacketDistributor.sendToPlayer((ServerPlayer) player,
                new OpenBrassScrapBucketGuiPacket(pos, attachType, be.keepAmount, be.keepInStacks,
                        maxItems, maxStacks, currentAmount, currentStacks));
        return InteractionResult.SUCCESS;
    }
}