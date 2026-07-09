package com.molox.createimp.block.work_warehouse;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.template_panel.TemplateMaterialCalculator;
import com.molox.createimp.network.WorkWarehouseActivateEffectPacket;
import com.molox.createimp.network.WorkWarehouseMaterialsReadyEffectPacket;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.CapManipulationBehaviourBase;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class WorkWarehouseBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /**
     * 工作仓库当前所处的生产阶段，作为客户端护目镜文案展示的唯一依据。
     */
    public enum WorkStage {
        IDLE,
        REQUESTING_MATERIALS,
        PRODUCTION
    }

    private static final Random RNG = new Random();

    public LogisticallyLinkedBehaviour behaviour;
    public InvManipulationBehaviour extractBehaviour;
    public final WorkWarehouseItemStackHandler storage = new WorkWarehouseItemStackHandler(this);
    private String address = "";
    private String targetAddress = "";
    private WorkStage stage = WorkStage.IDLE;

    // 连接库存监控转移/网络请求重试的节奏计数器，不需要持久化。间隔与仓储
    // 管理员界面判断"是否需要重新拉取网络库存快照"的节奏（约 16 tick）保持一致。
    private int ticksSinceLastMonitor = 0;

    /**
     * 标记"需求列表在打包机回调（{@link #consumeFromDemandList}）里发生了
     * 变化，需要在下一个 tick 重新核对"。不持久化，纯运行时标记，见
     * {@link #consumeFromDemandList} 里的详细说明。
     */
    private boolean pendingReconcile = false;

    /**
     * 本次被分配到的那一个模板链的结构快照，激活时写入，供生产阶段使用。
     * 根节点固定是列表的最后一个元素。只在服务端持久化，不需要同步给客户端。
     */
    private List<WorkWarehouseTemplateSnapshot.PanelSnapshot> templateSnapshot = new ArrayList<>();

    /**
     * 需求列表：原料请求阶段的初始一次性需求，以及生产阶段中每个正在
     * WAITING_MATERIALS 的节点各自登记的原料需求（含虚拟末端需求）共用同一份
     * 列表，靠 {@code ownerNode} 区分归属。只在服务端持久化。
     */
    private List<WorkWarehouseTemplateSnapshot.DemandEntry> demandList = new ArrayList<>();

    /**
     * 请求列表：需求列表中已经向网络发起请求、还在路上尚未到达的部分。
     * 只在服务端持久化。
     */
    private List<WorkWarehouseTemplateSnapshot.InTransitEntry> inTransitList = new ArrayList<>();

    /**
     * 生产阶段专用："虚拟末端需求"（等待根节点自身产出物返回仓库）是否已经
     * 登记过。用于区分"还没登记"与"登记后已经被满足清空"这两种情况——两者
     * 都会表现为需求列表里找不到 owner 为 OWNER_FINAL_PRODUCT 的条目。
     */
    private boolean finalDemandRegistered = false;

    /**
     * 整次生产是否已经彻底完成（根节点自身产出物已经全部回到仓库内部存储）。
     * 目前只实现到"产物停留在仓库内"，不涉及后续发货，因此这个字段单纯作为
     * 一个可供护目镜信息读取的完成标记。会同步给客户端。
     */
    private boolean productionComplete = false;

    /**
     * 本次正在生产的目标物品与请求数量，用于护目镜信息展示。
     */
    private ItemStack requestedProduct = ItemStack.EMPTY;
    private int requestedAmount = 0;

    public WorkWarehouseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.behaviour = new LogisticallyLinkedBehaviour(this, false);
        behaviours.add(this.behaviour);
        this.extractBehaviour = InvManipulationBehaviour.forExtraction(this,
                CapManipulationBehaviourBase.InterfaceProvider.oppositeOfBlockFacing());
        behaviours.add(this.extractBehaviour);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
        setChanged();
        notifyUpdate();
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
        setChanged();
    }

    public boolean isWorking() {
        return getBlockState().getValue(WorkWarehouseBlock.POWERED);
    }

    public void setWorking(boolean working) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (isWorking() == working) {
            return;
        }
        level.setBlockAndUpdate(worldPosition, getBlockState().setValue(WorkWarehouseBlock.POWERED, working));
    }

    public void activate(String targetAddress) {
        if (level == null || level.isClientSide()) {
            return;
        }
        setTargetAddress(targetAddress);
        setWorking(true);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 center = Vec3.atCenterOf(worldPosition);
            PacketDistributor.sendToPlayersNear(serverLevel, null, center.x, center.y, center.z, 32.0,
                    new WorkWarehouseActivateEffectPacket(worldPosition));
        }
    }

    /**
     * 进入需求原料阶段的入口：必须在 {@link #setDemandList} 写入本次需求列表
     * 之后调用。
     */
    public void startMaterialRequestStage() {
        if (level == null || level.isClientSide()) {
            return;
        }
        CreateImp.LOGGER.info("[WorkWarehouse {}] 进入原料请求阶段，初始需求列表: {}", worldPosition,
                demandList.stream().map(e -> e.item().getItem() + " x" + e.amount() + "(owner=" + e.ownerNode() + ")").toList());
        monitorConnectedInventory();
        requestRemainingDemandFromNetwork();
        reconcileDemandList();
    }

    // ------------------------------------------------------------------
    // 需求列表 / 请求列表的共用机制
    // ------------------------------------------------------------------

    /**
     * 连接模式下，检查连接库存里是否有需求列表中的物品，有则转移进内部存储
     * 并从需求列表对应项里扣减（数量不足则扣减部分，划除则整项移除），同时
     * 按相同数量扣减请求列表里对应的在途记录。非连接模式直接跳过。
     */
    private void monitorConnectedInventory() {
        if (extractBehaviour == null || !extractBehaviour.hasInventory() || demandList.isEmpty()) {
            return;
        }
        List<WorkWarehouseTemplateSnapshot.DemandEntry> updated = new ArrayList<>();
        boolean changed = false;
        for (WorkWarehouseTemplateSnapshot.DemandEntry entry : demandList) {
            ItemStack toMatch = entry.item();
            ItemStack extracted = extractBehaviour.extract(ItemHelper.ExtractionCountMode.UPTO, entry.amount(),
                    stack -> ItemStack.isSameItemSameComponents(stack, toMatch));
            if (extracted.isEmpty()) {
                updated.add(entry);
                continue;
            }
            changed = true;
            ItemHandlerHelper.insertItemStacked(storage, extracted, false);
            decrementInTransit(extracted.copy());
            int remaining = entry.amount() - extracted.getCount();
            CreateImp.LOGGER.info("[WorkWarehouse {}] 连接库存转移: {} x{} -> 仓库内部存储 (owner={}, 该条需求剩余 {})",
                    worldPosition, extracted.getItem(), extracted.getCount(), entry.ownerNode(), Math.max(remaining, 0));
            if (remaining > 0) {
                updated.add(new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), remaining, entry.ownerNode()));
            }
        }
        if (changed) {
            demandList = updated;
            setChanged();
            logStorageSnapshot("连接库存转移后");
            reconcileDemandList();
        }
    }

    /**
     * 对需求列表里仍未满足、且尚未被请求列表覆盖的部分，按物流网络分组向
     * 网络发起打包请求（收货地址统一是仓库自己的 {@link #address}）。
     * <p>
     * 同一个网络分组里可能同时有多种不同物品的缺口（比如云杉木板和橡木
     * 木板），{@link LogisticsManager#findPackagersForRequest} 是按"这一整批
     * 里每一种物品分别去找有没有库存"的方式处理的——某一种物品当时如果在
     * 网络里还不存在（比如转换还没做完），就不会为它生成任何
     * {@code PackagingRequest}，但只要*其他*物品找到了库存，返回的结果整体
     * 就不是空的。之前的实现只判断"这一整批的结果是否为空"，只要有任何一种
     * 物品命中就把这一整批全部记入在途，导致没找到库存的那部分物品被误判
     * 成"已经在处理"、再也不会被重新请求。这里改为逐个物品核对真正被匹配到
     * 的数量，只有真正命中的部分才记入在途，没命中的部分留给下一次周期性
     * 重试。
     * <p>
     * 另外，查找时会把工作仓库自己的连接库存（{@link #extractBehaviour}）
     * 作为 {@code ignoredHandler} 传给 {@code findPackagersForRequest}——
     * 如果工作仓库本身就连接着某个仓储容器，那份库存应该完全交给
     * {@link #monitorConnectedInventory} 直接搬运，不应该让网络请求又绕回
     * 同一份连接库存自身（哪怕物理上只有这一份库存，仓储连接站也会让网络
     * 查找"找到"它，导致明明是直连关系却多绕了一次打包/寄送）。传入这个
     * 参数后，机械动力自带的 {@code PackagerBlockEntity#isTargetingSameInventory}
     * 判断会直接跳过目标库存和这份连接库存相同的打包机，网络请求就只会
     * 匹配到网络里真正独立于这份连接库存之外的其他来源。
     */
    private void requestRemainingDemandFromNetwork() {
        if (demandList.isEmpty()) {
            return;
        }
        Map<UUID, List<WorkWarehouseTemplateSnapshot.DemandEntry>> shortfallByNetwork = new LinkedHashMap<>();
        for (WorkWarehouseTemplateSnapshot.DemandEntry entry : demandList) {
            int inTransit = countInTransit(entry.network(), entry.item(), entry.ownerNode());
            int shortfall = entry.amount() - inTransit;
            if (shortfall <= 0) {
                continue;
            }
            CreateImp.LOGGER.info("[WorkWarehouse {}] 需求列表条目 owner={} 物品 {} 还缺 {}（需求 {} - 在途 {}），准备向网络 {} 发起请求",
                    worldPosition, entry.ownerNode(), entry.item().getItem(), shortfall, entry.amount(), inTransit, entry.network());
            shortfallByNetwork.computeIfAbsent(entry.network(), key -> new ArrayList<>())
                    .add(new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), shortfall, entry.ownerNode()));
        }

        boolean changed = false;
        for (Map.Entry<UUID, List<WorkWarehouseTemplateSnapshot.DemandEntry>> networkGroup : shortfallByNetwork.entrySet()) {
            UUID network = networkGroup.getKey();
            List<BigItemStack> stacks = new ArrayList<>();
            for (WorkWarehouseTemplateSnapshot.DemandEntry e : networkGroup.getValue()) {
                stacks.add(new BigItemStack(e.item(), e.amount()));
            }
            PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(stacks);
            com.google.common.collect.Multimap<PackagerBlockEntity, com.simibubi.create.content.logistics.packager.PackagingRequest> requests =
                    LogisticsManager.findPackagersForRequest(network, order,
                            extractBehaviour != null ? extractBehaviour.getIdentifiedInventory() : null, address);
            if (requests.isEmpty()) {
                for (WorkWarehouseTemplateSnapshot.DemandEntry e : networkGroup.getValue()) {
                    CreateImp.LOGGER.info("[WorkWarehouse {}] 网络 {} 里找不到任何能提供 {} x{} 的打包机，本次请求放弃，等待下次周期性重试",
                            worldPosition, network, e.item().getItem(), e.amount());
                }
                continue;
            }
            boolean tooBusy = false;
            for (PackagerBlockEntity packager : requests.keySet()) {
                if (packager.isTooBusyFor(LogisticallyLinkedBehaviour.RequestType.RESTOCK)) {
                    tooBusy = true;
                    break;
                }
            }
            if (tooBusy) {
                for (WorkWarehouseTemplateSnapshot.DemandEntry e : networkGroup.getValue()) {
                    CreateImp.LOGGER.info("[WorkWarehouse {}] 网络 {} 里找到的打包机暂时太忙，物品 {} x{} 本次请求放弃，等待下次周期性重试",
                            worldPosition, network, e.item().getItem(), e.amount());
                }
                continue;
            }

            // 在 performPackageRequests 真正执行、修改这些 PackagingRequest 的
            // 数量之前，先统计每种物品各自实际被匹配到多少——这才是这一批里
            // 真正找到库存的部分，不能直接假设"整批都成功了"。
            List<ItemMatchAmount> matched = new ArrayList<>();
            for (com.simibubi.create.content.logistics.packager.PackagingRequest req : requests.values()) {
                addMatchAmount(matched, req.item(), req.getCount());
            }

            LogisticsManager.performPackageRequests(requests);

            for (WorkWarehouseTemplateSnapshot.DemandEntry e : networkGroup.getValue()) {
                int matchedAmount = takeMatchAmount(matched, e.item(), e.amount());
                if (matchedAmount <= 0) {
                    CreateImp.LOGGER.info("[WorkWarehouse {}] 网络 {} 里没有找到任何能提供 {} x{} 的库存（本批里其他物品可能找到了，但这一种没有），本次请求放弃，等待下次周期性重试",
                            worldPosition, network, e.item().getItem(), e.amount());
                    continue;
                }
                addInTransit(e.network(), e.item(), matchedAmount, e.ownerNode());
                changed = true;
                CreateImp.LOGGER.info("[WorkWarehouse {}] 向网络 {} 请求打包成功: {} x{} -> 仓库地址 \"{}\"{}",
                        worldPosition, network, e.item().getItem(), matchedAmount, address,
                        matchedAmount < e.amount() ? "（只匹配到部分数量，剩余 " + (e.amount() - matchedAmount) + " 等待下次重试）" : "");
            }
        }
        if (changed) {
            setChanged();
        }
    }

    private static final class ItemMatchAmount {
        final ItemStack sample;
        int count;

        ItemMatchAmount(ItemStack sample, int count) {
            this.sample = sample.copyWithCount(1);
            this.count = count;
        }
    }

    private static void addMatchAmount(List<ItemMatchAmount> list, ItemStack item, int count) {
        if (count <= 0) {
            return;
        }
        for (ItemMatchAmount m : list) {
            if (ItemStack.isSameItemSameComponents(m.sample, item)) {
                m.count += count;
                return;
            }
        }
        list.add(new ItemMatchAmount(item, count));
    }

    private static int takeMatchAmount(List<ItemMatchAmount> list, ItemStack item, int want) {
        for (ItemMatchAmount m : list) {
            if (ItemStack.isSameItemSameComponents(m.sample, item)) {
                int take = Math.min(want, m.count);
                m.count -= take;
                return take;
            }
        }
        return 0;
    }

    /**
     * 供 {@link WorkWarehouseUnpackingHandler} 在包裹解包成功后调用，按传入的
     * 物品与数量，依次从需求列表里扣减（同一物品可能因为来自不同网络/不同
     * 节点而拆成多条记录，按记录顺序依次扣减，扣满即止，不会重复扣减），
     * 并按实际扣减掉的数量同步扣减请求列表。
     */
    public void consumeFromDemandList(List<ItemStack> items) {
        if (demandList.isEmpty()) {
            return;
        }
        List<WorkWarehouseTemplateSnapshot.DemandEntry> working = new ArrayList<>(demandList);
        for (ItemStack item : items) {
            int originalCount = item.getCount();
            int toConsume = originalCount;
            for (int i = 0; i < working.size() && toConsume > 0; i++) {
                WorkWarehouseTemplateSnapshot.DemandEntry entry = working.get(i);
                if (entry == null || !ItemStack.isSameItemSameComponents(entry.item(), item)) {
                    continue;
                }
                int consumed = Math.min(entry.amount(), toConsume);
                toConsume -= consumed;
                int remaining = entry.amount() - consumed;
                working.set(i, remaining > 0
                        ? new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), remaining, entry.ownerNode())
                        : null);
            }
            int consumedTotal = originalCount - toConsume;
            if (consumedTotal > 0) {
                decrementInTransit(item.copyWithCount(consumedTotal));
            }
        }
        working.removeIf(java.util.Objects::isNull);
        demandList = working;
        setChanged();
        CreateImp.LOGGER.info("[WorkWarehouse {}] 打包机解包/包裹接收，本次消耗物品: {}",
                worldPosition, items.stream().map(i -> i.getItem() + " x" + i.getCount()).toList());
        // 注意：这里不能同步调用 reconcileDemandList()。这个方法是从
        // WorkWarehouseUnpackingHandler.unpack() 被调用的，而 unpack() 本身
        // 又是在 PackagerBlockEntity.unwrapBox() 内部、"入库动画相关字段
        // （previouslyUnwrapped/animationInward/animationTicks）被设置之前"
        // 调用的——如果这里同步触发 reconcileDemandList -> completeNode ->
        // dispatchNodeIngredients，很可能会把这批新的出库包裹注入到同一个
        // 正处理入库的打包机身上（此刻它的 animationTicks 字段还没来得及被
        // 设置成"正在播放入库动画"，会被误判为空闲），随后 unwrapBox 剩余的
        // 代码会把 animationInward/animationTicks 重新覆盖成入库动画的值，
        // 导致刚刚设置的 heldBox 永远不会被渲染、也永远不会被漏斗等取走。
        // 因此这里只标记"待处理"，真正的 reconcileDemandList 推迟到下一个
        // tick（彻底跳出 unwrapBox 调用栈之后）再执行。
        pendingReconcile = true;
    }

    /**
     * 打印仓库内部存储当前真实的物理内容快照（按物品种类合并计数），用于
     * 在几个关键节点核对"物理上到底有多少"和"账面需求认为已经到了多少"
     * 是否一致，方便排查类似"同一批库存被重复认领"这种账面与实物对不上
     * 的问题。
     */
    private void logStorageSnapshot(String context) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < storage.getSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            counts.merge(s.getItem().toString(), s.getCount(), Integer::sum);
        }
        CreateImp.LOGGER.info("[WorkWarehouse {}] [仓库存储快照:{}] {}", worldPosition, context, counts);
    }

    /**
     * 检查仓库自己内部存储里当前已有的库存能否直接满足需求列表里的条目
     * （不需要经过连接库存监控或者网络请求）。用一份 {@code storage.copy()}
     * 的临时快照按需求列表原始顺序依次"认领"，保证多个节点/多份需求同时
     * 需要同一种物品时，不会出现两边都误以为自己的那一份已经够了的情况——
     * 谁在列表里排得靠前，谁先从这份临时快照里拿。
     * <p>
     * 重要：这个方法只应该在 {@link #beginProductionStage()} 里调用一次
     * （生产刚开始那一刻，捕捉原料请求阶段已经预先收集好、还没被任何需求
     * 条目认领过的现有库存）。之后所有新到货、新满足的判断都应该只交给
     * {@link #monitorConnectedInventory()}（从外部连接库存真实提取，具有
     * "一次性、不可重复发现"的物理特性）——不能再周期性地反复调用这个方法：
     * 副产物、虚拟末端需求这类"满足之后不会被任何派发动作真正取走物理库存"
     * 的条目，它们对应的实物会一直原样躺在仓库存储里，如果每个 tick 都重新
     * 扫描一遍，会把同一批从未被取走的库存反复当成"新发现的库存"重复认领，
     * 导致账面需求远快于实际到货速度被清零——这正是之前出现"全部产品到达
     * 前订单就结束"这个 bug 的根源，务必不要再在别处调用这个方法。
     */
    private void settleFromOwnStorage() {
        if (demandList.isEmpty()) {
            return;
        }
        logStorageSnapshot("settleFromOwnStorage 结算前");
        WorkWarehouseItemStackHandler scratch = storage.copy();
        List<WorkWarehouseTemplateSnapshot.DemandEntry> updated = new ArrayList<>();
        boolean changed = false;
        for (WorkWarehouseTemplateSnapshot.DemandEntry entry : demandList) {
            int available = countMatching(scratch, entry.item());
            int claim = Math.min(entry.amount(), available);
            if (claim > 0) {
                extractExact(scratch, entry.item(), claim);
                changed = true;
                int remaining = entry.amount() - claim;
                CreateImp.LOGGER.info("[WorkWarehouse {}] 仓库自身现有库存直接认领: owner={} 物品 {} x{}（该条需求剩余 {}）",
                        worldPosition, entry.ownerNode(), entry.item().getItem(), claim, Math.max(remaining, 0));
                if (remaining > 0) {
                    updated.add(new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), remaining, entry.ownerNode()));
                }
            } else {
                updated.add(entry);
            }
        }
        if (changed) {
            demandList = updated;
            setChanged();
            reconcileDemandList();
        }
    }

    private void decrementInTransit(ItemStack arrived) {
        if (inTransitList.isEmpty()) {
            return;
        }
        int toConsume = arrived.getCount();
        List<WorkWarehouseTemplateSnapshot.InTransitEntry> updated = new ArrayList<>();
        for (WorkWarehouseTemplateSnapshot.InTransitEntry entry : inTransitList) {
            if (toConsume > 0 && ItemStack.isSameItemSameComponents(entry.item(), arrived)) {
                int consumed = Math.min(entry.amount(), toConsume);
                toConsume -= consumed;
                int remaining = entry.amount() - consumed;
                if (remaining > 0) {
                    updated.add(new WorkWarehouseTemplateSnapshot.InTransitEntry(entry.network(), entry.item(), remaining, entry.ownerNode()));
                }
            } else {
                updated.add(entry);
            }
        }
        inTransitList = updated;
    }

    private int countInTransit(UUID network, ItemStack item, int ownerNode) {
        int total = 0;
        for (WorkWarehouseTemplateSnapshot.InTransitEntry entry : inTransitList) {
            if (entry.ownerNode() == ownerNode && entry.network().equals(network)
                    && ItemStack.isSameItemSameComponents(entry.item(), item)) {
                total += entry.amount();
            }
        }
        return total;
    }

    private void addInTransit(UUID network, ItemStack item, int amount, int ownerNode) {
        for (int i = 0; i < inTransitList.size(); i++) {
            WorkWarehouseTemplateSnapshot.InTransitEntry entry = inTransitList.get(i);
            if (entry.ownerNode() == ownerNode && entry.network().equals(network)
                    && ItemStack.isSameItemSameComponents(entry.item(), item)) {
                inTransitList.set(i, new WorkWarehouseTemplateSnapshot.InTransitEntry(network, item.copy(), entry.amount() + amount, ownerNode));
                return;
            }
        }
        inTransitList.add(new WorkWarehouseTemplateSnapshot.InTransitEntry(network, item.copy(), amount, ownerNode));
    }

    private static int countMatching(ItemStackHandler handler, ItemStack sample) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, sample)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static void extractExact(ItemStackHandler handler, ItemStack sample, int amount) {
        int remaining = amount;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, sample)) {
                continue;
            }
            int take = Math.min(remaining, s.getCount());
            handler.extractItem(i, take, false);
            remaining -= take;
        }
    }

    // ------------------------------------------------------------------
    // 生产阶段状态机
    // ------------------------------------------------------------------

    public List<WorkWarehouseTemplateSnapshot.PanelSnapshot> getTemplateSnapshot() {
        return java.util.Collections.unmodifiableList(templateSnapshot);
    }

    /**
     * 存入这次被分配到的模板链结构快照，覆盖任何之前残留的快照，并重置本次
     * 生产专用的请求列表 / 虚拟末端需求登记状态 / 完成标记。
     */
    public void setTemplateSnapshot(List<WorkWarehouseTemplateSnapshot.PanelSnapshot> snapshot) {
        this.templateSnapshot = snapshot != null ? new ArrayList<>(snapshot) : new ArrayList<>();
        this.inTransitList = new ArrayList<>();
        this.finalDemandRegistered = false;
        this.productionComplete = false;
        setChanged();
        for (int i = 0; i < this.templateSnapshot.size(); i++) {
            WorkWarehouseTemplateSnapshot.PanelSnapshot node = this.templateSnapshot.get(i);
            CreateImp.LOGGER.info("[WorkWarehouse {}] 快照节点[{}]: {} 批次={} 初始状态={} 预期总产出={} 上游={}",
                    worldPosition, i, node.filterItem().getItem(), node.requiredBatches(), node.state(),
                    node.expectedOutputTotal(),
                    node.ingredients().stream().map(WorkWarehouseTemplateSnapshot.IngredientEntry::sourceIndex).toList());
        }
    }

    public List<WorkWarehouseTemplateSnapshot.DemandEntry> getDemandList() {
        return demandList;
    }

    /**
     * 设置本次生产的初始需求列表（原料请求阶段的"现有材料"）：先清空再写入，
     * 避免残留上一次生产留下的脏数据。
     */
    public void setDemandList(List<TemplateMaterialCalculator.NetworkBigItemStack> demand) {
        this.demandList.clear();
        if (demand != null) {
            for (TemplateMaterialCalculator.NetworkBigItemStack entry : demand) {
                this.demandList.add(new WorkWarehouseTemplateSnapshot.DemandEntry(
                        entry.network(), entry.stack().copy(), entry.count(),
                        WorkWarehouseTemplateSnapshot.DemandEntry.OWNER_INITIAL_GATHER));
            }
        }
        setChanged();
        setStage(WorkStage.REQUESTING_MATERIALS);
    }

    public WorkStage getStage() {
        return stage;
    }

    public boolean isProductionComplete() {
        return productionComplete;
    }

    private void setStage(WorkStage newStage) {
        if (this.stage == newStage) {
            return;
        }
        WorkStage oldStage = this.stage;
        this.stage = newStage;
        setChanged();
        notifyUpdate();
        CreateImp.LOGGER.info("[WorkWarehouse {}] 工作阶段切换: {} -> {}", worldPosition, oldStage, newStage);
        if (newStage == WorkStage.PRODUCTION && level instanceof ServerLevel serverLevel) {
            Vec3 center = Vec3.atCenterOf(worldPosition);
            PacketDistributor.sendToPlayersNear(serverLevel, null, center.x, center.y, center.z, 32.0,
                    new WorkWarehouseMaterialsReadyEffectPacket(worldPosition));
            beginProductionStage();
        }
    }

    private int rootIndex() {
        return templateSnapshot.size() - 1;
    }

    /**
     * 需求列表被原料请求阶段的机制清空、正式进入生产阶段的那一刻调用：
     * 快照生成时如果某些节点在材料确认阶段就已经被现有库存直接满足
     * （requiredBatches 为 0），它们在快照里已经是 COMPLETED 状态，但还没有
     * 由它们自己登记"我的产出物一共有多少"这份需求给下游——这里统一补上，
     * 补上之后下游节点（如果因此凑齐了全部上游）自然会在
     * {@link #reconcileDemandList()} 里被识别为可以进入生产。
     */
    private void beginProductionStage() {
        logStorageSnapshot("生产阶段开始");
        for (int i = 0; i < templateSnapshot.size(); i++) {
            if (i == rootIndex()) {
                continue;
            }
            WorkWarehouseTemplateSnapshot.PanelSnapshot node = templateSnapshot.get(i);
            if (node.state() == WorkWarehouseTemplateSnapshot.PanelState.COMPLETED) {
                registerOutputDemand(i);
            }
        }
        settleFromOwnStorage();
        reconcileDemandList();
    }

    /**
     * 找到快照里全部"以 producerIndex 为上游原料来源"的消费节点——同一个
     * 物理仪表可能同时是多个不同下游节点的上游（模板链本质上是有向无环图，
     * 不是树），每一项返回 {consumerIndex, 这个消费者对这份原料的实际需求量}，
     * 需求量按消费者自己的"批次数 × 连接消耗量"计算，这是这个消费者的真实
     * 需求，不受生产者那边是否有现有库存/批次颗粒度取整的影响。
     */
    private List<int[]> findConsumersOf(int producerIndex) {
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < templateSnapshot.size(); i++) {
            WorkWarehouseTemplateSnapshot.PanelSnapshot consumer = templateSnapshot.get(i);
            for (WorkWarehouseTemplateSnapshot.IngredientEntry ie : consumer.ingredients()) {
                if (ie.sourceIndex() == producerIndex) {
                    int qty = ie.amount() * consumer.requiredBatches();
                    result.add(new int[]{i, qty});
                }
            }
        }
        return result;
    }

    /**
     * 由刚变为 COMPLETED 的节点（producerIndex）登记它自己的产出物需求，
     * 分别挂在它的每一个下游消费节点名下——一个生产者可能同时服务多个
     * 消费者，每个消费者各自登记各自需要的数量（消费者自己的批次数 × 连接
     * 消耗量），不是把生产者的总产出重复登记给每一个消费者。
     * <p>
     * 但生产者这一批实际产出（{@link WorkWarehouseTemplateSnapshot.PanelSnapshot#expectedOutputTotal()}，
     * 已经包含材料确认阶段现有库存确认的部分和自己按批次生产的部分）往往
     * 会比"全部消费者需求之和"多——批次颗粒度取整必然产生这个差额。这部分
     * 差额如果不登记任何需求列表条目，就不会被主动收进仓库、永远滞留在
     * 仓库外部，所以这里额外登记一条归属为"副产物"（{@code OWNER_BYPRODUCT}）
     * 的需求，把全部实际产出都收进仓库，多出来的部分最后由生产彻底完成后
     * 的"打包全部剩余物品"那一步一并处理。
     */
    private void registerOutputDemand(int producerIndex) {
        WorkWarehouseTemplateSnapshot.PanelSnapshot producer = templateSnapshot.get(producerIndex);
        List<int[]> consumers = findConsumersOf(producerIndex);
        if (consumers.isEmpty()) {
            CreateImp.LOGGER.warn("[WorkWarehouse {}] 节点[{}]({}) 找不到任何下游消费节点，快照结构异常，全部产出按副产物收集",
                    worldPosition, producerIndex, producer.filterItem().getItem());
        }
        int allocated = 0;
        for (int[] c : consumers) {
            int consumerIndex = c[0];
            int qty = c[1];
            if (qty <= 0) {
                continue;
            }
            demandList.add(new WorkWarehouseTemplateSnapshot.DemandEntry(
                    producer.network(), producer.filterItem().copy(), qty, consumerIndex));
            allocated += qty;
            CreateImp.LOGGER.info("[WorkWarehouse {}] 节点[{}]({}) 登记产出需求给节点[{}]: {} x{}",
                    worldPosition, producerIndex, producer.filterItem().getItem(), consumerIndex,
                    producer.filterItem().getItem(), qty);
        }
        int surplus = producer.expectedOutputTotal() - allocated;
        if (surplus > 0) {
            demandList.add(new WorkWarehouseTemplateSnapshot.DemandEntry(
                    producer.network(), producer.filterItem().copy(), surplus,
                    WorkWarehouseTemplateSnapshot.DemandEntry.OWNER_BYPRODUCT));
            CreateImp.LOGGER.info("[WorkWarehouse {}] 节点[{}]({}) 登记副产物收集需求: {} x{}（预计总产出{} - 消费者需求合计{}）",
                    worldPosition, producerIndex, producer.filterItem().getItem(),
                    producer.filterItem().getItem(), surplus, producer.expectedOutputTotal(), allocated);
        }
        setChanged();
    }

    /**
     * 一个节点变为 COMPLETED 之后，检查下游消费节点是否因此满足"全部上游都已
     * COMPLETED"的条件，如果满足就把它从 IDLE 切换为 WAITING_MATERIALS。
     * 这里只做状态切换，不再登记需求列表条目——需求列表条目改由每个上游
     * 生产者在自己完成时登记（见 {@link #registerOutputDemand}）。
     */
    private void activateConsumerIfReady(int consumerIndex) {
        WorkWarehouseTemplateSnapshot.PanelSnapshot node = templateSnapshot.get(consumerIndex);
        if (node.state() != WorkWarehouseTemplateSnapshot.PanelState.IDLE) {
            return;
        }
        for (WorkWarehouseTemplateSnapshot.IngredientEntry ie : node.ingredients()) {
            if (templateSnapshot.get(ie.sourceIndex()).state() != WorkWarehouseTemplateSnapshot.PanelState.COMPLETED) {
                return;
            }
        }
        templateSnapshot.set(consumerIndex, node.withState(WorkWarehouseTemplateSnapshot.PanelState.WAITING_MATERIALS));
        setChanged();
        notifyUpdate();
        CreateImp.LOGGER.info("[WorkWarehouse {}] 节点[{}]({}) 状态切换: IDLE -> WAITING_MATERIALS（全部上游已完成）",
                worldPosition, consumerIndex, node.filterItem().getItem());
        // 注意：这里不再调用 settleFromOwnStorage()。如果在同一个 tick 的
        // 级联反应里（比如上游节点刚完成、触发这里的下游激活）反复调用它，
        // 每次都会用当前仓库存储的实时快照重新结算一遍需求列表——但如果
        // 这批库存在这同一个 tick 里已经被 monitorConnectedInventory 的
        // 连接库存转移记过一次账了，物理上它们还原样躺在仓库存储里（尚未
        // 被对应节点真正取走），再调用一次 settleFromOwnStorage 会把这
        // 同一批库存当成"新发现的现成库存"重复认领一次，导致需求列表被
        // 提前、错误地清零，而后续真正产出的材料因为已经找不到对应的需求
        // 条目去认领，只能滞留在连接库存里进不了仓库。现在统一改为只在
        // {@link #beginProductionStage()}（生产刚开始，安全）和每个周期性
        // tick 循环里各结算一次，避免同一批库存被多次重复认领。
    }

    /**
     * 根节点寄出最后一批原料、变为 COMPLETED 之后，补一条"等待根节点自身
     * 产出物返回仓库"的虚拟末端需求，收货地址是仓库自己的地址（不是任何
     * 仪表的 recipeAddress），复用同一套需求列表机制来判定整次生产是否
     * 真正完成。
     */
    private void registerFinalDemand(WorkWarehouseTemplateSnapshot.PanelSnapshot rootNode) {
        int totalOutput = rootNode.expectedOutputTotal();
        finalDemandRegistered = true;
        CreateImp.LOGGER.info("[WorkWarehouse {}] 根节点({}) 已完成，登记虚拟末端需求: {} x{} -> 仓库自身地址 \"{}\"",
                worldPosition, rootNode.filterItem().getItem(), rootNode.filterItem().getItem(), totalOutput, address);
        if (totalOutput <= 0) {
            productionComplete = true;
            setChanged();
            notifyUpdate();
            CreateImp.LOGGER.info("[WorkWarehouse {}] 虚拟末端需求数量为0，生产直接判定完成", worldPosition);
            attemptFinalShipment();
            return;
        }
        demandList.add(new WorkWarehouseTemplateSnapshot.DemandEntry(rootNode.network(), rootNode.filterItem().copy(),
                totalOutput, WorkWarehouseTemplateSnapshot.DemandEntry.OWNER_FINAL_PRODUCT));
        setChanged();
        // 注意：这里不再调用 settleFromOwnStorage()，原因见
        // activateConsumerIfReady 里的说明——避免同一个 tick 内对同一批
        // 已经被连接库存监控记过账的库存重复认领。这份虚拟末端需求会在
        // 下一次周期性 tick 里被正常结算。
    }

    /**
     * 目标地址的特殊值：最终产物不走任何打包机（无论是自身贴合的还是连接
     * 库存背后仓储连接站的），而是直接插入工作仓库自己的连接库存里。
     * 这个地址是玩家在配置文件（Cloth Config，"工作仓库配置" 下"返回连接
     * 库存专用地址"）里实时可改的文本项，不是写死的常量——每次判断都直接
     * 读取当前配置值，玩家改了配置立刻生效，不需要重启或者重新加载。
     */
    private static String backToConnectedInventoryAddress() {
        return CreateImp.getConfig().workWarehouseConfig.backToConnectedInventoryAddress;
    }

    /**
     * 生产彻底完成后的最后一步：把工作仓库内部存储里当前的全部物品（包括
     * 最终产物和生产过程中因为批次颗粒度对不齐而剩下的副产物）一次性打包
     * 寄出，收货地址是这次下单真正激活工作仓库时使用的目标地址
     * （{@link #targetAddress}，不是仓库自己的 {@link #address}）。找打包机
     * 的方式和生产阶段发货完全一样——优先用自身贴合的打包机，其次借用连接
     * 库存背后已经接入仓库自身所在网络的打包机；找不到就先不清空存储，
     * 等下一次周期性 tick 再试。寄出成功后才真正清空所有本次生产相关的
     * 缓存数据、切回空闲阶段。
     * <p>
     * 目标地址如果等于配置里的"返回连接库存专用地址"（{@link #backToConnectedInventoryAddress()}，
     * 默认 "/back"），则完全不走打包机这一套流程，直接调用
     * {@link #attemptFinalShipmentBackToConnectedInventory()}。
     */
    private void attemptFinalShipment() {
        logStorageSnapshot("尝试最终发货");
        boolean anyItem = false;
        for (int i = 0; i < storage.getSlots(); i++) {
            if (!storage.getStackInSlot(i).isEmpty()) {
                anyItem = true;
                break;
            }
        }
        if (!anyItem) {
            CreateImp.LOGGER.info("[WorkWarehouse {}] 最终产物为空，直接重置为空闲状态", worldPosition);
            resetToIdle();
            return;
        }
        String backAddress = backToConnectedInventoryAddress();
        if (!backAddress.isBlank() && backAddress.equals(targetAddress)) {
            attemptFinalShipmentBackToConnectedInventory();
            return;
        }

        List<ItemStack> allItems = new ArrayList<>();
        for (int i = 0; i < storage.getSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (!s.isEmpty()) {
                allItems.add(s.copy());
            }
        }
        PackagerBlockEntity packager = findDispatchPackager(behaviour.freqId);
        if (packager == null) {
            CreateImp.LOGGER.info("[WorkWarehouse {}] 生产已完成，但暂时找不到可用打包机寄出最终产物，等待下次重试", worldPosition);
            return;
        }
        for (int i = 0; i < storage.getSlots(); i++) {
            storage.setStackInSlot(i, ItemStack.EMPTY);
        }
        sendItemsSplitIntoPackages(packager, allItems, targetAddress, null, 0, "生产彻底完成后的最终产物打包发货（含副产物）");
        CreateImp.LOGGER.info("[WorkWarehouse {}] 最终产物（含副产物）已全部寄出 -> 目标地址 \"{}\"，共 {} 种物品: {}",
                worldPosition, targetAddress, allItems.size(),
                allItems.stream().map(i -> i.getItem() + " x" + i.getCount()).toList());
        resetToIdle();
    }

    /**
     * 目标地址为 "/back" 时的最终产物处理：不打包、不找任何打包机，直接把
     * 仓库内部存储里的全部物品用 {@link #extractBehaviour} 插入连接库存本身
     * （{@code InvManipulationBehaviour.insert}，机械动力自带的方法，和
     * {@code extract} 用的是同一份 {@code targetCapability}，创建时虽然标记
     * 为"用于提取"，插入方向同样可用）。按槽位逐个尝试插入，插入成功多少
     * 就从仓库存储里真正扣减多少；连接库存已经满、插不下的部分保留在仓库
     * 存储里，不清空、等待下一次重试，避免物品凭空消失。
     */
    private void attemptFinalShipmentBackToConnectedInventory() {
        if (extractBehaviour == null || !extractBehaviour.hasInventory()) {
            CreateImp.LOGGER.info("[WorkWarehouse {}] 目标地址为 \"/back\" 但工作仓库当前没有连接库存，无法送回，等待接好连接库存后重试",
                    worldPosition);
            return;
        }
        boolean allInserted = true;
        List<ItemStack> insertedSummary = new ArrayList<>();
        for (int i = 0; i < storage.getSlots(); i++) {
            ItemStack stack = storage.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remaining = extractBehaviour.insert(stack.copy());
            int insertedCount = stack.getCount() - remaining.getCount();
            if (insertedCount > 0) {
                storage.extractItem(i, insertedCount, false);
                insertedSummary.add(stack.copyWithCount(insertedCount));
            }
            if (!remaining.isEmpty()) {
                allInserted = false;
            }
        }
        if (!allInserted) {
            CreateImp.LOGGER.info("[WorkWarehouse {}] 连接库存已满，部分最终产物未能放回，保留在仓库存储里，等待下次重试", worldPosition);
            return;
        }
        CreateImp.LOGGER.info("[WorkWarehouse {}] 最终产物（含副产物）已全部放回连接库存: {}",
                worldPosition, insertedSummary.stream().map(i -> i.getItem() + " x" + i.getCount()).toList());
        resetToIdle();
    }

    /**
     * 清空所有本次生产相关的缓存数据，切回空闲阶段，工作状态（POWERED）
     * 也一并关闭，等待下一次下单重新激活。
     */
    private void resetToIdle() {
        templateSnapshot = new ArrayList<>();
        demandList = new ArrayList<>();
        inTransitList = new ArrayList<>();
        finalDemandRegistered = false;
        productionComplete = false;
        requestedProduct = ItemStack.EMPTY;
        requestedAmount = 0;
        setStage(WorkStage.IDLE);
        setWorking(false);
        setChanged();
        notifyUpdate();
        CreateImp.LOGGER.info("[WorkWarehouse {}] 已重置为空闲状态，等待下一次下单", worldPosition);
    }

    /**
     * 需求列表变化后统一在这里判断状态推进：原料请求阶段清空即进入生产阶段；
     * 生产阶段里，任何一个 WAITING_MATERIALS 节点自己的需求条目被清空，就
     * 尝试让它完成（寄出原料 + 切换 COMPLETED + 解锁下游/登记虚拟末端需求）；
     * 虚拟末端需求被清空则标记整次生产彻底完成。
     * <p>
     * 每次有节点真正完成后都会重新扫描一遍剩余需求，而不是用一次性算好的
     * 归属集合，避免"下游节点在同一轮里被激活、但因为用的是激活前的归属
     * 快照而被误判为已经满足"的问题。
     */
    private void reconcileDemandList() {
        if (stage == WorkStage.REQUESTING_MATERIALS) {
            if (demandList.isEmpty()) {
                setStage(WorkStage.PRODUCTION);
            }
            return;
        }
        if (stage != WorkStage.PRODUCTION || productionComplete) {
            return;
        }
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            Set<Integer> remainingOwners = new HashSet<>();
            for (WorkWarehouseTemplateSnapshot.DemandEntry entry : demandList) {
                remainingOwners.add(entry.ownerNode());
            }
            for (int i = 0; i < templateSnapshot.size(); i++) {
                WorkWarehouseTemplateSnapshot.PanelSnapshot node = templateSnapshot.get(i);
                if (node.state() != WorkWarehouseTemplateSnapshot.PanelState.WAITING_MATERIALS) {
                    continue;
                }
                if (remainingOwners.contains(i)) {
                    continue;
                }
                if (completeNode(i)) {
                    progressed = true;
                    break;
                }
                // 寄出失败（找不到可用打包机），跳过这个节点继续检查其他已经
                // 就绪的节点，不把它算作"本轮已经处理"，避免对同一个找不到
                // 打包机的节点反复重试造成死循环——它会在下一次周期性 tick
                // 触发 reconcileDemandList 时自然被重新扫描到。
            }
            if (progressed) {
                continue;
            }
            if (finalDemandRegistered && demandList.isEmpty()) {
                // 必须整个需求列表都清空（包括所有归属为副产物的条目）才能判定
                // 生产彻底完成，不能只看虚拟末端需求是否满足——否则某个副产物
                // 还在路上没到货时就会提前打包发货、清空缓存，导致这部分永远丢失。
                productionComplete = true;
                setChanged();
                notifyUpdate();
                CreateImp.LOGGER.info("[WorkWarehouse {}] 需求列表已全部清空（含虚拟末端需求与全部副产物），整次生产彻底完成", worldPosition);
                attemptFinalShipment();
            }
        }
    }

    /**
     * 一个 WAITING_MATERIALS 节点自己的原料需求已经全部到齐，尝试把这批
     * 原料寄出去。返回 true 表示寄出成功，节点已经真正切换为 COMPLETED
     * 并登记好产出需求/解锁下游；返回 false 表示暂时找不到可用的打包机，
     * 节点保持 WAITING_MATERIALS、仓库存储不受影响，等下一次周期性 tick
     * 触发的重新扫描再试——调用方（{@link #reconcileDemandList()}）不会
     * 对同一个失败节点在同一轮内反复重试，避免死循环。
     */
    private boolean completeNode(int index) {
        WorkWarehouseTemplateSnapshot.PanelSnapshot node = templateSnapshot.get(index);
        logStorageSnapshot("节点[" + index + "](" + node.filterItem().getItem() + ") 发货前");
        if (!dispatchNodeIngredients(node)) {
            CreateImp.LOGGER.info("[WorkWarehouse {}] 节点[{}]({}) 原料已齐但暂时找不到可用打包机，保持 WAITING_MATERIALS 等待重试",
                    worldPosition, index, node.filterItem().getItem());
            return false;
        }
        logStorageSnapshot("节点[" + index + "](" + node.filterItem().getItem() + ") 发货后");
        templateSnapshot.set(index, node.withState(WorkWarehouseTemplateSnapshot.PanelState.COMPLETED));
        setChanged();
        notifyUpdate();
        CreateImp.LOGGER.info("[WorkWarehouse {}] 节点[{}]({}) 状态切换: WAITING_MATERIALS -> COMPLETED（原料已寄出）",
                worldPosition, index, node.filterItem().getItem());
        if (index == rootIndex()) {
            registerFinalDemand(node);
            return true;
        }
        List<int[]> consumers = findConsumersOf(index);
        registerOutputDemand(index);
        for (int[] c : consumers) {
            activateConsumerIfReady(c[0]);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 打包机发货
    // ------------------------------------------------------------------

    /**
     * 按节点的按量请求/动力合成规则，把这个节点自己需要的原料从仓库内部
     * 存储里寄出。返回 false 表示找不到任何可用打包机、这次没能寄出（原料
     * 未被扣减，保留在内部存储里，等待下次重试）。
     */
    private boolean dispatchNodeIngredients(WorkWarehouseTemplateSnapshot.PanelSnapshot node) {
        if (node.ingredients().isEmpty() || node.address() == null || node.address().isBlank()) {
            return true;
        }
        PackagerBlockEntity packager = findDispatchPackager(node.network());
        if (packager == null) {
            return false;
        }
        if (node.demandMode()) {
            List<ItemStack> toSend = new ArrayList<>();
            for (WorkWarehouseTemplateSnapshot.IngredientEntry ing : node.ingredients()) {
                int total = ing.amount() * node.requiredBatches();
                if (total <= 0) {
                    continue;
                }
                toSend.add(ing.item().copyWithCount(total));
            }
            for (ItemStack s : toSend) {
                extractExact(storage, s, s.getCount());
            }
            String source = String.format("节点(%s) 按量请求模式=开 批次数=%d（全部批次一次性发出）",
                    node.filterItem().getItem(), node.requiredBatches());
            sendItemsSplitIntoPackages(packager, toSend, node.address(),
                    node.craftingMode() ? node.craftingArrangement() : null, node.requiredBatches(), source);
        } else {
            for (int batch = 0; batch < node.requiredBatches(); batch++) {
                List<ItemStack> toSend = new ArrayList<>();
                for (WorkWarehouseTemplateSnapshot.IngredientEntry ing : node.ingredients()) {
                    if (ing.amount() <= 0) {
                        continue;
                    }
                    toSend.add(ing.item().copyWithCount(ing.amount()));
                }
                for (ItemStack s : toSend) {
                    extractExact(storage, s, s.getCount());
                }
                String source = String.format("节点(%s) 按量请求模式=关 第%d/%d批（按配方单批数量逐批发出）",
                        node.filterItem().getItem(), batch + 1, node.requiredBatches());
                sendItemsSplitIntoPackages(packager, toSend, node.address(),
                        node.craftingMode() ? node.craftingArrangement() : null, 1, source);
            }
        }
        return true;
    }

    /**
     * 把一批物品打包寄出，超出单个包裹容量（9 格）时自动拆分成多个包裹依次
     * 寄出——和仓储管理员普通请求模式下需要多个包裹寄出的情况处理方式一样，
     * 不会因为按量请求模式一次性数量过大就把超出部分静默丢弃。
     * <p>
     * 附加到每个包裹上的合成请求信息（{@code PackageOrderWithCrafts}），其
     * {@code orderedStacks} 字段必须填入这个包裹里真实装的物品——参照原版
     * {@code MixinFactoryPanelBehaviour} 的做法，那边构造发货请求时用的正是
     * "真实物品列表 + 合成表信息"两者合在一起的同一个对象，不是像我之前那样
     * 只给合成表信息、把 orderedStacks 留空。拆分成多个包裹时，每个包裹各自
     * 附加只包含"这个包裹自己装了什么"的 orderedStacks，不是共享同一份。
     *
     * @param craftPattern      动力合成模式下的九宫格配方物品列表，非动力合成
     *                          模式传 null，不附加任何合成请求信息。
     * @param craftCount        这次合成请求声明的批次数（按量请求模式下是
     *                          实际发出的总批次数；非按量请求模式下固定是 1，
     *                          对应"这一批"）。
     * @param sourceDescription 这次发货是由哪个节点、按什么规则触发的说明
     *                          文字，写进日志里，方便直接判断按量请求模式
     *                          有没有生效，而不用靠批次数是否大于 1 反推。
     */
    private void sendItemsSplitIntoPackages(PackagerBlockEntity packager, List<ItemStack> items, String address,
                                            List<ItemStack> craftPattern, int craftCount, String sourceDescription) {
        List<ItemStackHandler> chunks = splitIntoPackageChunks(items);
        int chunkCount = chunks.size();
        for (int i = 0; i < chunkCount; i++) {
            ItemStackHandler chunk = chunks.get(i);
            PackageOrderWithCrafts orderContext = buildCraftContext(chunk, craftPattern, craftCount);
            injectPackage(packager, chunk, address, orderContext);
            CreateImp.LOGGER.info("[WorkWarehouse {}] 发货来源: {}；打包机[{}] 寄出包裹（{}/{}） -> 地址 \"{}\"，内容: {}{}",
                    worldPosition, sourceDescription, packager.getBlockPos(), i + 1, chunkCount, address,
                    describeHandlerContents(chunk), orderContext != null ? "（携带合成请求）" : "");
        }
    }

    /**
     * 把一批物品按 9 格包裹的容量依次分组：每种物品先尝试塞进当前正在组装
     * 的包裹，塞不下（当前包裹已经满）就另起一个新包裹继续塞，直到全部
     * 物品都被分配完毕。
     */
    private static List<ItemStackHandler> splitIntoPackageChunks(List<ItemStack> items) {
        List<ItemStackHandler> chunks = new ArrayList<>();
        ItemStackHandler current = new ItemStackHandler(9);
        for (ItemStack original : items) {
            ItemStack remaining = original.copy();
            while (!remaining.isEmpty()) {
                ItemStack leftover = ItemHandlerHelper.insertItemStacked(current, remaining, false);
                if (leftover.getCount() == remaining.getCount()) {
                    // 当前包裹已经装不下（9 格全部占满），另起一个新包裹继续装。
                    chunks.add(current);
                    current = new ItemStackHandler(9);
                    continue;
                }
                remaining = leftover;
            }
        }
        if (!isHandlerEmpty(current)) {
            chunks.add(current);
        }
        return chunks;
    }

    private static boolean isHandlerEmpty(ItemStackHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String describeHandlerContents(ItemStackHandler handler) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(s.getItem()).append(" x").append(s.getCount());
        }
        return sb.toString();
    }

    /**
     * 构造附加到某一个具体包裹上的合成请求信息，{@code orderedStacks} 填入
     * 这个包裹（{@code chunk}）里真实装的物品——参照原版
     * {@code MixinFactoryPanelBehaviour} 的做法，附加到包裹上的
     * {@code PackageOrderWithCrafts} 应该同时携带真实物品清单和合成表信息，
     * 而不是只有合成表、物品清单留空。{@code craftPattern} 为 null 表示这个
     * 节点没有开启动力合成模式，直接返回 null，不附加任何合成请求。
     */
    private static PackageOrderWithCrafts buildCraftContext(ItemStackHandler chunk, List<ItemStack> craftPattern, int craftCount) {
        if (craftPattern == null || craftPattern.isEmpty()) {
            return null;
        }
        List<BigItemStack> chunkStacks = new ArrayList<>();
        for (int i = 0; i < chunk.getSlots(); i++) {
            ItemStack s = chunk.getStackInSlot(i);
            if (!s.isEmpty()) {
                chunkStacks.add(new BigItemStack(s.copy()));
            }
        }
        return new PackageOrderWithCrafts(
                new PackageOrder(chunkStacks),
                List.of(new PackageOrderWithCrafts.CraftingEntry(
                        new PackageOrder(craftPattern.stream()
                                .map(s -> new BigItemStack(s.copyWithCount(1))).toList()),
                        craftCount)));
    }

    /**
     * 找一个可以用来把这批原料真正寄出去的打包机：优先在工作仓库自身任意
     * 一面贴合的打包机里随机选一个（贴合多个时的选择方式，参照原版多个
     * 仓储连接站打包机随机分配请求的做法）；如果自身没有贴合任何打包机，
     * 退而检查工作仓库的连接库存背后是否另外贴合了一个已经通过仓储连接站
     * 接入本仪表所在网络的打包机，如果有，就借用那一个。两者都找不到则
     * 返回 null，本次发货放弃，等待下次重试。
     */
    private PackagerBlockEntity findDispatchPackager(UUID network) {
        List<PackagerBlockEntity> adjacent = findAdjacentPackagers();
        if (!adjacent.isEmpty()) {
            return adjacent.get(RNG.nextInt(adjacent.size()));
        }
        return findPackagerServingConnectedInventory(network);
    }

    private List<PackagerBlockEntity> findAdjacentPackagers() {
        List<PackagerBlockEntity> result = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(d);
            if (!(level.getBlockEntity(neighborPos) instanceof PackagerBlockEntity pbe)) {
                continue;
            }
            Direction packagerFacing = pbe.getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(null);
            if (packagerFacing == null) {
                continue;
            }
            if (neighborPos.relative(packagerFacing.getOpposite()).equals(worldPosition)) {
                result.add(pbe);
            }
        }
        return result;
    }

    /**
     * 借用连接库存背后、已经通过仓储连接站接入目标网络的打包机——不按方块
     * 坐标扫描（连接库存本身可能是双箱子等跨多个物理方块的结构，单纯扫描
     * 相邻坐标会漏判），而是直接复用机械动力自己的库存身份识别机制：
     * {@link InvManipulationBehaviour#getIdentifiedInventory()} 得到的
     * {@code IdentifiedInventory} 内部的 {@code InventoryIdentifier} 就是
     * 专门处理"同一份库存横跨多个方块"这种情况的，{@code PackagerBlockEntity}
     * 自己也用同一套机制判断"是否在操作同一份库存"（见
     * {@code PackagerBlockEntity#isTargetingSameInventory}），这里直接复用，
     * 而不是自己重新发明一套坐标扫描逻辑。
     */
    private PackagerBlockEntity findPackagerServingConnectedInventory(UUID network) {
        if (extractBehaviour == null || !extractBehaviour.hasInventory()) {
            return null;
        }
        IdentifiedInventory myIdentified = extractBehaviour.getIdentifiedInventory();
        if (myIdentified == null) {
            return null;
        }
        List<PackagerBlockEntity> candidates = new ArrayList<>();
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(network, false)) {
            if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe)) {
                continue;
            }
            PackagerBlockEntity packager = plbe.getPackager();
            if (packager == null) {
                continue;
            }
            if (packager.isTargetingSameInventory(myIdentified)) {
                candidates.add(packager);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(RNG.nextInt(candidates.size()));
    }

    /**
     * 直接向一个真实打包机"注入"一个已经打好包的包裹，效果与
     * {@code PackagerBlockEntity.attemptToSend} 从自己背后容器里取出物品、
     * 打包、播放弹出动画完全一致——只是物品来源换成了工作仓库自己的内部
     * 存储，不经过打包机自身的库存查询流程。
     */
    private static void injectPackage(PackagerBlockEntity packager, ItemStackHandler contents, String address,
                                      PackageOrderWithCrafts orderContext) {
        ItemStack createdBox = PackageItem.containing(contents);
        PackageItem.clearAddress(createdBox);
        if (address != null && !address.isBlank()) {
            PackageItem.addAddress(createdBox, address);
        }
        if (orderContext != null) {
            // 注意：不能用 orderContext.isEmpty() 判断——那个方法只检查
            // orderedStacks（普通物品部分），合成请求信息全部放在
            // orderedCrafts 里，isEmpty() 永远不会因为 orderedCrafts 有内容
            // 而返回 false。buildCraftContext 只有在节点开启了动力合成模式
            // 时才会返回非 null，所以这里直接判断非 null 就足够。
            PackageItem.setOrder(createdBox, RNG.nextInt(), 0, true, 0, true, orderContext);
        }
        if (!packager.heldBox.isEmpty() || packager.animationTicks != 0) {
            packager.queuedExitingPackages.add(new BigItemStack(createdBox, 1));
        } else {
            packager.heldBox = createdBox;
            packager.animationInward = false;
            packager.animationTicks = 20;
            packager.notifyUpdate();
        }
        packager.setChanged();
    }

    // ------------------------------------------------------------------

    public ItemStack getRequestedProduct() {
        return requestedProduct;
    }

    public int getRequestedAmount() {
        return requestedAmount;
    }

    /**
     * 记录本次正在生产的目标物品与请求数量，供护目镜信息展示。
     * 会立即同步给客户端（不像模板链快照/需求列表那样只落盘）。
     */
    public void setRequestedProduct(ItemStack item, int amount) {
        this.requestedProduct = item == null ? ItemStack.EMPTY : item.copy();
        this.requestedAmount = amount;
        setChanged();
        notifyUpdate();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 护目镜提示"避让图标"的真实机制是 LangBuilder.forGoggles 内部会给每行
        // 文字前面加上等宽空格缩进（约4个空格宽度），把文字推离左上角的图标，
        // 不是加空行。这里用我们自己的命名空间 "createimp" 构造 LangBuilder
        // （不能用 CreateLang.builder()，它内部命名空间硬编码成了 "create"，
        // 会导致我们自己的翻译键查不到）。
        if (address.isEmpty()) {
            new LangBuilder("createimp").translate("gui.work_warehouse.no_address")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip);
        }
        if (!isWorking() || requestedProduct.isEmpty()) {
            new LangBuilder("createimp").translate("gui.work_warehouse.no_active_request").forGoggles(tooltip);
            return true;
        }
        new LangBuilder("createimp").translate("gui.work_warehouse.working").forGoggles(tooltip);
        new LangBuilder("createimp").translate("gui.work_warehouse.current_request_prefix")
                .add(CreateLang.itemName(requestedProduct).style(ChatFormatting.GOLD))
                .text(ChatFormatting.GOLD, " x" + requestedAmount)
                .forGoggles(tooltip);
        new LangBuilder("createimp").translate("gui.work_warehouse.stage_prefix")
                .add(new LangBuilder("createimp").translate(stageValueLangKey())
                        .style(ChatFormatting.GOLD))
                .forGoggles(tooltip);
        return true;
    }

    private String stageValueLangKey() {
        if (stage == WorkStage.PRODUCTION) {
            return productionComplete
                    ? "gui.work_warehouse.stage_production_complete_value"
                    : "gui.work_warehouse.stage_production_value";
        }
        return "gui.work_warehouse.stage_requesting_materials_value";
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!isWorking()) {
            return;
        }
        if (pendingReconcile) {
            pendingReconcile = false;
            reconcileDemandList();
        }
        if (stage == WorkStage.PRODUCTION && productionComplete) {
            if (++ticksSinceLastMonitor <= 15) {
                return;
            }
            ticksSinceLastMonitor = 0;
            attemptFinalShipment();
            return;
        }
        if (stage != WorkStage.REQUESTING_MATERIALS && stage != WorkStage.PRODUCTION) {
            return;
        }
        if (++ticksSinceLastMonitor <= 15) {
            return;
        }
        ticksSinceLastMonitor = 0;
        CreateImp.LOGGER.info("[WorkWarehouse {}] [周期性检查] 阶段={} 需求列表条目数={} 请求列表条目数={}",
                worldPosition, stage, demandList.size(), inTransitList.size());
        monitorConnectedInventory();
        requestRemainingDemandFromNetwork();
        reconcileDemandList();
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        address = tag.getString("Address");
        targetAddress = tag.getString("TargetAddress");
        requestedProduct = tag.contains("RequestedProduct")
                ? CatnipCodecUtils.decode(ItemStack.CODEC, registries, tag.get("RequestedProduct")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        requestedAmount = tag.getInt("RequestedAmount");
        stage = tag.contains("Stage")
                ? parseStage(tag.getString("Stage"))
                : WorkStage.IDLE;
        productionComplete = tag.getBoolean("ProductionComplete");
        if (!clientPacket && tag.contains("Storage", Tag.TAG_COMPOUND)) {
            storage.deserializeNBT(registries, tag.getCompound("Storage"));
        }
        if (!clientPacket) {
            templateSnapshot = new ArrayList<>(tag.contains("TemplateSnapshot")
                    ? CatnipCodecUtils.decode(WorkWarehouseTemplateSnapshot.PanelSnapshot.CODEC.listOf(), registries,
                    tag.get("TemplateSnapshot")).orElse(List.of())
                    : List.of());
            demandList = new ArrayList<>(tag.contains("DemandList")
                    ? CatnipCodecUtils.decode(WorkWarehouseTemplateSnapshot.DemandEntry.CODEC.listOf(), registries,
                    tag.get("DemandList")).orElse(List.of())
                    : List.of());
            inTransitList = new ArrayList<>(tag.contains("InTransitList")
                    ? CatnipCodecUtils.decode(WorkWarehouseTemplateSnapshot.InTransitEntry.CODEC.listOf(), registries,
                    tag.get("InTransitList")).orElse(List.of())
                    : List.of());
            finalDemandRegistered = tag.getBoolean("FinalDemandRegistered");
        }
    }

    private static WorkStage parseStage(String name) {
        try {
            return WorkStage.valueOf(name);
        } catch (IllegalArgumentException e) {
            return WorkStage.IDLE;
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Address", address);
        tag.putString("TargetAddress", targetAddress);
        CatnipCodecUtils.encode(ItemStack.CODEC, registries, requestedProduct)
                .ifPresent(encoded -> tag.put("RequestedProduct", encoded));
        tag.putInt("RequestedAmount", requestedAmount);
        tag.putString("Stage", stage.name());
        tag.putBoolean("ProductionComplete", productionComplete);
        if (!clientPacket) {
            tag.put("Storage", storage.serializeNBT(registries));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.PanelSnapshot.CODEC.listOf(), registries, templateSnapshot)
                    .ifPresent(encoded -> tag.put("TemplateSnapshot", encoded));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.DemandEntry.CODEC.listOf(), registries, demandList)
                    .ifPresent(encoded -> tag.put("DemandList", encoded));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.InTransitEntry.CODEC.listOf(), registries, inTransitList)
                    .ifPresent(encoded -> tag.put("InTransitList", encoded));
            tag.putBoolean("FinalDemandRegistered", finalDemandRegistered);
        }
    }
}