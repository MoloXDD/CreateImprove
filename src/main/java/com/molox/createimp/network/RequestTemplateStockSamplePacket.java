package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.item.TemplateOrderSummaryHelper;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 材料检查窗口打开期间，按固定节奏查询"这几种材料现在的网络库存数量分别是
 * 多少"，用于低开销地判断是否需要重新触发一次完整的材料计算。
 * <p>
 * 只按物流网络 UUID 查询，不依赖任何方块坐标——{@link TemplateMaterialsScreen}
 * 因此不需要关心这次材料检查究竟是由 Create 原版仓管方块、还是由某个没有
 * 方块坐标可言的第三方手持界面触发的，两者走的是完全相同的一份查询逻辑。
 * <p>
 * 【重要】样本里可能混有模板下单凭证本身（用于判断链是否已失效），这类
 * 令牌只存在于经过 {@link TemplateOrderSummaryHelper#augment} 混入过的库存
 * 汇总里，原始的 {@link LogisticsManager#getSummaryOfNetwork} 天然查不到——
 * 必须对查询用的汇总数据做同样的混入处理，否则模板令牌永远查到 0，被
 * 误判为链已失效，材料窗口会在打开的下一 tick 就被判定失效退出。
 */
public record RequestTemplateStockSamplePacket(UUID freqId, List<ItemStack> samples) implements CustomPacketPayload {

    public static final Type<RequestTemplateStockSamplePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "request_template_stock_sample"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTemplateStockSamplePacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, RequestTemplateStockSamplePacket::freqId,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), RequestTemplateStockSamplePacket::samples,
                    RequestTemplateStockSamplePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestTemplateStockSamplePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            UUID freqId = packet.freqId();
            List<Integer> counts = new ArrayList<>(packet.samples().size());
            boolean allowed = freqId != null && Create.LOGISTICS.mayInteract(freqId, player);
            if (allowed) {
                InventorySummary summary = TemplateOrderSummaryHelper.augment(
                        LogisticsManager.getSummaryOfNetwork(freqId, false), freqId);
                for (ItemStack sample : packet.samples()) {
                    counts.add(summary.getCountOf(sample));
                }
            } else {
                for (int i = 0; i < packet.samples().size(); i++) {
                    counts.add(0);
                }
            }
            context.reply(new TemplateStockSampleResultPacket(counts));
        });
    }
}