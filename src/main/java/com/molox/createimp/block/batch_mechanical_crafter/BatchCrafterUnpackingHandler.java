package com.molox.createimp.block.batch_mechanical_crafter;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public enum BatchCrafterUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side,
                          List<ItemStack> items, @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        if (orderContext != null && !orderContext.orderedCrafts().isEmpty()) {
            return simulate;
        }
        return DEFAULT.unpack(level, pos, state, side, items, null, simulate);
    }

    public static void processBatchPackage(PackagerBlockEntity packager,
                                           BatchMechanicalCrafterBlockEntity crafter) {
        ItemStack box = packager.heldBox;
        if (box.isEmpty()) return;

        PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(box);
        if (orderContext == null || orderContext.orderedCrafts().isEmpty()) return;

        Level level = crafter.getLevel();
        if (level == null || level.isClientSide()) return;

        BatchConnectedInputHandler.ConnectedInput input = crafter.getInput();
        List<BatchMechanicalCrafterBlockEntity.Inventory> inventories =
                input.getInventories(level, crafter.getBlockPos());
        if (inventories.isEmpty()) return;

        int incomingOrderId = PackageItem.getOrderId(box);
        List<PackageOrderWithCrafts.CraftingEntry> allEntries = orderContext.orderedCrafts();

        // orderId不匹配时，新订单覆盖旧进度，从头开始
        if (incomingOrderId != crafter.packageProgressOrderId) {
            crafter.packageProgressOrderId = incomingOrderId;
            crafter.packageProgressEntryIndex = 0;
            crafter.packageProgressEntryRemaining = -1;
            crafter.setChanged();
        }

        int entryIndex = crafter.packageProgressEntryIndex;

        // entryIndex超出范围说明所有entry已完成（不应该发生，但安全起见）
        if (entryIndex >= allEntries.size()) {
            crafter.packageProgressOrderId = -1;
            crafter.packageProgressEntryIndex = 0;
            crafter.packageProgressEntryRemaining = -1;
            crafter.setChanged();
            return;
        }

        PackageOrderWithCrafts.CraftingEntry currentEntry = allEntries.get(entryIndex);
        List<BigItemStack> pattern = currentEntry.pattern().stacks();

        // 初始化当前entry剩余批次
        int remaining = crafter.packageProgressEntryRemaining < 0
                ? currentEntry.count()
                : crafter.packageProgressEntryRemaining;

        // 检查合成器槽位是否全空
        for (int i = 0; i < pattern.size(); i++) {
            if (i >= inventories.size()) return;
            if (pattern.get(i).stack.isEmpty()) continue;
            if (!inventories.get(i).getStackInSlot(0).isEmpty()) return;
        }

        // 取出包裹内容
        ItemStackHandler contents = PackageItem.getContents(box);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack s = contents.getStackInSlot(i);
            if (!s.isEmpty()) items.add(s.copy());
        }

        // 计算本次批次数：受最小堆叠上限、剩余批次数、包裹里的实际材料量三重约束
        int maxBatches = remaining;
        for (BigItemStack slot : pattern) {
            if (slot.stack.isEmpty()) continue;
            int maxByStack = slot.stack.getMaxStackSize() / Math.max(slot.count, 1);
            maxBatches = Math.min(maxBatches, maxByStack);
        }
        if (maxBatches <= 0) maxBatches = 1;

        for (BigItemStack slot : pattern) {
            if (slot.stack.isEmpty()) continue;
            int available = 0;
            for (ItemStack item : items) {
                if (ItemStack.isSameItemSameComponents(item, slot.stack))
                    available += item.getCount();
            }
            maxBatches = Math.min(maxBatches, available / Math.max(slot.count, 1));
        }
        // 材料不够，包裹安全卡在打包机里不动
        if (maxBatches <= 0) return;

        // 分配材料到合成器槽位
        int totalInserted = 0;
        for (int i = 0; i < pattern.size(); i++) {
            if (i >= inventories.size()) break;
            BigItemStack slot = pattern.get(i);
            if (slot.stack.isEmpty()) continue;

            int needed = slot.count * maxBatches;
            int rem = needed;
            for (ItemStack item : items) {
                if (rem <= 0) break;
                if (!ItemStack.isSameItemSameComponents(item, slot.stack)) continue;
                int take = Math.min(rem, item.getCount());
                ItemStack leftover = inventories.get(i).insertItem(0, item.copyWithCount(take), false);
                int inserted = take - leftover.getCount();
                item.shrink(inserted);
                rem -= inserted;
                totalInserted += inserted;
            }
        }

        // 如果实际没有材料被放进去（合成器phase不是IDLE），不更新进度，包裹留着等待
        if (totalInserted == 0) return;

        crafter.checkCompletedRecipe(true);

        // 更新合成器上的进度
        int newRemaining = remaining - maxBatches;
        if (newRemaining <= 0) {
            // 当前entry完成，推进到下一个
            crafter.packageProgressEntryIndex = entryIndex + 1;
            crafter.packageProgressEntryRemaining = -1;
        } else {
            crafter.packageProgressEntryRemaining = newRemaining;
        }
        crafter.setChanged();

        // 把剩余物品写回包裹内容
        ItemStackHandler newContents = new ItemStackHandler(9);
        for (ItemStack item : items) {
            if (!item.isEmpty())
                ItemHandlerHelper.insertItemStacked(newContents, item, false);
        }
        box.set(AllDataComponents.PACKAGE_CONTENTS,
                ItemHelper.containerContentsFromHandler(newContents));

        // 检查包裹内容是否清空
        boolean contentsEmpty = true;
        for (int i = 0; i < newContents.getSlots(); i++) {
            if (!newContents.getStackInSlot(i).isEmpty()) {
                contentsEmpty = false;
                break;
            }
        }

        if (contentsEmpty) {
            packager.previouslyUnwrapped = box;
            packager.heldBox = ItemStack.EMPTY;
            packager.animationInward = true;
            packager.animationTicks = 20;
            packager.notifyUpdate();
        } else {
            packager.notifyUpdate();
        }
    }
}