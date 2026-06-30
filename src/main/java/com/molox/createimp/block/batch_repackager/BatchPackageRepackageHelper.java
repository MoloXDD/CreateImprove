package com.molox.createimp.block.batch_repackager;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BatchPackageRepackageHelper {

    public static final int SLOT_COUNT = 9;

    public List<BigItemStack> repackBasedOnRecipes(InventorySummary summary, PackageOrderWithCrafts order,
                                                   String address, int orderId, RandomSource random) {
        List<PackageOrderWithCrafts.CraftingEntry> entries = order.orderedCrafts();
        if (entries.isEmpty()) {
            return List.of();
        }

        int entryCount = entries.size();
        int[] remainingBatches = new int[entryCount];
        List<List<BigItemStack>> mergedPatterns = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            remainingBatches[i] = entries.get(i).count();
            mergedPatterns.add(mergePattern(entries.get(i).pattern().stacks()));
        }

        List<BigItemStack> outputPackages = new ArrayList<>();

        // 每个包裹只装一个entry的内容，避免合成器侧的entryIndex跨entry混装时出现
        // "entry被标记完成但材料仍遗留在某个混装包裹里未被消费"的问题。
        for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
            List<BigItemStack> mergedPattern = mergedPatterns.get(entryIndex);

            while (remainingBatches[entryIndex] > 0) {
                int maxBatchesBySlots = computeMaxBatchesForSlots(mergedPattern, SLOT_COUNT);
                int batchesToLoad = Math.min(maxBatchesBySlots, remainingBatches[entryIndex]);

                if (batchesToLoad <= 0) break;

                ItemStackHandler target = new ItemStackHandler(SLOT_COUNT);
                int slotCursor = 0;
                for (BigItemStack material : mergedPattern) {
                    if (material.stack.isEmpty() || material.count <= 0) continue;
                    int amount = material.count * batchesToLoad;
                    placeIntoHandler(target, slotCursor, material.stack, amount);
                    slotCursor += slotsForAmount(amount, material.stack.getMaxStackSize());
                }

                // count字段填"处理这一包之前，该entry全局还剩多少批"（扣除前的值），
                // 而不是"这一包自己装了多少批"。批量合成器消费端依赖这个值
                // 跨包裹累计扣减，只有归零才真正判定entry完成、推进到下一个entry。
                int globalRemainingBeforeThisPackage = remainingBatches[entryIndex];
                remainingBatches[entryIndex] -= batchesToLoad;

                List<PackageOrderWithCrafts.CraftingEntry> packageEntries = new ArrayList<>(entryCount);
                for (int i = 0; i < entryCount; i++) {
                    int countForThisPackage = (i == entryIndex) ? globalRemainingBeforeThisPackage : 0;
                    packageEntries.add(new PackageOrderWithCrafts.CraftingEntry(
                            entries.get(i).pattern(), countForThisPackage));
                }
                PackageOrderWithCrafts packageOrderContext = new PackageOrderWithCrafts(
                        new PackageOrder(List.of()), packageEntries);

                ItemStack box = PackageItem.containing(target);
                PackageItem.addAddress(box, address);
                PackageItem.setOrder(box, orderId, 0, true, 0, true, packageOrderContext);
                outputPackages.add(new BigItemStack(box, 1));
            }
        }

        return outputPackages;
    }

    private List<BigItemStack> mergePattern(List<BigItemStack> pattern) {
        Map<ItemStack, BigItemStack> merged = new LinkedHashMap<>();
        for (BigItemStack material : pattern) {
            if (material.stack.isEmpty() || material.count <= 0) continue;
            ItemStack key = null;
            for (ItemStack existingKey : merged.keySet()) {
                if (ItemStack.isSameItemSameComponents(existingKey, material.stack)) {
                    key = existingKey;
                    break;
                }
            }
            if (key == null) {
                merged.put(material.stack, new BigItemStack(material.stack, material.count));
            } else {
                merged.get(key).count += material.count;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private int computeMaxBatchesForSlots(List<BigItemStack> mergedPattern, int availableSlots) {
        int low = 0;
        int high = Integer.MAX_VALUE / 2;
        boolean hasMaterial = false;
        for (BigItemStack material : mergedPattern) {
            if (!material.stack.isEmpty() && material.count > 0) {
                hasMaterial = true;
            }
        }
        if (!hasMaterial) return 0;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (computeSlotsUsed(mergedPattern, mid) <= availableSlots) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private int computeSlotsUsed(List<BigItemStack> mergedPattern, int batches) {
        if (batches <= 0) return 0;
        int slots = 0;
        for (BigItemStack material : mergedPattern) {
            if (material.stack.isEmpty() || material.count <= 0) continue;
            long amount = (long) material.count * (long) batches;
            int maxStack = material.stack.getMaxStackSize();
            slots += slotsForAmount(amount, maxStack);
        }
        return slots;
    }

    private int slotsForAmount(long amount, int maxStack) {
        if (amount <= 0) return 0;
        return (int) ((amount + maxStack - 1) / maxStack);
    }

    private void placeIntoHandler(ItemStackHandler target, int startSlot, ItemStack item, int amount) {
        int maxStack = item.getMaxStackSize();
        int remaining = amount;
        int slot = startSlot;
        while (remaining > 0 && slot < SLOT_COUNT) {
            int take = Math.min(remaining, maxStack);
            target.setStackInSlot(slot, item.copyWithCount(take));
            remaining -= take;
            slot++;
        }
    }
}