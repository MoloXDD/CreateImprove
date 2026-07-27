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
import java.util.HashMap;
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

        // 整条连接链上，任意一个格子只要phase不是IDLE，或者phase虽然是IDLE
        // 但格子里还留有尚未开始组装的物品（两者不等价：phase变回IDLE后，
        // 格子被玩家手动放入物品、下一次tick真正触发checkCompletedRecipe之前
        // 的这一小段时间里，phase是IDLE但格子并不空——tick()里"phase==IDLE
        // 且craftingItemPresent()为真时才调用checkCompletedRecipe(false)"这
        // 一段逻辑本身就是这个状态真实存在的证据），都说明链上还没有完全
        // 空闲下来，此时绝不能开始分配这次包裹的材料——否则会出现"链条被
        // ejectWholeGrid()逐格重置为IDLE期间，只有刚重置的那一个格子空闲、
        // 其余仍在WAITING/CRAFTING"的半吊子状态：本该整条链一起收到材料，
        // 结果只有那一个格子插入成功，紧接着的checkCompletedRecipe(true)又
        // 会把其余8个"其实没轮到处理、只是槽位恰好也是空气"的格子一并强行
        // begin()，导致合成结果完全错乱；也可能出现新旧物品混入同一次组装
        // 导致合成结果错乱的情况。因此这里必须要求链上全部格子都真正处于
        // "phase为IDLE且格子本身为空"这个状态才允许处理，包裹原样留在打包机
        // 内等待，直到整条链真正全部闲下来为止。
        for (BatchMechanicalCrafterBlockEntity.Inventory inv : inventories) {
            if (inv.getBlockEntity().phase != BatchMechanicalCrafterBlockEntity.Phase.IDLE) return;
            if (!inv.getItem(0).isEmpty()) return;
        }
        // 配方的pattern必须是一个n×n正方形（n=max(配方宽度,配方高度)，由
        // TemplatePanelScreen在构造阶段按左上角对齐补齐空位得到），列表长度
        // 本身就能反推出n，不需要额外传递宽度信息。旧的仅靠九宫格3×3合成表
        // 产生的pattern长度恒为9，同样是完全平方数，天然兼容，不受影响。
        int patternSide = (int) Math.round(Math.sqrt(pattern.size()));
        if (patternSide <= 0 || patternSide * patternSide != pattern.size()) {
            // pattern本身不是一个正方形，理论上不会出现（除非数据来源异常），
            // 为安全起见直接放弃，包裹原样留在打包机里，不做任何处理。
            return;
        }
        // 不要求整条连接链条本身恰好是一个正方形——链条可能比配方大得多、
        // 形状也可能不规整（比如缺角），只要链条里存在至少一块边长为
        // patternSide、内部完全填满、不缺格子的正方形区域就足够用来放这个
        // 配方。存在多块候选区域，或者可用区域本身比配方大时，优先选择
        // 最靠左上角的那一块（先比较行、再比较列，行列都从整条链条的左上角
        // 算起）。找不到任何一块满足条件的区域时，包裹原样留在打包机里，
        // 不做任何处理。
        List<BatchMechanicalCrafterBlockEntity.Inventory> placement =
                findSquarePlacement(crafter, inventories, patternSide);
        if (placement == null) {
            return;
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
        // placement已经是findSquarePlacement按行优先顺序选好的n×n子区域，
        // 下标直接与pattern一一对应，不需要再换算行列号。
        int totalInserted = 0;
        for (int i = 0; i < pattern.size(); i++) {
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

            BatchMechanicalCrafterBlockEntity.Inventory targetInv = placement.get(i);
            ItemStack leftover = targetInv.insertItem(0, combined, false);
            int inserted = collected - leftover.getCount();
            totalInserted += inserted;

            if (!leftover.isEmpty()) {
                items.add(leftover);
            }
        }

        // 如果实际没有材料被放进去（合成器phase不是IDLE），包裹留着等待，不做后续处理
        if (totalInserted == 0) return;

        // 触发合成检测必须从placement区域内、真正持有了材料的某一台合成器
        // 发起，不能用packager相邻的crafter参数——findSquarePlacement允许
        // 选中的区域落在链条里的任意位置，crafter自己完全可能落在这次选中
        // 的区域之外、格子本身仍是空的，从一个空格子发起检测会导致检测不到
        // 真正持有材料的那些格子，合成永远不会自动开始。
        BatchMechanicalCrafterBlockEntity triggerCrafter = placement.get(0).getBlockEntity();
        if (triggerCrafter.getSpeed() == 0f) {
            // checkCompletedRecipe(true)内部会因为getSpeed()==0直接返回、什么都不做，
            // 且不会留下任何"材料已就绪、等动力恢复后再试一次"的记录——如果这里不主动
            // 记录，之后即便动力恢复，也没有任何代码会重新尝试启动这次合成，只能靠玩家
            // 手动通一次红石信号。这里记录pendingForcedStart，交给onSpeedChanged在
            // 动力真正恢复的那一刻补一次checkCompletedRecipe(true)。
            triggerCrafter.pendingForcedStart = true;
            triggerCrafter.setChanged();
        } else {
            triggerCrafter.checkCompletedRecipe(true);
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

    /**
     * 在整条连接链条里寻找一块边长为 n、内部完全填满（不缺格子）的正方形
     * 区域，用于放置这份配方；找到时按行优先顺序（与pattern的下标顺序
     * 完全对应）返回这 n×n 台合成器，找不到时返回 null。
     * <p>
     * 存在多块候选区域、或者链条本身比 n 大出不止一圈时，优先选择最靠
     * 左上角的一块——先比较区域左上角所在的行，行相同再比较列，行列的
     * 参照系是整条链条自己的左上角，不是世界坐标原点。
     * <p>
     * 行的方向取自世界坐标 Y（Y 越大越靠上，与 {@link BatchConnectedInputHandler.ConnectedInput#getInventories}
     * 排序时"从上到下"的约定一致）；列的方向取自合成器朝向对应的
     * {@code compareAxis}（facing顺时针方向的那根水平轴），同样沿用
     * {@code getInventories} 排序时"从左到右"的符号约定，保证这里认定的
     * "左上角"和玩家在配方界面里看到的左上角是同一个方向。
     */
    private static List<BatchMechanicalCrafterBlockEntity.Inventory> findSquarePlacement(
            BatchMechanicalCrafterBlockEntity crafter,
            List<BatchMechanicalCrafterBlockEntity.Inventory> inventories, int n) {
        Direction facing = Direction.SOUTH;
        BlockState blockState = crafter.getBlockState();
        if (blockState.hasProperty(BatchMechanicalCrafterBlock.HORIZONTAL_FACING)) {
            facing = blockState.getValue(BatchMechanicalCrafterBlock.HORIZONTAL_FACING);
        }
        Direction.AxisDirection axisDirection = facing.getAxisDirection();
        Direction.Axis compareAxis = facing.getClockWise().getAxis();
        int modifier = axisDirection.getStep() * (compareAxis == Direction.Axis.Z ? -1 : 1);

        int maxY = Integer.MIN_VALUE;
        int minColRaw = Integer.MAX_VALUE;
        Map<BatchMechanicalCrafterBlockEntity.Inventory, int[]> rawCoords = new LinkedHashMap<>();
        for (BatchMechanicalCrafterBlockEntity.Inventory inv : inventories) {
            BlockPos p = inv.getBlockEntity().getBlockPos();
            int y = p.getY();
            int colRaw = modifier * compareAxis.choose(p.getX(), p.getY(), p.getZ());
            rawCoords.put(inv, new int[]{y, colRaw});
            maxY = Math.max(maxY, y);
            minColRaw = Math.min(minColRaw, colRaw);
        }

        Map<Long, BatchMechanicalCrafterBlockEntity.Inventory> grid = new HashMap<>();
        int maxRow = 0, maxCol = 0;
        for (Map.Entry<BatchMechanicalCrafterBlockEntity.Inventory, int[]> e : rawCoords.entrySet()) {
            int row = maxY - e.getValue()[0];
            int col = e.getValue()[1] - minColRaw;
            grid.put(gridKey(row, col), e.getKey());
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);
        }

        for (int r0 = 0; r0 + n - 1 <= maxRow; r0++) {
            for (int c0 = 0; c0 + n - 1 <= maxCol; c0++) {
                List<BatchMechanicalCrafterBlockEntity.Inventory> candidate = new ArrayList<>(n * n);
                boolean valid = true;
                search:
                for (int row = 0; row < n; row++) {
                    for (int col = 0; col < n; col++) {
                        BatchMechanicalCrafterBlockEntity.Inventory inv = grid.get(gridKey(r0 + row, c0 + col));
                        if (inv == null) {
                            valid = false;
                            break search;
                        }
                        candidate.add(inv);
                    }
                }
                if (valid) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static long gridKey(int row, int col) {
        return ((long) row << 32) ^ (col & 0xffffffffL);
    }
}