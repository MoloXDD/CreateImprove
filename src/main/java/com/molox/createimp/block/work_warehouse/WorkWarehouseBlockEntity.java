package com.molox.createimp.block.work_warehouse;

import com.molox.createimp.block.template_panel.TemplateMaterialCalculator;
import com.molox.createimp.network.WorkWarehouseActivateEffectPacket;
import com.molox.createimp.network.WorkWarehouseMaterialsReadyEffectPacket;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorkWarehouseBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /**
     * 工作仓库当前所处的生产阶段，作为客户端护目镜文案展示的唯一依据。
     * 不依赖需求列表是否为空来反推——需求列表在后续真正的生产阶段会被
     * 挪作他用（记录实际消耗的原料），空/非空的含义届时不再等同于阶段。
     */
    public enum WorkStage {
        IDLE,
        REQUESTING_MATERIALS,
        PRODUCTION
    }

    public LogisticallyLinkedBehaviour behaviour;
    public InvManipulationBehaviour extractBehaviour;
    public final WorkWarehouseItemStackHandler storage = new WorkWarehouseItemStackHandler(this);
    private String address = "";
    private String targetAddress = "";
    private WorkStage stage = WorkStage.IDLE;

    // 连接库存监控转移的节奏计数器，不需要持久化。间隔与仓储管理员界面
    // 判断"是否需要重新拉取网络库存快照"的节奏（约 16 tick）保持一致。
    private int ticksSinceLastMonitor = 0;

    /**
     * 本次被分配到的那一个模板链的结构快照，激活时写入，供后续生产环节使用。
     * 只在服务端持久化，不需要同步给客户端。
     */
    private List<WorkWarehouseTemplateSnapshot.PanelSnapshot> templateSnapshot = List.of();

    /**
     * 本次生产期待收到的原料清单（即分配给这个仓库的那一个模板自己的
     * "现有材料"，按物流网络拆分）。激活时写入，需求原料阶段期间会被
     * 连接库存监控转移、打包机解包两条途径逐步扣减。只在服务端持久化，
     * 不需要同步给客户端——客户端展示阶段改为读取 {@link #stage} 字段。
     */
    private List<WorkWarehouseTemplateSnapshot.DemandEntry> demandList = new ArrayList<>();

    /**
     * 本次正在生产的目标物品与请求数量，用于护目镜信息展示，客户端也需要
     * 这份数据，因此不像上面两个字段那样只在服务端持久化。
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
     * 之后调用。会先执行一次连接库存监控转移，再对监控转移后仍然剩余的需求
     * 按网络分组，各自向对应物流网络发起一次性打包请求。此后不再重试或跟踪
     * 这次请求是否成功，工作仓库转为单纯等待连接库存监控与打包机解包。
     */
    public void startMaterialRequestStage() {
        if (level == null || level.isClientSide()) {
            return;
        }
        monitorConnectedInventory();
        requestRemainingDemandFromNetwork();
        advanceStageIfDemandFulfilled();
    }

    private void requestRemainingDemandFromNetwork() {
        if (demandList.isEmpty()) {
            return;
        }
        Map<UUID, List<BigItemStack>> byNetwork = new LinkedHashMap<>();
        for (WorkWarehouseTemplateSnapshot.DemandEntry entry : demandList) {
            byNetwork.computeIfAbsent(entry.network(), key -> new ArrayList<>())
                    .add(new BigItemStack(entry.item(), entry.amount()));
        }
        for (Map.Entry<UUID, List<BigItemStack>> networkEntry : byNetwork.entrySet()) {
            PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(networkEntry.getValue());
            LogisticsManager.broadcastPackageRequest(networkEntry.getKey(),
                    LogisticallyLinkedBehaviour.RequestType.RESTOCK, order, null, address);
        }
    }

    /**
     * 连接模式下，检查连接库存里是否有需求列表中的物品，有则转移进内部存储
     * 并从需求列表对应项里扣减（数量不足则扣减部分，划除则整项移除）。
     * 非连接模式（{@code extractBehaviour} 找不到 {@code IItemHandler} 能力）
     * 直接跳过。
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
            int remaining = entry.amount() - extracted.getCount();
            if (remaining > 0) {
                updated.add(new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), remaining));
            }
        }
        if (changed) {
            demandList = updated;
            setChanged();
            advanceStageIfDemandFulfilled();
        }
    }

    /**
     * 供 {@link WorkWarehouseUnpackingHandler} 在包裹解包成功后调用，按传入的
     * 物品与数量，依次从需求列表里扣减（同一物品可能因为来自不同网络而拆成
     * 多条记录，按记录顺序依次扣减，扣满即止，不会重复扣减）。
     */
    public void consumeFromDemandList(List<ItemStack> items) {
        if (demandList.isEmpty()) {
            return;
        }
        List<WorkWarehouseTemplateSnapshot.DemandEntry> working = new ArrayList<>(demandList);
        for (ItemStack item : items) {
            int toConsume = item.getCount();
            for (int i = 0; i < working.size() && toConsume > 0; i++) {
                WorkWarehouseTemplateSnapshot.DemandEntry entry = working.get(i);
                if (entry == null || !ItemStack.isSameItemSameComponents(entry.item(), item)) {
                    continue;
                }
                int consumed = Math.min(entry.amount(), toConsume);
                toConsume -= consumed;
                int remaining = entry.amount() - consumed;
                working.set(i, remaining > 0
                        ? new WorkWarehouseTemplateSnapshot.DemandEntry(entry.network(), entry.item(), remaining)
                        : null);
            }
        }
        working.removeIf(java.util.Objects::isNull);
        demandList = working;
        setChanged();
        advanceStageIfDemandFulfilled();
    }

    public List<WorkWarehouseTemplateSnapshot.PanelSnapshot> getTemplateSnapshot() {
        return templateSnapshot;
    }

    /**
     * 存入这次被分配到的模板链结构快照，覆盖任何之前残留的快照。
     */
    public void setTemplateSnapshot(List<WorkWarehouseTemplateSnapshot.PanelSnapshot> snapshot) {
        this.templateSnapshot = snapshot != null ? List.copyOf(snapshot) : List.of();
        setChanged();
    }

    public List<WorkWarehouseTemplateSnapshot.DemandEntry> getDemandList() {
        return demandList;
    }

    /**
     * 设置本次生产的需求列表：先清空再写入，避免残留上一次生产留下的脏数据。
     */
    public void setDemandList(List<TemplateMaterialCalculator.NetworkBigItemStack> demand) {
        this.demandList.clear();
        if (demand != null) {
            for (TemplateMaterialCalculator.NetworkBigItemStack entry : demand) {
                this.demandList.add(new WorkWarehouseTemplateSnapshot.DemandEntry(
                        entry.network(), entry.stack().copy(), entry.count()));
            }
        }
        setChanged();
        // 新一轮生产开始，先回到原料请求阶段；如果这次需求列表一开始就是
        // 空的（或紧接着的监控/请求流程立刻补齐），会在 startMaterialRequestStage
        // 里被推进到生产阶段。
        setStage(WorkStage.REQUESTING_MATERIALS);
    }

    public WorkStage getStage() {
        return stage;
    }

    private void setStage(WorkStage newStage) {
        if (this.stage == newStage) {
            return;
        }
        this.stage = newStage;
        setChanged();
        notifyUpdate();
        if (newStage == WorkStage.PRODUCTION && level instanceof ServerLevel serverLevel) {
            Vec3 center = Vec3.atCenterOf(worldPosition);
            PacketDistributor.sendToPlayersNear(serverLevel, null, center.x, center.y, center.z, 32.0,
                    new WorkWarehouseMaterialsReadyEffectPacket(worldPosition));
        }
    }

    /**
     * 需求列表被清空后，把阶段推进到生产阶段。集中放在这一个方法里调用，
     * 避免每个修改需求列表的地方都重复写"清空了就切阶段"的判断。
     */
    private void advanceStageIfDemandFulfilled() {
        if (demandList.isEmpty()) {
            setStage(WorkStage.PRODUCTION);
        }
    }

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
                .add(new LangBuilder("createimp").translate(stage == WorkStage.PRODUCTION
                                ? "gui.work_warehouse.stage_production_value"
                                : "gui.work_warehouse.stage_requesting_materials_value")
                        .style(ChatFormatting.GOLD))
                .forGoggles(tooltip);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!isWorking() || demandList.isEmpty()) {
            return;
        }
        if (++ticksSinceLastMonitor <= 15) {
            return;
        }
        ticksSinceLastMonitor = 0;
        monitorConnectedInventory();
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
        if (!clientPacket && tag.contains("Storage", Tag.TAG_COMPOUND)) {
            storage.deserializeNBT(registries, tag.getCompound("Storage"));
        }
        if (!clientPacket) {
            templateSnapshot = tag.contains("TemplateSnapshot")
                    ? CatnipCodecUtils.decode(WorkWarehouseTemplateSnapshot.PanelSnapshot.CODEC.listOf(), registries,
                    tag.get("TemplateSnapshot")).orElse(List.of())
                    : List.of();
            demandList = new ArrayList<>(tag.contains("DemandList")
                    ? CatnipCodecUtils.decode(WorkWarehouseTemplateSnapshot.DemandEntry.CODEC.listOf(), registries,
                    tag.get("DemandList")).orElse(List.of())
                    : List.of());
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
        if (!clientPacket) {
            tag.put("Storage", storage.serializeNBT(registries));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.PanelSnapshot.CODEC.listOf(), registries, templateSnapshot)
                    .ifPresent(encoded -> tag.put("TemplateSnapshot", encoded));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.DemandEntry.CODEC.listOf(), registries, demandList)
                    .ifPresent(encoded -> tag.put("DemandList", encoded));
        }
    }
}