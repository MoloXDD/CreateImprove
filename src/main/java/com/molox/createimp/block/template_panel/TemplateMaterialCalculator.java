package com.molox.createimp.block.template_panel;

import com.molox.createimp.block.work_warehouse.WorkWarehouseTemplateSnapshot;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.util.IFactoryPanelBehaviourDemandMode;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
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

    /**
     * 一条普通物品在 {@link #calculatePartial} 里的结算结果：请求的原始条目，
     * 以及本次实际能从共享库存里认领到多少（可能小于请求数量，不代表失败）。
     */
    public record RegularFulfillment(BigItemStack requested, int sent) {
        public boolean isFull() {
            return sent >= requested.count;
        }
    }

    /**
     * 一个模板在 {@link #calculatePartial} 里被分配到工作仓库时的结算结果，
     * {@code amount} 是这次实际能生产的数量，可能小于 {@code template.amount()}
     * （请求的数量）。
     */
    public record TemplateDispatch(OrderedTemplate template, int amount,
                                   List<WorkWarehouseTemplateSnapshot.PanelSnapshot> snapshot,
                                   List<NetworkBigItemStack> demand) {
    }

    /**
     * {@link #calculatePartial} 的返回结果。
     */
    public record PartialResult(List<RegularFulfillment> regularFulfillments,
                                List<TemplateDispatch> templatesToActivate) {
        public boolean isEmpty() {
            for (RegularFulfillment r : regularFulfillments) {
                if (r.sent() > 0) {
                    return false;
                }
            }
            return templatesToActivate.isEmpty();
        }

        /**
         * 请求栏里每一件普通物品都被完整满足，且每一个请求的模板都以完整
         * 请求数量、按各自独立的工作仓库被生产——三者但凡有一项不成立
         * （包括因为可用工作仓库数量不够、导致排在后面的模板整个没能进入
         * {@code templatesToActivate} 的情况），都视为没有完全满足。
         */
        public boolean isFullMatch(int totalRequestedTemplates) {
            for (RegularFulfillment r : regularFulfillments) {
                if (!r.isFull()) {
                    return false;
                }
            }
            if (templatesToActivate.size() != totalRequestedTemplates) {
                return false;
            }
            for (TemplateDispatch d : templatesToActivate) {
                if (d.amount() < d.template().amount()) {
                    return false;
                }
            }
            return true;
        }
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
        return calculate(level, primaryNetwork, entries, false);
    }

    /**
     * 与 {@link #calculate(Level, UUID, List)} 完全相同，额外允许指定库存查询
     * 是否使用精确（逐 tick 刷新）快照而不是默认的较新（20 tick 刷新）缓存。
     * 仓管请求界面、材料检查窗口等既有调用方一律通过上面那个不带 accurate 参数
     * 的重载间接调用本方法，固定传 false，行为与之前完全一致，不受影响。
     */
    public static Result calculate(Level level, UUID primaryNetwork, List<RequestEntry> entries, boolean accurate) {
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
                    () -> queryNetworkStock(primaryNetwork, item, accurate),
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
                            usedAggThis, usedAggThisByNetwork, missingAgg, snapshotThis, accurate);
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

    /**
     * 服务端专用：红石请求器"允许部分请求"开关开启、且请求中含有模板时的
     * 材料分配算法。与 {@link #calculate} 只回答"能不能完全满足"不同，本方法
     * 要算出"最多能满足到什么程度"：
     * <p>
     * 1. 按请求栏原始顺序逐条结算普通物品，每条目独立地从共享库存里尽量
     * 认领，认领不到的部分不算失败，只是这一条目实际发出的数量比请求的少，
     * 结算后立刻从共享库存池扣除这部分，供后面继续用。
     * <p>
     * 2. 普通物品结算完毕后，按请求栏原始顺序逐个处理模板：对每个模板用
     * 二分查找，在 [0, 请求数量] 之间找出不会让链路任何一层出现缺口的最大
     * 批次数——二分之所以成立，是因为需求量越大，链路每一层需要的批次数只会
     * 更多不会更少，"能否完全满足"这件事随需求量单调不增。二分试算过程只
     * 操作共享库存的一份临时拷贝，不影响真实的共享库存；只有确定这个模板
     * 能生产多少之后，才用这个数量对共享库存做一次真正的扣除，让后面排队的
     * 模板看到的是扣除之后的余量。
     * <p>
     * 3. 每确定一个模板可以生产（数量大于零），就占用一个调用方传入的"当前
     * 可用工作仓库数量"里的名额；名额用完后，后面排队的模板一律视为本次不
     * 生产（不影响它们各自的材料判断结果，仅仅是没有仓库可分）。
     * <p>
     * 本方法只读取库存，不会对物流网络产生任何真实副作用（不实际扣除任何
     * 物品、不激活任何工作仓库），返回结果由调用方决定是否真正据此发货、
     * 激活工作仓库；调用方就算把结果整体丢弃（比如没开"允许部分请求"、发现
     * 没能完全满足就整单取消），也不需要任何回滚。
     * <p>
     * 完全不复用、不修改 {@link #calculate} 本身及其调用的私有方法的既有
     * 行为，仓管请求界面、材料检查窗口等既有功能不受任何影响。
     */
    public static PartialResult calculatePartial(Level level, UUID primaryNetwork, List<BigItemStack> regularStacks,
                                                 List<OrderedTemplate> orderedTemplates, int availableWarehouses,
                                                 boolean accurate) {
        Map<StockKey, Integer> stockCache = new HashMap<>();

        List<RegularFulfillment> regularFulfillments = new ArrayList<>();
        for (BigItemStack entry : regularStacks) {
            if (entry.stack.isEmpty() || entry.count <= 0) {
                regularFulfillments.add(new RegularFulfillment(entry, 0));
                continue;
            }
            Map<ItemOnlyKey, Accumulator> discardUsed = new LinkedHashMap<>();
            int deficit = consumeStock(primaryNetwork, entry.stack, entry.count,
                    () -> queryNetworkStock(primaryNetwork, entry.stack, accurate), stockCache, discardUsed);
            regularFulfillments.add(new RegularFulfillment(entry, entry.count - deficit));
        }

        List<TemplateDispatch> templatesToActivate = new ArrayList<>();
        int warehousesLeft = Math.max(0, availableWarehouses);
        for (OrderedTemplate ordered : orderedTemplates) {
            if (warehousesLeft <= 0) {
                break;
            }
            if (ordered.amount() <= 0) {
                continue;
            }
            TemplateOrderTarget target = ordered.target();
            TemplatePanelBehaviour root = TemplatePanelBehaviour.at(level, target.position());
            if (root == null || !root.validTemplateChain) {
                continue;
            }

            int low = 0;
            int high = ordered.amount();
            int best = 0;
            while (low <= high) {
                int mid = low + (high - low) / 2;
                boolean feasible;
                if (mid == 0) {
                    feasible = true;
                } else {
                    Map<StockKey, Integer> trialCache = new HashMap<>(stockCache);
                    Map<ItemOnlyKey, Accumulator> trialUsed = new LinkedHashMap<>();
                    Map<StockKey, NetworkAccumulator> trialUsedByNetwork = new LinkedHashMap<>();
                    Map<ItemOnlyKey, Accumulator> trialMissing = new LinkedHashMap<>();
                    List<WorkWarehouseTemplateSnapshot.PanelSnapshot> trialSnapshot = new ArrayList<>();
                    buildSnapshotForTemplate(level, target.position(), mid, trialCache,
                            trialUsed, trialUsedByNetwork, trialMissing, trialSnapshot, accurate);
                    feasible = trialMissing.isEmpty();
                }
                if (feasible) {
                    best = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            if (best <= 0) {
                continue;
            }

            Map<ItemOnlyKey, Accumulator> committedUsed = new LinkedHashMap<>();
            Map<StockKey, NetworkAccumulator> committedUsedByNetwork = new LinkedHashMap<>();
            Map<ItemOnlyKey, Accumulator> committedMissing = new LinkedHashMap<>();
            List<WorkWarehouseTemplateSnapshot.PanelSnapshot> committedSnapshot = new ArrayList<>();
            buildSnapshotForTemplate(level, target.position(), best, stockCache,
                    committedUsed, committedUsedByNetwork, committedMissing, committedSnapshot, accurate);

            templatesToActivate.add(new TemplateDispatch(ordered, best, committedSnapshot,
                    toNetworkList(committedUsedByNetwork)));
            warehousesLeft--;
        }

        return new PartialResult(regularFulfillments, templatesToActivate);
    }

    private static void buildSnapshotForTemplate(Level level, TemplatePanelPosition rootPos, int requestedAmount,
                                                 Map<StockKey, Integer> stockCache,
                                                 Map<ItemOnlyKey, Accumulator> usedAgg,
                                                 Map<StockKey, NetworkAccumulator> usedAggByNetwork,
                                                 Map<ItemOnlyKey, Accumulator> missingAgg,
                                                 List<WorkWarehouseTemplateSnapshot.PanelSnapshot> snapshotOut,
                                                 boolean accurate) {
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
                            () -> queryNetworkStock(data.network, data.filter, accurate), stockCache, usedAgg);
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
                Map<TemplatePanelPosition, Integer> connectionAmounts = data.craftingArrangement.isEmpty()
                        ? null
                        : distributeCraftingConnectionAmounts(level, data.ownConnections, data.craftingArrangement);
                for (TemplatePanelConnection conn : data.ownConnections) {
                    Integer childIndex = indexOf.get(conn.from);
                    if (childIndex == null) {
                        continue;
                    }
                    int amount = connectionAmounts != null
                            ? connectionAmounts.getOrDefault(conn.from, 0)
                            : conn.amount;
                    if (amount <= 0) {
                        continue;
                    }
                    ItemStack childFilter = snapshotOut.get(childIndex).filterItem();
                    ingredientEntries.add(new WorkWarehouseTemplateSnapshot.IngredientEntry(
                            childFilter.copy(), amount, childIndex));
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
            if (data.craftingArrangement.isEmpty()) {
                for (TemplatePanelConnection conn : data.ownConnections) {
                    discoverUpstream(level, conn.from, pos, conn.amount, discovered, consumersOf);
                }
            } else {
                for (Map.Entry<TemplatePanelPosition, Integer> e : distributeCraftingConnectionAmounts(
                        level, data.ownConnections, data.craftingArrangement).entrySet()) {
                    if (e.getValue() > 0) {
                        discoverUpstream(level, e.getKey(), pos, e.getValue(), discovered, consumersOf);
                    }
                }
            }
        }
    }

    /**
     * 动力合成模式下，原版对 3x3 配方格子里同一种物品的每一个连接格，都会把
     * "整个配方格子里这种物品一共占了几格"原样填成这个连接的数量——如果恰好
     * 有多个不同的上游仪表监测的是同一种物品，原版给出的这个数字会在每一个
     * 连接上重复出现（而不是各自的真实份额），直接拿来对每个上游各自登记一遍
     * 需求会导致总需求被放大到原来的好几倍。
     * <p>
     * 这里按上游仪表当前监测的物品种类重新分组：同一组内按连接在
     * {@code targetedBy} 里出现的先后顺序，把配方格数尽量平均分配，排在
     * 前面的连接优先多分到一格（比如 5 格分给 2 个来源就是 3+2）；如果同一
     * 种物品连接的上游数量比配方格数还多，排在后面、分不到格子的连接直接
     * 分配为 0（视为废弃，不再参与这种物品的任何需求计算）。
     */
    private static Map<TemplatePanelPosition, Integer> distributeCraftingConnectionAmounts(
            Level level, List<TemplatePanelConnection> connections, List<ItemStack> craftingArrangement) {
        Map<TemplatePanelPosition, Integer> result = new LinkedHashMap<>();
        Map<ItemStack, List<TemplatePanelPosition>> groups =
                new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
        for (TemplatePanelConnection conn : connections) {
            NodeData sourceData = resolveNode(level, conn.from);
            if (sourceData == null || sourceData.filter.isEmpty()) {
                // 上游暂时无法解析（比如所在区块未加载），没法判断它跟哪些
                // 连接同属一组，保底沿用原始数量，不参与分组平分。
                result.put(conn.from, conn.amount);
                continue;
            }
            groups.computeIfAbsent(sourceData.filter, $ -> new ArrayList<>()).add(conn.from);
        }
        for (Map.Entry<ItemStack, List<TemplatePanelPosition>> group : groups.entrySet()) {
            int n = 0;
            for (ItemStack cell : craftingArrangement) {
                if (!cell.isEmpty() && ItemStack.isSameItemSameComponents(cell, group.getKey())) {
                    n++;
                }
            }
            List<TemplatePanelPosition> members = group.getValue();
            int groupSize = members.size();
            int base = n / groupSize;
            int remainder = n % groupSize;
            for (int i = 0; i < groupSize; i++) {
                result.put(members.get(i), base + (i < remainder ? 1 : 0));
            }
        }
        return result;
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

    private static int queryNetworkStock(UUID network, ItemStack item, boolean accurate) {
        return LogisticsManager.getSummaryOfNetwork(network, accurate).getCountOf(item);
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