package com.molox.createimp.block.process_manager;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.work_warehouse.WorkWarehouseNetworkHelper;
import com.molox.createimp.block.work_warehouse.WorkWarehouseTemplateSnapshot;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.codecs.CatnipCodecUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProcessManagerBlockEntity extends SmartBlockEntity {

    public LogisticallyLinkedBehaviour behaviour;

    // 网络状态检查节奏，与工作仓库的连接库存监控/周期性检查保持一致（约16 tick一次），不需要持久化。
    private int ticksSinceLastCheck = 0;

    /**
     * "历史请求日志"界面用的数据：网络里的工作仓库每完成一次生产、回到
     * 空闲状态前，都会把这次工作的完整记录（产物、数量、归档时刻的世界
     * 时间、完整日志）打包发给网络里所有现存的进程面板，存在这里。新的
     * 记录插在列表最前面（下标0 = 最新），超出配置的保留数量时从列表
     * 末尾（最老的）开始清理。会完整同步给客户端，供界面直接展示。
     */
    private final List<ProcessManagerHistoryEntry> historyEntries = new ArrayList<>();

    public ProcessManagerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.behaviour = new LogisticallyLinkedBehaviour(this, false);
        behaviours.add(this.behaviour);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (++ticksSinceLastCheck <= 15) {
            return;
        }
        ticksSinceLastCheck = 0;

        boolean shouldBePowered = WorkWarehouseNetworkHelper.hasWorkingWorkWarehouse(behaviour.freqId);
        BlockState state = getBlockState();
        if (state.getValue(ProcessManagerBlock.POWERED) != shouldBePowered) {
            level.setBlockAndUpdate(getBlockPos(), state.setValue(ProcessManagerBlock.POWERED, shouldBePowered));
        }
    }

    public List<ProcessManagerHistoryEntry> getHistoryEntries() {
        return Collections.unmodifiableList(historyEntries);
    }

    /**
     * 由工作仓库在回到空闲状态前调用：把这次工作的完整记录归档进来，插在
     * 列表最前面，随后立即按当前配置的保留数量清理超出部分。
     */
    public void archiveHistory(ItemStack requestedProduct, int requestedAmount, long completionGameTime,
                               List<WorkWarehouseTemplateSnapshot.LogEntry> logEntries) {
        if (level == null || level.isClientSide()) {
            return;
        }
        historyEntries.add(0, new ProcessManagerHistoryEntry(
                requestedProduct.copy(), requestedAmount, completionGameTime, new ArrayList<>(logEntries)));
        trimHistoryIfNeeded();
        setChanged();
        notifyUpdate();
    }

    /**
     * 按 ClothConfig 里"进程面板配置 -> 历史日志保留数"当前的设置清理多余
     * 的历史记录，从列表末尾（最老的）开始删。在归档新记录时、以及玩家
     * 打开这个进程面板的 GUI 时都会调用一次——后者是为了应对"配置数值被
     * 调小了，但期间没有新记录产生"这种情况，保证显示的数量始终符合当前
     * 配置。
     */
    public void trimHistoryIfNeeded() {
        if (level == null || level.isClientSide()) {
            return;
        }
        int max = Math.max(0, CreateImp.getConfig().templateFunctionConfig.historyLogRetentionCount);
        boolean changed = false;
        while (historyEntries.size() > max) {
            historyEntries.remove(historyEntries.size() - 1);
            changed = true;
        }
        if (changed) {
            setChanged();
            notifyUpdate();
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        historyEntries.clear();
        historyEntries.addAll(tag.contains("HistoryEntries")
                ? CatnipCodecUtils.decode(ProcessManagerHistoryEntry.CODEC.listOf(), registries,
                tag.get("HistoryEntries")).orElse(List.of())
                : List.of());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        CatnipCodecUtils.encode(ProcessManagerHistoryEntry.CODEC.listOf(), registries, historyEntries)
                .ifPresent(encoded -> tag.put("HistoryEntries", encoded));
    }
}