package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.simibubi.create.content.logistics.BigItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * 携带"模板所需材料"计算结果，以及校验工作仓库数量、重新发起计算所需的
 * 全部上下文，触发客户端打开（或原地刷新）次级材料窗口。
 * <p>
 * 注意：{@link StreamCodec#composite} 最多只支持 6 个字段，因此把
 * "仓管坐标 + 原始请求栏内容"合并进 {@link RequestContext}，
 * "能否完成 + 链是否已失效"合并进 {@link CompletionState}，
 * 各自作为一个整体字段传输，避免超出参数上限。
 */
public record OpenTemplateMaterialsGuiPacket(CompletionState completionState,
                                             List<BigItemStack> missing,
                                             List<BigItemStack> usedFromStock,
                                             UUID freqId,
                                             int templateCount,
                                             RequestContext requestContext) implements CustomPacketPayload {

    public record RequestContext(BlockPos stockTickerPos, List<BigItemStack> itemsToOrder) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestContext> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, RequestContext::stockTickerPos,
                        BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), RequestContext::itemsToOrder,
                        RequestContext::new
                );
    }

    /**
     * @param canCompleteAll 材料是否足够（缺少材料列表是否为空）
     * @param anyChainBroken 本次请求里是否有任意一个模板的链已经失效
     *                       （仪表被拆除、所在区块卸载、连接/地址被清空等）。
     *                       为 true 时客户端不应该展示/刷新材料窗口，而是
     *                       直接退回仓管界面并清空请求栏。
     */
    public record CompletionState(boolean canCompleteAll, boolean anyChainBroken) {
        public static final StreamCodec<RegistryFriendlyByteBuf, CompletionState> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, CompletionState::canCompleteAll,
                        ByteBufCodecs.BOOL, CompletionState::anyChainBroken,
                        CompletionState::new
                );
    }

    public static final Type<OpenTemplateMaterialsGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "open_template_materials_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTemplateMaterialsGuiPacket> STREAM_CODEC =
            StreamCodec.composite(
                    CompletionState.STREAM_CODEC, OpenTemplateMaterialsGuiPacket::completionState,
                    BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenTemplateMaterialsGuiPacket::missing,
                    BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenTemplateMaterialsGuiPacket::usedFromStock,
                    UUIDUtil.STREAM_CODEC, OpenTemplateMaterialsGuiPacket::freqId,
                    ByteBufCodecs.VAR_INT, OpenTemplateMaterialsGuiPacket::templateCount,
                    RequestContext.STREAM_CODEC, OpenTemplateMaterialsGuiPacket::requestContext,
                    OpenTemplateMaterialsGuiPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}