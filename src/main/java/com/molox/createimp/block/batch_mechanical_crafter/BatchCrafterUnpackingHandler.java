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
        // 包裹的持有状态以heldBox为唯一真实来源，不读previouslyUnwrapped
        // (该字段只在动画播放瞬间有意义，animationTicks==0时每tick都会被原版逻辑清空)。
        ItemStack box = packager.heldBox;
        if (box.isEmpty()) return;
        if (packager.animationTicks > 0) return;

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

        if (incomingOrderId != crafter.packageProgressOrderId) {
            crafter.packageProgressOrderId = incomingOrderId;
            crafter.packageProgressEntryIndex = 0;
            crafter.packageProgressEntryRemaining = -1;
            crafter.setChanged();
        }

        int entryIndex = crafter.packageProgressEntryIndex;

        if (entryIndex >= allEntries.size()) {
            crafter.packageProgressOrderId = -1;
            crafter.packageProgressEntryIndex = 0;
            crafter.packageProgressEntryRemaining = -1;
            crafter.setChanged();
            return;
        }

        PackageOrderWithCrafts.CraftingEntry currentEntry = allEntries.get(entryIndex);
        List<BigItemStack> pattern = currentEntry.pattern().stacks();

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

        int maxBatches = remaining;
        for (BigItemStack slot : pattern) {
            if (slot.stack.isEmpty() || slot.count <= 0) continue;
            int maxByStack = slot.stack.getMaxStackSize() / Math.max(slot.count, 1);
            maxBatches = Math.min(maxBatches, maxByStack);
        }
        if (maxBatches <= 0) maxBatches = 1;

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
        if (maxBatches <= 0) return;

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

            if (!leftover.isEmpty()) {
                items.add(leftover);
            }
        }

        if (totalInserted == 0) return;

        crafter.checkCompletedRecipe(true);

        int newRemaining = remaining - maxBatches;
        if (newRemaining <= 0) {
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

        boolean contentsEmpty = true;
        for (int i = 0; i < newContents.getSlots(); i++) {
            if (!newContents.getStackInSlot(i).isEmpty()) {
                contentsEmpty = false;
                break;
            }
        }

        if (contentsEmpty) {
            // 包裹内容已被吃空：heldBox清空(交还容量给PackagerItemHandler.insertItem的检查)，
            // 同时借previouslyUnwrapped+animationTicks播放一次性的"消失"动画。
            packager.previouslyUnwrapped = box;
            packager.heldBox = ItemStack.EMPTY;
            packager.animationInward = true;
            packager.animationTicks = 20;
            packager.notifyUpdate();
        } else {
            // 包裹内容还没吃空：更新后的内容写回heldBox长期持有，
            // 不触碰previouslyUnwrapped/animationTicks，等待下次合成器槽位腾空时
            // 由tryProcessPackagerBox重新触发处理。
            packager.heldBox = box;
            packager.notifyUpdate();
        }
    }
}