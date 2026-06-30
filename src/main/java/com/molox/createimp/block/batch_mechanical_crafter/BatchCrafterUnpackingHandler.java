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

    /**
     * 处理一个带合成表的包裹。每个包裹只携带单一配方（orderedCrafts长度恒为1），
     * 不再需要在合成器上记录任何跨包裹的订单/entry/批次进度——包裹自身携带的材料量
     * 就是全部需要处理的内容，处理多少算多少，材料不够凑一批就让包裹原地卡住。
     */
    public static void processBatchPackage(PackagerBlockEntity packager,
                                           BatchMechanicalCrafterBlockEntity crafter) {
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

        // 包裹只携带一个配方，直接取第一个（也是唯一一个）entry。
        PackageOrderWithCrafts.CraftingEntry entry = orderContext.orderedCrafts().get(0);
        List<BigItemStack> pattern = entry.pattern().stacks();

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

        // 计算本次批次数：受最小堆叠上限、包裹里实际材料量两重约束。
        // 堆叠上限约束必须逐格独立计算（每个格子是独立槽位，各自上限64）。
        int maxBatches = Integer.MAX_VALUE;
        for (BigItemStack slot : pattern) {
            if (slot.stack.isEmpty() || slot.count <= 0) continue;
            int maxByStack = slot.stack.getMaxStackSize() / Math.max(slot.count, 1);
            maxBatches = Math.min(maxBatches, maxByStack);
        }
        if (maxBatches == Integer.MAX_VALUE) maxBatches = 1;
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
        // 材料不够凑一批，包裹安全卡在打包机里不动，等待玩家处理
        if (maxBatches <= 0) return;

        // 分配材料到合成器槽位。
        // Inventory.insertItem 单槽一旦非空即整体拒绝任何后续插入（即使是同种物品），
        // 因此每个格子所需的数量必须先从items列表里凑齐成一份，再对该槽位调用一次insertItem。
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

        // 如果实际没有材料被放进去（合成器phase不是IDLE），包裹留着等待，不做后续处理
        if (totalInserted == 0) return;

        crafter.checkCompletedRecipe(true);

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
            // 包裹内容已被吃空：heldBox清空，播放一次性的"消失"动画。
            packager.previouslyUnwrapped = box;
            packager.heldBox = ItemStack.EMPTY;
            packager.animationInward = true;
            packager.animationTicks = 20;
            packager.notifyUpdate();
        } else {
            // 包裹内容还没吃空：更新后的内容写回heldBox长期持有，
            // 等待下次合成器槽位腾空时由tryProcessPackagerBox重新触发处理。
            packager.heldBox = box;
            packager.notifyUpdate();
        }
    }
}