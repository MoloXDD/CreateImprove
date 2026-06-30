package com.molox.createimp.block.batch_repackager;

import com.molox.createimp.CreateImp;
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

        List<BigItemStack> outputPackages = new ArrayList<>();

        // 跨entry共享、可变的"剩余库存"视图，从summary初始化，每打出一批就真实扣减，
        // 确保不同entry共用同一种材料时不会重复计算同一份库存。
        Map<ItemStack, Integer> sharedRemainingStock = new LinkedHashMap<>();

        for (PackageOrderWithCrafts.CraftingEntry entry : entries) {
            List<BigItemStack> mergedPattern = mergePattern(entry.pattern().stacks());
            if (mergedPattern.isEmpty()) continue;

            // 该entry的请求数量上限：尊重订单声明的count()。
            // 实际可分配批次 = min(请求数量, 当前共享库存能凑出的最大批次)。
            int requestedBatches = entry.count();

            int maxBatchesByStock = Integer.MAX_VALUE;
            for (BigItemStack material : mergedPattern) {
                ItemStack sharedKey = findOrInitSharedKey(sharedRemainingStock, summary, material.stack);
                int available = sharedRemainingStock.get(sharedKey);
                int perBatch = Math.max(material.count, 1);
                maxBatchesByStock = Math.min(maxBatchesByStock, available / perBatch);
            }
            if (maxBatchesByStock == Integer.MAX_VALUE) maxBatchesByStock = 0;

            int totalBatchesToAllocate = Math.min(requestedBatches, maxBatchesByStock);

            CreateImp.LOGGER.info("[BatchRepackager] entry pattern={} requestedBatches={} maxBatchesByStock={} totalBatchesToAllocate={}",
                    describePattern(mergedPattern), requestedBatches, maxBatchesByStock, totalBatchesToAllocate);

            if (totalBatchesToAllocate <= 0) continue;

            // 从共享库存里为这个entry划出"totalBatchesToAllocate批"对应的材料总量，
            // 切分成多个包裹（受9槽容量限制），每个包裹携带的批次数据实反映该包裹实际装载量。
            int batchesLeftToPack = totalBatchesToAllocate;
            while (batchesLeftToPack > 0) {
                int batchesForThisPackage = computeMaxBatchesForSlots(mergedPattern, batchesLeftToPack, SLOT_COUNT);
                if (batchesForThisPackage <= 0) {
                    // 连一个批次都装不进9槽（理论上不会发生，除非单批材料本身就超过9槽容量），
                    // 为避免死循环，直接break。
                    break;
                }

                ItemStackHandler target = new ItemStackHandler(SLOT_COUNT);
                int slotCursor = 0;
                for (BigItemStack material : mergedPattern) {
                    if (material.stack.isEmpty() || material.count <= 0) continue;
                    int amount = material.count * batchesForThisPackage;
                    placeIntoHandler(target, slotCursor, material.stack, amount);
                    slotCursor += slotsForAmount(amount, material.stack.getMaxStackSize());

                    ItemStack sharedKey = findOrInitSharedKey(sharedRemainingStock, summary, material.stack);
                    sharedRemainingStock.put(sharedKey, sharedRemainingStock.get(sharedKey) - amount);
                }

                PackageOrderWithCrafts.CraftingEntry singleEntry =
                        new PackageOrderWithCrafts.CraftingEntry(entry.pattern(), batchesForThisPackage);
                PackageOrderWithCrafts packageOrderContext = new PackageOrderWithCrafts(
                        new PackageOrder(List.of()), List.of(singleEntry));

                ItemStack box = PackageItem.containing(target);
                PackageItem.addAddress(box, address);
                PackageItem.setOrder(box, orderId, 0, true, 0, true, packageOrderContext);
                outputPackages.add(new BigItemStack(box, 1));

                CreateImp.LOGGER.info("[BatchRepackager] package #{} built: declaredBatches={} contents={} sharedStockAfter={}",
                        outputPackages.size(), batchesForThisPackage, describeHandler(target),
                        describeAvailable(sharedRemainingStock));

                batchesLeftToPack -= batchesForThisPackage;
            }
        }

        // 所有entry处理完毕后，把共享库存里仍有剩余的材料作为余料打包送出，
        // 不携带orderContext，走原版普通包裹流程（不会触发任何合成检查）。
        List<BigItemStack> leftoverPackages = packLeftovers(sharedRemainingStock, address, orderId);
        if (!leftoverPackages.isEmpty()) {
            CreateImp.LOGGER.info("[BatchRepackager] {} leftover package(s) built from remaining stock", leftoverPackages.size());
        }
        outputPackages.addAll(leftoverPackages);

        return outputPackages;
    }

    /**
     * 处理没有配方信息的普通分片订单：直接把summary中的所有材料按物品种类
     * 紧凑打包成新包裹输出，不涉及任何配方逻辑，复用packLeftovers的紧凑装填算法。
     */
    public List<BigItemStack> repackPlainMaterials(InventorySummary summary, String address, int orderId) {
        Map<ItemStack, Integer> stock = new LinkedHashMap<>();
        for (BigItemStack s : summary.getStacks()) {
            if (s.stack.isEmpty() || s.count <= 0) continue;
            ItemStack key = null;
            for (ItemStack existingKey : stock.keySet()) {
                if (ItemStack.isSameItemSameComponents(existingKey, s.stack)) {
                    key = existingKey;
                    break;
                }
            }
            if (key == null) {
                stock.put(s.stack, s.count);
            } else {
                stock.put(key, stock.get(key) + s.count);
            }
        }
        return packLeftovers(stock, address, orderId);
    }

    private List<BigItemStack> packLeftovers(Map<ItemStack, Integer> sharedRemainingStock, String address, int orderId) {
        List<BigItemStack> leftoverPackages = new ArrayList<>();
        List<Map.Entry<ItemStack, Integer>> nonEmpty = new ArrayList<>();
        for (Map.Entry<ItemStack, Integer> e : sharedRemainingStock.entrySet()) {
            if (e.getValue() > 0) nonEmpty.add(e);
        }
        if (nonEmpty.isEmpty()) return leftoverPackages;

        int index = 0;
        while (index < nonEmpty.size()) {
            ItemStackHandler target = new ItemStackHandler(SLOT_COUNT);
            int slotCursor = 0;
            while (index < nonEmpty.size() && slotCursor < SLOT_COUNT) {
                Map.Entry<ItemStack, Integer> e = nonEmpty.get(index);
                int amount = e.getValue();
                if (amount <= 0) {
                    index++;
                    continue;
                }
                int slotsAvailable = SLOT_COUNT - slotCursor;
                int maxStack = e.getKey().getMaxStackSize();
                int slotsNeeded = slotsForAmount(amount, maxStack);
                int amountToPlace;
                if (slotsNeeded <= slotsAvailable) {
                    amountToPlace = amount;
                } else {
                    amountToPlace = slotsAvailable * maxStack;
                }
                placeIntoHandler(target, slotCursor, e.getKey(), amountToPlace);
                slotCursor += slotsForAmount(amountToPlace, maxStack);
                int newAmount = amount - amountToPlace;
                e.setValue(newAmount);
                if (newAmount <= 0) index++;
            }
            if (slotCursor == 0) break;

            ItemStack box = PackageItem.containing(target);
            PackageItem.addAddress(box, address);
            PackageItem.setOrder(box, orderId, 0, true, 0, true, null);
            leftoverPackages.add(new BigItemStack(box, 1));
        }
        return leftoverPackages;
    }

    private ItemStack findOrInitSharedKey(Map<ItemStack, Integer> sharedStock, InventorySummary summary, ItemStack item) {
        for (ItemStack key : sharedStock.keySet()) {
            if (ItemStack.isSameItemSameComponents(key, item)) {
                return key;
            }
        }
        int initial = summary.getCountOf(item);
        sharedStock.put(item, initial);
        return item;
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

    /**
     * 计算在availableSlots槽位限制下，这一包最多能装下多少个完整批次，
     * 不超过batchesUpperBound（该entry本次还剩多少批待分配）。
     */
    private int computeMaxBatchesForSlots(List<BigItemStack> mergedPattern, int batchesUpperBound, int availableSlots) {
        if (batchesUpperBound <= 0) return 0;
        int low = 0;
        int high = batchesUpperBound;
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

    private String describePattern(List<BigItemStack> pattern) {
        StringBuilder sb = new StringBuilder();
        for (BigItemStack s : pattern) {
            if (s.stack.isEmpty()) continue;
            sb.append(s.stack.getItem()).append("x").append(s.count).append(" ");
        }
        return sb.toString();
    }

    private String describeAvailable(Map<ItemStack, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ItemStack, Integer> e : map.entrySet()) {
            sb.append(e.getKey().getItem()).append("=").append(e.getValue()).append(" ");
        }
        return sb.toString();
    }

    private String describeHandler(ItemStackHandler handler) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty()) continue;
            sb.append("[").append(i).append("]").append(s.getItem()).append("x").append(s.getCount()).append(" ");
        }
        return sb.toString();
    }
}