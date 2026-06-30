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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        // 初始化当前entry剩余批次。只有当packageProgressEntryRemaining处于初始状态(-1)时，
        // 才信任包裹自带的currentEntry.count()作为该entry的全局剩余批次；
        // 否则使用合成器自己跨包裹记忆的剩余值，确保同一entry分散在多个包裹时不会被提前判定为完成。
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

        // pattern中同一种物品可能出现在配方的多个格子（例如4铁锭围一圈合成铁块）。
        // 先按物品类型合并，得到每种材料"单批总需求"，用于库存量约束的计算。
        Map<ItemStack, Integer> perBatchNeedByItem = new LinkedHashMap<>();
        for (BigItemStack slot : pattern) {
            if (slot.stack.isEmpty() || slot.count <= 0) continue;
            ItemStack key = null;
            for (ItemStack existingKey : perBatchNeedByItem.keySet()) {
                if (ItemStack.isSameItemSameComponents(existingKey, slot.stack)) {
                    key = existingKey;
                    break;
                }
            }
            if (key == null) {
                perBatchNeedByItem.put(slot.stack, slot.count);
            } else {
                perBatchNeedByItem.put(key, perBatchNeedByItem.get(key) + slot.count);
            }
        }

        // 计算本次批次数：受最小堆叠上限、剩余批次数、包裹里的实际材料量三重约束。
        // 堆叠上限约束必须逐格独立计算（每个格子是独立槽位，各自上限64），
        // 不能用合并后的单批总需求去算，否则会把批次数错误地压低。
        int maxBatches = remaining;
        for (BigItemStack slot : pattern) {
            if (slot.stack.isEmpty() || slot.count <= 0) continue;
            int maxByStack = slot.stack.getMaxStackSize() / Math.max(slot.count, 1);
            maxBatches = Math.min(maxBatches, maxByStack);
        }
        if (maxBatches <= 0) maxBatches = 1;

        // 库存量约束使用合并后的单批总需求，因为库存按物品类型统一计量，不分槽位。
        for (Map.Entry<ItemStack, Integer> e : perBatchNeedByItem.entrySet()) {
            ItemStack material = e.getKey();
            int perBatchNeed = e.getValue();
            int available = 0;
            for (ItemStack item : items) {
                if (ItemStack.isSameItemSameComponents(item, material))
                    available += item.getCount();
            }
            maxBatches = Math.min(maxBatches, available / Math.max(perBatchNeed, 1));
        }
        // 材料不够，包裹安全卡在打包机里不动
        if (maxBatches <= 0) return;

        // 分配材料到合成器槽位。
        // Inventory.insertItem 单槽一旦非空即整体拒绝任何后续插入（即使是同种物品），
        // 因此每个格子所需的数量必须先从items列表里凑齐成一份，再对该槽位调用一次insertItem，
        // 不能对同一个槽位分多次调用insertItem。
        int totalInserted = 0;
        for (int i = 0; i < pattern.size(); i++) {
            if (i >= inventories.size()) break;
            BigItemStack slot = pattern.get(i);
            if (slot.stack.isEmpty()) continue;

            int needed = slot.count * maxBatches;
            int collected = 0;
            ItemStack combined = ItemStack.EMPTY;
            for (ItemStack item : items) {
                if (collected >= needed) break;
                if (item.isEmpty() || !ItemStack.isSameItemSameComponents(item, slot.stack)) continue;
                int take = Math.min(needed - collected, item.getCount());
                if (take <= 0) continue;
                if (combined.isEmpty()) {
                    combined = item.copyWithCount(take);
                } else {
                    combined.grow(take);
                }
                item.shrink(take);
                collected += take;
            }

            if (collected <= 0) continue;

            ItemStack leftover = inventories.get(i).insertItem(0, combined, false);
            int inserted = collected - leftover.getCount();
            totalInserted += inserted;

            // 如果insertItem未能全部接收，把未被接收的部分还给items列表，避免物品凭空消失。
            if (!leftover.isEmpty()) {
                items.add(leftover);
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