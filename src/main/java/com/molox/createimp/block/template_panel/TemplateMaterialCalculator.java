package com.molox.createimp.block.template_panel;

import com.molox.createimp.block.work_warehouse.WorkWarehouseTemplateSnapshot;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.util.IFactoryPanelBehaviourDemandMode;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端专用：根据模板链的配方比例与当前网络库存，递归计算某一批模板下单
 * 请求所需的原料是否足够，并汇总"缺少的材料"与"被现有库存满足的材料"。
 * <p>
 * 模板链在结构上是一个有向无环图（DAG），不是树——同一个物理仪表可以同时
 * 是多个不同下游节点的上游（比如去皮橡木原木既直接喂给橡木木板，橡木木板
 * 又同时喂给木棍和栏杆两条下游）。本类的计算过程因此分两步：
 * <p>
 * 1. 图结构发现（{@link #discoverUpstream}）：从根节点开始沿着上游连接展开，
 * 用坐标+槽位判断是否为同一个物理仪表，同一个仪表只解析一次，但会记录
 * 它被哪些下游节点各自引用、各自需要多少。
 * <p>
 * 2. 拓扑计算（{@link #buildSnapshotForTemplate}）：先按"消费者先于生产者"
 * 的拓扑顺序（从根节点出发）汇总每个物理仪表收到的全部下游需求、查库存、
 * 算批次数——只有当一个仪表的全部下游消费者都已经处理完毕，才能算出这个
 * 仪表自己收到的总需求，这也是为什么必须按拓扑顺序而不能像单纯的树那样
 * 边递归边计算。再反过来按"生产者先于消费者"的顺序（即上一步顺序的反向）
 * 确定每个节点的初始状态——这一步则相反，必须先知道上游是否已经
 * COMPLETED，才能判断当前节点的状态。
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

    public record OrderedTemplate(TemplateOrderTarget target, int amount) {
    }

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

    public record NetworkBigItemStack(UUID network, ItemStack stack, int count) {
    }

    public record Result(boolean canCompleteAll, List<BigItemStack> missing, List<BigItemStack> usedFromStock,
                         List<List<NetworkBigItemStack>> usedFromStockPerTemplate, boolean anyChainBroken,
                         List<List<WorkWarehouseTemplateSnapshot.PanelSnapshot>> snapshotPerTemplate) {
    }

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

    private static final class NetworkAccumulator {
        final UUID network;
        final ItemStack sample;
        int count;

        NetworkAccumulator(UUID network, ItemStack sample, int count) {
            this.network = network;
            this.sample = sample.copyWithCount(1);
            this.count = count;
        }
    }

    private record ConsumerLink(TemplatePanelPosition consumer, int amountPerConsumerBatch) {
    }

    private static final class NodeData {
        boolean template;
        UUID network;
        ItemStack filter;
        int recipeOutput;
        boolean demandMode;
        List<ItemStack> craftingArrangement;
        String address;
        List<TemplatePanelConnection> ownConnections;
    }

    public static Result calculate(Level level, UUID primaryNetwork, List<RequestEntry> entries) {
        Map<StockKey, Integer> stockCache = new HashMap<>();
        Map<ItemOnlyKey, Accumulator> usedAggTotal = new LinkedHashMap<>();
        Map<ItemOnlyKey, Accumulator> missingAgg = new LinkedHashMap<>();
        List<List<NetworkBigItemStack>> perTemplate = new ArrayList<>();
        List<List<WorkWarehouseTemplateSnapshot.PanelSnapshot>> snapshotPerTemplate = new ArrayList<>();

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

        boolean anyChainBroken = false;
        for (RequestEntry entry : entries) {
            if (!entry.isTemplate()) {
                continue;
            }
            OrderedTemplate order = entry.template();
            Map<ItemOnlyKey, Accumulator> usedAggThis = new LinkedHashMap<>();
            Map<StockKey, NetworkAccumulator> usedAggThisByNetwork = new LinkedHashMap<>();
            List<WorkWarehouseTemplateSnapshot.PanelSnapshot> snapshotThis = new ArrayList<>();

            if (order.amount() > 0) {
                TemplateOrderTarget target = order.target();
                TemplatePanelBehaviour root = TemplatePanelBehaviour.at(level, target.position());
                if (root == null || !root.validTemplateChain) {
                    addTo(missingAgg, target.display(), order.amount());
                    anyChainBroken = true;
                } else {
                    buildSnapshotForTemplate(level, target.position(), order.amount(), stockCache,
                            usedAggThis, usedAggThisByNetwork, missingAgg, snapshotThis);
                }
            }

            for (Accumulator acc : usedAggThis.values()) {
                addTo(usedAggTotal, acc.sample, acc.count);
            }
            perTemplate.add(toNetworkList(usedAggThisByNetwork));
            snapshotPerTemplate.add(snapshotThis);
        }

        List<BigItemStack> missing = toList(missingAgg);
        List<BigItemStack> usedFromStock = toList(usedAggTotal);
        return new Result(missing.isEmpty(), missing, usedFromStock, perTemplate, anyChainBroken, snapshotPerTemplate);
    }

    private static void buildSnapshotForTemplate(Level level, TemplatePanelPosition rootPos, int requestedAmount,
                                                 Map<StockKey, Integer> stockCache,
                                                 Map<ItemOnlyKey, Accumulator> usedAgg,
                                                 Map<StockKey, NetworkAccumulator> usedAggByNetwork,
                                                 Map<ItemOnlyKey, Accumulator> missingAgg,
                                                 List<WorkWarehouseTemplateSnapshot.PanelSnapshot> snapshotOut) {
        Map<TemplatePanelPosition, NodeData> discovered = new LinkedHashMap<>();
        Map<TemplatePanelPosition, List<ConsumerLink>> consumersOf = new LinkedHashMap<>();

        NodeData rootData = resolveNode(level, rootPos);
        if (rootData == null) {
            return;
        }
        discovered.put(rootPos, rootData);
        for (TemplatePanelConnection conn : rootData.ownConnections) {
            discoverUpstream(level, conn.from, rootPos, conn.amount, discovered, consumersOf);
        }

        Map<TemplatePanelPosition, Integer> pendingConsumers = new HashMap<>();
        for (Map.Entry<TemplatePanelPosition, List<ConsumerLink>> e : consumersOf.entrySet()) {
            pendingConsumers.put(e.getKey(), e.getValue().size());
        }
        Map<TemplatePanelPosition, Integer> batchesOf = new HashMap<>();
        Map<TemplatePanelPosition, Integer> satisfiedOf = new HashMap<>();
        List<TemplatePanelPosition> forwardOrder = new ArrayList<>();

        ArrayDeque<TemplatePanelPosition> queue = new ArrayDeque<>();
        Set<TemplatePanelPosition> enqueued = new HashSet<>();
        queue.add(rootPos);
        enqueued.add(rootPos);

        while (!queue.isEmpty()) {
            TemplatePanelPosition pos = queue.poll();
            forwardOrder.add(pos);
            NodeData data = discovered.get(pos);

            int demand = pos.equals(rootPos) ? requestedAmount : sumConsumerDemand(pos, consumersOf, batchesOf);

            if (data == null) {
                batchesOf.put(pos, 0);
                satisfiedOf.put(pos, 0);
            } else {
                int deficit;
                int satisfied;
                if (pos.equals(rootPos)) {
                    deficit = demand;
                    satisfied = 0;
                } else {
                    deficit = consumeStock(data.network, data.filter, demand,
                            () -> queryNetworkStock(data.network, data.filter), stockCache, usedAgg);
                    satisfied = demand - deficit;
                    if (satisfied > 0) {
                        addToNetwork(usedAggByNetwork, data.network, data.filter, satisfied);
                    }
                }
                int batches = deficit <= 0 ? 0 : Mth.positiveCeilDiv(deficit, Math.max(1, data.recipeOutput));
                if (!data.template) {
                    // 普通仪表是叶子节点，没有上游可以继续追溯，缺口就是真的
                    // 缺材料，必须无条件记入"缺少材料"，这里之前漏掉了。
                    if (deficit > 0) {
                        addTo(missingAgg, data.filter, deficit);
                    }
                } else if (batches > 0 && data.ownConnections.isEmpty()) {
                    // 理论上不会发生（validTemplateChain 已经保证模板节点必有上游），
                    // 防御性地记为缺失，不让计算结果凭空产生一个无法满足的批次。
                    addTo(missingAgg, data.filter, deficit);
                }
                batchesOf.put(pos, batches);
                satisfiedOf.put(pos, satisfied);
            }

            if (data != null && data.template) {
                for (TemplatePanelConnection conn : data.ownConnections) {
                    int remaining = pendingConsumers.merge(conn.from, -1, Integer::sum);
                    if (remaining <= 0 && enqueued.add(conn.from)) {
                        queue.add(conn.from);
                    }
                }
            }
        }

        List<TemplatePanelPosition> reverseOrder = new ArrayList<>(forwardOrder);
        Collections.reverse(reverseOrder);

        Map<TemplatePanelPosition, WorkWarehouseTemplateSnapshot.PanelState> stateOf = new HashMap<>();
        Map<TemplatePanelPosition, Integer> indexOf = new HashMap<>();

        for (TemplatePanelPosition pos : reverseOrder) {
            NodeData data = discovered.get(pos);
            int batches = batchesOf.getOrDefault(pos, 0);

            WorkWarehouseTemplateSnapshot.PanelState state;
            if (data == null || !data.template || batches <= 0) {
                state = WorkWarehouseTemplateSnapshot.PanelState.COMPLETED;
            } else {
                boolean allUpstreamComplete = !data.ownConnections.isEmpty();
                for (TemplatePanelConnection conn : data.ownConnections) {
                    if (stateOf.get(conn.from) != WorkWarehouseTemplateSnapshot.PanelState.COMPLETED) {
                        allUpstreamComplete = false;
                        break;
                    }
                }
                state = allUpstreamComplete
                        ? WorkWarehouseTemplateSnapshot.PanelState.WAITING_MATERIALS
                        : WorkWarehouseTemplateSnapshot.PanelState.IDLE;
            }
            stateOf.put(pos, state);

            List<WorkWarehouseTemplateSnapshot.IngredientEntry> ingredientEntries = new ArrayList<>();
            if (data != null) {
                for (TemplatePanelConnection conn : data.ownConnections) {
                    Integer childIndex = indexOf.get(conn.from);
                    if (childIndex == null) {
                        continue;
                    }
                    ItemStack childFilter = snapshotOut.get(childIndex).filterItem();
                    ingredientEntries.add(new WorkWarehouseTemplateSnapshot.IngredientEntry(
                            childFilter.copy(), conn.amount, childIndex));
                }
            }

            int satisfied = satisfiedOf.getOrDefault(pos, 0);
            int recipeOutput = data != null ? data.recipeOutput : 1;
            int expectedOutputTotal = pos.equals(rootPos)
                    ? batches * recipeOutput
                    : satisfied + batches * recipeOutput;

            snapshotOut.add(new WorkWarehouseTemplateSnapshot.PanelSnapshot(
                    data != null ? data.network : UUID.randomUUID(),
                    data != null ? data.filter.copy() : ItemStack.EMPTY,
                    data != null && data.template,
                    recipeOutput,
                    ingredientEntries,
                    data != null && data.demandMode,
                    data != null ? data.craftingArrangement : List.of(),
                    data != null ? data.address : "",
                    batches, state, expectedOutputTotal));
            indexOf.put(pos, snapshotOut.size() - 1);
        }
    }

    private static int sumConsumerDemand(TemplatePanelPosition pos,
                                         Map<TemplatePanelPosition, List<ConsumerLink>> consumersOf,
                                         Map<TemplatePanelPosition, Integer> batchesOf) {
        int total = 0;
        for (ConsumerLink link : consumersOf.getOrDefault(pos, List.of())) {
            Integer consumerBatches = batchesOf.get(link.consumer());
            total += link.amountPerConsumerBatch() * (consumerBatches != null ? consumerBatches : 0);
        }
        return total;
    }

    private static void discoverUpstream(Level level, TemplatePanelPosition pos, TemplatePanelPosition consumerPos,
                                         int amount, Map<TemplatePanelPosition, NodeData> discovered,
                                         Map<TemplatePanelPosition, List<ConsumerLink>> consumersOf) {
        consumersOf.computeIfAbsent(pos, k -> new ArrayList<>()).add(new ConsumerLink(consumerPos, amount));
        if (discovered.containsKey(pos)) {
            return;
        }
        NodeData data = resolveNode(level, pos);
        discovered.put(pos, data);
        if (data != null && data.template) {
            for (TemplatePanelConnection conn : data.ownConnections) {
                discoverUpstream(level, conn.from, pos, conn.amount, discovered, consumersOf);
            }
        }
    }

    private static NodeData resolveNode(Level level, TemplatePanelPosition pos) {
        if (!level.isLoaded(pos.pos())) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos.pos());
        if (be instanceof TemplatePanelBlockEntity tpbe) {
            TemplatePanelBehaviour node = tpbe.panels.get(pos.slot());
            if (node == null || !node.isActive() || node.getFilter().isEmpty()
                    || node.recipeAddress == null || node.recipeAddress.isEmpty()) {
                return null;
            }
            NodeData data = new NodeData();
            data.template = true;
            data.network = node.network;
            data.filter = node.getFilter().copy();
            data.recipeOutput = node.recipeOutput;
            data.demandMode = node.demandMode;
            data.craftingArrangement = List.copyOf(node.activeCraftingArrangement);
            data.address = node.recipeAddress;
            data.ownConnections = new ArrayList<>(node.targetedBy.values());
            return data;
        } else if (be instanceof FactoryPanelBlockEntity fpbe) {
            FactoryPanelBlock.PanelSlot vanillaSlot = FactoryPanelBlock.PanelSlot.valueOf(pos.slot().name());
            FactoryPanelBehaviour node = fpbe.panels.get(vanillaSlot);
            if (node == null || !node.isActive() || node.getFilter().isEmpty()) {
                return null;
            }
            NodeData data = new NodeData();
            data.template = false;
            data.network = node.network;
            data.filter = node.getFilter().copy();
            data.recipeOutput = node.recipeOutput;
            data.demandMode = ((IFactoryPanelBehaviourDemandMode) node).createimp$isDemandMode();
            data.craftingArrangement = List.copyOf(node.activeCraftingArrangement);
            data.address = node.recipeAddress;
            data.ownConnections = List.of();
            return data;
        }
        return null;
    }

    private static int queryNetworkStock(UUID network, ItemStack item) {
        return LogisticsManager.getSummaryOfNetwork(network, false).getCountOf(item);
    }

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

    private static void addToNetwork(Map<StockKey, NetworkAccumulator> map, UUID network, ItemStack stack, int amount) {
        if (amount <= 0 || stack.isEmpty()) {
            return;
        }
        StockKey key = new StockKey(network, stack);
        NetworkAccumulator existing = map.get(key);
        if (existing == null) {
            map.put(key, new NetworkAccumulator(network, stack, amount));
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

    private static List<NetworkBigItemStack> toNetworkList(Map<StockKey, NetworkAccumulator> map) {
        List<NetworkBigItemStack> result = new ArrayList<>();
        for (NetworkAccumulator acc : map.values()) {
            result.add(new NetworkBigItemStack(acc.network, acc.sample, acc.count));
        }
        return result;
    }
}