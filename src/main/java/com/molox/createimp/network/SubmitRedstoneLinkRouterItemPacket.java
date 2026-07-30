package com.molox.createimp.network;

import com.molox.createimp.CreateImp;
import com.molox.createimp.screen.RedstoneLinkRouterSetItemMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** JEI 把物品拖到路由器物品终端配置界面的某个槽位（0 或 1）时，客户端提交的落地结果。 */
public record SubmitRedstoneLinkRouterItemPacket(
        BlockPos pos,
        int rowIndex,
        int slotIndex,
        int ghostSlot,
        ItemStack item
) implements CustomPacketPayload {

    public static final Type<SubmitRedstoneLinkRouterItemPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateImp.MODID, "submit_redstone_link_router_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitRedstoneLinkRouterItemPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SubmitRedstoneLinkRouterItemPacket::pos,
                    ByteBufCodecs.VAR_INT, SubmitRedstoneLinkRouterItemPacket::rowIndex,
                    ByteBufCodecs.VAR_INT, SubmitRedstoneLinkRouterItemPacket::slotIndex,
                    ByteBufCodecs.VAR_INT, SubmitRedstoneLinkRouterItemPacket::ghostSlot,
                    ItemStack.OPTIONAL_STREAM_CODEC, SubmitRedstoneLinkRouterItemPacket::item,
                    SubmitRedstoneLinkRouterItemPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SubmitRedstoneLinkRouterItemPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!(player.containerMenu instanceof RedstoneLinkRouterSetItemMenu menu)) return;
            if (!menu.pos.equals(packet.pos())
                    || menu.rowIndex != packet.rowIndex()
                    || menu.slotIndex != packet.slotIndex()) return;
            menu.submitGhostItem(packet.ghostSlot(), packet.item(), player);
        });
    }
}