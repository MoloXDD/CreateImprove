package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.template_panel.TemplateMaterialCalculator;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端在仓储管理员请求界面按下确认键、且请求栏内含有模板时发送，
 * 请求服务端按当前网络的权威库存和模板链结构计算一遍所需材料。
 */
public record RequestTemplateMaterialsPacket(BlockPos stockTickerPos,
                                             List<BigItemStack> itemsToOrder) implements CustomPacketPayload {

    public static final Type<RequestTemplateMaterialsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "request_template_materials"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTemplateMaterialsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestTemplateMaterialsPacket::stockTickerPos,
                    BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), RequestTemplateMaterialsPacket::itemsToOrder,
                    RequestTemplateMaterialsPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestTemplateMaterialsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Level level = player.level();
            BlockEntity be = level.getBlockEntity(packet.stockTickerPos());
            if (!(be instanceof StockTickerBlockEntity stbe) || stbe.behaviour == null) {
                return;
            }

            List<TemplateMaterialCalculator.RequestEntry> entries = new ArrayList<>();
            int templateCount = 0;
            for (BigItemStack entry : packet.itemsToOrder()) {
                if (TemplateOrderTokenHelper.isToken(entry.stack)) {
                    TemplateOrderTarget target = TemplateOrderTokenHelper.getTarget(entry.stack);
                    if (target == null) {
                        continue;
                    }
                    entries.add(TemplateMaterialCalculator.RequestEntry.ofTemplate(
                            new TemplateMaterialCalculator.OrderedTemplate(target, entry.count)));
                    templateCount++;
                } else {
                    entries.add(TemplateMaterialCalculator.RequestEntry.ofRegular(entry.stack.copy(), entry.count));
                }
            }

            TemplateMaterialCalculator.Result result =
                    TemplateMaterialCalculator.calculate(level, stbe.behaviour.freqId, entries);
            context.reply(new OpenTemplateMaterialsGuiPacket(
                    new OpenTemplateMaterialsGuiPacket.CompletionState(result.canCompleteAll(), result.anyChainBroken()),
                    result.missing(), result.usedFromStock(),
                    stbe.behaviour.freqId, templateCount,
                    new OpenTemplateMaterialsGuiPacket.RequestContext(packet.stockTickerPos(), packet.itemsToOrder())));
        });
    }
}