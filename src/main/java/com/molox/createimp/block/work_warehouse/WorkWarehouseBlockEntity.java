package com.molox.createimp.block.work_warehouse;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.template_panel.TemplateMaterialCalculator;
import com.molox.createimp.compat.fluidlogistics.FluidLogisticsCompat;
import com.molox.createimp.compat.fluidlogistics.TemplateFluidDisplayHelper;
import com.molox.createimp.util.IPackagerFluidCache;
import net.neoforged.neoforge.fluids.FluidStack;
import com.molox.createimp.util.PackagerSignAddressHelper;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorkWarehouseBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /**
     * 工作仓库当前所处的生产阶段，作为客户端护目镜文案展示的唯一依据。
     */
    public enum WorkStage {
        IDLE,
        REQUESTING_MATERIALS,
        PRODUCTION,
        /**
         * 玩家在进程面板详情界面手动确认中断当前请求后进入的阶段：需求
         * 列表/请求列表已清空，不再接收任何物品、不再产生新的需求，只是
         * 持续尝试把仓库里现有的物品发出去，发出去之后回到 IDLE。
         */
        INTERRUPTING
    }

    private static final Random RNG = new Random();

    /**
     * 按物流网络频率分组、记录"当前真实加载在世界里的工作仓库方块实体"的
     * 注册表——服务端与客户端各自独立一份，沿用 {@link WorkWarehouseNetworkHelper}
     * 原有对外接口（只接受 freqId，不接受 Level 参数）的约定，保持调用方无需改动。
     * <p>
     * 之前查找可用工作仓库依赖的是 Create 自己的
     * {@code LogisticallyLinkedBehaviour.getAllPresent}——那是一份靠方块实体
     * 每隔固定 tick 数"打卡"续命、20 秒过期的临时性缓存，专为打包机链接这类
     * 短暂交互场景设计。一旦某次打卡因为任何原因被跳过或延迟超过 20 秒
     * （不管什么原因导致的），仓库就会从这份缓存里静默消失，即使方块实体本身
     * 完好无损地立在世界里。这里改为登记时机直接绑定方块实体真实的加载/卸载
     * 生命周期（{@link #initialize()}/{@link #remove()}/{@link #onChunkUnloaded()}），
     * 不存在"记录过期"这回事，只要方块实体还真实加载在世界里，就一定能被查到。
     */
    private static final Map<UUID, Set<WorkWarehouseBlockEntity>> ACTIVE_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<WorkWarehouseBlockEntity>> ACTIVE_REGISTRY_CLIENT = new ConcurrentHashMap<>();

    private static Map<UUID, Set<WorkWarehouseBlockEntity>> registryFor(boolean clientSide) {
        return clientSide ? ACTIVE_REGISTRY_CLIENT : ACTIVE_REGISTRY;
    }

    /**
     * 供 {@link WorkWarehouseNetworkHelper} 查询某个网络下所有当前真实加载的
     * 工作仓库，替代原先的 {@code LogisticallyLinkedBehaviour.getAllPresent}。
     */
    public static Collection<WorkWarehouseBlockEntity> getAllPresent(UUID freqId, boolean clientSide) {
        if (freqId == null) {
            return List.of();
        }
        Set<WorkWarehouseBlockEntity> set = registryFor(clientSide).get(freqId);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        // 防御性过滤：正常情况下 remove()/onChunkUnloaded() 会及时清理，
        // 这里再兜底剔除任何已经失效但未及时清理掉的实例。
        List<WorkWarehouseBlockEntity> result = new ArrayList<>(set.size());
        for (WorkWarehouseBlockEntity be : set) {
            if (be.getLevel() != null && !be.isRemoved()) {
                result.add(be);
            }
        }
        return result;
    }

    /**
     * 跨所有网络频率、遍历当前所有真实加载的工作仓库——供打包机 Mixin 反查
     * "这个连接储存背后到底归属哪些工作仓库"时使用。之所以不按网络过滤，
     * 是因为在打包机这一侧（{@code unwrapBox} 触发的那一刻）并不必然能提前
     * 知道该按哪个网络频率去缩小范围；而"是不是同一份库存"这个判断本身
     * （见 {@code PackagerBlockEntity#isTargetingSameInventory}）跟网络频率
     * 无关，只看物理容器身份，所以直接把所有当前在工作、且判定标准要求
     * "只按连接储存筛选、允许被多个工作仓库共用"的候选一次性给全，交由
     * 调用方自己按需求列表逐个匹配。
     */
    public static Collection<WorkWarehouseBlockEntity> getAllActiveAcrossAllNetworks(boolean clientSide) {
        Map<UUID, Set<WorkWarehouseBlockEntity>> registry = registryFor(clientSide);
        if (registry.isEmpty()) {
            return List.of();
        }
        List<WorkWarehouseBlockEntity> result = new ArrayList<>();
        for (Set<WorkWarehouseBlockEntity> set : registry.values()) {
            for (WorkWarehouseBlockEntity be : set) {
                if (be.getLevel() != null && !be.isRemoved()) {
                    result.add(be);
                }
            }
        }
        return result;
    }

    private UUID registeredFreqId = null;

    /**
     * 把自己登记进（或者从旧频率移出、登记进新频率）注册表。
     * 调用时机：{@link #initialize()} 时首次登记；{@link #tick()} 里每次
     * 顺带核对一次，覆盖玩家用网络管理器重新调谐、频率发生变化的情况。
     */
    private void syncActiveRegistration() {
        if (level == null) {
            return;
        }
        UUID currentFreqId = (behaviour != null) ? behaviour.freqId : null;
        if (Objects.equals(currentFreqId, registeredFreqId)) {
            return;
        }
        boolean clientSide = level.isClientSide();
        if (registeredFreqId != null) {
            Set<WorkWarehouseBlockEntity> oldSet = registryFor(clientSide).get(registeredFreqId);
            if (oldSet != null) {
                oldSet.remove(this);
                if (oldSet.isEmpty()) {
                    registryFor(clientSide).remove(registeredFreqId);
                }
            }
        }
        if (currentFreqId != null) {
            registryFor(clientSide)
                    .computeIfAbsent(currentFreqId, key -> ConcurrentHashMap.newKeySet())
                    .add(this);
        }
        registeredFreqId = currentFreqId;
    }

    private void unregisterFromActiveRegistry() {
        if (level == null || registeredFreqId == null) {
            return;
        }
        boolean clientSide = level.isClientSide();
        Set<WorkWarehouseBlockEntity> set = registryFor(clientSide).get(registeredFreqId);
        if (set != null) {
            set.remove(this);
            if (set.isEmpty()) {
                registryFor(clientSide).remove(registeredFreqId);
            }
        }
        registeredFreqId = null;
    }

    public LogisticallyLinkedBehaviour behaviour;
    public InvManipulationBehaviour extractBehaviour;
    public final WorkWarehouseItemStackHandler storage = new WorkWarehouseItemStackHandler(this);
    public final WorkWarehouseFluidStorage fluidStorage = new WorkWarehouseFluidStorage(this);
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
     * 已经通过 {@link #announceProducerCompletions()} 记录过"产物生产完成"
     * 日志的生产节点下标集合，避免同一个节点被重复通报。只在服务端持久化。
     */
    private final Set<Integer> producerCompletionAnnounced = new HashSet<>();

    /**
     * 生产阶段专用："虚拟末端需求"（等待根节点自身产出物返回仓库）是否已经
     * 登记过。用于区分"还没登记"与"登记后已经被满足清空"这两种情况——两者
     * 都会表现为需求列表里找不到 owner 为 OWNER_FINAL_PRODUCT 的条目。
     */
    private boolean finalDemandRegistered = false;

    /**
     * 每个节点在材料确认阶段就已经被现有库存直接确认、原料请求阶段已经运抵
     * 仓库、但截至目前还没被任何需求条目认领过的"现成产出"数量，键是节点在
     * {@link #templateSnapshot} 里的下标。
     * <p>
     * 这份账本在 {@link #beginProductionStage()} 生产刚开始那一刻，用一份
     * 仓库存储的共享临时快照统一结算一次性算出——必须共享同一份快照而不是
     * 每个节点各自单独核对仓库存储，否则模板链里如果有多个不同节点（不管
     * 是模板仪表还是普通仪表叶子节点）恰好监测的是同一种物品，会各自把
     * 仓库里同一批物理库存重复当成"我的现成产出"分别认领，凭空多算出根本
     * 不存在的库存。算好之后固定不变，节点无论什么时候真正变为 COMPLETED，
     * 都直接查这份账本（见 {@link #registerOutputDemand}），不再临时去查
     * 仓库存储，从根源上避免"先完成的节点抢跑，后完成的节点查到的库存已经
     * 被别人拿走"这类时序问题。只在服务端持久化。
     */
    private final Map<Integer, Integer> preExistingCredit = new LinkedHashMap<>();

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

    /**
     * 本次工作在 {@link #activate} 时刻记录的世界时间（{@code level.getGameTime()}），
     * 供进程面板界面计算并展示"经过时间"。每次重新激活都会覆盖为新的时间点。
     * 会同步给客户端。
     */
    private long activationGameTime = 0;

    public long getActivationGameTime() {
        return activationGameTime;
    }

    /**
     * 本次工作从激活到当前发生过的全部事件日志，只在服务端持久化。
     * 每条日志都存了记录那一刻相对 {@link #activationGameTime} 的经过 tick 数，
     * 供以后做"日志详情"界面时使用；{@link #resetToIdle()} 回到空闲状态时
     * 会被整体清空。
     */
    private final List<WorkWarehouseTemplateSnapshot.LogEntry> logEntries = new ArrayList<>();

    /**
     * 最新一条日志的翻译键、参数与经过时间，专门拆出来单独同步给客户端
     * （不像 {@link #logEntries} 那样只在服务端持久化），供进程面板界面
     * 展示"最新日志"这一行使用，避免把完整日志列表也一起同步造成不必要
     * 的网络开销。
     */
    private String latestLogKey = "";
    private List<WorkWarehouseTemplateSnapshot.LogArg> latestLogArgs = new ArrayList<>();
    private WorkWarehouseTemplateSnapshot.LogCategory latestLogCategory = WorkWarehouseTemplateSnapshot.LogCategory.NORMAL;
    private long latestLogElapsedTicks = 0;

    public List<WorkWarehouseTemplateSnapshot.LogEntry> getLogEntries() {
        return java.util.Collections.unmodifiableList(logEntries);
    }

    /**
     * 按调用方（客户端界面调用时就是那个客户端自己）当前选择的语言，
     * 把最新一条日志的翻译键+参数解析成最终要显示的文字。
     */
    public String getLatestLogMessage() {
        return new WorkWarehouseTemplateSnapshot.LogEntry(latestLogElapsedTicks, latestLogKey, latestLogArgs, latestLogCategory)
                .resolveMessage();
    }

    /** 最新一条日志的展示分类（普通/请求中断专用红色），供卡片界面决定颜色。 */
    public WorkWarehouseTemplateSnapshot.LogCategory getLatestLogCategory() {
        return latestLogCategory;
    }

    public long getLatestLogElapsedTicks() {
        return latestLogElapsedTicks;
    }

    /**
     * 记录一条普通分类的事件日志，等价于
     * {@code addLog(LogCategory.NORMAL, key, args)}。
     */
    private void addLog(String key, WorkWarehouseTemplateSnapshot.LogArg... args) {
        addLog(WorkWarehouseTemplateSnapshot.LogCategory.NORMAL, key, args);
    }

    /**
     * 记录一条事件日志：计算当前相对激活时刻的经过 tick 数，追加进
     * {@link #logEntries}，同时更新同步给客户端的"最新一条日志"字段。
     * {@code key} 是语言文件里的翻译键，{@code args} 是代入其中 {@code %s}
     * 占位符的参数——不管是物品名字还是措辞文字，都不在这里直接拼成中文
     * 字符串存死，而是等界面渲染那一刻，由渲染它的客户端自己的语言解析，
     * 这样同一条日志在不同语言的客户端上会显示成对应的语言。{@code category}
     * 决定这条日志整体的展示颜色（目前只有"请求中断"这几条用 CANCEL）。
     */
    private void addLog(WorkWarehouseTemplateSnapshot.LogCategory category, String key,
                        WorkWarehouseTemplateSnapshot.LogArg... args) {
        if (level == null || level.isClientSide()) {
            return;
        }
        long elapsed = Math.max(0, level.getGameTime() - activationGameTime);
        List<WorkWarehouseTemplateSnapshot.LogArg> argList = List.of(args);
        logEntries.add(new WorkWarehouseTemplateSnapshot.LogEntry(elapsed, key, argList, category));
        latestLogKey = key;
        latestLogArgs = argList;
        latestLogCategory = category;
        latestLogElapsedTicks = elapsed;
        setChanged();
        notifyUpdate();
    }

    private static WorkWarehouseTemplateSnapshot.LogArg itemArg(ItemStack item, int amount) {
        return WorkWarehouseTemplateSnapshot.LogArg.items(
                List.of(new WorkWarehouseTemplateSnapshot.LogArg.ItemCount(item, amount)));
    }

    /**
     * 把同一种物品（按 {@link ItemStack#isSameItemSameComponents}）的数量
     * 合并到一起，用于日志内容展示——同一次事件里同一种物品可能因为分批
     * 提取/请求而拆成多个 {@code ItemStack}，日志里应该显示合并后的总量。
     */
    private static List<ItemStack> mergeItems(List<ItemStack> items) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack item : items) {
            if (item.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (int i = 0; i < merged.size(); i++) {
                if (ItemStack.isSameItemSameComponents(merged.get(i), item)) {
                    merged.set(i, merged.get(i).copyWithCount(merged.get(i).getCount() + item.getCount()));
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(item.copy());
            }
        }
        return merged;
    }

    private static WorkWarehouseTemplateSnapshot.LogArg itemsArg(List<ItemStack> items) {
        List<WorkWarehouseTemplateSnapshot.LogArg.ItemCount> counts = new ArrayList<>();
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                counts.add(new WorkWarehouseTemplateSnapshot.LogArg.ItemCount(item, item.getCount()));
            }
        }
        return WorkWarehouseTemplateSnapshot.LogArg.items(counts);
    }

    /**
     * 把一组真实 {@link FluidStack} 转成日志系统能理解的展示物列表——每种
     * 流体转成一份虚拟流体展示物（只用来取名字/图标），真实数量单独通过
     * {@link ItemStack#copyWithCount} 承载，{@link WorkWarehouseTemplateSnapshot.LogArg#resolve()}
     * 检测到是流体展示物时会自动改用流体单位格式化这个数量，不需要另外
     * 维护一套流体专属的日志参数类型。
     */
    private static List<ItemStack> fluidsToLogItems(List<FluidStack> fluids) {
        List<ItemStack> result = new ArrayList<>();
        if (!FluidLogisticsCompat.isLoaded()) {
            return result;
        }
        for (FluidStack fluid : fluids) {
            if (fluid == null || fluid.isEmpty()) {
                continue;
            }
            ItemStack virtual = TemplateFluidDisplayHelper.createVirtualFluidGhostStack(fluid);
            result.add(virtual.copyWithCount(fluid.getAmount()));
        }
        return result;
    }

    private static WorkWarehouseTemplateSnapshot.LogArg demandEntriesArg(
            List<WorkWarehouseTemplateSnapshot.DemandEntry> entries) {
        List<ItemStack> items = new ArrayList<>();
        for (WorkWarehouseTemplateSnapshot.DemandEntry entry : entries) {
            items.add(entry.item().copyWithCount(entry.amount()));
        }
        return itemsArg(mergeItems(items));
    }

    private List<ItemStack> currentStorageContents() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < storage.getSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (!s.isEmpty()) {
                items.add(s.copy());
            }
        }
        return mergeItems(items);
    }


    /**
     * 生产阶段/最终产物阶段共用的"哪个环节"措辞翻译键：原料请求阶段是
     * "原料"，生产阶段是"产物"，代入"从连接储存/打包机/物流网络接收%s"
     * 这类模板的第一个参数。
     */
    private WorkWarehouseTemplateSnapshot.LogArg materialOrProductLabelArg() {
        return WorkWarehouseTemplateSnapshot.LogArg.key(stage == WorkStage.PRODUCTION
                ? "createimp.log.label_product" : "createimp.log.label_material");
    }

    /**
     * 目标地址在日志里的呈现：如果这个地址正好是配置里的"返回连接库存"
     * 专用地址，就显示成翻译后的"连接储存/连接库存"这类 UI 词汇，否则
     * 原样显示玩家自己设置的地址字符串（不需要翻译）。
     */
    private WorkWarehouseTemplateSnapshot.LogArg addressArg(String address, String connectedStorageKey) {
        String backAddr = backToConnectedInventoryAddress();
        if (!backAddr.isBlank() && backAddr.equals(address)) {
            return WorkWarehouseTemplateSnapshot.LogArg.key(connectedStorageKey);
        }
        return WorkWarehouseTemplateSnapshot.LogArg.text(address);
    }

    /**
     * 快照列表里，每个已经登记过产出需求（即已经变为 COMPLETED，或者材料
     * 计算阶段就已经被现有库存直接满足）的生产节点，一旦它自己登记出去的
     * 全部产出需求条目（{@link WorkWarehouseTemplateSnapshot.DemandEntry#sourceProducerIndex()}
     * 等于该节点下标）都从需求列表里清空，就代表这个节点的产出物已经
     * 全部到达仓库——包括根节点自己（对应的是"虚拟末端需求"清空，也就是
     * 整次生产彻底完成的那一刻），每个节点只会被记录一次。
     */
    private void announceProducerCompletions() {
        Set<Integer> remainingProducers = new HashSet<>();
        for (WorkWarehouseTemplateSnapshot.DemandEntry entry : demandList) {
            if (entry.sourceProducerIndex() >= 0) {
                remainingProducers.add(entry.sourceProducerIndex());
            }
        }
        for (int i = 0; i < templateSnapshot.size(); i++) {
            WorkWarehouseTemplateSnapshot.PanelSnapshot node = templateSnapshot.get(i);
            if (node.state() != WorkWarehouseTemplateSnapshot.PanelState.COMPLETED) {
                continue;
            }
            if (remainingProducers.contains(i) || producerCompletionAnnounced.contains(i)) {
                continue;
            }
            // 叶子节点（普通仪表，不是模板仪表）本身不生产任何东西，只是原料
            // 来源，不需要"生产完成"这条日志；最开始材料计算阶段就已经被
            // 现有库存直接满足的模板仪表（requiredBatches 为 0，从来没有真正
            // 走过 completeNode 的寄出流程）同样不需要——它没有"开始生产"，
            // 也就谈不上"生产完成"。这两种情况都直接标记为已通报，跳过日志。
            producerCompletionAnnounced.add(i);
            if (!node.templatePanel() || node.requiredBatches() == 0) {
                continue;
            }
            addLog("createimp.log.node_production_complete",
                    itemArg(node.filterItem(), node.requiredBatches() * node.recipeOutput()));
        }
    }

    /**
     * 整次生产彻底完成的共用处理：{@link #registerFinalDemand} 里虚拟末端
     * 需求数量为 0 的特殊分支，和 {@link #reconcileDemandList()} 里需求列表
     * 正常清空的分支，都会走到这里，避免两处各自重复写一遍同样的三条日志。
     */
    private void markProductionComplete() {
        productionComplete = true;
        setChanged();
        notifyUpdate();
        addLog("createimp.log.all_production_complete");
        addLog("createimp.log.all_products", itemsArg(currentStorageContents()));
        addLog("createimp.log.enter_production_complete");
        attemptFinalShipment();
    }

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

    @Override
    public void initialize() {
        super.initialize();
        syncActiveRegistration();
    }

    @Override
    public void remove() {
        unregisterFromActiveRegistry();
        super.remove();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        // 区块卸载期间这个方块实体不会再被 tick，也就不可能真正参与任何
        // 生产调度——和"真的被移除"一样，先从注册表里移出；区块重新加载、
        // tick() 恢复执行后，syncActiveRegistration() 会自动重新登记回去。
        unregisterFromActiveRegistry();
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
        activationGameTime = level.getGameTime();
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
        addLog("createimp.log.request_sent",
                itemArg(requestedProduct, requestedAmount),
                addressArg(targetAddress, "createimp.log.connected_storage_short"));
        addLog("createimp.log.enter_requesting_materials");
        addLog("createimp.log.waiting_materials", demandEntriesArg(demandList));
        monitorConnectedInventory();
        reconcileDemandList();
        requestRemainingDemandFromNetwork();
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
        List<ItemStack> transferred = new ArrayList<>();
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
            transferred.add(extracted.copy());
            int remaining = entry.amount() - extracted.getCount();
            if (remaining > 0) {
                updated.add(new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), remaining,
                        entry.ownerNode(), entry.sourceProducerIndex()));
            }
        }
        if (changed) {
            demandList = updated;
            setChanged();
            addLog("createimp.log.received_from_storage", materialOrProductLabelArg(), itemsArg(mergeItems(transferred)));
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
            shortfallByNetwork.computeIfAbsent(entry.network(), key -> new ArrayList<>())
                    .add(new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), shortfall,
                            entry.ownerNode(), entry.sourceProducerIndex()));
        }

        boolean changed = false;
        List<ItemStack> requested = new ArrayList<>();
        for (Map.Entry<UUID, List<WorkWarehouseTemplateSnapshot.DemandEntry>> networkGroup : shortfallByNetwork.entrySet()) {
            UUID network = networkGroup.getKey();
            List<WorkWarehouseTemplateSnapshot.DemandEntry> shortfalls = networkGroup.getValue();

            // 同一个网络内，如果好几条需求都要同一种物品，先按物品把数量合并
            // 成一条再发出去——这样同一种物品只发一次请求，Create 会尽量把
            // 它们塞进同一批包裹，不会因为拆成好几条小请求而多发几个包裹、
            // 拖慢物流效率。因为每种物品现在只对应一条合并后的请求，Create
            // 返回"这条请求实际匹配到了多少"是唯一、准确、没有归属歧义的
            // （不像之前"每条需求各自独立发"那样啰嗦，也不像更早"合并发送但
            // 又用共享池瞎猜每条各分到多少"那样会算错账）。
            List<ItemMatchAmount> mergedShortfalls = new ArrayList<>();
            for (WorkWarehouseTemplateSnapshot.DemandEntry e : shortfalls) {
                addMatchAmount(mergedShortfalls, e.item(), e.amount());
            }
            List<BigItemStack> stacks = new ArrayList<>();
            for (ItemMatchAmount m : mergedShortfalls) {
                stacks.add(new BigItemStack(m.sample, m.count));
            }

            PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(stacks);
            com.google.common.collect.Multimap<PackagerBlockEntity, com.simibubi.create.content.logistics.packager.PackagingRequest> requests =
                    LogisticsManager.findPackagersForRequest(network, order,
                            extractBehaviour != null ? extractBehaviour.getIdentifiedInventory() : null, address);
            if (requests.isEmpty()) {
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
                continue;
            }

            // 在 performPackageRequests 真正执行、修改这些 PackagingRequest 的
            // 数量之前，先按物品汇总一下"这次总共计划匹配到多少"——因为每种
            // 物品现在只对应一条合并请求，这个汇总值就是准确的实际匹配量。
            List<ItemMatchAmount> matched = new ArrayList<>();
            for (com.simibubi.create.content.logistics.packager.PackagingRequest req : requests.values()) {
                addMatchAmount(matched, req.item(), req.getCount());
            }

            LogisticsManager.performPackageRequests(requests);

            // 按需求列表原本的先后顺序，把每种物品准确匹配到的总量依次分给
            // 各条原始需求——库存不够满足全部需求时，排在后面的这一轮先分不
            // 到，等下一次周期性重试时再继续申请剩余缺口。
            for (WorkWarehouseTemplateSnapshot.DemandEntry e : shortfalls) {
                int matchedAmount = takeMatchAmount(matched, e.item(), e.amount());
                if (matchedAmount <= 0) {
                    continue;
                }
                addInTransit(e.network(), e.item(), matchedAmount, e.ownerNode());
                changed = true;
                requested.add(e.item().copyWithCount(matchedAmount));
            }
        }
        if (changed) {
            setChanged();
            addLog("createimp.log.requested_from_network",
                    materialOrProductLabelArg(), itemsArg(mergeItems(requested)),
                    WorkWarehouseTemplateSnapshot.LogArg.text(address));
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
                        ? new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), remaining,
                        entry.ownerNode(), entry.sourceProducerIndex())
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
        addLog("createimp.log.received_from_packager", materialOrProductLabelArg(), itemsArg(mergeItems(items)));
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
    private long lastSettleGameTime = -1L;

    /**
     * 这份需求列表结算，同一个游戏 tick 内只会真正执行一次——{@code
     * settleFromOwnStorage} 每次都是拿 {@code storage}/{@code fluidStorage}
     * 现在这一刻的真实内容去核对需求列表，本身不会改变真实存储（真正的
     * 物理扣减发生在消费节点自己发货的时候），如果同一个 tick 内因为
     * {@code pendingReconcile} 即时触发和每16 tick一次的周期性触发恰好都
     * 命中、而这中间存储又没有发生任何变化，就会把同一批"看起来可用"的
     * 存量重复认领两次，错误地把需求条目清得比实际到账的还多——这正是
     * 之前那次"最终多输出了10B岩浆"的根因（当时是同一次调用内部反复
     * 递归导致，这里额外加一层同 tick 去重，把"不同调用点凑巧撞在同一
     * tick"这个更小概率的同类风险也一并堵上）。
     */
    private void settleFromOwnStorage() {
        if (demandList.isEmpty()) {
            return;
        }
        if (level != null) {
            long now = level.getGameTime();
            if (now == lastSettleGameTime) {
                return;
            }
            lastSettleGameTime = now;
        }
        WorkWarehouseItemStackHandler scratch = storage.copy();
        WorkWarehouseFluidStorage fluidScratch = fluidStorage.copy();
        List<WorkWarehouseTemplateSnapshot.DemandEntry> updated = new ArrayList<>();
        boolean changed = false;
        for (WorkWarehouseTemplateSnapshot.DemandEntry entry : demandList) {
            int claim;
            if (isFluidIngredient(entry.item())) {
                FluidStack sample = TemplateFluidDisplayHelper.getFluid(entry.item());
                int available = fluidScratch.getAmount(sample);
                claim = Math.min(entry.amount(), available);
                if (claim > 0) {
                    fluidScratch.extractFluid(sample, claim);
                }
            } else {
                int available = countMatching(scratch, entry.item());
                claim = Math.min(entry.amount(), available);
                if (claim > 0) {
                    extractExact(scratch, entry.item(), claim);
                }
            }
            if (claim > 0) {
                changed = true;
                int remaining = entry.amount() - claim;
                if (remaining > 0) {
                    updated.add(new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), remaining,
                            entry.ownerNode(), entry.sourceProducerIndex()));
                }
            } else {
                updated.add(entry);
            }
        }
        if (changed) {
            demandList = updated;
            setChanged();
        }
    }

    /**
     * 判断一份需求/原料条目代表的是不是流体包裹的虚拟流体过滤物——只有
     * 装了流体包裹时才可能为真；未装的情况下这个判断恒为 false，所有流体
     * 相关分支自然不会被触发，行为退化成纯物品，与普通仪表一致。
     */
    private static boolean isFluidIngredient(ItemStack item) {
        return FluidLogisticsCompat.isLoaded() && TemplateFluidDisplayHelper.isVirtualFluidDisplay(item);
    }

    /** 工作仓库"连接储存"的真实坐标；非连接模式或者没有连接库存行为时返回 null。 */
    public BlockPos getConnectedInventoryPos() {
        if (extractBehaviour == null || !extractBehaviour.hasInventory()) {
            return null;
        }
        try {
            return extractBehaviour.getTarget().getConnectedPos();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 工作仓库"连接储存"的 {@code IdentifiedInventory}——机械动力自己判断
     * "是不是同一份库存"的标准身份标识，天然正确处理跨方块的多方块容器
     * （比如保险库）。非连接模式或者没有连接库存行为时返回 null。
     */
    public com.simibubi.create.content.logistics.packager.IdentifiedInventory getConnectedIdentifiedInventory() {
        if (extractBehaviour == null || !extractBehaviour.hasInventory()) {
            return null;
        }
        return extractBehaviour.getIdentifiedInventory();
    }

    /**
     * 只读检查：这份流体、这个数量，是否在需求列表里还有对应的剩余额度——
     * 供打包机 Mixin 在接收流体包裹时（包括 {@code simulate=true} 的模拟检查）
     * 判断要不要接收，本身不做任何扣减。
     */
    public boolean matchesFluidDemand(FluidStack fluid, int amount) {
        if (fluid == null || fluid.isEmpty() || amount <= 0 || demandList.isEmpty()) {
            return false;
        }
        int remaining = amount;
        for (WorkWarehouseTemplateSnapshot.DemandEntry entry : demandList) {
            if (remaining <= 0) {
                break;
            }
            if (!isFluidIngredient(entry.item())
                    || !TemplateFluidDisplayHelper.isSameFluidType(entry.item(), fluid)) {
                continue;
            }
            remaining -= Math.min(remaining, entry.amount());
        }
        return remaining <= 0;
    }

    /**
     * 真正扣减需求列表里这份流体的额度——只应该在打包机 Mixin 确认
     * {@code simulate=false}（真正执行、不是模拟检查）时调用一次。跟
     * {@link #consumeFromDemandList} 同样的原因，这里只标记
     * {@code pendingReconcile}，不同步触发 {@link #reconcileDemandList()}，
     * 避免在打包机 {@code unwrapBox} 的调用栈内部重入。
     */
    public void consumeFluidFromDemandList(FluidStack fluid, int amount) {
        if (fluid == null || fluid.isEmpty() || amount <= 0 || demandList.isEmpty()) {
            return;
        }
        List<WorkWarehouseTemplateSnapshot.DemandEntry> working = new ArrayList<>(demandList);
        int toConsume = amount;
        for (int i = 0; i < working.size() && toConsume > 0; i++) {
            WorkWarehouseTemplateSnapshot.DemandEntry entry = working.get(i);
            if (entry == null || !isFluidIngredient(entry.item())
                    || !TemplateFluidDisplayHelper.isSameFluidType(entry.item(), fluid)) {
                continue;
            }
            int consumed = Math.min(entry.amount(), toConsume);
            toConsume -= consumed;
            int remaining = entry.amount() - consumed;
            working.set(i, remaining > 0
                    ? new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), remaining,
                    entry.ownerNode(), entry.sourceProducerIndex())
                    : null);
        }
        working.removeIf(java.util.Objects::isNull);
        demandList = working;
        setChanged();
        addLog("createimp.log.received_from_packager", materialOrProductLabelArg(),
                itemArg(TemplateFluidDisplayHelper.createVirtualFluidGhostStack(fluid), amount));
        pendingReconcile = true;
    }

    /**
     * 周期性把"自己贴合的打包机"和"连接储存背后的打包机"身上累计的流体
     * 缓存转移进自己的流体存储——跟 {@link #monitorConnectedInventory()}
     * 同样的"先到先得、没有预留机制"的哲学：缓存里的流体在打包机接收那一刻
     * 就已经针对这个仓库的需求扣减过账面额度了，这里只是把物理位置真正
     * 转移过来，不再重复做匹配判断。
     */
    private void monitorPackagerFluidCaches() {
        if (!FluidLogisticsCompat.isLoaded() || level == null || level.isClientSide()) {
            return;
        }
        List<PackagerBlockEntity> adjacent = findAdjacentPackagers();
        List<PackagerBlockEntity> candidates = new ArrayList<>(adjacent);
        PackagerBlockEntity connected = extractBehaviour != null && extractBehaviour.hasInventory()
                ? findPackagerServingConnectedInventory(behaviour != null ? behaviour.freqId : null, null)
                : null;
        if (connected != null && !candidates.contains(connected)) {
            candidates.add(connected);
        }
        for (PackagerBlockEntity packager : candidates) {
            if (!(packager instanceof IPackagerFluidCache cache)) {
                continue;
            }
            if (cache.createimp$isCachedFluidEmpty()) {
                continue;
            }
            List<FluidStack> nonEmpty = cache.createimp$nonEmptyCachedFluids();
            for (FluidStack tank : nonEmpty) {
                FluidStack taken = cache.createimp$extractCachedFluid(tank, tank.getAmount());
                if (!taken.isEmpty()) {
                    int overflow = fluidStorage.addFluid(taken);
                    if (overflow > 0) {
                        // 极端情况下（100 个槽位全部占满且都不是同种流体）放不下，
                        // 原样放回缓存，等待下次重试，不凭空丢失。
                        FluidStack back = taken.copy();
                        back.setAmount(overflow);
                        cache.createimp$addCachedFluid(back);
                    }
                }
            }
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

    /**
     * 注意：不能假设 {@code handler.extractItem(slot, take, false)} 一次调用
     * 就能把 {@code take} 这么多全部取走——工作仓库自己的存储（{@link WorkWarehouseItemStackHandler}）
     * 允许单个槽位无限堆叠，但继承自原版 {@code ItemStackHandler} 的
     * {@code extractItem} 单次调用仍然会被物品本身的堆叠上限（比如原木/
     * 木板是 64）截断，一次最多只会真正拿出堆叠上限那么多，返回值的
     * 数量才是真实拿到的数量。之前这里没有检查返回值，直接假设 take 已经
     * 全部到手，导致"这一格堆了超过堆叠上限数量"时会少拿一部分，且这部分
     * 从此再也不会被尝试提取，永远滞留在仓库里、最后被当成多余产物一起
     * 打包发走——这是"凭空多出来的副产物"的真正原因。现在改成检查真实
     * 提取到的数量，同一格没拿够就继续对同一格重复提取，直到这一格被
     * 掏空或者凑够为止。
     */
    private static void extractExact(ItemStackHandler handler, ItemStack sample, int amount) {
        int remaining = amount;
        for (int i = 0; i < handler.getSlots() && remaining > 0; i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, sample)) {
                continue;
            }
            while (remaining > 0) {
                ItemStack current = handler.getStackInSlot(i);
                if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, sample)) {
                    break;
                }
                int want = Math.min(remaining, current.getCount());
                ItemStack extracted = handler.extractItem(i, want, false);
                int actuallyTaken = extracted.getCount();
                if (actuallyTaken <= 0) {
                    break;
                }
                remaining -= actuallyTaken;
            }
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
        this.preExistingCredit.clear();
        setChanged();
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
                        WorkWarehouseTemplateSnapshot.DemandEntry.OWNER_INITIAL_GATHER,
                        WorkWarehouseTemplateSnapshot.DemandEntry.NO_PRODUCER));
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
        if (newStage == WorkStage.PRODUCTION && oldStage == WorkStage.REQUESTING_MATERIALS) {
            addLog("createimp.log.materials_arrived");
        }
        if (newStage == WorkStage.PRODUCTION && level instanceof ServerLevel serverLevel) {
            Vec3 center = Vec3.atCenterOf(worldPosition);
            PacketDistributor.sendToPlayersNear(serverLevel, null, center.x, center.y, center.z, 32.0,
                    new WorkWarehouseMaterialsReadyEffectPacket(worldPosition));
            beginProductionStage();
        }
        if (newStage == WorkStage.IDLE && level instanceof ServerLevel serverLevel) {
            Vec3 center = Vec3.atCenterOf(worldPosition);
            PacketDistributor.sendToPlayersNear(serverLevel, null, center.x, center.y, center.z, 32.0,
                    new WorkWarehouseMaterialsReadyEffectPacket(worldPosition));
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
     * <p>
     * 生产开始前，先统一结算一次 {@link #preExistingCredit} 账本，详见该
     * 字段的说明——必须在这里、用同一份共享临时快照一次性算完整份账本，
     * 不能等到每个节点各自真正完成时才现查仓库存储。
     */
    private void beginProductionStage() {
        WorkWarehouseItemStackHandler creditScratch = storage.copy();
        WorkWarehouseFluidStorage fluidCreditScratch = fluidStorage.copy();
        preExistingCredit.clear();
        for (int i = 0; i < templateSnapshot.size(); i++) {
            if (i == rootIndex()) {
                continue;
            }
            WorkWarehouseTemplateSnapshot.PanelSnapshot node = templateSnapshot.get(i);
            int actualBatchOutput = node.requiredBatches() * node.recipeOutput();
            int preExisting = Math.max(0, node.expectedOutputTotal() - actualBatchOutput);
            if (preExisting <= 0) {
                continue;
            }
            int claim;
            if (isFluidIngredient(node.filterItem())) {
                FluidStack sample = TemplateFluidDisplayHelper.getFluid(node.filterItem());
                int available = fluidCreditScratch.getAmount(sample);
                claim = Math.min(preExisting, available);
                if (claim > 0) {
                    fluidCreditScratch.extractFluid(sample, claim);
                }
            } else {
                int available = countMatching(creditScratch, node.filterItem());
                claim = Math.min(preExisting, available);
                if (claim > 0) {
                    extractExact(creditScratch, node.filterItem(), claim);
                }
            }
            if (claim > 0) {
                preExistingCredit.put(i, claim);
            }
        }
        for (int i = 0; i < templateSnapshot.size(); i++) {
            if (i == rootIndex()) {
                continue;
            }
            WorkWarehouseTemplateSnapshot.PanelSnapshot node = templateSnapshot.get(i);
            if (node.state() == WorkWarehouseTemplateSnapshot.PanelState.COMPLETED) {
                registerOutputDemand(i);
            }
        }
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
     * 副产物基准注意：这里必须用 {@code requiredBatches() * recipeOutput()}
     * ——也就是"这一批次自己实际会产出多少"，而不能用
     * {@link WorkWarehouseTemplateSnapshot.PanelSnapshot#expectedOutputTotal()}。
     * {@code expectedOutputTotal} 的定义是"现有库存确认的部分 + 自己按批次
     * 生产的部分"，那部分"现有库存确认"的数量早在原料请求阶段就已经作为
     * 独立的一次性初始需求条目运到过仓库一次，此刻这个节点即将真正寄出的
     * 原料只会产出 {@code requiredBatches() * recipeOutput()} 这么多——用
     * {@code expectedOutputTotal} 会把"已经处理过一次的现有库存"重复计入
     * 这一批新产出里，凭空多算出一批本次生产根本不会真正产生的"副产物"，
     * 导致仓库永远等不到这部分幽灵产出、需求列表卡死。
     * <p>
     * 生产者这一批实际产出往往会比"全部消费者需求之和"多——批次颗粒度
     * 取整必然产生这个差额。这部分差额如果不登记任何需求列表条目，就不会
     * 被主动收进仓库、永远滞留在仓库外部，所以这里额外登记一条归属为
     * "副产物"（{@code OWNER_BYPRODUCT}）的需求，把全部实际产出都收进仓库，
     * 多出来的部分最后由生产彻底完成后的"打包全部剩余物品"那一步一并处理。
     */
    private void registerOutputDemand(int producerIndex) {
        WorkWarehouseTemplateSnapshot.PanelSnapshot producer = templateSnapshot.get(producerIndex);
        List<int[]> consumers = findConsumersOf(producerIndex);
        int actualBatchOutput = producer.requiredBatches() * producer.recipeOutput();

        // 这个节点材料确认阶段就已经现成、原料请求阶段已运抵仓库、目前还没
        // 被任何需求条目认领过的数量，已经在 beginProductionStage() 那一刻
        // 用共享临时快照统一结算进 preExistingCredit 账本，这里直接取用、
        // 不再临时查仓库存储（避免多个节点监测同一种物品时重复认领同一批
        // 物理库存）。
        int preExisting = preExistingCredit.getOrDefault(producerIndex, 0);

        int allocated = 0;
        int creditedFromExisting = 0;
        for (int[] c : consumers) {
            int consumerIndex = c[0];
            int qty = c[1];
            if (qty <= 0) {
                continue;
            }
            allocated += qty;
            int creditNow = Math.min(qty, preExisting - creditedFromExisting);
            creditedFromExisting += creditNow;
            int remainingQty = qty - creditNow;
            if (remainingQty > 0) {
                demandList.add(new WorkWarehouseTemplateSnapshot.DemandEntry(
                        producer.network(), producer.filterItem().copy(), remainingQty, consumerIndex, producerIndex));
            }
        }
        int surplus = actualBatchOutput - (allocated - creditedFromExisting);
        if (surplus > 0) {
            demandList.add(new WorkWarehouseTemplateSnapshot.DemandEntry(
                    producer.network(), producer.filterItem().copy(), surplus,
                    WorkWarehouseTemplateSnapshot.DemandEntry.OWNER_BYPRODUCT, producerIndex));
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
        if (totalOutput <= 0) {
            markProductionComplete();
            return;
        }
        demandList.add(new WorkWarehouseTemplateSnapshot.DemandEntry(rootNode.network(), rootNode.filterItem().copy(),
                totalOutput, WorkWarehouseTemplateSnapshot.DemandEntry.OWNER_FINAL_PRODUCT, rootIndex()));
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
        boolean anyItem = false;
        for (int i = 0; i < storage.getSlots(); i++) {
            if (!storage.getStackInSlot(i).isEmpty()) {
                anyItem = true;
                break;
            }
        }
        boolean anyFluid = !fluidStorage.isEmpty();
        if (!anyItem && !anyFluid) {
            resetToIdle();
            return;
        }
        String backAddress = backToConnectedInventoryAddress();
        boolean addressedBackToConnectedInventory = !backAddress.isBlank() && backAddress.equals(targetAddress);
        if (addressedBackToConnectedInventory && !anyFluid) {
            // 只有纯物品、且地址确实是"送回连接库存"这个特殊地址时，才走直接
            // 插入连接库存这条路——流体没法插入连接库存（物品容器装不了
            // 流体），这条路对含流体的最终产物无效。
            attemptFinalShipmentBackToConnectedInventory();
            return;
        }
        // 走到这里的情况：普通地址发货，或者目标虽然是连接库存但产物里含
        // 流体（按设计，流体固定走连接库存背后的打包机，不经过连接库存
        // 本身）。
        PackagerBlockEntity packager = addressedBackToConnectedInventory
                ? findPackagerServingConnectedInventory(behaviour != null ? behaviour.freqId : null, null)
                : findDispatchPackager(behaviour.freqId, targetAddress);
        if (packager == null) {
            return;
        }
        List<ItemStack> allItems = new ArrayList<>();
        for (int i = 0; i < storage.getSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (!s.isEmpty()) {
                allItems.add(s.copy());
            }
        }
        List<FluidStack> allFluids = fluidStorage.nonEmptyContents();
        for (int i = 0; i < storage.getSlots(); i++) {
            storage.setStackInSlot(i, ItemStack.EMPTY);
        }
        for (FluidStack f : allFluids) {
            fluidStorage.extractFluid(f, f.getAmount());
        }
        // "送回连接库存"这个特殊地址本身不是一个真正能被包裹地址匹配的地址
        // 标签，走打包机发货时（因为含流体只能这么发）改用空地址，效果等同
        // 于"发给这个打包机自己面朝的目标"，跟物品直接插入连接库存本质上
        // 是同一个终点。
        String packageAddress = addressedBackToConnectedInventory ? null : targetAddress;
        if (!allItems.isEmpty()) {
            sendItemsSplitIntoPackages(packager, allItems, packageAddress, null, 0,
                    "生产彻底完成后的最终产物打包发货（含副产物）", false);
        }
        if (!allFluids.isEmpty()) {
            sendFluidsSplitIntoPackages(packager, allFluids, packageAddress,
                    "生产彻底完成后的最终流体产物打包发货");
        }
        if (!allItems.isEmpty()) {
            addLog("createimp.log.final_shipment", itemsArg(mergeItems(allItems)),
                    addressArg(targetAddress, "createimp.log.connected_storage"));
        }
        if (!allFluids.isEmpty()) {
            addLog("createimp.log.final_shipment", itemsArg(fluidsToLogItems(allFluids)),
                    addressArg(targetAddress, "createimp.log.connected_storage"));
        }
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
    /**
     * 从指定槽位安全地提取"恰好这么多"——和 {@link #extractExact} 同样的
     * 原因，不能假设 {@code extractItem} 一次调用就能把 amount 全部取走，
     * 需要检查真实提取到的数量，不够就对同一格重复提取直到取满或者格子
     * 被掏空。用于"已经确认这一格至少有 amount 那么多"的场景（调用方已经
     * 用这一格当前的真实数量算出了 amount，只是需要把它真正从存储里移除）。
     */
    private static void extractFromSlotExact(ItemStackHandler handler, int slot, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack current = handler.getStackInSlot(slot);
            if (current.isEmpty()) {
                break;
            }
            int want = Math.min(remaining, current.getCount());
            ItemStack extracted = handler.extractItem(slot, want, false);
            int actuallyTaken = extracted.getCount();
            if (actuallyTaken <= 0) {
                break;
            }
            remaining -= actuallyTaken;
        }
    }

    private void attemptFinalShipmentBackToConnectedInventory() {
        if (extractBehaviour == null || !extractBehaviour.hasInventory()) {
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
                extractFromSlotExact(storage, i, insertedCount);
                insertedSummary.add(stack.copyWithCount(insertedCount));
            }
            if (!remaining.isEmpty()) {
                allInserted = false;
            }
        }
        if (!allInserted) {
            return;
        }
        addLog("createimp.log.final_shipment", itemsArg(mergeItems(insertedSummary)),
                WorkWarehouseTemplateSnapshot.LogArg.key("createimp.log.connected_storage"));
        resetToIdle();
    }

    /**
     * 玩家在进程面板详情界面手动确认"中断请求"时调用（服务端，来自
     * {@code RequestWorkWarehouseInterruptPacket}）。只在正料请求/生产
     * 这两个"正在工作"的阶段有效，已经在空闲或者已经在中断中都直接忽略。
     * <p>
     * 立即清空需求列表/请求列表（不再接收任何物品、不再产生新的需求），
     * 记录两条中断专属日志（红色分类），然后马上尝试把仓库里现有的物品
     * 全部发出去；发不出去（找不到打包机/连接库存插不下）就停留在
     * {@code INTERRUPTING} 阶段，交给 {@link #tick()} 里的周期性重试。
     */
    public void requestInterrupt() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (stage != WorkStage.REQUESTING_MATERIALS && stage != WorkStage.PRODUCTION) {
            return;
        }
        demandList = new ArrayList<>();
        inTransitList = new ArrayList<>();
        setStage(WorkStage.INTERRUPTING);
        addLog(WorkWarehouseTemplateSnapshot.LogCategory.CANCEL, "createimp.log.enter_interrupting");
        addLog(WorkWarehouseTemplateSnapshot.LogCategory.CANCEL, "createimp.log.current_storage",
                itemsArg(currentStorageContents()));
        setChanged();
        notifyUpdate();
        attemptInterruptShipment();
    }

    /**
     * {@code INTERRUPTING} 阶段的发货尝试：目标地址是这次请求原本设置的
     * {@link #targetAddress}，是"返回连接库存专用地址"就走连接库存插入，
     * 否则找打包机寄出。找不到可用的寄出方式就什么都不做，留在这个阶段，
     * 等 {@link #tick()} 下一次周期性重试；仓库本身已经没有任何物品时，
     * 视为中断直接完成。
     */
    private void attemptInterruptShipment() {
        if (stage != WorkStage.INTERRUPTING) {
            return;
        }
        boolean anyItem = false;
        for (int i = 0; i < storage.getSlots(); i++) {
            if (!storage.getStackInSlot(i).isEmpty()) {
                anyItem = true;
                break;
            }
        }
        if (!anyItem) {
            completeInterrupt();
            return;
        }
        String backAddress = backToConnectedInventoryAddress();
        if (!backAddress.isBlank() && backAddress.equals(targetAddress)) {
            attemptInterruptShipmentBackToConnectedInventory();
            return;
        }

        List<ItemStack> allItems = new ArrayList<>();
        for (int i = 0; i < storage.getSlots(); i++) {
            ItemStack s = storage.getStackInSlot(i);
            if (!s.isEmpty()) {
                allItems.add(s.copy());
            }
        }
        PackagerBlockEntity packager = findDispatchPackager(behaviour.freqId, targetAddress);
        if (packager == null) {
            return;
        }
        for (int i = 0; i < storage.getSlots(); i++) {
            storage.setStackInSlot(i, ItemStack.EMPTY);
        }
        sendItemsSplitIntoPackages(packager, allItems, targetAddress, null, 0, "请求中断后，把仓库现有物品打包发货", false);
        addLog(WorkWarehouseTemplateSnapshot.LogCategory.CANCEL, "createimp.log.interrupt_items_sent",
                itemsArg(mergeItems(allItems)), addressArg(targetAddress, "createimp.log.connected_storage"));
        completeInterrupt();
    }

    /** {@code INTERRUPTING} 阶段、目标地址为连接库存专用地址时的发货尝试，写法和 {@link #attemptFinalShipmentBackToConnectedInventory()} 一致。 */
    private void attemptInterruptShipmentBackToConnectedInventory() {
        if (extractBehaviour == null || !extractBehaviour.hasInventory()) {
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
                extractFromSlotExact(storage, i, insertedCount);
                insertedSummary.add(stack.copyWithCount(insertedCount));
            }
            if (!remaining.isEmpty()) {
                allInserted = false;
            }
        }
        if (!allInserted) {
            return;
        }
        addLog(WorkWarehouseTemplateSnapshot.LogCategory.CANCEL, "createimp.log.interrupt_items_sent",
                itemsArg(mergeItems(insertedSummary)),
                WorkWarehouseTemplateSnapshot.LogArg.key("createimp.log.connected_storage"));
        completeInterrupt();
    }

    /**
     * 正常完成一次生产请求时的收尾：记录"进入空闲阶段"日志、归档完整日志
     * 给网络内所有进程面板、清空所有残余数据回到空闲。
     */
    private void resetToIdle() {
        addLog("createimp.log.enter_idle");
        archiveHistoryToProcessManagers();
        clearAllStateAndGoIdle();
    }

    /**
     * "请求中断"流程走完（仓库内物品已经全部发出去）之后的收尾：记录专用
     * 的"请求中断成功"日志（红色分类），归档、清空，和 {@link #resetToIdle()}
     * 是同一套收尾动作，只是记的日志不一样，所以没有直接复用那个方法。
     */
    private void completeInterrupt() {
        addLog(WorkWarehouseTemplateSnapshot.LogCategory.CANCEL, "createimp.log.interrupt_success");
        archiveHistoryToProcessManagers();
        clearAllStateAndGoIdle();
    }

    private void clearAllStateAndGoIdle() {
        templateSnapshot = new ArrayList<>();
        demandList = new ArrayList<>();
        inTransitList = new ArrayList<>();
        finalDemandRegistered = false;
        productionComplete = false;
        requestedProduct = ItemStack.EMPTY;
        requestedAmount = 0;
        activationGameTime = 0;
        producerCompletionAnnounced.clear();
        preExistingCredit.clear();
        logEntries.clear();
        latestLogKey = "";
        latestLogArgs = new ArrayList<>();
        latestLogCategory = WorkWarehouseTemplateSnapshot.LogCategory.NORMAL;
        latestLogElapsedTicks = 0;
        setStage(WorkStage.IDLE);
        setWorking(false);
        setChanged();
        notifyUpdate();
    }

    /**
     * 把这次工作的产物、请求数量、归档时刻的世界时间、以及完整日志历史，
     * 打包发送给所在物流网络下当前所有现存的进程面板，供它们的"历史请求
     * 日志"界面展示。发生在 {@link #logEntries} 被清空之前。
     */
    private void archiveHistoryToProcessManagers() {
        if (level == null || behaviour == null || behaviour.freqId == null) {
            return;
        }
        long completionTime = level.getGameTime();
        for (com.molox.createimp.block.process_manager.ProcessManagerBlockEntity pmbe
                : com.molox.createimp.block.process_manager.ProcessManagerNetworkHelper.findAll(behaviour.freqId, false)) {
            pmbe.archiveHistory(requestedProduct, requestedAmount, completionTime, logEntries);
        }
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
        // 每次这个方法被调用都重新结算一次需求列表，而不是只在刚进入生产
        // 阶段那一刻结算一次——原料从打包机缓存真正转移进仓库自己的存储
        // （物品/流体都一样）跟这里对需求列表的推进不是同一时刻发生的，
        // 如果只在 beginProductionStage() 里结算一次，某个内部生产者→消费者
        // 需求条目刚好赶在"原料还没转移进仓库"那一刻登记，就会永远卡在
        // 需求列表里出不去（明明仓库里已经有这批原料了，却因为没人重新
        // 核对过而一直傻乎乎地继续向网络请求）。这里补一次调用，让每次
        // 周期性检查都有机会追上这个时间差。
        settleFromOwnStorage();
        // 先通报"上一轮已经完成、产物现在到齐了"的节点，再去扫描这一轮新
        // 可以完成的节点——保证同一次调用里，"产物生产完成"日志排在"开始
        // 生产"日志前面，符合日志顺序要求。
        announceProducerCompletions();
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
            // 这一轮级联完成之后再检查一次：级联过程中可能刚好也有某个生产者
            // 自己的产出需求被清空（虽然按现有实现分析不会发生，但多查一次
            // 开销很小，能多一层保险，避免"生产完成"日志错位到下一个"开始
            // 生产"日志后面）。
            announceProducerCompletions();
            if (finalDemandRegistered && demandList.isEmpty()) {
                // 必须整个需求列表都清空（包括所有归属为副产物的条目）才能判定
                // 生产彻底完成，不能只看虚拟末端需求是否满足——否则某个副产物
                // 还在路上没到货时就会提前打包发货、清空缓存，导致这部分永远丢失。
                markProductionComplete();
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
        if (!canDispatchNode(node)) {
            return false;
        }
        WorkWarehouseTemplateSnapshot.LogArg productArg = itemArg(node.filterItem(), node.requiredBatches() * node.recipeOutput());
        addLog("createimp.log.node_start_production", productArg);
        dispatchNodeIngredients(node);
        addLog("createimp.log.expect_receive_product", productArg);
        templateSnapshot.set(index, node.withState(WorkWarehouseTemplateSnapshot.PanelState.COMPLETED));
        setChanged();
        notifyUpdate();
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

    /**
     * 提前判断这个节点这次能不能真的寄出去（有没有可用打包机），不实际
     * 扣减仓库存储、不实际寄出——只是为了保证"开始生产"这条日志一定能
     * 排在"发出物品"（可能不止一条，按量请求关闭时每批各发一次）之前：
     * 先确认能成功，再记日志，再真正执行 {@link #dispatchNodeIngredients}。
     */
    private boolean canDispatchNode(WorkWarehouseTemplateSnapshot.PanelSnapshot node) {
        if (node.ingredients().isEmpty() || node.address() == null || node.address().isBlank()) {
            return true;
        }
        return findDispatchPackager(node.network(), node.address()) != null;
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
        PackagerBlockEntity packager = findDispatchPackager(node.network(), node.address());
        if (packager == null) {
            return false;
        }
        if (node.demandMode()) {
            List<ItemStack> itemsToSend = new ArrayList<>();
            List<FluidStack> fluidsToSend = new ArrayList<>();
            for (WorkWarehouseTemplateSnapshot.IngredientEntry ing : node.ingredients()) {
                int total = ing.amount() * node.requiredBatches();
                if (total <= 0) {
                    continue;
                }
                if (isFluidIngredient(ing.item())) {
                    fluidsToSend.add(TemplateFluidDisplayHelper.getFluid(ing.item()).copyWithAmount(total));
                } else {
                    itemsToSend.add(ing.item().copyWithCount(total));
                }
            }
            for (ItemStack s : itemsToSend) {
                extractExact(storage, s, s.getCount());
            }
            for (FluidStack f : fluidsToSend) {
                fluidStorage.extractFluid(f, f.getAmount());
            }
            String source = String.format("节点(%s) 按量请求模式=开 批次数=%d（全部批次一次性发出）",
                    node.filterItem().getItem(), node.requiredBatches());
            if (!itemsToSend.isEmpty()) {
                sendItemsSplitIntoPackages(packager, itemsToSend, node.address(),
                        node.craftingMode() ? node.craftingArrangement() : null, node.requiredBatches(), source);
            }
            if (!fluidsToSend.isEmpty()) {
                sendFluidsSplitIntoPackages(packager, fluidsToSend, node.address(), source);
            }
        } else {
            for (int batch = 0; batch < node.requiredBatches(); batch++) {
                List<ItemStack> itemsToSend = new ArrayList<>();
                List<FluidStack> fluidsToSend = new ArrayList<>();
                for (WorkWarehouseTemplateSnapshot.IngredientEntry ing : node.ingredients()) {
                    if (ing.amount() <= 0) {
                        continue;
                    }
                    if (isFluidIngredient(ing.item())) {
                        fluidsToSend.add(TemplateFluidDisplayHelper.getFluid(ing.item()).copyWithAmount(ing.amount()));
                    } else {
                        itemsToSend.add(ing.item().copyWithCount(ing.amount()));
                    }
                }
                for (ItemStack s : itemsToSend) {
                    extractExact(storage, s, s.getCount());
                }
                for (FluidStack f : fluidsToSend) {
                    fluidStorage.extractFluid(f, f.getAmount());
                }
                String source = String.format("节点(%s) 按量请求模式=关 第%d/%d批（按配方单批数量逐批发出）",
                        node.filterItem().getItem(), batch + 1, node.requiredBatches());
                if (!itemsToSend.isEmpty()) {
                    sendItemsSplitIntoPackages(packager, itemsToSend, node.address(),
                            node.craftingMode() ? node.craftingArrangement() : null, 1, source);
                }
                if (!fluidsToSend.isEmpty()) {
                    sendFluidsSplitIntoPackages(packager, fluidsToSend, node.address(), source);
                }
            }
        }
        return true;
    }

    /**
     * 流体版的发货：固液分包，这里只处理流体部分，超出单个压缩罐容量
     * （流包自己的配置项）时自动拆分成多个包裹依次寄出，跟物品那边"超出
     * 单包容量自动拆分"是同一个原则。不附加 orderId/合成请求信息——按最新
     * 设计，流体包裹的接收判断只看"种类是否在需求列表内、数量是否超出
     * 剩余量"，不依赖 orderId，出库这一侧也不需要为此另外维护这份信息。
     */
    private void sendFluidsSplitIntoPackages(PackagerBlockEntity packager, List<FluidStack> fluids, String address, String sourceDescription) {
        if (fluids.isEmpty()) {
            return;
        }
        String logAddress = (address == null || address.isBlank()) ? "（连接库存）" : address;
        addLog("createimp.log.fluids_sent", itemsArg(fluidsToLogItems(fluids)), WorkWarehouseTemplateSnapshot.LogArg.text(logAddress));
        int capacity = Math.max(1, TemplateFluidDisplayHelper.tankCapacity());
        for (FluidStack fluid : fluids) {
            int remaining = fluid.getAmount();
            while (remaining > 0) {
                int take = Math.min(remaining, capacity);
                FluidStack chunk = fluid.copyWithAmount(take);
                injectFluidPackage(packager, chunk, address);
                remaining -= take;
            }
        }
    }

    private static void injectFluidPackage(PackagerBlockEntity packager, FluidStack fluid, String address) {
        ItemStack createdBox = TemplateFluidDisplayHelper.createFluidPackageBox(fluid);
        PackageItem.clearAddress(createdBox);
        if (address != null && !address.isBlank()) {
            PackageItem.addAddress(createdBox, address);
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
        sendItemsSplitIntoPackages(packager, items, address, craftPattern, craftCount, sourceDescription, true);
    }

    /**
     * {@code logItemsSent} 为 false 时不记录这里通用的"发出物品"日志——
     * 请求中断流程自己会在调用这个方法之后记一条专用的"发送物品"日志
     * （红色分类），如果这里还照常记一条，就会变成同一次发货连续出现
     * 两条颜色不同的日志。
     */
    private void sendItemsSplitIntoPackages(PackagerBlockEntity packager, List<ItemStack> items, String address,
                                            List<ItemStack> craftPattern, int craftCount, String sourceDescription,
                                            boolean logItemsSent) {
        if (logItemsSent) {
            addLog("createimp.log.items_sent", itemsArg(mergeItems(items)), WorkWarehouseTemplateSnapshot.LogArg.text(address));
        }
        List<ItemStackHandler> chunks = splitIntoPackageChunks(items);
        int chunkCount = chunks.size();
        // 同一次发货如果因为 9 格容量限制被拆成了多个物理包裹，这些包裹
        // 必须共用同一个 orderId、并按顺序标好 fragmentIndex/isFinal，批量
        // 理包机（BatchRepackagerBlockEntity#isOrderComplete）才能正确地把
        // 它们重新拼回同一份订单——先汇总全部包裹里的真实材料，再判断够不
        // 够凑够声明的合成批次数。如果每个包裹各自随机生成 orderId、都标成
        // "自己就是唯一且完整的订单"（之前的写法），批量理包机会把每个包裹
        // 当成互不相关的独立小订单分别处理：材料分散在不同包裹里时（比如
        // 木板和木棍因为分包顺序被拆进了不同的包裹），任何一个包裹单独看
        // 都凑不够声明的批次数，会被误判成"库存不够"直接原样打包退回，
        // 而不是等所有包裹到齐后一起合成。
        int orderId = RNG.nextInt();
        for (int i = 0; i < chunkCount; i++) {
            ItemStackHandler chunk = chunks.get(i);
            PackageOrderWithCrafts orderContext = buildCraftContext(chunk, craftPattern, craftCount);
            injectPackage(packager, chunk, address, orderContext, orderId, i, i == chunkCount - 1);
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
     * 一面贴合的打包机里选一个（贴合多个时按 {@link #pickPackagerByAddress}
     * 的优先级规则挑选）；如果自身没有贴合任何打包机，退而检查工作仓库的
     * 连接库存背后是否另外贴合了一个已经通过仓储连接站接入本仪表所在网络
     * 的打包机，如果有，就借用那一个（贴合多个同样按同一套优先级规则挑选）。
     * 两者都找不到则返回 null，本次发货放弃，等待下次重试。
     */
    private PackagerBlockEntity findDispatchPackager(UUID network, String address) {
        List<PackagerBlockEntity> adjacent = findAdjacentPackagers();
        if (!adjacent.isEmpty()) {
            return pickPackagerByAddress(adjacent, address);
        }
        return findPackagerServingConnectedInventory(network, address);
    }

    /**
     * 从候选打包机里挑一个用来出货，行为跟
     * {@code MixinLogisticsManager#createimp$selectPackagerLinkByAddress} 里
     * 针对"同一目标库存被多个仓储连接站打包机瞄准"场景的优先级判断完全
     * 一致（先挑告示牌地址匹配的，没有再挑没挂告示牌的，都没有则退回原版
     * 随机），因为这里走的不是 {@code LogisticsManager.findPackagersForRequest}
     * 那条路径（工作仓库自己的发货逻辑，不经过物流网络的包裹请求分配），
     * 所以那个 Mixin 管不到这里，需要单独实现一份同样的判断。
     */
    private static PackagerBlockEntity pickPackagerByAddress(List<PackagerBlockEntity> candidates, String address) {
        if (candidates.size() <= 1) {
            return candidates.get(RNG.nextInt(candidates.size()));
        }
        // address为null代表调用方本来就没有"目标地址"这个概念（比如周期性回收
        // 打包机身上的流体缓存、或者最终产物按特殊地址直接返回连接库存背后的
        // 打包机），这种情况下按地址过滤没有意义，直接跳过匹配，退回随机挑选，
        // 和玩家在配置里关闭地址过滤功能时走的是同一条分支。
        if (address == null || !CreateImp.getConfig().packagerAddressFilterConfig.enabled) {
            return candidates.get(RNG.nextInt(candidates.size()));
        }

        List<PackagerBlockEntity> matched = new ArrayList<>();
        List<PackagerBlockEntity> noSign = new ArrayList<>();
        for (PackagerBlockEntity packager : candidates) {
            String signAddress = PackagerSignAddressHelper.resolveSignAddress(packager);
            if (signAddress == null) {
                noSign.add(packager);
            } else if (PackageItem.matchAddress(signAddress, address)) {
                matched.add(packager);
            }
        }
        if (!matched.isEmpty()) {
            return matched.get(RNG.nextInt(matched.size()));
        }
        if (!noSign.isEmpty()) {
            return noSign.get(RNG.nextInt(noSign.size()));
        }
        return candidates.get(RNG.nextInt(candidates.size()));
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
    private PackagerBlockEntity findPackagerServingConnectedInventory(UUID network, String address) {
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
        return pickPackagerByAddress(candidates, address);
    }

    /**
     * 直接向一个真实打包机"注入"一个已经打好包的包裹，效果与
     * {@code PackagerBlockEntity.attemptToSend} 从自己背后容器里取出物品、
     * 打包、播放弹出动画完全一致——只是物品来源换成了工作仓库自己的内部
     * 存储，不经过打包机自身的库存查询流程。{@code orderId}/{@code fragmentIndex}/
     * {@code isFinal} 由调用方（{@link #sendItemsSplitIntoPackages}）统一
     * 分配，确保同一次发货拆出的多个包裹能被批量理包机正确识别成同一份
     * 订单的不同碎片，而不是各自独立的订单。
     */
    private static void injectPackage(PackagerBlockEntity packager, ItemStackHandler contents, String address,
                                      PackageOrderWithCrafts orderContext, int orderId, int fragmentIndex, boolean isFinal) {
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
            // linkIndex 固定传 0、isFinalLink 固定传 true——我们自己的发货
            // 逻辑里不存在"多个打包机链路分段"这种概念，只有"一次发货拆成
            // 多个包裹"这一层，用 fragmentIndex/isFinal 表达就够了。
            PackageItem.setOrder(createdBox, orderId, 0, true, fragmentIndex, isFinal, orderContext);
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
        // 归一化数量为 1——真实数量由 amount 单独承载。requestedProduct
        // 用的是原版 ItemStack.CODEC 直接编码，这个 codec 对内部 count 字段
        // 做了 [1,99] 范围校验，如果调用方传进来的 item 本身就带着请求的
        // 真实数量（有可能远超 99），这个字段会编码失败，导致整个方块实体
        // 的存档写入失败（和之前 LogEntries/DemandEntry 那几处是同一类问题）。
        this.requestedProduct = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
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
        if (stage == WorkStage.INTERRUPTING) {
            return "gui.work_warehouse.stage_interrupting_value";
        }
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
        syncActiveRegistration();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!FluidLogisticsCompat.isLoaded() && !fluidStorage.isEmpty()) {
            // 中途卸载流体包裹：仓库内还有流体存量的话直接清空，流体存储功能
            // 整体禁用（isFluidIngredient 恒为 false 之后，所有流体相关分支都
            // 不会再被触发，行为退化成纯物品，和普通仪表卸载流体包裹后的
            // 处理原则一致）。
            fluidStorage.clear();
            setChanged();
            CreateImp.LOGGER.info("检测到流体包裹已卸载，已清空工作仓库内的流体存量");
        }
        if (!isWorking()) {
            return;
        }
        if (pendingReconcile) {
            pendingReconcile = false;
            monitorPackagerFluidCaches();
            reconcileDemandList();
        }
        if (stage == WorkStage.INTERRUPTING) {
            if (++ticksSinceLastMonitor <= 15) {
                return;
            }
            ticksSinceLastMonitor = 0;
            attemptInterruptShipment();
            return;
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
        monitorConnectedInventory();
        monitorPackagerFluidCaches();
        reconcileDemandList();
        requestRemainingDemandFromNetwork();
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
        activationGameTime = tag.getLong("ActivationGameTime");
        latestLogKey = tag.getString("LatestLogKey");
        latestLogArgs = new ArrayList<>(tag.contains("LatestLogArgs")
                ? CatnipCodecUtils.decode(WorkWarehouseTemplateSnapshot.LogArg.CODEC.listOf(), registries,
                tag.get("LatestLogArgs")).orElse(List.of())
                : List.of());
        latestLogCategory = parseLogCategory(tag.getString("LatestLogCategory"));
        latestLogElapsedTicks = tag.getLong("LatestLogElapsedTicks");
        logEntries.clear();
        logEntries.addAll(tag.contains("LogEntries")
                ? CatnipCodecUtils.decode(WorkWarehouseTemplateSnapshot.LogEntry.CODEC.listOf(), registries,
                tag.get("LogEntries")).orElse(List.of())
                : List.of());
        stage = tag.contains("Stage")
                ? parseStage(tag.getString("Stage"))
                : WorkStage.IDLE;
        productionComplete = tag.getBoolean("ProductionComplete");
        if (!clientPacket && tag.contains("Storage", Tag.TAG_COMPOUND)) {
            storage.deserializeNBT(registries, tag.getCompound("Storage"));
        }
        if (!clientPacket && tag.contains("FluidStorage", Tag.TAG_COMPOUND)) {
            fluidStorage.deserializeNBT(registries, tag.getCompound("FluidStorage"));
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
            producerCompletionAnnounced.clear();
            for (int i : tag.getIntArray("ProducerCompletionAnnounced")) {
                producerCompletionAnnounced.add(i);
            }
            preExistingCredit.clear();
            int[] creditKeys = tag.getIntArray("PreExistingCreditKeys");
            int[] creditValues = tag.getIntArray("PreExistingCreditValues");
            for (int i = 0; i < creditKeys.length && i < creditValues.length; i++) {
                preExistingCredit.put(creditKeys[i], creditValues[i]);
            }
        }
    }

    private static WorkStage parseStage(String name) {
        try {
            return WorkStage.valueOf(name);
        } catch (IllegalArgumentException e) {
            return WorkStage.IDLE;
        }
    }

    private static WorkWarehouseTemplateSnapshot.LogCategory parseLogCategory(String name) {
        try {
            return WorkWarehouseTemplateSnapshot.LogCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return WorkWarehouseTemplateSnapshot.LogCategory.NORMAL;
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
        tag.putLong("ActivationGameTime", activationGameTime);
        tag.putString("LatestLogKey", latestLogKey);
        CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.LogArg.CODEC.listOf(), registries, latestLogArgs)
                .ifPresent(encoded -> tag.put("LatestLogArgs", encoded));
        tag.putString("LatestLogCategory", latestLogCategory.name());
        tag.putLong("LatestLogElapsedTicks", latestLogElapsedTicks);
        CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.LogEntry.CODEC.listOf(), registries, logEntries)
                .ifPresent(encoded -> tag.put("LogEntries", encoded));
        tag.putString("Stage", stage.name());
        tag.putBoolean("ProductionComplete", productionComplete);
        if (!clientPacket) {
            tag.put("Storage", storage.serializeNBT(registries));
            tag.put("FluidStorage", fluidStorage.serializeNBT(registries));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.PanelSnapshot.CODEC.listOf(), registries, templateSnapshot)
                    .ifPresent(encoded -> tag.put("TemplateSnapshot", encoded));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.DemandEntry.CODEC.listOf(), registries, demandList)
                    .ifPresent(encoded -> tag.put("DemandList", encoded));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.InTransitEntry.CODEC.listOf(), registries, inTransitList)
                    .ifPresent(encoded -> tag.put("InTransitList", encoded));
            tag.putBoolean("FinalDemandRegistered", finalDemandRegistered);
            tag.putIntArray("ProducerCompletionAnnounced",
                    producerCompletionAnnounced.stream().mapToInt(Integer::intValue).toArray());
            int[] creditKeys = preExistingCredit.keySet().stream().mapToInt(Integer::intValue).toArray();
            int[] creditValues = new int[creditKeys.length];
            for (int i = 0; i < creditKeys.length; i++) {
                creditValues[i] = preExistingCredit.get(creditKeys[i]);
            }
            tag.putIntArray("PreExistingCreditKeys", creditKeys);
            tag.putIntArray("PreExistingCreditValues", creditValues);
        }
    }
}