package com.molox.createimp.block.brass_scrap_bucket;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("block.createimp.brass_scrap_bucket.tooltip1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(
                Component.translatable("block.createimp.brass_scrap_bucket.tooltip2_prefix").withStyle(ChatFormatting.GRAY)
                        .append(Component.translatable("block.createimp.brass_scrap_bucket.tooltip2_exp").withStyle(ChatFormatting.AQUA))
                        .append(Component.translatable("block.createimp.brass_scrap_bucket.tooltip2_suffix").withStyle(ChatFormatting.GRAY))
        );
        tooltipComponents.add(Component.translatable("block.createimp.brass_scrap_bucket.tooltip3").withStyle(ChatFormatting.GRAY));
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BrassScrapBucketBlockEntity be)) return InteractionResult.PASS;
        if (be.getNuggetCount() <= 0) return InteractionResult.PASS;

        ItemStack nuggets = be.takeAllNuggets();
        Inventory inventory = player.getInventory();
        if (!inventory.add(nuggets)) {
            player.drop(nuggets, false);
        }
        return InteractionResult.SUCCESS;
    }
}