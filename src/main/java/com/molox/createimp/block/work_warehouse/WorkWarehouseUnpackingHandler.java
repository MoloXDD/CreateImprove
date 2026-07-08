package com.molox.createimp.block.work_warehouse;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum WorkWarehouseUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side,
                          List<ItemStack> items, @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        if (!(level.getBlockEntity(pos) instanceof WorkWarehouseBlockEntity be)) {
            return false;
        }

        if (!matchesDemandList(be, items)) {
            return false;
        }

        WorkWarehouseItemStackHandler scratch = be.storage.copy();
        for (ItemStack item : items) {
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(scratch, item.copy(), false);
            if (!leftover.isEmpty()) {
                return false;
            }
        }

        if (simulate) {
            return true;
        }

        for (ItemStack item : items) {
            ItemHandlerHelper.insertItemStacked(be.storage, item.copy(), false);
        }
        be.consumeFromDemandList(items);

        return true;
    }

    /**
     * 包裹内的所有物品种类都必须在需求列表里，且各自数量都不能超过需求列表
     * 对应剩余量，才允许解包；只要有一件不满足，整个包裹判定失败（不做部分
     * 解包）。同一物品可能因为来自不同网络而在需求列表里拆成多条记录，这里
     * 按记录顺序累加可用量，与 {@link WorkWarehouseBlockEntity#consumeFromDemandList}
     * 实际扣减时的顺序保持一致，避免校验通过但扣减时对不上的情况。
     */
    private static boolean matchesDemandList(WorkWarehouseBlockEntity be, List<ItemStack> items) {
        List<WorkWarehouseTemplateSnapshot.DemandEntry> demand = be.getDemandList();
        if (demand.isEmpty()) {
            return false;
        }
        int[] remainingPerEntry = new int[demand.size()];
        for (int i = 0; i < demand.size(); i++) {
            remainingPerEntry[i] = demand.get(i).amount();
        }
        for (ItemStack item : items) {
            int toConsume = item.getCount();
            for (int i = 0; i < demand.size() && toConsume > 0; i++) {
                if (!ItemStack.isSameItemSameComponents(demand.get(i).item(), item)) {
                    continue;
                }
                int consumed = Math.min(remainingPerEntry[i], toConsume);
                remainingPerEntry[i] -= consumed;
                toConsume -= consumed;
            }
            if (toConsume > 0) {
                return false;
            }
        }
        return true;
    }
}