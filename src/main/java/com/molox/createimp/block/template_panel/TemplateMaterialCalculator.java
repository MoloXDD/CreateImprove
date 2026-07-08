package com.molox.createimp.block.template_panel;

import com.molox.createimp.item.TemplateOrderTarget;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端专用：根据模板链的配方比例与当前网络库存，递归计算某一批模板下单
 * 请求所需的原料是否足够，并汇总"缺少的材料"与"被现有库存满足的材料"。
 * <p>
 * 请求栏里同时存在的普通物品请求也会一并参与同一套共享库存池的计算——
 * 它们本质上也是"要从这份库存里拿走一部分"，如果不把它们算进去，模板这边
 * 会误以为那部分库存还能用，导致"现有材料"虚高、后续需求列表数据失真。
 * <p>
 * 仅应在服务端调用（依赖 {@link FactoryPanelBehaviour#getLevelInStorage()} /
 * {@link TemplatePanelBehaviour#getLevelInStorage()} / {@link LogisticsManager}
 * 的服务端权威库存查询分支）。
 */
public final class TemplateMaterialCalculator {

    private TemplateMaterialCalculator() {
    }

    /**
     * 一次下单请求：请求的模板本身，以及请求的目标物品数量。
     */
    public record OrderedTemplate(TemplateOrderTarget target, int amount) {
    }

    /**
     * 按原始请求栏顺序排列的一个条目：要么是一个模板，要么是一个普通物品请求。
     * 两者互斥，用 {@link #isTemplate()} 判断具体是哪一种。
     */
    public record RequestEntry(OrderedTemplate template, ItemStack regularItem, int regularAmount) {
        public static RequestEntry ofTemplate(OrderedTemplate template) {
            return new RequestEntry(template, ItemStack.EMPTY, 0);
        }

        public static RequestEntry ofRegular(ItemStack item, int amount) {
            return new RequestEntry(null, item, amount);
        }

        public boolean isTemplate() {
            return template != null;
        }
    }

    /**
     * 计算结果：
     * - canCompleteAll / missing / usedFromStock：合计结果，用于材料窗口展示
     *   （usedFromStock 里既包含模板消耗的库存，也包含普通物品请求占用的库存）。
     * - usedFromStockPerTemplate：与本次请求里出现的模板按顺序一一对应（不含
     *   普通物品条目），每个模板单独消耗掉的现有库存明细，用于分配给对应
     *   工作仓库的"需求列表"。
     */
    public record Result(boolean canCompleteAll, List<BigItemStack> missing, List<BigItemStack> usedFromStock,
                         List<List<BigItemStack>> usedFromStockPerTemplate, boolean anyChainBroken) {
    }

    /**
     * 库存快照的键：同时区分物流网络与物品种类（含数据组件），
     * 不同网络的同名物品不会被当作同一份库存处理。
     */
    private record StockKey(UUID network, ItemStack sample) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StockKey other)) {
                return false;
            }
            return this.network.equals(other.network) && ItemStack.isSameItemSameComponents(this.sample, other.sample);
        }

        @Override
        public int hashCode() {
            return 31 * this.network.hashCode() + this.sample.getItem().hashCode();
        }
    }

    /**
     * 汇总列表（缺少材料 / 现有材料）的键：只按物品种类（含数据组件）区分，
     * 不区分物流网络，因为展示给玩家看的是"这个物品一共缺/一共用了多少"。
     */
    private record ItemOnlyKey(ItemStack sample) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ItemOnlyKey other)) {
                return false;
            }
            return ItemStack.isSameItemSameComponents(this.sample, other.sample);
        }

        @Override
        public int hashCode() {
            return this.sample.getItem().hashCode();
        }
    }

    private static final class Accumulator {
        final ItemStack sample;
        int count;

        Accumulator(ItemStack sample, int count) {
            this.sample = sample.copyWithCount(1);
            this.count = count;
        }
    }

    /**
     * @param level          用于解析模板链上各仪表的世界
     * @param primaryNetwork 普通物品请求所属的物流网络（即发起这次请求的
     *                       仓储管理员自身所在的网络），模板各自的网络仍然
     *                       各自使用自己 TemplatePanelBehaviour/FactoryPanelBehaviour
     *                       上记录的 network 字段，不受这个参数影响。
     * @param entries        按原始请求栏顺序排列的模板与普通物品混合列表
     */
    public static Result calculate(Level level, UUID primaryNetwork, List<RequestEntry> entries) {
        Map<StockKey, Integer> stockCache = new HashMap<>();
        Map<ItemOnlyKey, Accumulator> usedAggTotal = new LinkedHashMap<>();
        Map<ItemOnlyKey, Accumulator> missingAgg = new LinkedHashMap<>();
        List<List<BigItemStack>> perTemplate = new ArrayList<>();

        // 第一阶段：普通物品请求视为"已经确定要实际发出"的消耗，无条件优先从
        // 共享库存池里扣除，不管它在请求栏里排在模板前面还是后面。这样才能
        // 保证模板接下来计算需求时，看到的是"刨掉普通请求之后真正还剩多少"，
        // 不会出现模板抢先把库存预订走、导致普通请求反而落空的情况，也不会
        // 出现两边重复认领同一份库存的情况。
        for (RequestEntry entry : entries) {
            if (entry.isTemplate()) {
                continue;
            }
            ItemStack item = entry.regularItem();
            int amount = entry.regularAmount();
            if (amount <= 0 || item.isEmpty()) {
                continue;
            }
            consumeStock(primaryNetwork, item, amount,
                    () -> queryNetworkStock(primaryNetwork, item),
                    stockCache, usedAggTotal);
        }

        // 第二阶段：按模板在请求栏里的原始相对顺序，依次用第一阶段扣减后
        // 剩余的库存计算每个模板的需求，模板之间依然遵循"先到先得"。
        boolean anyChainBroken = false;
        for (RequestEntry entry : entries) {
            if (!entry.isTemplate()) {
                continue;
            }
            OrderedTemplate order = entry.template();
            Map<ItemOnlyKey, Accumulator> usedAggThis = new LinkedHashMap<>();

            if (order.amount() > 0) {
                TemplateOrderTarget target = order.target();
                TemplatePanelBehaviour root = TemplatePanelBehaviour.at(level, target.position());
                if (root == null || !root.validTemplateChain) {
                    // 链已经失效（仪表被拆除、区块卸载、连接/地址被清空等）。
                    // 保底处理为"这条模板整体视为缺失"，同时标记 anyChainBroken，
                    // 供材料窗口检测到后立即退回仓管界面并清空请求栏。
                    addTo(missingAgg, target.display(), order.amount());
                    anyChainBroken = true;
                } else {
                    int firings = Mth.positiveCeilDiv(order.amount(), Math.max(1, root.recipeOutput));
                    for (TemplatePanelConnection connection : root.targetedBy.values()) {
                        int demand = firings * connection.amount;
                        processUpstream(level, connection.from, demand, stockCache, usedAggThis, missingAgg);
                    }
                }
            }

            for (Accumulator acc : usedAggThis.values()) {
                addTo(usedAggTotal, acc.sample, acc.count);
            }
            perTemplate.add(toList(usedAggThis));
        }

        List<BigItemStack> missing = toList(missingAgg);
        List<BigItemStack> usedFromStock = toList(usedAggTotal);
        return new Result(missing.isEmpty(), missing, usedFromStock, perTemplate, anyChainBroken);
    }

    private static int queryNetworkStock(UUID network, ItemStack item) {
        return LogisticsManager.getSummaryOfNetwork(network, false).getCountOf(item);
    }

    private static void processUpstream(Level level, TemplatePanelPosition pos, int demand,
                                        Map<StockKey, Integer> stockCache,
                                        Map<ItemOnlyKey, Accumulator> usedAgg,
                                        Map<ItemOnlyKey, Accumulator> missingAgg) {
        if (demand <= 0) {
            return;
        }
        if (!level.isLoaded(pos.pos())) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos.pos());
        if (be instanceof TemplatePanelBlockEntity tpbe) {
            TemplatePanelBehaviour node = tpbe.panels.get(pos.slot());
            if (node == null || !node.isActive() || node.getFilter().isEmpty()
                    || node.recipeAddress == null || node.recipeAddress.isEmpty()) {
                return;
            }
            int deficit = consumeStock(node.network, node.getFilter(), demand, () -> node.getLevelInStorage(),
                    stockCache, usedAgg);
            if (deficit <= 0) {
                return;
            }
            if (node.targetedBy.isEmpty()) {
                addTo(missingAgg, node.getFilter(), deficit);
                return;
            }
            int firings = Mth.positiveCeilDiv(deficit, Math.max(1, node.recipeOutput));
            for (TemplatePanelConnection connection : node.targetedBy.values()) {
                int nextDemand = firings * connection.amount;
                processUpstream(level, connection.from, nextDemand, stockCache, usedAgg, missingAgg);
            }
        } else if (be instanceof FactoryPanelBlockEntity fpbe) {
            FactoryPanelBlock.PanelSlot vanillaSlot = FactoryPanelBlock.PanelSlot.valueOf(pos.slot().name());
            FactoryPanelBehaviour node = fpbe.panels.get(vanillaSlot);
            if (node == null || !node.isActive() || node.getFilter().isEmpty()) {
                return;
            }
            int deficit = consumeStock(node.network, node.getFilter(), demand, node::getLevelInStorage,
                    stockCache, usedAgg);
            if (deficit > 0) {
                addTo(missingAgg, node.getFilter(), deficit);
            }
        }
    }

    /**
     * 用共享的库存快照缓存尝试满足一份需求，返回仍未满足的部分（deficit）。
     * 首次遇到某个 (网络, 物品) 组合时才会真正查询网络库存，之后复用缓存里递减后的余量。
     * 注意：stockCache 在整个 calculate() 调用范围内共享、按请求栏原始顺序递减，
     * 模板和普通物品请求都从同一份缓存里扣减，谁在前面谁先拿。
     */
    private static int consumeStock(UUID network, ItemStack filter, int demand,
                                    java.util.function.IntSupplier freshValueSupplier,
                                    Map<StockKey, Integer> stockCache,
                                    Map<ItemOnlyKey, Accumulator> usedAgg) {
        StockKey key = new StockKey(network, filter);
        Integer cached = stockCache.get(key);
        int available = cached != null ? cached : freshValueSupplier.getAsInt();
        int satisfied = Math.min(demand, available);
        stockCache.put(key, available - satisfied);
        if (satisfied > 0) {
            addTo(usedAgg, filter, satisfied);
        }
        return demand - satisfied;
    }

    private static void addTo(Map<ItemOnlyKey, Accumulator> map, ItemStack stack, int amount) {
        if (amount <= 0 || stack.isEmpty()) {
            return;
        }
        ItemOnlyKey key = new ItemOnlyKey(stack);
        Accumulator existing = map.get(key);
        if (existing == null) {
            map.put(key, new Accumulator(stack, amount));
        } else {
            existing.count += amount;
        }
    }

    private static List<BigItemStack> toList(Map<ItemOnlyKey, Accumulator> map) {
        List<BigItemStack> result = new ArrayList<>();
        for (Accumulator acc : map.values()) {
            result.add(new BigItemStack(acc.sample, acc.count));
        }
        return result;
    }
}