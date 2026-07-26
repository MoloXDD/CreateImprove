package com.molox.createimp.block.brass_scrap_bucket;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 黄铜废料桶自己的打包机解包处理器，原理与
 * {@code AndesiteScrapBucketUnpackingHandler} 完全一致——黄铜废料桶的
 * {@code itemHandler} 同样是"输入槽（0号）永远报告为空、无限量接收"的
 * 单概念槽位语义（还额外叠加了黑名单与过滤图标两层判断），跟 Create 默认
 * 解包处理器按多槽位仓库模型设计的模拟算法不兼容，需要绕开默认处理器，
 * 改成直接对 {@code itemHandler} 逐个物品调用 {@code insertItem} 判断。
 */
public enum BrassScrapBucketUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side,
                          List<ItemStack> items, @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BrassScrapBucketBlockEntity bucket)) {
            return false;
        }
        IItemHandler handler = bucket.itemHandler;
        for (ItemStack item : items) {
            if (item.isEmpty()) {
                continue;
            }
            if (!handler.insertItem(0, item, true).isEmpty()) {
                return false;
            }
        }
        if (simulate) {
            return true;
        }
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (item.isEmpty()) {
                continue;
            }
            handler.insertItem(0, item, false);
            items.set(i, ItemStack.EMPTY);
        }
        return true;
    }
}