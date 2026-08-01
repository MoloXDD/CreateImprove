package com.molox.createimp.block.process_manager;

import com.molox.createimp.block.work_warehouse.WorkWarehouseTemplateSnapshot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 进程面板"历史请求日志"界面里的一条记录：某个工作仓库完成一次生产、
 * 回到空闲状态之前，把这次工作的完整信息打包发给了它所在网络下的这个
 * 进程面板。{@code completionGameTime} 是归档那一刻的世界时间，用于计算
 * 界面上"XX分XX秒前"；{@code logEntries} 里每条日志自带的 {@code elapsedTicks}
 * 仍然是相对那次工作自己激活时刻的经过时间，和实时进程卡片、详情界面
 * 用的是同一份数据、同一套格式，不需要额外转换。
 */
public record ProcessManagerHistoryEntry(ItemStack requestedProduct, int requestedAmount, long completionGameTime,
                                         List<WorkWarehouseTemplateSnapshot.LogEntry> logEntries,
                                         boolean logTruncated) {
    /**
     * 防御性归一化：requestedProduct 只是类型标记，真实数量由
     * requestedAmount 单独承载。原版 ItemStack.CODEC 对内部 count 字段做了
     * [1,99] 范围校验，一旦意外传入带着真实数量的物品（可能远超 99）会
     * 导致这条历史记录编码失败、进而拖累整个历史列表都存不进存档。
     */
    public ProcessManagerHistoryEntry {
        requestedProduct = requestedProduct.isEmpty() ? requestedProduct : requestedProduct.copyWithCount(1);
    }

    public static final Codec<ProcessManagerHistoryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("product").forGetter(ProcessManagerHistoryEntry::requestedProduct),
            Codec.INT.fieldOf("amount").forGetter(ProcessManagerHistoryEntry::requestedAmount),
            Codec.LONG.fieldOf("completion_time").forGetter(ProcessManagerHistoryEntry::completionGameTime),
            WorkWarehouseTemplateSnapshot.LogEntry.CODEC.listOf().fieldOf("logs").forGetter(ProcessManagerHistoryEntry::logEntries),
            Codec.BOOL.optionalFieldOf("log_truncated", false).forGetter(ProcessManagerHistoryEntry::logTruncated)
    ).apply(instance, ProcessManagerHistoryEntry::new));
}