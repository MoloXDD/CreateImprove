package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.template_panel.TemplateMaterialCalculator;
import com.molox.createimp.item.TemplateOrderTarget;
import com.molox.createimp.item.TemplateOrderTokenHelper;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 客户端在仓储管理员请求界面（或任何其他承载模板下单的界面，例如第三方
 * 模组提供的手持式发报机）按下确认键、且请求栏内含有模板时发送，请求服务端
 * 按当前物流网络的权威库存和模板链结构计算一遍所需材料。
 * <p>
 * 以物流网络 UUID（{@code freqId}）为唯一锚点，不再要求发起方必须是"某个
 * 方块坐标上的仓储发报机"——这样任何能够合法查看该网络库存的界面（不论是
 * Create 原版仓管方块，还是没有方块坐标可言的手持式界面）都能复用同一套
 * 材料计算与展示逻辑，不需要各自实现一份。
 */
public record RequestTemplateMaterialsPacket(UUID freqId,
                                             List<BigItemStack> itemsToOrder) implements CustomPacketPayload {

    public static final Type<RequestTemplateMaterialsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "request_template_materials"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestTemplateMaterialsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, RequestTemplateMaterialsPacket::freqId,
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
            UUID freqId = packet.freqId();
            boolean allowed = freqId != null && Create.LOGISTICS.mayInteract(freqId, player);
            if (!allowed) {
                CreateImp.LOGGER.info(
                        "[模板材料] 材料计算请求被拒绝：玩家={}, 网络={}, freqId为空={}",
                        player.getGameProfile().getName(), freqId, freqId == null);
                return;
            }
            Level level = player.level();

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
                    TemplateMaterialCalculator.calculate(level, freqId, entries);
            CreateImp.LOGGER.info(
                    "[模板材料] 材料计算完成：玩家={}, 网络={}, 模板数={}, 能否完全满足={}, 链是否失效={}, 缺少材料种类数={}, 现有材料种类数={}",
                    player.getGameProfile().getName(), freqId, templateCount,
                    result.canCompleteAll(), result.anyChainBroken(),
                    result.missing().size(), result.usedFromStock().size());
            context.reply(new OpenTemplateMaterialsGuiPacket(
                    new OpenTemplateMaterialsGuiPacket.CompletionState(result.canCompleteAll(), result.anyChainBroken()),
                    result.missing(), result.usedFromStock(),
                    freqId, templateCount,
                    new OpenTemplateMaterialsGuiPacket.RequestContext(packet.itemsToOrder())));
        });
    }
}