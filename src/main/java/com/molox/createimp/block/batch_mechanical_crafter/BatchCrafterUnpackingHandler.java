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

        // 只要合成器链上任意一格的phase不是IDLE（哪怕它自己的槽位因为begin()已经
        // 把物品搬进groupedItems、看起来是空的），就说明链上还在处理上一次的合成，
        // 此时绝不能开始分配这次包裹的材料——否则会出现"链条被ejectWholeGrid()逐格
        // 重置为IDLE期间，只有刚重置的那一个格子空闲、其余仍在WAITING/CRAFTING"的
        // 半吊子状态：本该整条链一起收到材料，结果只有那一个格子插入成功，
        // 紧接着的checkCompletedRecipe(true)又会把其余8个"其实没轮到处理、
        // 只是槽位恰好也是空气"的格子一并强行begin()，导致合成结果完全错乱。
        // 因此这里必须要求链上全部格子的phase都是IDLE才允许处理，包裹原样留在
        // 打包机内等待，直到ejectWholeGrid()彻底跑完、整条链真正全部闲下来为止。
        for (BatchMechanicalCrafterBlockEntity.Inventory inv : inventories) {
            if (inv.getBlockEntity().phase != BatchMechanicalCrafterBlockEntity.Phase.IDLE) return;
        }
        // 配方格数超出了当前合成器链能提供的槽位数，链路配置有问题，同样不处理。
        if (pattern.size() > inventories.size()) return;

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

            BatchMechanicalCrafterBlockEntity.Inventory targetInv = inventories.get(i);
            ItemStack leftover = targetInv.insertItem(0, combined, false);
            int inserted = collected - leftover.getCount();
            totalInserted += inserted;

            if (!leftover.isEmpty()) {
                items.add(leftover);
            }
        }

        // 如果实际没有材料被放进去（合成器phase不是IDLE），包裹留着等待，不做后续处理
        if (totalInserted == 0) return;

        if (crafter.getSpeed() == 0f) {
            // checkCompletedRecipe(true)内部会因为getSpeed()==0直接返回、什么都不做，
            // 且不会留下任何"材料已就绪、等动力恢复后再试一次"的记录——如果这里不主动
            // 记录，之后即便动力恢复，也没有任何代码会重新尝试启动这次合成，只能靠玩家
            // 手动通一次红石信号。这里记录pendingForcedStart，交给onSpeedChanged在
            // 动力真正恢复的那一刻补一次checkCompletedRecipe(true)。
            crafter.pendingForcedStart = true;
            crafter.setChanged();
        } else {
            crafter.checkCompletedRecipe(true);
        }

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