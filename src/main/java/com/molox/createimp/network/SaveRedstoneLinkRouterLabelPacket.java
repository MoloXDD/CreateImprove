package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.block.redstone_link_router.RedstoneLinkRouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 路由器文本终端配置界面保存频率文本时，客户端发给服务端定点更新这一个模块的文本数据位。 */
public record SaveRedstoneLinkRouterLabelPacket(
        BlockPos pos,
        int rowIndex,
        int slotIndex,
        String labelText
) implements CustomPacketPayload {

    public static final Type<SaveRedstoneLinkRouterLabelPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "save_redstone_link_router_label"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveRedstoneLinkRouterLabelPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SaveRedstoneLinkRouterLabelPacket::pos,
                    ByteBufCodecs.VAR_INT, SaveRedstoneLinkRouterLabelPacket::rowIndex,
                    ByteBufCodecs.VAR_INT, SaveRedstoneLinkRouterLabelPacket::slotIndex,
                    ByteBufCodecs.STRING_UTF8, SaveRedstoneLinkRouterLabelPacket::labelText,
                    SaveRedstoneLinkRouterLabelPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SaveRedstoneLinkRouterLabelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.level().isLoaded(packet.pos())) return;
            if (!player.canInteractWithBlock(packet.pos(), 20.0)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof RedstoneLinkRouterBlockEntity router)) return;
            router.setComponentLabelText(packet.rowIndex(), packet.slotIndex(), packet.labelText());
        });
    }
}