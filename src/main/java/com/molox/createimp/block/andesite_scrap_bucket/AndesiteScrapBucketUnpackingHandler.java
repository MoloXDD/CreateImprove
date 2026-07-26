package com.molox.createimp.block.andesite_scrap_bucket;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 安山废料桶自己的打包机解包处理器——注册进 Create 公开的
 * {@link UnpackingHandler#REGISTRY}，替代打包机默认会使用的
 * {@code DefaultUnpackingHandler}。
 * <p>
 * 【为什么必须单独实现，不能靠默认处理器】反编译确认
 * {@code DefaultUnpackingHandler} 的模拟阶段是按"多槽位、每个槽位装一种
 * 物品"的真实仓库模型设计的：它会把"这一格已经被处理过的第一种物品占用"
 * 这件事记在一个局部变量里，第二种及以后不同的物品类型经过这一格时，因为
 * 类型对不上就被跳过、既没有被判定为放得下也没有被消耗，最终整个包裹因为
 * "还有物品没处理完"而判定解包失败。这个假设对一个真正有限容量的仓库是
 * 对的，但{@link AndesiteScrapBucketBlockEntity#VOID_ITEM_HANDLER}
 * 语义完全不同——它只有一个概念上的槽位、{@code getStackInSlot} 永远返回
 * 空气、对任意未被黑名单拦截的物品都无限量接收，不存在"这一格被占用"这个
 * 概念。默认处理器一旦处理完包裹里第一种物品就会误判这个槽位已经满了，
 * 导致含有两种及以上物品类型的包裹（打包机日常收发的绝大多数包裹）全部
 * 解包失败——这正是打包机此前无法把包裹解到废料桶里的原因。
 * <p>
 * 这里改为直接对 {@code VOID_ITEM_HANDLER} 逐个物品调用
 * {@code insertItem}（跟废料桶自己流体/物品接口一直以来的判断口径完全
 * 一致，只是不再套用默认处理器那套多槽位模拟算法），先用模拟模式确认
 * 包裹里所有物品都能被接收（只要有一种在黑名单里就整体判定失败，包裹
 * 原样保留，不做部分销毁——跟废料桶本身"黑名单物品直接拒收"的语义一致），
 * 确认后才在非模拟模式下真正逐个销毁。
 */
public enum AndesiteScrapBucketUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side,
                          List<ItemStack> items, @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        IItemHandler handler = AndesiteScrapBucketBlockEntity.VOID_ITEM_HANDLER;
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