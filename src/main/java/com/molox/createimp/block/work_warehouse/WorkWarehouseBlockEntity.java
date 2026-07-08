package com.molox.createimp.block.work_warehouse;

import com.molox.createimp.network.WorkWarehouseActivateEffectPacket;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class WorkWarehouseBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public LogisticallyLinkedBehaviour behaviour;
    public final WorkWarehouseItemStackHandler storage = new WorkWarehouseItemStackHandler(this);
    private String address = "";
    private String targetAddress = "";

    /**
     * 本次被分配到的那一个模板链的结构快照，激活时写入，供后续生产环节使用。
     * 只在服务端持久化，不需要同步给客户端。
     */
    private List<WorkWarehouseTemplateSnapshot.PanelSnapshot> templateSnapshot = List.of();

    /**
     * 本次生产期待收到的原料清单（即分配给这个仓库的那一个模板自己的
     * "现有材料"），激活时写入，目前仅作存储，供后续生产环节读取使用。
     * 只在服务端持久化，不需要同步给客户端。
     */
    private List<WorkWarehouseTemplateSnapshot.IngredientEntry> demandList = new ArrayList<>();

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

    public List<WorkWarehouseTemplateSnapshot.IngredientEntry> getDemandList() {
        return demandList;
    }

    /**
     * 设置本次生产的需求列表：先清空再写入，避免残留上一次生产留下的脏数据。
     */
    public void setDemandList(List<BigItemStack> demand) {
        this.demandList.clear();
        if (demand != null) {
            for (BigItemStack entry : demand) {
                this.demandList.add(new WorkWarehouseTemplateSnapshot.IngredientEntry(entry.stack.copy(), entry.count));
            }
        }
        setChanged();
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
        return true;
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
        if (!clientPacket && tag.contains("Storage", Tag.TAG_COMPOUND)) {
            storage.deserializeNBT(registries, tag.getCompound("Storage"));
        }
        if (!clientPacket) {
            templateSnapshot = tag.contains("TemplateSnapshot")
                    ? CatnipCodecUtils.decode(WorkWarehouseTemplateSnapshot.PanelSnapshot.CODEC.listOf(), registries,
                    tag.get("TemplateSnapshot")).orElse(List.of())
                    : List.of();
            demandList = new ArrayList<>(tag.contains("DemandList")
                    ? CatnipCodecUtils.decode(WorkWarehouseTemplateSnapshot.IngredientEntry.CODEC.listOf(), registries,
                    tag.get("DemandList")).orElse(List.of())
                    : List.of());
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
        if (!clientPacket) {
            tag.put("Storage", storage.serializeNBT(registries));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.PanelSnapshot.CODEC.listOf(), registries, templateSnapshot)
                    .ifPresent(encoded -> tag.put("TemplateSnapshot", encoded));
            CatnipCodecUtils.encode(WorkWarehouseTemplateSnapshot.IngredientEntry.CODEC.listOf(), registries, demandList)
                    .ifPresent(encoded -> tag.put("DemandList", encoded));
        }
    }
}